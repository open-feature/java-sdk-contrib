#!/usr/bin/env bash
# setup.sh — Creates the five sample feature-flag parameters in GCP Parameter Manager.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated (gcloud auth application-default login)
#   - Parameter Manager API enabled: gcloud services enable parametermanager.googleapis.com
#   - GCP_PROJECT_ID environment variable set to your GCP project ID
#
# Usage:
#   export GCP_PROJECT_ID=my-gcp-project
#   bash setup.sh

set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?Please set GCP_PROJECT_ID (e.g. export GCP_PROJECT_ID=my-project)}"
LOCATION="global"

echo "Creating sample feature-flag parameters in project: ${PROJECT} (location: ${LOCATION})"
echo "All parameters are prefixed with 'of-sample-' to match the sample app."
echo ""

# ────────────────────────────────────────────────────────────────────────────────
create_parameter() {
    local name="$1"
    local value="$2"
    local full_name="of-sample-${name}"

    # Create the parameter resource (idempotent — ignores "already exists")
    if gcloud parametermanager parameters describe "${full_name}" \
            --project="${PROJECT}" --location="${LOCATION}" &>/dev/null; then
        echo "  [EXISTS] ${full_name} — adding new version"
    else
        gcloud parametermanager parameters create "${full_name}" \
            --project="${PROJECT}" \
            --location="${LOCATION}" \
            --parameter-format=UNFORMATTED \
            --quiet
        echo "  [CREATED] ${full_name}"
    fi

    # Add a parameter version with the flag value
    # NOTE: versions in Parameter Manager are named; we use "v1" for the initial version
    gcloud parametermanager parameters versions create "v1" \
        --parameter="${full_name}" \
        --project="${PROJECT}" \
        --location="${LOCATION}" \
        --payload-data="${value}" \
        --quiet 2>/dev/null || \
    gcloud parametermanager parameters versions create "v$(date +%s)" \
        --parameter="${full_name}" \
        --project="${PROJECT}" \
        --location="${LOCATION}" \
        --payload-data="${value}" \
        --quiet
    echo "  [VERSION] ${full_name} → ${value}"
}
# ────────────────────────────────────────────────────────────────────────────────

# Boolean flag: dark UI theme toggle
create_parameter "dark-mode" "true"

# String flag: hero banner text
create_parameter "banner-text" "Welcome! 10% off today only"

# Integer flag: maximum items in cart
create_parameter "max-cart-items" "25"

# Double flag: discount multiplier (10%)
create_parameter "discount-rate" "0.10"

# Object flag: structured checkout configuration (JSON)
create_parameter "checkout-config" '{"paymentMethods":["card","paypal"],"expressCheckout":true,"maxRetries":3}'

echo ""
echo "✓ All parameters created successfully."
echo ""
echo "Next steps:"
echo "  1. Authenticate: gcloud auth application-default login"
echo "  2. Run the sample:"
echo "       cd gcp-parameter-manager-sample"
echo "       mvn exec:java   # GCP_PROJECT_ID must still be set in your shell"
echo "  3. To clean up: bash teardown.sh"
