# OMS-WEB Python Automation & Load Testing Suite

A complete, modern, standalone Python implementation of the **UFTFAST Order Management System (OMS-WEB)** automation platform.

---

## 🌟 Key Features

1. **Anti-Detection Chrome WebDriver (`driver_factory.py`)**:
   - Disables automation flags (`excludeSwitches: ["enable-automation"]`, `useAutomationExtension: false`, `--disable-blink-features=AutomationControlled`).
   - Masks `navigator.webdriver = undefined`.
   - Auto-resolves ChromeDriver via `webdriver-manager` with local fallbacks.

2. **Automated Authentication & Modal Management (`oms_login.py`)**:
   - Credentials input (`#username`, `#password`).
   - Concurrent active session override detection (`#continue_to_force_login`).
   - Auto-dismisses breach popups and system alerts.

3. **Widget Color Linking FIRST (`widget_color.py`)**:
   - Links all 6 GoldenLayout widgets (Trade, Marketwatch, Market Depth, Order Terminal, Position, Client Portfolio) to the selected color channel (**Turquoise**, **Yellow**, **Orange**, **Green**, **Red**, **Pink**).
   - Executed **FIRST** immediately upon loading the Trade page before symbol input and order placement.

4. **Multi-Order Execution Engine (`oms_multi_order.py`)**:
   - **Dynamic High-Cash Client Discovery**: Automatically filters clients with Cash $\ge 100,000\text{ BDT}$, valid BO ID ($\ge 8\text{ digits}$), and Active status, randomly selecting 4–5 clients for BUY orders.
   - **Holding Client Discovery**: Queries Position widget for real holding clients with saleable shares for SELL orders.
   - **Non-Crossing Limit Pricing**: BUY orders placed at down prices ($< P_{ref}$), SELL orders placed at upper prices ($> P_{ref}$) so orders remain `Accepted` with `0%` progress on placement.
   - **Order Terminal Right-Click "Execute at Market"**: Automatically checks target order rows, triggers right-click context menu, selects **"Execute at Market"**, confirms dialog, and verifies fills.
   - **Real-Time 6-Widget Audit**: Captures baseline snapshots before placement and compares against post-execution state across all 6 widgets.

5. **High-Speed Multi-Trade & Load Testing Engine (`oms_multiple_trade.py`)**:
   - Automated rapid trade loops with real-time throughput metrics (TPS), latency, and execution summaries.

6. **Full Widget Viewers & API Extractors**:
   - `position.py`: Position & saleable holdings viewer.
   - `client_portfolio.py`: Direct `/shared/getclientdata/?cln_code=` JSON API extractor.
   - `market_depth.py`: 5-tier bid/ask depth and live quotes.
   - `order_summary.py`: Aggregate order statistics.
   - `order_terminal.py`: Order terminal inspection & status auditing.

---

## 🚀 Running in Visual Studio Code

### Method 1: One-Click Run (F5)
1. Open this folder in VS Code (`code .`).
2. Press **F5** (or open the **Run and Debug** sidebar on the left).
3. Select any of the pre-configured run profiles:
   - `🚀 OMS Web: Interactive Main Runner (main.py)`
   - `⚡ OMS Web: Multi-Order Execution & Market Trigger`
   - `🏎️ OMS Web: High-Speed Multi-Trade Load Tester`
   - `📊 OMS Web: Standard Trade & 6-Widget Verification`
   - `🔍 OMS Web: Account Limits & High-Cash Client Discovery`

### Method 2: Terminal Execution
Open a terminal in the `OMS_WEB_Python` directory and run:

```bash
# Interactive Main Runner
python main.py

# Direct Multi-Order Execution Engine
python main.py --mode multi-order

# Direct High-Speed Load Testing
python main.py --mode load-test

# Headless execution
python main.py --mode multi-order --headless
```

---

## 📦 Requirements & Installation

Dependencies are pre-installed in your environment. If installing into a new environment:

```bash
pip install -r requirements.txt
```
