# DevOMS Host Server Automated Testing Setup

This document provides instructions for the server administrator or DevOps team hosting `https://devoms.quantbd.com` to integrate the automated end-to-end testing suite directly on the host server.

---

## 1. Prerequisites on the Host Server
* **Docker & Docker Compose** installed (`docker --version` & `docker compose version`).
* **Git** installed (`git --version`).

---

## 2. One-Time Setup on the Server

Run these commands on the server to clone the repository:

```bash
cd /opt  # or your preferred deployment directory
git clone https://github.com/hr9294/OMS-WEB.git
cd OMS-WEB
chmod +x scripts/run-container-tests.sh
```

---

## 3. How to Run the Tests Manually on the Server

```bash
cd /opt/OMS-WEB
./scripts/run-container-tests.sh
```

### What Happens:
1. Pulls the latest test code from GitHub (`origin/main`).
2. Checks that `https://devoms.quantbd.com` is up and responding (`200 OK`).
3. Launches a headless Google Chrome container running Java 21 & Selenium.
4. Executes Normal Trade (BUY/SELL), queries database purchasing power (`192.168.155.22:5443`), and exports the trade files.
5. Saves the exported XML & CSV files into `/opt/OMS-WEB/exported-trades/`.

---

## 4. How to Trigger Automatically After Every Deployment

Add this line to your existing server deployment script (e.g. `deploy.sh` or post-deploy CI/CD step):

```bash
# Add at the very end of your deployment script:
echo ">>> Triggering automated OMS test suite..."
/opt/OMS-WEB/scripts/run-container-tests.sh
```

---

## 5. Environment Overrides (Optional)

If the web app or database uses specific ports or internal hostnames:

```bash
# Test against a different port or local IP:
TARGET_URL="http://localhost:3000" ./scripts/run-container-tests.sh
```
