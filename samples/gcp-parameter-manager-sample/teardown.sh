#!/usr/bin/env bash
# teardown.sh — Deletes the sample feature-flag parameters from GCP Parameter Manager.
#
# Usage:
#   export GCP_PROJECT_ID=my-gcp-project
#   bash teardown.sh

set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?Please set GCP_PROJECT_ID (e.g. export GCP_PROJECT_ID=my-project)}"
LOCATION="global"

echo "Deleting sample parameters from project: ${PROJECT} (location: ${LOCATION})"
echo ""

for name in dark-mode banner-text max-cart-items discount-rate checkout-config; do
    full_name="of-sample-${name}"
    if gcloud parametermanager parameters describe "${full_name}" \
            --project="${PROJECT}" --location="${LOCATION}" &>/dev/null; then
        for version in $(gcloud parametermanager parameters versions list \
            --parameter="${full_name}" --project="${PROJECT}" --location="${LOCATION}" \
            --format="value(name.basename())" 2>/dev/null); do
            gcloud parametermanager parameters versions delete "${version}" \
                --parameter="${full_name}" --project="${PROJECT}" --location="${LOCATION}" --quiet
            echo "  [DELETED] ${full_name}/versions/${version}"
        done
        gcloud parametermanager parameters delete "${full_name}" \
            --project="${PROJECT}" --location="${LOCATION}" --quiet
        echo "  [DELETED] ${full_name}"
    else
        echo "  [SKIP]    ${full_name} (not found)"
    fi
done

echo ""
echo "✓ Cleanup complete."
