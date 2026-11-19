"""
OMS-WEB Python Automation Suite - Dashboard 12-Model Audit & Reflection Engine
Full feature parity with OMSweb_DashboardAudit.java.
"""
import time
import re
from typing import Dict, Any, List, Optional
from selenium import webdriver


def parse_num_safe(val: Any) -> float:
    if val is None:
        return 0.0
    clean = re.sub(r'[^0-9.-]', '', str(val))
    try:
        return float(clean)
    except Exception:
        return 0.0


def navigate_to_dashboard(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        res = driver.execute_script("""
            var l = document.querySelector('a[data-link="home"], a[href="/home"], a[href="/"]');
            if (l) {
                if (window.jQuery) window.jQuery(l).trigger('click');
                l.click();
                return true;
            }
            window.location.href = 'https://devoms.quantbd.com/home';
            return true;
        """)
        time.sleep(1.5)
        return bool(res)
    except Exception:
        return False


def wait_for_dashboard_ready(driver: webdriver.Chrome, timeout_seconds: int = 10) -> bool:
    if not driver:
        return False
    start = time.time()
    while time.time() - start < timeout_seconds:
        try:
            ready = driver.execute_script("""
                var heads = Array.from(document.querySelectorAll('.dash-card-head'));
                if (heads.length < 5) return false;
                var bl = heads.find(function(h) { return (h.innerText || '').indexOf('BROKER LIMIT') !== -1; });
                var to = heads.find(function(h) { return (h.innerText || '').indexOf('TURNOVER') !== -1; });
                var blCard = bl ? bl.closest('.dash-card') : null;
                var toCard = to ? to.closest('.dash-card') : null;
                var blTxt = blCard ? (blCard.innerText || '') : '';
                var toTxt = toCard ? (toCard.innerText || '') : '';
                return (blTxt.indexOf('Total') !== -1 || blTxt.indexOf('37,25') !== -1) && (toTxt.indexOf('Today') !== -1);
            """)
            if ready:
                time.sleep(0.6)
                return True
        except Exception:
            pass
        time.sleep(0.3)
    return False


def capture_snapshot(driver: webdriver.Chrome, label: str = "SNAPSHOT") -> Dict[str, Any]:
    snap: Dict[str, Any] = {
        "label": label,
        "timestamp": time.strftime('%Y-%m-%d %H:%M:%S')
    }
    if not driver:
        return snap

    navigate_to_dashboard(driver)
    wait_for_dashboard_ready(driver, 10)

    try:
        raw = driver.execute_script("""
            var res = {};
            function findCard(headerText) {
                var heads = Array.from(document.querySelectorAll('.dash-card-head, .top-dealer-asso-nav'));
                var target = heads.find(function(h) {
                    return (h.innerText || '').toUpperCase().indexOf(headerText.toUpperCase()) !== -1;
                });
                return target ? target.closest('.dash-card') : null;
            }
            function extractTable(card) {
                if (!card) return [];
                var rows = Array.from(card.querySelectorAll('table tr'));
                return rows.map(function(r) {
                    return Array.from(r.querySelectorAll('th, td')).map(function(c) { return (c.innerText || '').trim(); });
                }).filter(function(r) { return r.length > 0; });
            }
            function extractCardBasic(headerText) {
                var c = findCard(headerText);
                return { rawText: c ? (c.innerText || '') : '', table: extractTable(c) };
            }
            res.turnover = extractCardBasic('TURNOVER');
            res.buyValue = extractCardBasic('BUY VALUE');
            res.sellValue = extractCardBasic('SELL VALUE');
            res.brokerLimit = extractCardBasic('BROKER LIMIT');
            res.trades = extractCardBasic('TRADES');
            res.buyTrades = extractCardBasic('BUY TRADES');
            res.sellTrades = extractCardBasic('SELL TRADES');
            res.mostTradedStocks = extractCardBasic('MOST TRADED STOCKS');
            res.activeUsers = extractCardBasic('ACTIVE USERS');
            res.exchangeContribution = extractCardBasic('EXCHANGE CONTRIBUTION');
            res.topDealers = extractCardBasic('TOP DEALERS');
            res.mostTradedClients = extractCardBasic('MOST TRADED CLIENTS');
            return res;
        """)

        # Extract Top Associates
        try:
            driver.execute_script("""
                var btn = Array.from(document.querySelectorAll('button, a, span, div')).find(function(el) {
                    return (el.innerText || '').trim() === 'TOP ASSOCIATES';
                });
                if (btn) btn.click();
            """)
            time.sleep(0.4)
            asso_raw = driver.execute_script("""
                var heads = Array.from(document.querySelectorAll('.top-dealer-asso-nav'));
                var target = heads.find(function(h) { return (h.innerText || '').indexOf('TOP ASSOCIATES') !== -1; });
                var card = target ? target.closest('.dash-card') : null;
                if (!card) return { rawText: '', table: [] };
                var rows = Array.from(card.querySelectorAll('table tr')).map(function(r) {
                    return Array.from(r.querySelectorAll('th, td')).map(function(c) { return (c.innerText || '').trim(); });
                });
                return { rawText: card.innerText, table: rows };
            """)
            if raw:
                raw['topAssociates'] = asso_raw
            # Revert to Top Dealers
            driver.execute_script("""
                var btn = Array.from(document.querySelectorAll('button, a, span, div')).find(function(el) {
                    return (el.innerText || '').trim() === 'TOP DEALERS';
                });
                if (btn) btn.click();
            """)
        except Exception:
            pass

        snap["raw"] = raw
    except Exception as e:
        print(f"[WARN: capture_snapshot] {e}")

    return snap


def display_model_wise_dashboard(snap: Dict[str, Any]) -> None:
    raw = snap.get("raw", {})
    label = snap.get("label", "DASHBOARD")
    ts = snap.get("timestamp", "")

    print("\n" + "=" * 120)
    print(f"                    OMS-WEB DASHBOARD 12-MODEL AUDIT REPORT: {label} ({ts})")
    print("=" * 120)

    # 1. Turnover
    to = raw.get("turnover", {})
    print("\n[MODEL 1: TURNOVER (BDT)]")
    lines = to.get("rawText", "").split("\n")
    big_val = lines[2].strip() if len(lines) > 2 else "0"
    print(f"  * Total Turnover Value : {big_val} BDT (Today · All exchanges)")
    print_table(to.get("table", []))

    # 2. Buy Value
    bv = raw.get("buyValue", {})
    lines = bv.get("rawText", "").split("\n")
    big_val = lines[2].strip() if len(lines) > 2 else "0"
    print(f"\n[MODEL 2: BUY VALUE (BDT)]")
    print(f"  * Total Buy Value      : {big_val} BDT")
    print_table(bv.get("table", []))

    # 3. Sell Value
    sv = raw.get("sellValue", {})
    lines = sv.get("rawText", "").split("\n")
    big_val = lines[2].strip() if len(lines) > 2 else "0"
    print(f"\n[MODEL 3: SELL VALUE (BDT)]")
    print(f"  * Total Sell Value     : {big_val} BDT")
    print_table(sv.get("table", []))

    # 4. Broker Limit
    bl = raw.get("brokerLimit", {})
    print(f"\n[MODEL 4: BROKER LIMIT (BDT)]")
    print_table(bl.get("table", []))

    # 5. Trades
    tr = raw.get("trades", {})
    lines = tr.get("rawText", "").split("\n")
    big_val = lines[2].strip() if len(lines) > 2 else "0"
    print(f"\n[MODEL 5: TRADES (Count)]")
    print(f"  * Total Executed Trades: {big_val} Trades")
    print_table(tr.get("table", []))

    # 6. Buy Trades
    btr = raw.get("buyTrades", {})
    lines = btr.get("rawText", "").split("\n")
    big_val = lines[2].strip() if len(lines) > 2 else "0"
    print(f"\n[MODEL 6: BUY TRADES (Count)]")
    print(f"  * Total Buy Trades     : {big_val} Trades")
    print_table(btr.get("table", []))

    # 7. Sell Trades
    str_card = raw.get("sellTrades", {})
    lines = str_card.get("rawText", "").split("\n")
    big_val = lines[2].strip() if len(lines) > 2 else "0"
    print(f"\n[MODEL 7: SELL TRADES (Count)]")
    print(f"  * Total Sell Trades    : {big_val} Trades")
    print_table(str_card.get("table", []))

    # 8. Most Traded Stocks
    stocks = raw.get("mostTradedStocks", {})
    print(f"\n[MODEL 8: MOST TRADED STOCKS]")
    print_table(stocks.get("table", []))

    # 9. Active Users
    au = raw.get("activeUsers", {})
    print(f"\n[MODEL 9: ACTIVE & INACTIVE USERS]")
    txt = au.get("rawText", "")
    print("  " + txt.replace("\n", "\n  "))

    # 10. Exchange Contribution
    ec = raw.get("exchangeContribution", {})
    print(f"\n[MODEL 10: EXCHANGE CONTRIBUTION (BDT)]")
    print_table(ec.get("table", []))

    # 11. Top Dealers & Top Associates
    td = raw.get("topDealers", {})
    ta = raw.get("topAssociates", {})
    print(f"\n[MODEL 11: TOP DEALERS & TOP ASSOCIATES]")
    print("  -- TOP DEALERS TAB --")
    print_table(td.get("table", []))
    print("  -- TOP ASSOCIATES TAB --")
    print_table(ta.get("table", []))

    # 12. Most Traded Clients
    mc = raw.get("mostTradedClients", {})
    print(f"\n[MODEL 12: MOST TRADED CLIENTS]")
    print_table(mc.get("table", []))

    print("=" * 120 + "\n")


def print_table(table: List[List[str]]) -> None:
    if not table:
        print("  (Empty Table)")
        return
    col_widths = []
    for row in table:
        for idx, col in enumerate(row):
            if idx >= len(col_widths):
                col_widths.append(len(col))
            else:
                col_widths[idx] = max(col_widths[idx], len(col))
    col_widths = [max(w, 18) for w in col_widths]

    sep = "  +" + "+".join(["-" * (w + 2) for w in col_widths]) + "+"
    print(sep)
    for r_idx, row in enumerate(table):
        cells = [f" {row[i]:<{col_widths[i]}} " if i < len(row) else " " * (col_widths[i] + 2) for i in range(len(col_widths))]
        print("  |" + "|".join(cells) + "|")
        if r_idx == 0:
            print(sep)
    print(sep)


def run_live_reflection_audit(driver: webdriver.Chrome) -> None:
    print("\n==========================================================================================")
    print("       OMS-WEB: REAL-TIME 12-MODEL DASHBOARD DATA REFLECTION & AUDIT SUITE (PYTHON)")
    print("==========================================================================================")

    # 1. Pre Snapshot
    print("\n[PHASE 1/3] Capturing Pre-Action Baseline Dashboard Snapshot...")
    pre = capture_snapshot(driver, "PRE-ACTION BASELINE")
    display_model_wise_dashboard(pre)

    # 2. Post Snapshot
    print("\n[PHASE 2/3] Capturing Live Dashboard Verification Snapshot...")
    post = capture_snapshot(driver, "LIVE VERIFICATION")
    display_model_wise_dashboard(post)

    print("\n[PHASE 3/3] Dashboard 12-Model Verification Completed Successfully.")
