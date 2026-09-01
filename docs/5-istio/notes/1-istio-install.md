# 11 Istio Mesh Bootstrap (Phase 0)

> This guide covers installing Istio onto the existing Minikube cluster, deciding sidecar-injection scope, and replacing the NGINX Ingress Controller with an Istio-native ingress path. No traffic-management policy (`VirtualService`/`DestinationRule`) is introduced yet — the goal of this phase is a mesh that is present but transparent, i.e. the existing system behaves identically to Sprint 4 once the sidecars are attached.

---

## 1. Why This Phase Exists Separately

Every later Istio phase (traffic management, canary, mTLS, authorization) assumes sidecars are already injected and routing already flows through the mesh's ingress path. Bundling that bootstrap work into the first "real" feature phase would make it impossible to tell, if something breaks, whether the mesh itself is misconfigured or the policy on top of it is. This phase's only success criterion is: **the existing Sprint 4 system works exactly as before, now with an Envoy sidecar in every application pod.**

---

## 2. Istio Version and Install Profile

Istio `1.30.3` (current stable at time of writing) via `istioctl`.

```bash
curl -L https://istio.io/downloadIstio | ISTIO_VERSION=1.30.3 sh -
cd istio-1.30.3
export PATH=$PWD/bin:$PATH
istioctl version --remote=false
```

**Profile: `demo`, not `default` or `minimal`.** The `demo` profile enables the full ingress/egress gateway pair and relaxed resource requests out of the box, which matters on a single-node Minikube cluster where CPU headroom is already contended by five application workloads plus Postgres and Kafka (`sprint-3-recap.md` §11.1 already flags the worker as having no resource limits — adding a resource-hungry mesh control plane on top of that is worth doing with the more permissive profile rather than `default`'s production-sized requests, which risk pods stuck `Pending`).

```bash
istioctl install --set profile=demo -y
kubectl get pods -n istio-system
```

Expected: `istiod` and `istio-ingressgateway` running in `istio-system`.

---

## 3. Sidecar Injection Scope — Decision

**Decision: automatic injection is enabled at the namespace level for `imageprocessing`, with Postgres and Kafka explicitly opted out via pod-level annotation.**

```bash
kubectl label namespace imageprocessing istio-injection=enabled
```

This means every *new* pod scheduled into `imageprocessing` gets an Envoy sidecar injected automatically — gateway, orchestrator, worker, keycloak all included. Postgres and Kafka are excluded explicitly rather than left to default behavior:

```yaml
# postgres/statefulset.yml, kafka/statefulset.yml — added to spec.template.metadata
metadata:
  annotations:
    sidecar.istio.io/inject: "false"
```

**Rationale for excluding the data/messaging tier specifically:**

- **Kafka's advertised-listener DNS story is already fragile.** `KAFKA_ADVERTISED_LISTENERS` (`9-kafka.md` §3.6, §8) is wired to `kafka-0.kafka.imageprocessing.svc.cluster.local`, and the KRaft controller-quorum voter address depends on that resolving consistently. Adding a transparent-proxy sidecar into that path introduces a second thing that has to be correctly configured before the broker even forms quorum, for zero traffic-management benefit — nothing routes *to* Kafka via HTTP semantics that Istio understands; it's a raw TCP consumer/producer protocol.
- **StatefulSet + mTLS + sidecar readiness ordering is a known sharp edge.** Both Postgres and Kafka already carry hand-tuned probe timing and an `initContainer: wait-for-postgres` pattern (`7-probes.md`, `8-pods-desing-patterns.md`) built around the assumption that the main container's own health, not a sidecar's, gates readiness. Istio sidecars have their own startup ordering relative to the main container (`holdApplicationUntilProxyStarts`, proxy readiness gating pod readiness) that would need to be re-validated against every existing probe decision in `7-probes.md` for no immediate payoff.
- **No traffic-management, canary, or authorization work in this sprint's scope touches these two services.** The mandatory Phase 1/2 work (routing, canary) is scoped to Gateway↔Orchestrator; Worker↔Kafka is a message-queue hop the mesh doesn't route in any meaningful sense anyway.

This is documented as a **scoping decision, not a permanent architectural stance** — meshing the data tier (in particular, mTLS between Orchestrator/Worker and Postgres/Kafka) is a legitimate stretch item if Phase 3 (security/mTLS) is reached with time remaining, at which point it would need its own validation pass rather than being folded in silently here.

Keycloak is **included** in injection (no exclusion annotation) — its traffic (OIDC redirects, the Gateway backchannel token swap) is exactly the kind of HTTP path this thesis's Istio work is meant to demonstrate, and it carries no StatefulSet/DNS fragility of its own (`3-add-keycloak.md` — stateless Deployment, state lives in Postgres).

| Workload | Sidecar injected? | Reason |
|---|---|---|
| `frontend` | Yes | Public HTTP entry point; primary subject of Phase 1/2 |
| `orchestrator` | Yes | Internal HTTP hop; primary subject of Phase 1/2 |
| `keycloak` | Yes | HTTP/OIDC traffic, stateless Deployment, no DNS fragility |
| `worker` | Yes | Consistency with app tier; note it has no inbound HTTP callers post-Kafka-migration, so mesh routing has nothing to act on for it yet |
| `postgres` | **No** | StatefulSet DNS/probe fragility, raw SQL protocol, no routing benefit |
| `kafka` | **No** | KRaft quorum DNS fragility, raw TCP protocol, no routing benefit |

---

## 4. Ingress: Istio Gateway Replaces NGINX

**Decision: retire the NGINX Ingress Controller (`6-ingress.md`) and move `api.imageprocessing.local` routing to an Istio `Gateway` + `VirtualService`.**

Running both simultaneously would leave "who owns external routing" ambiguous for the rest of the report, and Sprint 3 already set the precedent of following whichever manifest is actually on disk when two mechanisms could plausibly answer the same question (`sprint-3-recap.md` §7.1, the `NodePort`→`ClusterIP` correction). Since Phase 1's traffic-management work is the mandatory deliverable and needs to visibly own the request path end-to-end for the report's routing diagrams to make sense, NGINX is removed rather than kept as a second, now-redundant hop in front of the mesh.

```bash
minikube addons disable ingress
kubectl delete -f frontend/ingress.yml   # old NGINX Ingress resource
```

Minimal passthrough `Gateway` (no routing rules yet — that's Phase 1):

```yaml
# istio/gateway.yml
apiVersion: networking.istio.io/v1
kind: Gateway
metadata:
  name: imageprocessing-gateway
  namespace: imageprocessing
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
        - "api.imageprocessing.local"
        - "keycloak.imageprocessing.local"
```

```yaml
# istio/virtualservice-bootstrap.yml — pure passthrough, no weighting/retries yet
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
---
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata:
  name: keycloak-passthrough
  namespace: imageprocessing
spec:
  hosts:
    - "keycloak.imageprocessing.local"
  gateways:
    - imageprocessing-gateway
  http:
    - route:
        - destination:
            host: keycloak
            port:
              number: 8080
```

**`/etc/hosts` target changes** from the Minikube node IP to the Istio ingress gateway's address, which on Minikube requires `minikube tunnel` (a `LoadBalancer`-type Service, unlike NGINX's node-port-backed addon):

```bash
minikube tunnel   # separate terminal, must stay running
kubectl get svc istio-ingressgateway -n istio-system   # confirm EXTERNAL-IP populated
```

`/etc/hosts` entry updated accordingly; the `192.168.49.2` mapping documented in `6-ingress.md` is superseded and should be corrected to whatever `minikube tunnel` assigns (typically `127.0.0.1` once tunneled), documented explicitly here for the same reason `sprint-3-recap.md` §7.1 called out its own superseded note rather than leaving two contradictory sources.

---

## 5. Rollout

```bash
kubectl apply -f istio/gateway.yml
kubectl apply -f istio/virtualservice-bootstrap.yml
kubectl apply -f istio/virtualservice-keycloak-bootstrap.yml

kubectl rollout restart deployment/frontend deployment/orchestrator deployment/worker deployment/keycloak -n imageprocessing
kubectl get pods -n imageprocessing
```

Expected: every pod except `postgres-0` and `kafka-0` shows `READY 2/2` (main container + `istio-proxy`). The `-o jsonpath='{...}{.spec.containers[*].name}{...}'` form used earlier in development is misleading here — it only lists container *names*, not readiness, and container names don't change when a sidecar is injected (the sidecar container is literally named `istio-proxy`, distinct from `.spec.containers[*].name` truncation seen in some shells). `READY 2/2` from a plain `kubectl get pods` is the reliable signal; trust that over a stale or misread `jsonpath` query.

---

## 6. Validation

The bar for this phase is regression, not new capability:

1. **Existing e2e suites unchanged and green.** Re-run the Sprint 2 pytest e2e suite and the Sprint 3 k6 load test (`test/loadtest.js`) against the mesh with zero policy applied. Istio's default behavior with no `DestinationRule`/`VirtualService` beyond the bootstrap passthrough is to route everything through, unmodified — this confirms the mesh is transparent rather than silently altering behavior before any deliberate policy exists.
2. **OIDC login flow smoke test.** Confirmed via `curl -I` against both ingress hosts rather than a full browser walkthrough:

   ```bash
   curl -I http://api.imageprocessing.local
   # HTTP/1.1 302 Found
   # location: http://api.imageprocessing.local/oauth2/authorization/keycloak

   curl -I http://keycloak.imageprocessing.local
   # HTTP/1.1 302 Found
   # location: http://keycloak.imageprocessing.local/admin/
   ```

   Both redirects are the expected unauthenticated behavior (Gateway bouncing to the OIDC login trigger; Keycloak's root bouncing to its own admin console) and confirm the `Gateway`/`VirtualService` host routing is correct end-to-end for both hosts. A full browser walkthrough of the login form itself is still worth doing once before Phase 1, since `curl` only confirms the redirect chain starts correctly, not that the full Authorization Code exchange completes through the mesh.
3. **`kubectl exec` sanity check on Postgres/Kafka pods** confirming exactly one container each (no accidental injection despite the namespace-wide label) — a quick regression guard against the annotation being dropped in a future manifest edit.
4. **`istioctl analyze -n imageprocessing`** run and any warnings triaged before moving to Phase 1, so Phase 1 starts from a clean baseline rather than debugging bootstrap issues under new policy.

---

## 7. Troubleshooting Notes From First Run-Through

Kept here rather than silently smoothed over, since the actual debugging sequence is a more useful reference than a clean happy path would be:

- **`curl: (7) Failed to connect`, `/etc/hosts` and `EXTERNAL-IP` both correct.** Root cause was `istio/gateway.yml` and the `VirtualService` manifests never having been `kubectl apply`'d — the ingress gateway pod was healthy and the tunnel route was correct, but Envoy had no listener/route configured for either host at all, so nothing answered on port 80. **Diagnostic order that would have caught this fastest:** check `kubectl get gateway,virtualservice -n imageprocessing` *before* investigating the tunnel or DNS, since a connection refused at the TCP level, with correct DNS and a confirmed tunnel route, most likely means nothing is listening application-side, not that the network path is wrong.
- **`503 Service Unavailable` from `istio-envoy` on `api.imageprocessing.local`.** The bootstrap `VirtualService` originally pointed at `host: gateway-service`, following the canonical naming used in `sprint-3-recap.md`. The actual on-disk Service is `frontend-service` (§4's naming-correction note). A `503` specifically — as opposed to `404` or connection refused — is Envoy's signal that the route matched but the destination host has no resolvable/healthy endpoints; worth remembering as the specific symptom of a right-route/wrong-service-name mismatch going forward, since Phase 1/2 will define several more `DestinationRule` host references where the same mistake is possible.
- **`404 Not Found` from `istio-envoy` on `keycloak.imageprocessing.local`.** The `Gateway` resource declared the host, but the bootstrap `VirtualService` set had no route for it yet (only `api.imageprocessing.local` was covered in the first draft). Envoy's `404` in this case means "your `Gateway` accepted the connection for a declared host, but no `VirtualService` claims that host" — distinct from the `503` case above, where the route existed but the target didn't. Fixed by adding `istio/virtualservice-keycloak-bootstrap.yml` as its own resource (§4).

## 8. Open Items Carried Into Later Phases

| Item | Deferred to | Note |
|---|---|---|
| Retries/timeouts/circuit breakers | Phase 1 | This phase intentionally ships zero resilience policy |
| Weighted routing / canary | Phase 2 | Bootstrap `VirtualService` above is 100% passthrough |
| mTLS (`PeerAuthentication`) | Phase 3 (optional) | Not enabled yet; sidecars present but mTLS mode untouched |
| Meshing Postgres/Kafka | Explicit stretch, not currently planned | Revisit only if Phase 3 has time remaining |

---

## 9. Phase 0 Completion Status

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Istio control plane installed | `istiod`, ingress gateway running in `istio-system` | Done | `demo` profile, `1.30.3` |
| Namespace injection enabled | `imageprocessing` pods carry `istio-proxy` sidecar | Done | Label-based, namespace-wide |
| Data-tier injection excluded | Postgres, Kafka run without sidecars | Done | Explicit pod-level annotation, not relied on implicitly |
| NGINX retired | Old Ingress resource removed, addon disabled | Done | Single source of routing truth going forward |
| Istio Gateway + bootstrap VirtualService | `api.imageprocessing.local` reachable through the mesh | Done | Passthrough only, no policy |
| Regression validation | Sprint 2 e2e suite + Sprint 3 k6 test pass unmodified | Done | Confirms mesh transparency before Phase 1 policy work begins |
