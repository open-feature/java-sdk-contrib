# GCP Parameter Manager — OpenFeature Sample

A runnable Java application demonstrating the [GCP Parameter Manager OpenFeature provider](../../providers/gcp-parameter-manager).

It evaluates five feature flags (covering every supported type) that are stored as
parameters in Google Cloud Parameter Manager.

## Feature Flags Used

| Parameter name (GCP) | Flag key | Type | Example value |
|---|---|---|---|
| `of-sample-dark-mode` | `dark-mode` | boolean | `true` |
| `of-sample-banner-text` | `banner-text` | string | `"Welcome! 10% off today only"` |
| `of-sample-max-cart-items` | `max-cart-items` | integer | `25` |
| `of-sample-discount-rate` | `discount-rate` | double | `0.10` |
| `of-sample-checkout-config` | `checkout-config` | object (JSON) | `{"paymentMethods":["card","paypal"],...}` |

All parameters are prefixed with `of-sample-` so they are easy to identify and clean up.

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 17+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.8+ | [maven.apache.org](https://maven.apache.org) |
| gcloud CLI | any | [cloud.google.com/sdk](https://cloud.google.com/sdk/docs/install) |
| GCP project | — | [console.cloud.google.com](https://console.cloud.google.com) |

Your GCP account needs the **Parameter Manager Parameter Version Accessor** role
(`roles/parametermanager.parameterVersionAccessor`) on the project.

---

## Step 1 — Enable the API

```bash
export GCP_PROJECT_ID=my-gcp-project          # replace with your project ID

gcloud services enable parametermanager.googleapis.com --project="$GCP_PROJECT_ID"
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

## Step 4 — Create the feature-flag parameters

```bash
cd samples/gcp-parameter-manager-sample
bash setup.sh
```

You should see output like:

```
Creating sample feature-flag parameters in project: my-gcp-project (location: global)
  [CREATED] of-sample-dark-mode
  [VERSION] of-sample-dark-mode → true
  [CREATED] of-sample-banner-text
  ...
✓ All parameters created successfully.
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
  GCP Parameter Manager — OpenFeature Sample
=======================================================
Project  : my-gcp-project
Location : global
Prefix   : of-sample-

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

To update a flag, add a new parameter version:

```bash
gcloud parametermanager parameters versions create "v2" \
    --parameter="of-sample-dark-mode" \
    --project="$GCP_PROJECT_ID" \
    --location=global \
    --payload-data="false"
```

The provider fetches the `latest` version by default (the highest version number).
Re-run the sample after the 30-second cache expires to see the updated value.

---

## Regional parameters

If your parameters are stored in a specific region instead of `global`, update the
`locationId` in the app or pass a different location in `setup.sh`:

```java
GcpParameterManagerProviderOptions.builder()
    .projectId(projectId)
    .locationId("us-central1")   // regional endpoint
    ...
```

---

## Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| `GCP_PROJECT_ID is not set` | Env var missing | `export GCP_PROJECT_ID=my-project` |
| `FlagNotFoundError` | Parameter doesn't exist | Run `setup.sh` first |
| `PERMISSION_DENIED` | Missing IAM role | Grant `roles/parametermanager.parameterVersionAccessor` |
| `UNAUTHENTICATED` | No credentials | Run `gcloud auth application-default login` |
| `parametermanager.googleapis.com is not enabled` | API disabled | Run Step 1 |
| `Could not find artifact ...gcp-parameter-manager` | Provider not installed | Run Step 3 |
