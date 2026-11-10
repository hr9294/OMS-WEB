#!/usr/bin/env bash
# ==============================================================================
# Linux Server Script: Build & Run Automated Testing Container on DevOMS Server
# ==============================================================================
set -e

echo "=============================================================================="
echo "    LAUNCHING OMS-WEB AUTOMATED CONTAINER TESTS ON DEVOMS SERVER             "
echo "=============================================================================="

# Ensure output directory for exported trade files exists
mkdir -p ./exported-trades

# Build container if not already built or if code changed
echo "[1/2] Building test container..."
docker compose build oms-tests

# Run the test container (removes itself after completion)
echo "[2/2] Running automated tests against DevOMS..."
docker compose run --rm oms-tests

echo "=============================================================================="
echo " [SUCCESS] Tests completed! Exported trade files saved in ./exported-trades"
echo "=============================================================================="
ls -lh ./exported-trades
