"""
OMS-WEB Order Summary Widget Module
Extracts aggregate metrics: Total Orders, Buy/Sell breakdown,
Accepted, Executed, Cancelled, and Rejected order totals.
"""
import time
from typing import Dict, Any, Optional
from selenium import webdriver


def navigate_to_order_summary(driver: webdriver.Chrome) -> None:
    """Navigates to Order Summary widget."""
    try:
        script = """
        var tabs = Array.from(document.querySelectorAll('.lm_tab'));
        var summaryTab = tabs.find(function(t) { return (t.innerText || '').includes('Summary'); });
        if (summaryTab) { summaryTab.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.4)
    except Exception:
        pass


def get_snapshot(
    driver: webdriver.Chrome,
    client_code: Optional[str] = None,
    symbol: Optional[str] = None
) -> Dict[str, str]:
    """Captures aggregate order counts and execution statistics."""
    script = """
    var metrics = {};
    var cards = Array.from(document.querySelectorAll('.summary-card, .metric-box, .stat-item, [id*="summary" i] td, [id*="summary" i] div'));
    cards.forEach(function(el) {
        var text = (el.innerText || '').trim();
        var lines = text.split('\\n').map(function(s) { return s.trim(); }).filter(Boolean);
        if (lines.length >= 2) {
            metrics[lines[0].toLowerCase()] = lines[1];
        }
    });

    var rows = Array.from(document.querySelectorAll('#order_summary_table tr, table[id*="summary"] tr'));
    rows.forEach(function(r) {
        var cells = Array.from(r.querySelectorAll('th, td'));
        if (cells.length >= 2) {
            var k = cells[0].innerText.trim().toLowerCase();
            var v = cells[1].innerText.trim();
            if (k) metrics[k] = v;
        }
    });
    return metrics;
    """
    snapshot = {
        "total_orders": "0",
        "buy_orders": "0",
        "sell_orders": "0",
        "executed_orders": "0",
        "accepted_orders": "0",
        "cancelled_orders": "0",
    }
    try:
        data = driver.execute_script(script) or {}
        for k, v in data.items():
            if "total" in k:
                snapshot["total_orders"] = v
            elif "buy" in k:
                snapshot["buy_orders"] = v
            elif "sell" in k:
                snapshot["sell_orders"] = v
            elif "executed" in k or "filled" in k:
                snapshot["executed_orders"] = v
            elif "accepted" in k:
                snapshot["accepted_orders"] = v
            elif "cancel" in k:
                snapshot["cancelled_orders"] = v
    except Exception:
        pass

    return snapshot
