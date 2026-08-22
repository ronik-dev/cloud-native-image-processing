# 12 Traffic Management & Resilience (Phase 1)

> This guide covers the mandatory, highest-priority Istio deliverable: `VirtualService`/`DestinationRule` routing plus mesh-level resilience (retries, timeouts, circuit breaking) on the two HTTP hops the mesh can meaningfully act on — external Ingress → Frontend/Gateway, and Frontend/Gateway → Orchestrator. Builds directly on the passthrough routing established in Phase 0 (`11-istio-install.md`); no weighted/canary routing yet — that's Phase 2.

---

## 0. Before Starting — Confirm Service Names

```bash
kubectl get svc -n imageprocessing
```

This guide assumes `orchestrator-service` on port `8080` (matching `sprint-3-recap.md` §3.3's `gateway-config` entry, `ORCHESTRATOR_URL=http://orchestrator-service:8080/internal`) and `frontend-service` on port `8080` (confirmed in Phase 0). **If either differs on your actual cluster, correct the `host:` fields below before applying** — do not assume the recap's naming is what's on disk, per the pattern already established in `sprint-3-recap.md` §7.1 and reinforced by Phase 0's own troubleshooting section.

---

## 1. Which Hops Traffic Management Actually Applies To

Not every service-to-service link in this architecture is a candidate for Istio traffic management — only HTTP hops the mesh actually proxies and can apply L7 policy to:

| Hop | Protocol | In scope? |
|---|---|---|
| Ingress Gateway → `frontend-service` | HTTP | Yes |
| `frontend-service` → `orchestrator-service` (`/internal/*`) | HTTP (`WebClient`) | Yes — primary subject of this phase |
| `orchestrator` → Kafka (`job.requests`/`job.results`) | Kafka wire protocol | No — Kafka is unmeshed by Phase 0 decision, and even if it were meshed, Istio's HTTP-oriented `VirtualService`/`DestinationRule` retry/timeout semantics don't apply to Kafka's own protocol |
| `orchestrator` → Postgres | PostgreSQL wire protocol | No — same reasoning, plus unmeshed by Phase 0 decision |
| Worker → Kafka | Kafka wire protocol | No |

This is why the Sprint 2 recap's original justification for Istio ("resilience patterns via Istio's traffic management to keep the core dashboard remains available even when high-latency processing services are under heavy load," `sprint-2-microservices-recap.md`/Thesis Proposal §Motivation) reads slightly differently now than it did when the proposal was written: at proposal time, Orchestrator→Worker was a synchronous HTTP call, so a circuit breaker on that hop would have shielded the Orchestrator directly from a struggling Worker. Since the Sprint 3 Kafka migration (`9-kafka.md`), that hop is asynchronous and queue-buffered — Kafka itself already provides the backpressure/decoupling role a circuit breaker would have. **The resilience story that remains genuinely meaningful post-Kafka is protecting the Orchestrator from the Gateway under load, and protecting external clients from a struggling Orchestrator**, which is exactly the hop this phase targets. Worth stating explicitly in the report as a case where an earlier architectural decision (Kafka in Sprint 3) changed what a later one (Istio in Sprint 5) actually needs to do.

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

- `maxConnections: 50` / `http1MaxPendingRequests: 20` — deliberately generous relative to this thesis's actual load (the Sprint 3 k6 profile tops out at 5 concurrent VUs, `sprint-3-recap.md` §10.5). The goal here is a circuit breaker that's provably *present and correctly wired*, not one tuned against a production traffic profile that doesn't exist for a thesis-scale deployment — the validation in §5 deliberately manufactures load past this ceiling rather than relying on organic traffic ever reaching it.
- `consecutive5xxErrors: 5` — an Orchestrator replica returning five consecutive server errors is ejected from the load-balancing pool for `baseEjectionTime`. With a single Orchestrator replica (no HPA/scaling defined on the Orchestrator itself, only the Worker scales), `maxEjectionPercent: 100` matters specifically because Istio's default of `10%` would refuse to eject the only replica at all — worth calling out as a case where the single-replica topology (`sprint-3-recap.md` §1's workload inventory table shows `orchestrator` at a fixed `1` replica) changes what a sensible circuit-breaker default even means. Ejecting the sole replica doesn't add capacity, but it does make the Gateway's retry-then-fail behavior (§3 below) fail fast against a definitively unhealthy backend rather than keep hammering it.

---

## 3. VirtualService: Frontend → Orchestrator (Internal)

This is a **mesh-internal** `VirtualService` — no `gateways:` field pointing at the Istio ingress `Gateway`, since this route only governs east-west traffic between sidecars, not north-south traffic from outside the cluster. It applies automatically to any in-mesh caller of `orchestrator-service`, which today is only the Frontend/Gateway.

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

**Why these values, and why this is layered on top of, not a replacement for, existing timeout handling:** the Gateway's own `WebClient` already has application-level behavior around Orchestrator errors — `GlobalExceptionHandler` deserializes `WebClientResponseException` bodies and forwards the original status code (`sprint-2-microservices-recap.md` §5.3). This mesh-level `timeout`/`retries` sits *in front of* that: Envoy will retry a failed request transparently, without the Gateway's own code ever seeing the first failed attempt, and only surfaces a failure to `GlobalExceptionHandler` if all attempts including retries are exhausted. `retryOn: 5xx,reset,connect-failure` deliberately excludes `4xx` — retrying a `409 Conflict` or `400 Bad Request` would just repeat a request that's wrong by construction, not transiently failed, wasting a retry budget on an error retrying can't fix.

**One correctness caveat worth flagging in the report rather than glossing over:** `POST /api/images/{id}/jobs` and `POST /api/jobs/{id}/process` are not naturally idempotent — a transparent retry of a timed-out `POST` risks the Orchestrator having actually processed the first attempt after all (a slow response, not a real failure), and the retry then creates a duplicate resource or double-triggers execution. This is a real, known tradeoff of blanket mesh-level retries on write endpoints, not fixed by this configuration. It's accepted here as proportionate for a thesis-scale system where the retry window (`perTryTimeout: 2s`, 2 attempts) is short and duplicate-job creation is caught downstream by the existing `(image_id, outputName)` uniqueness constraint (`sprint-1-recap.md` §3.1) surfacing as a `409` on the retried attempt rather than silently succeeding twice — but it's the kind of nuance worth one sentence in Chapter *Results* rather than presenting the retry policy as unconditionally safe.

---

## 4. VirtualService: Ingress → Frontend (External)

Extends Phase 0's pure-passthrough `gateway-passthrough` `VirtualService` with the same timeout/retry shape, now that the pattern is established:

```yaml
# istio/virtualservice-bootstrap.yml — updated from Phase 0
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

**Why the external hop's numbers differ from the internal one:** `8s`/`4s` here is deliberately longer than the Orchestrator hop's `5s`/`2s`, since a client-facing request often *includes* the Frontend→Orchestrator round trip inside it — an outer timeout shorter than or equal to the inner one would risk the external client timing out before the internal retry logic even finished running its course, turning a successful-but-slow internal retry into a client-visible failure anyway. `attempts: 1` (i.e. one retry, two total tries) is deliberately more conservative than the internal hop's two retries, since retrying at the external edge duplicates whatever retry behavior already happened internally — retrying twice at two layers compounds latency on an already-failing path.

A matching `DestinationRule` for `frontend-service` is intentionally **not** added at this phase — the Frontend is a single-replica Deployment with no meaningful connection-pool tuning question yet, and adding a circuit breaker with no failure mode to demonstrate against it would be process for its own sake. Documented here as a deliberate scope decision, not an oversight.

---

**Testing method note:** the validation commands throughout §5 use a throwaway in-mesh pod (`kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- ...`) calling `orchestrator-service` directly, rather than routing through `api.imageprocessing.local`. This is deliberate, not a shortcut: `/api/*` on the Frontend is behind Sprint 4's OIDC security filter (`.oauth2Login()` on all routes except `/actuator/health`), so an unauthenticated `curl` against the public host returns a `302` to Keycloak before ever reaching the Orchestrator — confirmed directly during this phase's testing when an initial attempt against `/api/users` returned a near-instant `302` regardless of fault-injection state, which was initially mistaken for the fault injection not working before the actual cause (auth redirect, not mesh misconfiguration) was isolated. Testing directly against `orchestrator-service` from an in-mesh pod isolates exactly the thing this phase validates — the `orchestrator-internal` VirtualService/DestinationRule — without Sprint 4's authentication layer as a confound. The target endpoint is `/internal/actuator/health` specifically because it has no business logic to fail on independently (an earlier attempt against `/internal/users` returned a real `500` from `UserController`'s own validation logic, which was a legitimate application response but told us nothing about Istio).

## 5. Validation

The bar for this phase, unlike Phase 0's "prove nothing changed," is **prove the policy actually does something** — applying YAML that parses cleanly is not evidence of a working circuit breaker or a working retry.

### 5.1 Fault Injection and Timeout — A Documented Interaction Worth Knowing Before Testing

**Finding, verified empirically before writing this section:** applying `fault.delay` and `timeout` on the *same* `VirtualService` HTTP route does not test what it appears to. Multiple independent sources confirm this is documented, expected Envoy/Istio behavior, not a misconfiguration on this project's part: when fault injection is present on a route, Istio does not apply that same route's timeout or retry policy. The official Istio request-timeout tutorial itself sidesteps this by injecting the delay on one service's `VirtualService` (the callee, e.g. `ratings`) and configuring the timeout on a *different* service's `VirtualService` (the caller, e.g. `reviews`) — two separate hops, two separate route configs, so the interaction never occurs. This architecture only has one HTTP hop of interest (Frontend → Orchestrator), so the naive "delay 10s against a 5s timeout" test recreates exactly the combination the docs warn against.

**This was discovered, not assumed**, via the following test:

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
```bash
kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "time curl -i http://orchestrator-service:8080/internal/actuator/health"
```
Result: `HTTP/1.1 200 OK`, `real 0m10.03s` — the full 10s delay elapsed and the request *succeeded*, meaning the 5s timeout was silently not enforced on this route. This matches the documented interaction exactly, and is treated here as a genuine finding worth citing, not a broken test to quietly redo and forget.

**Consequence for how this phase validates timeout, split into two independent proofs — with an honest gap acknowledged rather than papered over:**

**(a) Fail-fast behavior against a fully-down backend — proven, but via a different mechanism than the configured `timeout`.** §5.3's zero-replica test produces an actual connection failure at the destination, with no fault filter involved at all, so the delay/timeout interaction above does not apply to it. However, as detailed in §5.3, that test fails in `~0.01s` — via Envoy's endpoint-discovery layer immediately recognizing zero available endpoints, not via the `timeout: 5s` timer elapsing. It is real, useful evidence that the mesh degrades gracefully, but it is not direct evidence that the configured timeout value specifically is enforced.

**(b) Fault-delay injection itself is real — proven with delay alone, no timeout on the same route to interact with.**

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
          fixedDelay: 3s
'
```
```bash
kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "time curl -i http://orchestrator-service:8080/internal/actuator/health"
```

**Observed result:**
```
HTTP/1.1 200 OK
x-envoy-upstream-service-time: 6
real	0m 3.01s
```

`3.01s` wall-clock against a `3s` configured delay, `200 OK` body intact — clean confirmation that Envoy is genuinely intercepting and delaying the request by the configured amount. This, together with (a), is the evidence this phase has for the resilience configuration's mechanics — with the specific "timeout cuts off a slow-but-alive backend" scenario left as an acknowledged, explicitly-documented gap rather than asserted without evidence (§6).

Revert the fault patch immediately after testing:
```bash
kubectl apply -f istio/virtualservice-orchestrator.yml
```

### 5.2 Circuit Breaker — Induced Overload

**Method correction, discovered during testing:** the original plan called for driving load with the existing `k6` script (`test/loadtest.js`) from the host machine. Two independent problems ruled this out:

1. **The script is broken against the live system.** `loadtest.js`'s `setup()` calls `POST /api/users` through the public Gateway, which has been behind Sprint 4's `.oauth2Login()` security filter since Keycloak was introduced. The script has no OIDC client and cannot complete a browser-based Authorization Code flow, so its user-creation call receives a `302` redirect instead of the expected `201`, and `setup()` fails outright (`checks_failed: 100%`, followed by a `TypeError` when the script tries to use a user ID that was never returned). This is a real, standalone regression worth flagging in Chapter *Results*: the Sprint 3 k6 load test has been silently incompatible with the system since Sprint 4's auth layer landed, and re-authoring it to complete an OIDC login (or accept a pre-fetched session cookie) is out of scope for this validation.
2. **More fundamentally, even a working k6 run from the host machine would not have tested the right thing.** A `DestinationRule`'s connection-pool and outlier-detection policy is enforced by the *caller's own outbound Envoy sidecar*, not the destination's inbound one. `k6` running on the host machine has no sidecar at all — it sits entirely outside the mesh — so Istio's circuit-breaker configuration is never consulted for its requests, regardless of load volume. Any result from a host-side load generator would not be evidence of the `DestinationRule` working, coincidentally passing or not.

**Corrected method:** generate concurrent load from *inside* the mesh, using the same throwaway sidecar-injected pod pattern established in §5.1, so the requests actually flow through a client-side Envoy proxy that evaluates the `orchestrator-destination` policy:

```bash
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
kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "{ for i in \$(seq 1 60); do curl -s -o /dev/null -w '%{http_code}\n' http://orchestrator-service:8080/internal/actuator/health & done; wait; } | sort | uniq -c"
```

**Observed result:**
```
     10 200
     50 503
```

50 of 60 concurrent requests rejected with `503` against the tightened pool (`http1MaxPendingRequests: 2`, `maxRequestsPerConnection: 1`) — direct, load-tested confirmation that the `DestinationRule`'s connection-pool limits are real and enforced, not just declared. Confirmed further by checking for Envoy's explicit overload marker on the rejected responses:

```bash
kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "{ for i in \$(seq 1 60); do curl -s -D - -o /dev/null http://orchestrator-service:8080/internal/actuator/health & done; wait; } | grep -E 'HTTP|x-envoy'"
```

**Confirmed:** every `503` response in the run carried `x-envoy-overloaded: true`, and every `200` carried a real `x-envoy-upstream-service-time` (values observed: 3–24ms). This rules out the alternative explanation that the `503`s were coincidental application-level errors from the Orchestrator itself — `x-envoy-overloaded` is Envoy's specific marker for a request rejected by local circuit-breaking (connection pool / pending-request limits), set before the request ever reaches the upstream cluster. The clean 1:1 correspondence (every `503` tagged, every `200` genuinely served) is the strongest available evidence that §2's `DestinationRule` is doing exactly what it's configured to do.

Revert to the production-shaped pool values from §2 immediately after:
```bash
kubectl apply -f istio/destinationrule-orchestrator.yml
```

### 5.3 Zero-Replica Failure Mode — What It Actually Proves

**Correction made during testing, stated explicitly rather than left implicit:** this test was originally framed as validating outlier detection (§2) and/or the `VirtualService` timeout (§3) against a fully-down backend. The observed result shows it validates neither of those specifically — it demonstrates a third, distinct mesh property.

```bash
kubectl scale deployment/orchestrator --replicas=0 -n imageprocessing

kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "time curl -i http://orchestrator-service:8080/internal/actuator/health"

kubectl scale deployment/orchestrator --replicas=1 -n imageprocessing
```

**Observed result:**
```
HTTP/1.1 503 Service Unavailable
content-type: text/plain
no healthy upstream
real	0m 0.01s
```

The request failed in `0.01s` — not after the 5s configured `timeout`, and not after `outlierDetection`'s `interval`/`baseEjectionTime` windows. This is because, with **zero** endpoints behind `orchestrator-service` at all, Envoy's endpoint-discovery layer already knows there is nothing to route to and rejects the request immediately, before either the route-level timeout timer or the `DestinationRule`'s outlier-ejection logic is ever invoked. This specific failure mode (`no healthy upstream`, near-instant `503`) would occur identically even with no Istio traffic-management or resilience policy configured whatsoever — it is baseline Envoy/Kubernetes-Service behavior reacting to an empty endpoint list, not evidence that §2's `outlierDetection` or §3's `timeout` did anything.

**What this test does legitimately demonstrate:** the mesh degrades gracefully and fails fast, with a clear diagnostic body (`no healthy upstream`), when a destination has no available replicas — rather than hanging indefinitely or returning a confusing generic error. That is a real and worth-reporting property, just not the property this test was originally designed to isolate.

**Consequence for validating outlier detection specifically:** outlier detection's actual mechanism — ejecting *one* consistently-failing replica from a pool where *other* replicas remain healthy — cannot be exercised by a zero-replica test at all, regardless of how the test is run, since there is no pool to eject from. Demonstrating it properly would require the Orchestrator running at ≥2 replicas, with one replica made to fail (`consecutive5xxErrors`) while the other keeps serving. This is not attempted in this phase: the Orchestrator is a fixed single-replica Deployment throughout this thesis (`sprint-3-recap.md` §1's workload inventory), and artificially scaling it to a second replica purely to validate one Istio setting, then scaling back down, was judged out of proportion for what this phase needs to prove. This is stated as an explicit, acknowledged gap rather than glossed over — see §6.

### 5.4 Regression

Re-run the Sprint 2 e2e pytest suite one more time with all policy at its permanent (non-fault-injected, non-tightened) values, confirming the retry/timeout/circuit-breaker configuration doesn't introduce false failures under normal load. **The Sprint 3 k6 load test (`test/loadtest.js`) cannot serve this role until it's updated to authenticate through Keycloak** (§5.2) — this is now a tracked follow-up rather than an assumed-working regression check, and should not be silently treated as passing just because it predates this phase.

Confirm before closing this phase that every temporary patch applied during §5.1/§5.2/§5.3 has been reverted to the permanent manifests:
```bash
kubectl get virtualservice,destinationrule -n imageprocessing -o yaml
```
Check specifically that `orchestrator-internal` shows no `fault:` block, and `orchestrator-destination` shows the production connection-pool values from §2 (`http1MaxPendingRequests: 20`, `maxRequestsPerConnection: 10`), not the tightened test values from §5.2.

---

## 6. Known Limitations, Stated Explicitly

**Timeout enforcement against a genuinely slow-but-alive backend was not independently isolated.** This phase directly demonstrates two adjacent things — that Envoy can inject a synthetic delay (§5.1b, `3.01s` observed against a `3s` configured delay) and that the mesh fails fast with a clear diagnostic when a destination has zero available endpoints (§5.3, `0.01s`, `no healthy upstream`) — but not the specific scenario the `timeout: 5s` field in §3 is meant to guard against: a backend that is up, responding, but slower than the configured limit. Two independent obstacles prevented a direct test:

- Combining `fault.delay` and `timeout` on the same `VirtualService` route is a documented Envoy/Istio interaction where the timeout is silently not applied to that route (§5.1) — confirmed empirically during this phase (a `10s` delay against a `5s` timeout completed successfully at `10.03s`, not failing at `5s`).
- The textbook workaround (Istio's own Bookinfo tutorial) places the delay and the timeout on two different service hops — the delay on the callee's `VirtualService`, the timeout on a different, upstream caller's `VirtualService` — which works because Bookinfo has a multi-hop call chain (`productpage → reviews → ratings`) to exploit. This architecture has exactly one meshed HTTP hop of interest (Frontend → Orchestrator; the Orchestrator's only other outbound dependency, the Worker, communicates over Kafka since Sprint 3, not HTTP), so the same two-hop decoupling isn't structurally available here.

A resolvable path exists — injecting the fault via an `EnvoyFilter` at the Orchestrator's *inbound* sidecar listener, decoupled entirely from the client-side `VirtualService` that holds the timeout, sidestepping the same-route restriction by construction rather than by workaround — but this was deliberately not pursued in this phase. It steps meaningfully outside the declarative `VirtualService`/`DestinationRule` surface this phase's "traffic management" priority is scoped to, and the two proofs already in hand (fault-injection capability, fail-fast-on-zero-endpoints) were judged sufficient evidence for the mandatory deliverable without introducing `EnvoyFilter` complexity this early in the mesh work. Flagged here as a known, deliberate stopping point, not an oversight — and left as a candidate for revisiting in Phase 3 if time permits, alongside the other Istio-internals-adjacent stretch items already deferred.

Outlier detection's real value — ejecting *one* unhealthy replica from a pool of several while others keep serving — isn't fully demonstrable against this architecture's current single-replica Orchestrator. §5.3's test proves the mesh fails fast against a fully-down backend, which is a real and useful thing to prove, but it doesn't exercise the "eject one bad replica, keep routing to the healthy ones" behavior that's outlier detection's actual selling point — there is no pool to eject from at a single replica. Worth stating this plainly in Chapter *Results* rather than implying a fuller validation than what was actually possible — the configuration is correct and consistent with what a multi-replica Orchestrator would need, but the multi-replica scenario itself is untested, consistent with the honest-caveat pattern already established for the Worker's 10-replica scheduling ceiling (`sprint-3-recap.md` §11.1).

**The Sprint 3 k6 load test is currently non-functional against the post-Keycloak system** (§5.2), and, separately, would not have been a valid tool for in-mesh circuit-breaker validation even if it worked, since it runs outside the mesh with no sidecar. Both issues are tracked as a follow-up (re-author `loadtest.js` to complete an OIDC login, or accept an externally-provided session token, before it can serve as a regression check again) rather than silently patched over or left undiscovered.

---

## 7. Phase 1 Completion Status

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Scope decision: which hops get policy | Documented, Kafka/Postgres hops explicitly excluded with reasoning | Done | §1 |
| `DestinationRule` — Orchestrator | Connection pool + outlier detection applied | Done | §2, `maxEjectionPercent: 100` justified against single-replica topology |
| `VirtualService` — internal (Frontend→Orchestrator) | Timeout + retry, idempotency caveat documented | Done | §3 |
| `VirtualService` — external (Ingress→Frontend) | Timeout + retry, layered correctly against internal hop's timeout | Done | §4 |
| Timeout validation | Fault-injected delay proves timeout fires at configured value, not default | Gap, honestly documented | §5.1, §6 — combined delay+timeout test discovered non-functional (documented Istio behavior); zero-replica test fails too fast (`0.01s`) to exercise the timeout timer; direct proof against a genuinely slow-but-alive backend requires `EnvoyFilter`, deliberately not pursued this phase |
| Fault-injection capability validation | Delay-only test proves Envoy genuinely intercepts and delays traffic | Done | §5.1(b) — `3.01s` observed against `3s` configured, `200 OK` intact |
| Circuit breaker validation | In-mesh concurrent load proves `503` under tightened pool | Done (method corrected) | §5.2 — host-side k6 discovered invalid (broken by Keycloak auth, and structurally can't test a `DestinationRule` from outside the mesh regardless); corrected to in-mesh curl burst: `50/60` rejected, `10/60` succeeded |
| Outlier detection validation | Zero-replica test proves fast-fail; multi-replica ejection explicitly noted as unproven | Done (partial, honestly scoped) — mechanism is fail-fast-on-zero-endpoints, not outlier ejection specifically | §5.3, §6 |
| Regression | e2e suite passes at permanent policy values | Partial | §5.4 — k6 load test excluded from regression bar pending an OIDC-auth-capable rewrite; tracked as a follow-up, not silently assumed passing |
