# GCP — OpenFeature Sample

A runnable Java application demonstrating the [GCP Secret Manager OpenFeature provider](../../providers/gcp-secret-manager).

It evaluates five feature flags (covering every supported type) that are stored as secrets in
Google Cloud Secret Manager.

## Feature Flags Used

| Secret name (GCP) | Flag key | Type | Example value |
|---|---|---|---|
| `of-sample-dark-mode` | `dark-mode` | boolean | `true` |
| `of-sample-banner-text` | `banner-text` | string | `"Welcome! 10% off today only"` |
| `of-sample-max-cart-items` | `max-cart-items` | integer | `25` |
| `of-sample-discount-rate` | `discount-rate` | double | `0.10` |
| `of-sample-checkout-config` | `checkout-config` | object (JSON) | `{"paymentMethods":["card","paypal"],...}` |

All secrets are prefixed with `of-sample-` so they are easy to identify and clean up.

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 17+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+ | [maven.apache.org](https://maven.apache.org) |
| gcloud CLI | any | [cloud.google.com/sdk](https://cloud.google.com/sdk/docs/install) |
| GCP project | — | [console.cloud.google.com](https://console.cloud.google.com) |

Your GCP account needs the **Secret Manager Secret Accessor** role (`roles/secretmanager.secretAccessor`)
on the project.

---

## Step 1 — Enable the API

```bash
export GCP_PROJECT_ID=my-gcp-project          # replace with your project ID

gcloud services enable secretmanager.googleapis.com --project="$GCP_PROJECT_ID"
```

## Step 2 — Authenticate

```bash
gcloud auth application-default login
```

## Step 3 — Build the provider

From the **root** of `java-sdk-contrib`:

```bash
mvn install -DskipTests -P '!deploy'
```

This installs the provider JAR to your local Maven repository (`~/.m2`).

## Step 4 — Create the feature-flag secrets

```bash
cd samples/gcp-secret-manager-sample
bash setup.sh
```

You should see output like:

```
Creating sample feature-flag secrets in project: my-gcp-project
  [CREATED] of-sample-dark-mode
  [VERSION] of-sample-dark-mode → true
  [CREATED] of-sample-banner-text
  ...
✓ All secrets created successfully.
```

## Step 5 — Run the sample

```bash
mvn exec:java
```

The app reads `GCP_PROJECT_ID` from the environment. You can also pass it explicitly:

```bash
mvn exec:java -DGCP_PROJECT_ID=my-gcp-project
```

### Expected output

```
=======================================================
  GCP Secret Manager — OpenFeature Sample
=======================================================
Project : my-gcp-project
Prefix  : of-sample-

── Boolean Flag  »  dark-mode ──────────────────────────────────────
Value   : true
Effect  : Dark theme activated

── String Flag  »  banner-text ─────────────────────────────────────
Value   : Welcome! 10% off today only

── Integer Flag  »  max-cart-items ─────────────────────────────────
Value   : 25
Effect  : Cart is capped at 25 items

── Double Flag  »  discount-rate ───────────────────────────────────
Value   : 0.10
Effect  : 10% discount applied to cart total

── Object Flag  »  checkout-config ─────────────────────────────────
Value   : Structure{...}
Payment methods  : ["card", "paypal"]
Express checkout : true

=======================================================
  All flags evaluated successfully.
=======================================================
```

## Step 6 — Clean up

```bash
bash teardown.sh
```

---

## Changing flag values

To update a flag, add a new secret version:

```bash
echo -n "false" | gcloud secrets versions add of-sample-dark-mode \
    --project="$GCP_PROJECT_ID" --data-file=-
```

Re-run the sample to see the new value (cache expires after 30 seconds in this sample).

---

## Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| `GCP_PROJECT_ID is not set` | Env var missing | `export GCP_PROJECT_ID=my-project` |
| `FlagNotFoundError` | Secret doesn't exist | Run `setup.sh` first |
| `PERMISSION_DENIED` | Missing IAM role | Grant `roles/secretmanager.secretAccessor` |
| `UNAUTHENTICATED` | No credentials | Run `gcloud auth application-default login` |
| `secretmanager.googleapis.com is not enabled` | API disabled | Run Step 1 |
| `Could not find artifact ...gcp-secret-manager` | Provider not installed | Run Step 3 |
