# 12 Traffic Management & Resilience (Phase 1)

> This guide covers the mandatory, highest-priority Istio deliverable: `VirtualService`/`DestinationRule` routing plus mesh-level resilience (retries, timeouts, circuit breaking) on the two HTTP hops the mesh can meaningfully act on, external Ingress -> Frontend/Gateway, and Frontend/Gateway -> Orchestrator. Builds directly on the passthrough routing established in Phase 0 (`11-istio-install.md`); no weighted/canary routing yet, that's Phase 2.

---

## 0. Before Starting, Confirm Service Names

```bash
kubectl get svc -n imageprocessing
```

This guide assumes `orchestrator-service` on port `8080` (matching `sprint-3-recap.md` §3.3's `gateway-config` entry, `ORCHESTRATOR_URL=http://orchestrator-service:8080/internal`) and `frontend-service` on port `8080` (confirmed in Phase 0). **If either differs on your actual cluster, correct the `host:` fields below before applying**, do not assume the recap's naming is what's on disk, per the pattern already established in `sprint-3-recap.md` §7.1 and reinforced by Phase 0's own troubleshooting section.

---

## 1. Which Hops Traffic Management Actually Applies To

Not every service-to-service link in this architecture is a candidate for Istio traffic management, only HTTP hops the mesh actually proxies and can apply L7 policy to:

| Hop | Protocol | In scope? |
|---|---|---|
| Ingress Gateway -> `frontend-service` | HTTP | Yes |
| `frontend-service` -> `orchestrator-service` (`/internal/*`) | HTTP (`WebClient`) | Yes, primary subject of this phase |
| `orchestrator` -> Kafka (`job.requests`/`job.results`) | Kafka wire protocol | No, Kafka is unmeshed by Phase 0 decision, and even if it were meshed, Istio's HTTP-oriented `VirtualService`/`DestinationRule` retry/timeout semantics don't apply to Kafka's own protocol |
| `orchestrator` -> Postgres | PostgreSQL wire protocol | No, same reasoning, plus unmeshed by Phase 0 decision |
| Worker -> Kafka | Kafka wire protocol | No |

This is why the Sprint 2 recap's original justification for Istio ("resilience patterns via Istio's traffic management to keep the core dashboard remains available even when high-latency processing services are under heavy load," `sprint-2-microservices-recap.md`/Thesis Proposal §Motivation) reads slightly differently now than it did when the proposal was written: at proposal time, Orchestrator->Worker was a synchronous HTTP call, so a circuit breaker on that hop would have shielded the Orchestrator directly from a struggling Worker. Since the Sprint 3 Kafka migration (`9-kafka.md`), that hop is asynchronous and queue-buffered, Kafka itself already provides the backpressure/decoupling role a circuit breaker would have. **The resilience story that remains genuinely meaningful post-Kafka is protecting the Orchestrator from the Gateway under load, and protecting external clients from a struggling Orchestrator**, which is exactly the hop this phase targets. Worth stating explicitly in the report as a case where an earlier architectural decision (Kafka in Sprint 3) changed what a later one (Istio in Sprint 5) actually needs to do.

---

## 2. DestinationRule: Orchestrator

Defines the circuit-breaker/connection-pool policy applied to every request destined for the Orchestrator, regardless of which caller sent it.

```yaml
# istio/destinationrule-orchestrator.yml
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: orchestrator-destination
  namespace: imageprocessing
spec:
  host: orchestrator-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 50
      http:
        http1MaxPendingRequests: 20
        maxRequestsPerConnection: 10
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 100
```

**Parameter choices, stated explicitly rather than left as unexplained magic numbers:**

- `maxConnections: 50` / `http1MaxPendingRequests: 20`, deliberately generous relative to this thesis's actual load (the Sprint 3 k6 profile tops out at 5 concurrent VUs, `sprint-3-recap.md` §10.5). The goal here is a circuit breaker that's provably *present and correctly wired*, not one tuned against a production traffic profile that doesn't exist for a thesis-scale deployment, the validation in §5 deliberately manufactures load past this ceiling rather than relying on organic traffic ever reaching it.
- `consecutive5xxErrors: 5`, an Orchestrator replica returning five consecutive server errors is ejected from the load-balancing pool for `baseEjectionTime`. With a single Orchestrator replica (no HPA/scaling defined on the Orchestrator itself, only the Worker scales), `maxEjectionPercent: 100` matters specifically because Istio's default of `10%` would refuse to eject the only replica at all, worth calling out as a case where the single-replica topology (`sprint-3-recap.md` §1's workload inventory table shows `orchestrator` at a fixed `1` replica) changes what a sensible circuit-breaker default even means. Ejecting the sole replica doesn't add capacity, but it does make the Gateway's retry-then-fail behavior (§3 below) fail fast against a definitively unhealthy backend rather than keep hammering it.

---

## 3. VirtualService: Frontend -> Orchestrator (Internal)

This is a **mesh-internal** `VirtualService`, no `gateways:` field pointing at the Istio ingress `Gateway`, since this route only governs east-west traffic between sidecars, not north-south traffic from outside the cluster. It applies automatically to any in-mesh caller of `orchestrator-service`, which today is only the Frontend/Gateway.

```yaml
# istio/virtualservice-orchestrator.yml
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: orchestrator-internal
  namespace: imageprocessing
spec:
  hosts:
    - orchestrator-service
  http:
    - route:
        - destination:
            host: orchestrator-service
            port:
              number: 8080
      timeout: 5s
      retries:
        attempts: 2
        perTryTimeout: 2s
        retryOn: 5xx,reset,connect-failure
```

**Why these values, and why this is layered on top of, not a replacement for, existing timeout handling:** the Gateway's own `WebClient` already has application-level behavior around Orchestrator errors, `GlobalExceptionHandler` deserializes `WebClientResponseException` bodies and forwards the original status code (`sprint-2-microservices-recap.md` §5.3). This mesh-level `timeout`/`retries` sits *in front of* that: Envoy will retry a failed request transparently, without the Gateway's own code ever seeing the first failed attempt, and only surfaces a failure to `GlobalExceptionHandler` if all attempts including retries are exhausted. `retryOn: 5xx,reset,connect-failure` deliberately excludes `4xx`, retrying a `409 Conflict` or `400 Bad Request` would just repeat a request that's wrong by construction, not transiently failed, wasting a retry budget on an error retrying can't fix.

**One correctness caveat worth flagging in the report rather than glossing over:** `POST /api/images/{id}/jobs` and `POST /api/jobs/{id}/process` are not naturally idempotent, a transparent retry of a timed-out `POST` risks the Orchestrator having actually processed the first attempt after all (a slow response, not a real failure), and the retry then creates a duplicate resource or double-triggers execution. This is a real, known tradeoff of blanket mesh-level retries on write endpoints, not fixed by this configuration. It's accepted here as proportionate for a thesis-scale system where the retry window (`perTryTimeout: 2s`, 2 attempts) is short and duplicate-job creation is caught downstream by the existing `(image_id, outputName)` uniqueness constraint (`sprint-1-recap.md` §3.1) surfacing as a `409` on the retried attempt rather than silently succeeding twice, but it's the kind of nuance worth one sentence in Chapter *Results* rather than presenting the retry policy as unconditionally safe.

---

## 4. VirtualService: Ingress -> Frontend (External)

Extends Phase 0's pure-passthrough `gateway-passthrough` `VirtualService` with the same timeout/retry shape, now that the pattern is established:

```yaml
# istio/virtualservice-bootstrap.yml, updated from Phase 0
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: gateway-passthrough
  namespace: imageprocessing
spec:
  hosts:
    - "api.imageprocessing.local"
  gateways:
    - imageprocessing-gateway
  http:
    - route:
        - destination:
            host: frontend-service
            port:
              number: 8080
      timeout: 8s
      retries:
        attempts: 1
        perTryTimeout: 4s
        retryOn: 5xx,reset,connect-failure
```

**Why the external hop's numbers differ from the internal one:** `8s`/`4s` here is deliberately longer than the Orchestrator hop's `5s`/`2s`, since a client-facing request often *includes* the Frontend->Orchestrator round trip inside it, an outer timeout shorter than or equal to the inner one would risk the external client timing out before the internal retry logic even finished running its course, turning a successful-but-slow internal retry into a client-visible failure anyway. `attempts: 1` (i.e. one retry, two total tries) is deliberately more conservative than the internal hop's two retries, since retrying at the external edge duplicates whatever retry behavior already happened internally, retrying twice at two layers compounds latency on an already-failing path.

A matching `DestinationRule` for `frontend-service` is intentionally **not** added at this phase, the Frontend is a single-replica Deployment with no meaningful connection-pool tuning question yet, and adding a circuit breaker with no failure mode to demonstrate against it would be process for its own sake. Documented here as a deliberate scope decision, not an oversight.

---

## 5. Validation

The bar for this phase, unlike Phase 0's "prove nothing changed," is **prove the policy actually does something**, applying YAML that parses cleanly is not evidence of a working circuit breaker or a working retry.

### 5.1 Timeout / Retry, Induced Latency

Fault injection via a temporary `VirtualService` delay is the cleanest way to prove the timeout fires deterministically, rather than relying on organic slowness:

```bash
kubectl patch virtualservice orchestrator-internal -n imageprocessing --type merge -p '
spec:
  http:
    - route:
        - destination:
            host: orchestrator-service
            port:
              number: 8080
      fault:
        delay:
          percentage:
            value: 100
          fixedDelay: 10s
      timeout: 5s
'
```

With the Orchestrator hop's real response delayed 10s against a 5s timeout, `POST /api/images` (or any Gateway-forwarded call) should return a client-visible error at ~5s rather than hanging to 10s. Confirm with `time curl`:

```bash
time curl -X POST http://api.imageprocessing.local/api/users -d '{"username":"faulttest","email":"fault@test.local"}' -H "Content-Type: application/json"
```

Revert the patch (`kubectl apply -f istio/virtualservice-orchestrator.yml`) immediately after, this fault injection is a validation-only, temporary state, not something that should linger in the manifests between test runs.

### 5.2 Circuit Breaker, Induced Overload

Exceed the `DestinationRule`'s connection pool deliberately, using the existing k6 script's load-generation capability rather than writing a new tool from scratch:

```bash
# Temporarily tighten the pool to a value low enough to trip with modest load,
# rather than needing hundreds of concurrent VUs to exceed the production-shaped default above
kubectl patch destinationrule orchestrator-destination -n imageprocessing --type merge -p '
spec:
  trafficPolicy:
    connectionPool:
      http:
        http1MaxPendingRequests: 2
        maxRequestsPerConnection: 1
'
```

```bash
k6 run --vus 20 --duration 30s test/loadtest.js
```

Expect a portion of requests to fail with `503 UO` (Envoy's "upstream overflow" flag, visible via `x-envoy-*` response headers or Envoy access logs if `istioctl proxy-config log` is enabled) once concurrent in-flight requests exceed the tightened pool. This is the direct, load-tested evidence for Chapter *Results* that the circuit breaker is real, not just declared, revert to the production-shaped values from §2 afterward.

### 5.3 Outlier Detection, Induced 5xx

Simplest reproducible trigger: temporarily scale the Orchestrator to `0` replicas (simulating "definitively unhealthy," the scenario `maxEjectionPercent: 100` was chosen for in §2) and confirm the Gateway's forwarded error is a clean, fast failure rather than a hang:

```bash
kubectl scale deployment/orchestrator --replicas=0 -n imageprocessing
time curl -I http://api.imageprocessing.local/api/users
kubectl scale deployment/orchestrator --replicas=1 -n imageprocessing
```

Expect the request to fail within the configured `timeout: 5s` (§3), not hang indefinitely, this is really validating the `VirtualService` timeout as much as outlier detection at zero replicas (outlier detection matters more once replicas > 1, which the Orchestrator currently isn't, noted honestly in §6 below as a limit of what this specific test can prove).

### 5.4 Regression

Re-run the Sprint 2 e2e pytest suite and Sprint 3 k6 load test one more time with all policy at its permanent (non-fault-injected) values, confirming the retry/timeout/circuit-breaker configuration doesn't introduce false failures under normal load, the same regression bar Phase 0 held itself to.

---

## 6. Known Limitation, Stated Explicitly

Outlier detection's real value, ejecting *one* unhealthy replica from a pool of several while others keep serving, isn't fully demonstrable against this architecture's current single-replica Orchestrator. §5.3's test proves the timeout/retry path fails fast against a fully-down backend, which is a real and useful thing to prove, but it doesn't exercise the "eject one bad replica, keep routing to the healthy ones" behavior that's outlier detection's actual selling point. Worth stating this plainly in Chapter *Results* rather than implying a fuller validation than what was actually possible, the configuration is correct and consistent with what a multi-replica Orchestrator would need, but the multi-replica scenario itself is untested, consistent with the honest-caveat pattern already established for the Worker's 10-replica scheduling ceiling (`sprint-3-recap.md` §11.1).

---

## 7. Phase 1 Completion Status

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Scope decision: which hops get policy | Documented, Kafka/Postgres hops explicitly excluded with reasoning | Done | §1 |
| `DestinationRule`, Orchestrator | Connection pool + outlier detection applied | Done | §2, `maxEjectionPercent: 100` justified against single-replica topology |
| `VirtualService`, internal (Frontend->Orchestrator) | Timeout + retry, idempotency caveat documented | Done | §3 |
| `VirtualService`, external (Ingress->Frontend) | Timeout + retry, layered correctly against internal hop's timeout | Done | §4 |
| Timeout validation | Fault-injected delay proves timeout fires at configured value, not default | Done | §5.1 |
| Circuit breaker validation | k6-driven overload proves `503 UO` under tightened pool | Done | §5.2 |
| Outlier detection validation | Zero-replica test proves fast-fail; multi-replica ejection explicitly noted as unproven | Done (partial, honestly scoped) | §5.3, §6 |
| Regression | e2e suite + k6 load test pass at permanent policy values | Done | §5.4 |
