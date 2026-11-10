# CI/CD Pipeline & Automation Guide

This document describes the Continuous Integration (CI) and Continuous Delivery (CD) pipeline configured for the **OMS-WEB Automation Suite** ([hr9294/OMS-WEB](https://github.com/hr9294/OMS-WEB)).

---

## 1. Pipeline Architecture

```
                    ┌───────────────────────────────┐
                    │  Developer / Eclipse IDE      │
                    │      `git push origin main`   │
                    └───────────────┬───────────────┘
                                    │
                                    ▼
       ┌────────────────────────────────────────────────────────┐
       │             GitHub Actions CI/CD Workflows             │
       ├────────────────────────────┬───────────────────────────┤
       │                            │                           │
       ▼                            ▼                           ▼
┌──────────────┐           ┌──────────────────┐        ┌─────────────────┐
│  ci.yml      │           │ trade-           │        │ Artifacts Store │
│  (Every      │           │ automation.yml   │        │ (XML, CSV,      │
│  Push/PR)    │           │ (Daily Schedule  │        │ Screenshots,    │
│  Build/Check │           │ & On-Demand)     │        │ Logs)           │
└──────────────┘           └──────────────────┘        └─────────────────┘
```

---

## 2. Configured Workflows

| Workflow | File | Triggers | Description |
| :--- | :--- | :--- | :--- |
| **CI Build & Verification** | `.github/workflows/ci.yml` | Every `push` to `main`, `pull_request`, or Manual | Compiles the full suite with Maven on JDK 21 and validates all 15 class outputs. |
| **Daily Trade Automation** | `.github/workflows/trade-automation.yml` | `cron: 30 3 * * 0-4` (9:30 AM BD Time, Sun-Thu) or Manual | Executes trade engine, matches BUY/SELL pair, and automatically exports XML/CSV trade files as downloadable GitHub artifacts. |

---

## 3. How to Trigger Workflows Manually

1. Open **[GitHub Actions](https://github.com/hr9294/OMS-WEB/actions)** in your browser.
2. In the left sidebar, click **OMS Trade Automation & Export Pipeline** (or **OMS Automation CI Pipeline**).
3. Click the **Run workflow** dropdown on the right side.
4. Select your mode:
   - `NORMAL_TRADE` *(Default)*
   - `TRADE_EXPORT`
   - `MULTI_ORDER`
5. Select runner: `windows-latest` *(Cloud)* or `self-hosted` *(Local)*.
6. Click **Run workflow**.

---

## 4. Downloading Exported Trade Files from CI/CD

When a workflow run completes:
1. Click on the completed run in the **Actions** tab.
2. Scroll to the bottom to the **Artifacts** section.
3. Click `oms-trade-files-<run_id>` to download a zip archive containing:
   - `*_TRADES.xml`
   - `*_TRADES.csv`
   - Execution logs and screenshots

---

## 5. Local Maven Commands

You can run the suite locally using standard Maven commands without opening Eclipse:

```powershell
# Compile the entire project
mvn clean compile

# Run the main automation suite (Normal Trade & Export)
mvn exec:java -Dexec.mainClass="OMSwebmain"

# Run in headless mode (background / CI mode)
$env:HEADLESS = "true"
mvn exec:java -Dexec.mainClass="OMSwebmain"
```

---

## 6. Connecting a Local Self-Hosted Runner (Optional)

If you want GitHub Actions to run on this machine (with direct visibility into Chrome and access to internal DB `192.168.155.22:5443`):
1. Run `.\scripts\setup-github-runner.ps1` in PowerShell.
2. Follow the 3-step registration from [GitHub Runner Settings](https://github.com/hr9294/OMS-WEB/settings/actions/runners/new?arch=x64&os=win).
