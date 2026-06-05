#!/usr/bin/env bash
# teardown.sh — Deletes the sample feature-flag secrets or parameters from GCP.
#
# Usage:
#   export GCP_PROJECT_ID=my-gcp-project
#   bash teardown.sh [secret-manager|parameter-manager]
#   # Default: secret-manager

set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?Please set GCP_PROJECT_ID (e.g. export GCP_PROJECT_ID=my-project)}"
BACKEND="${1:-secret-manager}"

if [[ "$BACKEND" != "secret-manager" && "$BACKEND" != "parameter-manager" ]]; then
    echo "ERROR: Backend must be 'secret-manager' or 'parameter-manager'. Got: $BACKEND"
    exit 1
fi

echo "Deleting sample ${BACKEND} entries from project: ${PROJECT}"
echo ""

for name in dark-mode banner-text max-cart-items discount-rate checkout-config; do
    full_name="of-sample-${name}"
    if [[ "$BACKEND" == "secret-manager" ]]; then
        if gcloud secrets describe "${full_name}" --project="${PROJECT}" &>/dev/null; then
            gcloud secrets delete "${full_name}" --project="${PROJECT}" --quiet
            echo "  [DELETED] ${full_name}"
        else
            echo "  [SKIP]    ${full_name} (not found)"
        fi
    elif [[ "$BACKEND" == "parameter-manager" ]]; then
        if gcloud parametermanager parameters describe "${full_name}" --location=global --project="${PROJECT}" &>/dev/null 2>&1; then
            for v in $(gcloud parametermanager parameters versions list \
                --parameter="${full_name}" --location=global \
                --format="value(name.basename())"); do
            gcloud parametermanager parameters versions delete "$v" \
                --parameter="${full_name}" --location=global --quiet
            done && \

            gcloud parametermanager parameters delete "${full_name}" \
                --location=global \
                --project="${PROJECT}" \
                --quiet || echo "  [WARN] Could not delete parameter (may require gcloud alpha components)"
            echo "  [DELETED] ${full_name}"
        else
            echo "  [SKIP]    ${full_name} (not found)"
        fi
    fi
done

echo ""
echo "✓ ${BACKEND} cleanup complete."
