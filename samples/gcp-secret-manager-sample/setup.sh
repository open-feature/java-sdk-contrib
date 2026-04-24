#!/usr/bin/env bash
# setup.sh — Creates the five sample feature-flag secrets in GCP Secret Manager.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated (gcloud auth application-default login)
#   - Secret Manager API enabled: gcloud services enable secretmanager.googleapis.com
#   - GCP_PROJECT_ID environment variable set to your GCP project ID
#
# Usage:
#   export GCP_PROJECT_ID=my-gcp-project
#   bash setup.sh

set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?Please set GCP_PROJECT_ID (e.g. export GCP_PROJECT_ID=my-project)}"

echo "Creating sample feature-flag secrets in project: ${PROJECT}"
echo "All secrets are prefixed with 'of-sample-' to match the sample app."
echo ""

# ────────────────────────────────────────────────────────────────────────────────
create_secret() {
    local name="$1"
    local value="$2"
    local full_name="of-sample-${name}"

    # Create the secret resource (idempotent — ignores "already exists")
    if gcloud secrets describe "${full_name}" --project="${PROJECT}" &>/dev/null; then
        echo "  [EXISTS] ${full_name} — adding new version"
    else
        gcloud secrets create "${full_name}" \
            --project="${PROJECT}" \
            --replication-policy=automatic \
            --quiet
        echo "  [CREATED] ${full_name}"
    fi

    # Add a secret version with the flag value
    echo -n "${value}" | gcloud secrets versions add "${full_name}" \
        --project="${PROJECT}" \
        --data-file=- \
        --quiet
    echo "  [VERSION] ${full_name} → ${value}"
}
# ────────────────────────────────────────────────────────────────────────────────

# Boolean flag: dark UI theme toggle
create_secret "dark-mode" "true"

# String flag: hero banner text
create_secret "banner-text" "Welcome! 10% off today only"

# Integer flag: maximum items in cart
create_secret "max-cart-items" "25"

# Double flag: discount multiplier (10%)
create_secret "discount-rate" "0.10"

# Object flag: structured checkout configuration (JSON)
create_secret "checkout-config" '{"paymentMethods":["card","paypal"],"expressCheckout":true,"maxRetries":3}'

echo ""
echo "✓ All secrets created successfully."
echo ""
echo "Next steps:"
echo "  1. Authenticate: gcloud auth application-default login"
echo "  2. Run the sample:"
echo "       cd gcp-secret-manager-sample"
echo "       mvn exec:java   # GCP_PROJECT_ID must still be set in your shell"
echo "  3. To clean up: bash teardown.sh"
