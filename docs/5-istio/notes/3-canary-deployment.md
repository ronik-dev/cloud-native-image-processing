# 3 Canary Deployment (Phase 2)

> This guide covers the second-priority mandatory Istio deliverable: a weighted, 70/30 canary split between two versions of a service, demonstrated on the Orchestrator per the decision in the Phase 2 planning discussion (Kafka-consumed Worker has no HTTP hop for Istio to route; Gateway/Frontend sits behind Sprint 4's OIDC filter, adding validation friction unrelated to canary routing itself; Orchestrator already carries `DestinationRule`/`VirtualService` resources from Phase 1 and is reachable unauthenticated for clean validation).

---

## 1. What "v1 vs v2" Means Here — Option A: Config-Only Version Marker

Consistent with this thesis's own scope statement (Thesis Proposal, Motivation: "the core of the thesis is not the development of these underlying tools, but rather the architectural orchestration") — the two Orchestrator versions run from **the identical container image**. They differ only in one environment variable, surfaced through Spring Boot Actuator's built-in `env` info contributor, so a canary split is real and independently observable without introducing an application-logic change whose correctness would itself need testing.

**One real code change is required, exactly once, to enable this** — two `application.properties` additions: `management.info.env.enabled=true` populates the `info` endpoint with environment-derived values, and `management.endpoints.web.exposure.include=info` is required for the `info` endpoint to be reachable over HTTP at all (only `health` is exposed by default). The second property was not part of the original plan and was discovered necessary only after debugging a live failure — see the debugging note in §5 for the full story. Both apply identically to both versions, requiring one rebuild through the existing Kaniko pipeline (`sprint-4-part-1-cicd-recap.md` §3) before v1/v2 differentiation can be demonstrated. After that one rebuild, **no further rebuilds are needed** to add, change, or remove canary versions — only the `INFO_APP_VERSION` environment variable on each Deployment differs, which is exactly the property that makes this a legitimate, low-cost demonstration of canary mechanics rather than a one-off hack.

```properties
# application.properties — orchestrator, two-line addition
management.info.env.enabled=true
management.endpoints.web.exposure.include=info
```

```yaml
# orchestrator/deployment-v1.yml (excerpt)
env:
  - name: INFO_APP_VERSION
    value: "v1"

# orchestrator/deployment-v2.yml (excerpt) — identical image, only this differs
env:
  - name: INFO_APP_VERSION
    value: "v2"
```

```bash
curl http://orchestrator-service:8080/internal/actuator/info
# v1 pod answers: {"app":{"version":"v1"}}
# v2 pod answers: {"app":{"version":"v2"}}
```

---

## 2. Splitting the Single Deployment Into Two, Without Downtime

The existing `orchestrator` Deployment (`sprint-3-recap.md` §6.1, one Deployment, `replicas: 1`) is replaced by two Deployments sharing the Service's selector but distinguished by a `version` label:

```yaml
# orchestrator/deployment-v1.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orchestrator-v1
  namespace: imageprocessing
spec:
  replicas: 1
  selector:
    matchLabels:
      app: orchestrator
      version: v1
  template:
    metadata:
      labels:
        app: orchestrator      # unchanged — this is what orchestrator-service still selects on
        version: v1            # new — this is what Istio's DestinationRule subset selects on
    spec:
      # initContainer: wait-for-postgres, envFrom: orchestrator-config/postgres-secret,
      # volumeMounts, probes — all carried over unchanged from the original single Deployment
      containers:
        - name: orchestrator
          image: gitlab-edu.supsi.ch:5050/.../orchestrator:latest
          env:
            - name: INFO_APP_VERSION
              value: "v1"
```

```yaml
# orchestrator/deployment-v2.yml — identical structure, only name/labels/env differ
metadata:
  name: orchestrator-v2
spec:
  replicas: 1
  selector:
    matchLabels: { app: orchestrator, version: v2 }
  template:
    metadata:
      labels: { app: orchestrator, version: v2 }
    spec:
      containers:
        - name: orchestrator
          image: gitlab-edu.supsi.ch:5050/.../orchestrator:latest   # same image as v1
          env:
            - name: INFO_APP_VERSION
              value: "v2"
```

**Critical label point, worth stating explicitly since it's the exact class of mistake `3-labels-and-services.md` already flagged once for Service selectors:** `orchestrator-service`'s own selector remains `app: orchestrator` only (unchanged from Sprint 3) — it does **not** select on `version`. Both `orchestrator-v1` and `orchestrator-v2` pods therefore land in the *same* Service's endpoint list simultaneously. Routing preference between them is not a Service-level concern at all; it is decided entirely by the `DestinationRule` subsets and the `VirtualService` weights below, which operate at the Istio layer *on top of* the single, unchanged Service. Getting this backward — e.g. trying to split traffic via two separate Services — would fight against how the Service/Istio layers are meant to divide responsibility here.

**Rollout sequence, to avoid a gap in Orchestrator availability:**
```bash
kubectl apply -f orchestrator/deployment-v1.yml   # brings up orchestrator-v1 alongside the still-running old `orchestrator` Deployment
kubectl delete deployment orchestrator -n imageprocessing   # old Deployment removed only once v1 is confirmed healthy and serving via orchestrator-service
kubectl apply -f orchestrator/deployment-v2.yml   # canary version added last
```

---

## 3. DestinationRule: Subsets

Extends Phase 1's `orchestrator-destination` (§2 of `12-traffic-management-and-resilience.md`) with `subsets`, rather than replacing it — the existing `trafficPolicy` (connection pool, outlier detection) is retained as the *default*, applying to both subsets equally unless a subset overrides it:

```yaml
# istio/destinationrule-orchestrator.yml — updated from Phase 1
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
  subsets:
    - name: v1
      labels:
        version: v1
    - name: v2
      labels:
        version: v2
```

No per-subset `trafficPolicy` override is defined here — both versions are identical images, so there is no reason for them to warrant different connection-pool or outlier-detection behavior. This is a deliberate choice worth stating: subset-level `trafficPolicy` overrides exist precisely for cases where a canary version is *expected* to behave differently (e.g., a slower new code path warranting a more conservative pool) — that's not the case here, so inheriting the shared default is the correct, not merely convenient, choice.

---

## 4. VirtualService: Weighted Routing, 70/30

Replaces Phase 1's single-destination route in `orchestrator-internal` with a weighted split across the two subsets, keeping the same `timeout`/`retries` policy from Phase 1 applied identically regardless of which subset a given request lands on:

```yaml
# istio/virtualservice-orchestrator.yml — updated from Phase 1
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
            subset: v1
            port:
              number: 8080
          weight: 70
        - destination:
            host: orchestrator-service
            subset: v2
            port:
              number: 8080
          weight: 30
      timeout: 5s
      retries:
        attempts: 2
        perTryTimeout: 2s
        retryOn: 5xx,reset,connect-failure
```

Weights must sum to 100 across all `destination` entries in a single route rule — Istio validates this at admission and rejects a `VirtualService` where they don't (`istioctl analyze` will also flag it before that point).

---

## 5. Validation: Proving the Split Is Real, With a Number

Same in-mesh throwaway-pod method established in Phase 1 (§5.1–§5.3 of `12-traffic-management-and-resilience.md`), since `/internal/*` sits outside Sprint 4's OIDC filter and requires no authentication workaround.

```bash
kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "for i in \$(seq 1 100); do curl -s http://orchestrator-service:8080/internal/actuator/info; echo; done | grep -o '\"version\":\"v[12]\"' | sort | uniq -c"
```

100 sequential requests (sequential rather than concurrent here, unlike Phase 1's circuit-breaker test — canary weighting is a per-request routing decision independent of concurrency, so there is no need to race requests against each other the way the connection-pool test did) against the same `/internal/actuator/info` endpoint, tallied by which version answered.

**Expected result:** roughly 70 responses tagged `v1`, roughly 30 tagged `v2` — Istio's weighted routing is probabilistic per-request, not a strict round-robin, so an exact 70/100 split isn't guaranteed on every run; a result within a reasonable band (e.g. 60–80 for v1 given `n=100`) is the expected and acceptable outcome, and worth stating as such in the report rather than treating any deviation from exactly 70 as a failure.

**For a more rigorous, quantified result suitable for Chapter *Results*,** increase `n` and compute a proportion with a stated tolerance, rather than eyeballing a single small run:
```bash
kubectl run curl-test --rm -it --image=curlimages/curl -n imageprocessing --restart=Never -- \
  sh -c "for i in \$(seq 1 500); do curl -s http://orchestrator-service:8080/internal/actuator/info; echo; done | grep -o '\"version\":\"v[12]\"' | sort | uniq -c"
```
At `n=500`, the observed v2 proportion should land close to 30% (±5 percentage points is a reasonable band to state and check against, given Envoy's weighted round-robin implementation over a moderate sample size) — record the actual counts observed for the report rather than only the pass/fail outcome, consistent with how Phase 1's circuit-breaker test reported the real `10/60` split rather than just "it worked."

**Actual observed result, `n=10,000`:**
```
7061 "version":"v1"   (70.61%)
2939 "version":"v2"   (29.39%)
```
Against a configured 70/30 split, this is a 0.61-percentage-point deviation on the larger sample — well within any reasonable statistical tolerance for Envoy's weighted round-robin distribution, and strong, directly-quotable evidence for Chapter *Results* that the canary split is real, precise, and not a coincidental pass on a small sample.

**Debugging note worth keeping for the record:** the first validation attempt against this configuration returned `498/500 "no healthy upstream"`, which initially looked like a routing or subset-configuration problem. It was not — both `orchestrator-v1` and `orchestrator-v2` shared an identical latent bug (`/internal/actuator/info` returning `500` because `management.endpoints.web.exposure.include` never listed `info`, so the request fell through to a generic, unmatched route rather than Actuator's real handler), and Phase 1's own `outlierDetection` (`consecutive5xxErrors: 5`, `maxEjectionPercent: 100`) correctly and automatically ejected both endpoints from the pool once each failed five consecutive requests — which is *why* nothing was left to route to. Diagnosing this required, in order: ruling out the mesh config (confirmed subsets/weights were live via `-o yaml`), ruling out load-balancer-vs-endpoint-count issues (confirmed both pods present via `kubectl get endpoints`), discovering `GlobalExceptionHandler`'s catch-all had a pre-existing `// TODO: Log the actual exception trace here` (meaning every unexpected error had been silently opaque since it was written), and finally spotting the tell in the Observation logs themselves — `contextualName='http get /**'` instead of a matched route name — which pointed to the real cause. Once `management.endpoints.web.exposure.include=info` was added (alongside fixing the logging gap for future debugging), both versions responded correctly and the validation above passed cleanly. Kept here in full rather than presented as a clean first-try, since the debugging path is itself a legitimate demonstration of the observability and resilience mechanisms (Observation logging, outlier detection) working exactly as designed — just not in the way originally anticipated.

---

## 6. Promotion and Rollback

Worth documenting both directions explicitly, since a canary that can only go one way (partial rollout, never promoted or rolled back) isn't demonstrating the actual operational pattern:

**Promote v2 to 100%** (canary succeeded):
```bash
kubectl patch virtualservice orchestrator-internal -n imageprocessing --type merge -p '
spec:
  http:
    - route:
        - destination: { host: orchestrator-service, subset: v1, port: { number: 8080 } }
          weight: 0
        - destination: { host: orchestrator-service, subset: v2, port: { number: 8080 } }
          weight: 100
      timeout: 5s
      retries: { attempts: 2, perTryTimeout: 2s, retryOn: 5xx,reset,connect-failure }
'
```
Followed by scaling down/deleting `orchestrator-v1` once confidence in v2 is established, and eventually renaming `orchestrator-v2` back to a canonical `orchestrator` Deployment name for the next release cycle — noted here as the operational next step, not executed as part of this phase's validation.

**Roll back to v1 100%** (canary failed):
```bash
kubectl patch virtualservice orchestrator-internal -n imageprocessing --type merge -p '
spec:
  http:
    - route:
        - destination: { host: orchestrator-service, subset: v1, port: { number: 8080 } }
          weight: 100
        - destination: { host: orchestrator-service, subset: v2, port: { number: 8080 } }
          weight: 0
      timeout: 5s
      retries: { attempts: 2, perTryTimeout: 2s, retryOn: 5xx,reset,connect-failure }
'
```
The key operational property worth highlighting in the report: **both directions are a single `VirtualService` patch, with zero Deployment changes, zero pod restarts, and zero downtime** — the canary and stable pods are already running simultaneously throughout; only Envoy's routing weights change. This is the concrete, demonstrable payoff of doing canary routing at the mesh layer instead of, say, manipulating replica counts to approximate a traffic split (which would be coarser, slower to adjust, and would not survive being called "canary deployment" under scrutiny).

---

## 7. Regression

Re-run the Sprint 2 e2e pytest suite against the split configuration at the permanent 70/30 weights. Since both subsets are byte-identical images differing only in one environment variable that no application code path branches on, no behavioral regression is expected or would be meaningful to attribute to "v2" specifically — this regression pass is really confirming that weighted routing across two subsets of the same Service doesn't itself introduce latency, connection, or correctness issues into the existing test suite, not that "v2" is functionally sound (it's the same code as v1).

---

## 8. Phase 2 Completion Status

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Version differentiation mechanism | Observable difference between v1/v2 with zero application-logic change | Done | Actuator `info.env` + `management.endpoints.web.exposure.include=info` (the latter discovered necessary mid-implementation, §5), one rebuild, then env-var-only per version. `GlobalExceptionHandler`'s missing exception logging fixed in the same rebuild. |
| Deployment split | `orchestrator-v1`/`orchestrator-v2`, shared Service selector, distinct `version` label | Done | §2 — label-selector distinction documented explicitly per the `3-labels-and-services.md` precedent |
| `DestinationRule` subsets | `v1`/`v2` subsets defined, Phase 1's shared `trafficPolicy` retained as default | Done | §3 |
| `VirtualService` weighted routing | 70/30 split, Phase 1's timeout/retry policy preserved | Done | §4 |
| Quantified validation | Real observed split reported, not just "it worked" | Done | §5 — `n=10,000`: `7061 v1 / 2939 v2` (70.61%/29.39%) against configured 70/30, 0.61pp deviation |
| Promotion path | 100%-v2 patch documented and demonstrable with zero downtime | Done | §6 |
| Rollback path | 100%-v1 patch documented and demonstrable with zero downtime | Done | §6 |
| Regression | e2e suite passes against split configuration | Pending execution | §7 |
