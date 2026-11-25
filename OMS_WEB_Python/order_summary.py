"""
OMS-WEB Order Summary Widget Module
Extracts aggregate metrics: Total Orders, Buy/Sell breakdown,
Accepted, Executed, Cancelled, and Rejected order totals.
"""
import time
from typing import Dict, Optional
from selenium import webdriver
import oms_price

def navigate_to_order_summary(driver: webdriver.Chrome) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var tabs = Array.from(document.querySelectorAll('.lm_tab'));
            var s = tabs.find(function(t) { return (t.innerText || '').includes('Summary'); });
            if (s) s.click();
        """)
        time.sleep(0.4)
    except Exception:
        pass

def filter_order_summary(driver: webdriver.Chrome, client_code: str = "", symbol: str = "") -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var cln = (arguments[0] || '').trim(), sym = (arguments[1] || '').trim();
            var cInp = document.getElementById('summary_client_code') || document.querySelector('div.order-summary input[id*="client"], div[id*="summary"] input');
            if (cInp && cln) { cInp.value = cln; if (window.jQuery) $(cInp).val(cln).trigger('input').trigger('change'); }
            var sInp = document.querySelector('#summary_symbol, div[id*="summary"] input[placeholder*="Symbol" i]');
            if (sInp && sym) { sInp.value = sym; if (window.jQuery) $(sInp).val(sym).trigger('input').trigger('change'); }
        """, client_code, oms_price.to_public_symbol(symbol) if symbol else "")
        time.sleep(0.3)
    except Exception:
        pass

def get_snapshot(
    driver: webdriver.Chrome, client_code: Optional[str] = None, symbol: Optional[str] = None, preferred_exchange: str = "DSE"
) -> Dict[str, str]:
    snapshot = {
        "total_orders": "0", "buy_orders": "0", "sell_orders": "0",
        "executed_orders": "0", "accepted_orders": "0", "cancelled_orders": "0",
        "Total_Orders": "0", "Executed": "0", "Buy_Qty": "0",
        "Buy_Turnover": "0.00", "Sell_Turnover": "0.00",
    }
    if not driver:
        return snapshot
    try:
        data = driver.execute_script("""
            var metrics = {};
            document.querySelectorAll('#order_summary_table tr, table[id*="summary"] tr').forEach(function(r) {
                var cells = r.querySelectorAll('th, td');
                if (cells.length >= 2) {
                    var k = cells[0].innerText.trim().toLowerCase();
                    if (k) metrics[k] = cells[1].innerText.trim();
                }
            });
            return metrics;
        """) or {}
        for k, v in data.items():
            if "total" in k and "turn" not in k:
                snapshot["total_orders"] = snapshot["Total_Orders"] = v
            elif "buy" in k and "turn" in k:
                snapshot["Buy_Turnover"] = v
            elif "sell" in k and "turn" in k:
                snapshot["Sell_Turnover"] = v
            elif "buy" in k:
                snapshot["buy_orders"] = snapshot["Buy_Qty"] = v
            elif "sell" in k:
                snapshot["sell_orders"] = v
            elif "executed" in k or "filled" in k:
                snapshot["executed_orders"] = snapshot["Executed"] = v
            elif "accepted" in k:
                snapshot["accepted_orders"] = v
            elif "cancel" in k:
                snapshot["cancelled_orders"] = v
    except Exception:
        pass
    return snapshot

get_api_snapshot = get_snapshot
get_ui_snapshot = get_snapshot
