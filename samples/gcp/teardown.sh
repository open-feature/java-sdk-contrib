#!/usr/bin/env bash
# teardown.sh — Deletes the sample feature-flag secrets from GCP Secret Manager.
#
# Usage:
#   export GCP_PROJECT_ID=my-gcp-project
#   bash teardown.sh

set -euo pipefail

PROJECT="${GCP_PROJECT_ID:?Please set GCP_PROJECT_ID (e.g. export GCP_PROJECT_ID=my-project)}"

echo "Deleting sample secrets from project: ${PROJECT}"
echo ""

for name in dark-mode banner-text max-cart-items discount-rate checkout-config; do
    full_name="of-sample-${name}"
    if gcloud secrets describe "${full_name}" --project="${PROJECT}" &>/dev/null; then
        gcloud secrets delete "${full_name}" --project="${PROJECT}" --quiet
        echo "  [DELETED] ${full_name}"
    else
        echo "  [SKIP]    ${full_name} (not found)"
    fi
done

echo ""
echo "✓ Cleanup complete."
