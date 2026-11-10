#!/usr/bin/env bash
# ==============================================================================
# Linux Server Script: Automated Post-Deployment Testing on DevOMS Host
# ==============================================================================
set -e

echo "=============================================================================="
echo "    LAUNCHING OMS-WEB AUTOMATED CONTAINER TESTS ON DEVOMS HOST               "
echo "=============================================================================="

# 1. Pull latest testing code from GitHub repository
if [ -d .git ]; then
    echo "[1/4] Pulling latest test automation updates from GitHub..."
    git pull origin main || echo "[WARN] Git pull skipped, using local test files."
fi

# 2. Ensure exported-trades directory exists
mkdir -p ./exported-trades

# 3. Wait for DevOMS web application to be ready (Health check)
TARGET_URL="${TARGET_URL:-https://devoms.quantbd.com}"
echo "[2/4] Verifying $TARGET_URL is online and responsive..."

MAX_RETRIES=12
COUNT=0
until curl -k --output /dev/null --silent --head --fail "$TARGET_URL"; do
    COUNT=$((COUNT + 1))
    if [ $COUNT -ge $MAX_RETRIES ]; then
        echo "[ERROR] $TARGET_URL did not respond within 60 seconds. Aborting tests."
        exit 1
    fi
    echo "  -> Waiting for web application to start up... ($COUNT/$MAX_RETRIES)"
    sleep 5
done
echo "[SUCCESS] $TARGET_URL is online and healthy!"

# 4. Build or update Docker test image
echo "[3/4] Building / verifying Docker test container..."
docker compose build oms-tests

# 5. Run the automated test suite in the container
echo "[4/4] Running automated test suite against DevOMS..."
docker compose run --rm oms-tests

# 6. Verify trade file exports
echo "=============================================================================="
echo " [SUCCESS] Tests completed! Exported trade files saved in ./exported-trades:"
echo "=============================================================================="
ls -lh ./exported-trades || true
