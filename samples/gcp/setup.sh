#!/usr/bin/env bash
# setup.sh — Creates the five sample feature-flag secrets or parameters in GCP.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated (gcloud auth application-default login)
#   - Secret Manager API enabled: gcloud services enable secretmanager.googleapis.com
#   - GCP_PROJECT_ID environment variable set to your GCP project ID
#
# Usage:
#   export GCP_PROJECT_ID=my-gcp-project
#   bash setup.sh [secret-manager|parameter-manager]
#   # Default: secret-manager

set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?Please set GCP_PROJECT_ID (e.g. export GCP_PROJECT_ID=my-project)}"
BACKEND="${1:-secret-manager}"

if [[ "$BACKEND" != "secret-manager" && "$BACKEND" != "parameter-manager" ]]; then
    echo "ERROR: Backend must be 'secret-manager' or 'parameter-manager'. Got: $BACKEND"
    exit 1
fi

echo "Creating sample feature-flag ${BACKEND} in project: ${PROJECT}"
echo "All entries are prefixed with 'of-sample-' to match the sample app."
echo ""

# ────────────────────────────────────────────────────────────────────────────────
create_entry() {
    local name="$1"
    local value="$2"
    local full_name="of-sample-${name}"

    if [[ "$BACKEND" == "secret-manager" ]]; then
        # Create or update Secret Manager secret
        if gcloud secrets describe "${full_name}" --project="${PROJECT}" &>/dev/null; then
            echo "  [EXISTS] ${full_name} — adding new version"
        else
            gcloud secrets create "${full_name}" \
                --project="${PROJECT}" \
                --replication-policy=automatic \
                --quiet
            echo "  [CREATED] ${full_name}"
        fi
        echo -n "${value}" | gcloud secrets versions add "${full_name}" \
            --project="${PROJECT}" \
            --data-file=- \
            --quiet
        echo "  [VERSION] ${full_name} → ${value}"
    elif [[ "$BACKEND" == "parameter-manager" ]]; then
        # Create or update Parameter Manager parameter
        if gcloud parametermanager parameters describe "${full_name}" --location=global --project="${PROJECT}" &>/dev/null 2>&1; then
            echo "  [EXISTS] ${full_name} — updating value"
            gcloud parametermanager parameters update "${full_name}" \
                --location=global \
                --project="${PROJECT}" \
                --data="${value}" \
                --quiet || echo "  [WARN] Could not update parameter (may require gcloud alpha components)"
        else
            gcloud parametermanager parameters create "${full_name}" \
                --location=global \
                --project="${PROJECT}" \
                --parameter-format=UNFORMATTED \
                --quiet || echo "  [WARN] Could not create parameter (may require gcloud alpha components)"
            echo "  [CREATED] ${full_name}"

            gcloud parametermanager parameters versions create VERSION_ID \
                --parameter="${full_name}" \
                --project="${PROJECT}" \
                --location=global \
                --payload-data="${value}"


        fi
        echo "  [SET] ${full_name} → ${value}"
    fi
}
# ────────────────────────────────────────────────────────────────────────────────

# Boolean flag: dark UI theme toggle
create_entry "dark-mode" "true"

# String flag: hero banner text
create_entry "banner-text" "Welcome! 10% off today only"

# Integer flag: maximum items in cart
create_entry "max-cart-items" "25"

# Double flag: discount multiplier (10%)
create_entry "discount-rate" "0.10"

# Object flag: structured checkout configuration (JSON)
create_entry "checkout-config" '{"paymentMethods":["card","paypal"],"expressCheckout":true,"maxRetries":3}'

echo ""
echo "✓ All ${BACKEND} entries created successfully."
echo ""
echo "Next steps:"
echo "  1. Authenticate: gcloud auth application-default login"
echo "  2. Run the sample:"
echo "       cd samples/gcp"
if [[ "$BACKEND" == "secret-manager" ]]; then
    echo "       mvn exec:java   # Uses Secret Manager (default)"
else
    echo "       mvn exec:java -Dexec.mainClass=dev.openfeature.contrib.samples.gcp.ParameterManagerSampleApp"
fi
echo "  3. To clean up: bash teardown.sh $BACKEND"
