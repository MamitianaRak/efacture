# Kubernetes

Kustomize manifests for eFacture.

| Folder | Content |
|---|---|
| `base/` | The application: Deployment (probes, resources, graceful shutdown), Service, ConfigMap |
| `overlays/local/` | For a local cluster: adds a PostgreSQL and development-only secrets |

## Try it on a local cluster (kind)

```bash
# From the repository root, with Docker running
docker build -t efacture:local .
kind create cluster --name efacture --config k8s/kind-config.yaml   # longer timeouts for slow disks (WSL2)
kind load docker-image efacture:local --name efacture

kubectl apply -k k8s/overlays/local
kubectl rollout status deployment/efacture --timeout=180s
kubectl port-forward service/efacture 8080:80
```

Then open http://localhost:8080 and sign in with `gerant@demo.fr` / `demo1234`.

## On a real cluster

Use a managed PostgreSQL, and create the secret instead of the local overlay's:

```bash
kubectl create secret generic efacture-secrets \
  --from-literal=DATABASE_URL='jdbc:postgresql://<host>:5432/<db>?sslmode=require' \
  --from-literal=DATABASE_USER='<user>' \
  --from-literal=DATABASE_PASSWORD='<password>' \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)"
kubectl apply -k k8s/base
```

## Design notes

- **Probes.** The startup probe gives Spring Boot time to start before liveness is checked; readiness includes the database, so a pod that cannot reach PostgreSQL gets no traffic instead of failing requests.
- **Resources.** 512 MiB, the size the image was tuned for by measuring it without swap (see the main README).
- **Graceful shutdown.** Spring finishes in-flight requests (20 s per phase) within the pod's 30 s grace period.
- **One replica.** The outbox relay supports several instances (`FOR UPDATE SKIP LOCKED`, tested with four concurrent relays), but the simulated platform keeps its state in each pod's memory. With a real platform client, the Deployment could scale out.
- **No secrets in git.** Only the local overlay carries development values, generated as a Secret.
