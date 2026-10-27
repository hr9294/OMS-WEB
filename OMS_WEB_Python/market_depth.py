"""
OMS-WEB Market Depth & Quote Viewer Module
Inspects top 5 Bid & Ask market depth, order book queue, and live quote snapshots.
"""
import json
import time
from typing import Dict, Any, Optional
from selenium import webdriver

from config import SHARED_MARKET_DEPTH_URL
import oms_price


def navigate_to_market_depth(driver: webdriver.Chrome) -> None:
    """Navigates to Market Depth widget."""
    try:
        script = """
        var tabs = Array.from(document.querySelectorAll('.lm_tab'));
        var depthTab = tabs.find(function(t) { return (t.innerText || '').includes('Depth'); });
        if (depthTab) { depthTab.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.4)
    except Exception:
        pass


def search_symbol_in_depth(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> None:
    """Inputs symbol into Market Depth search input."""
    try:
        script = """
        var sym = arguments[0];
        var inp = document.querySelector('#depth_symbol, input[id*="depth" i]');
        if (inp) {
            inp.value = sym;
            inp.dispatchEvent(new Event('input', { bubbles: true }));
            inp.dispatchEvent(new Event('change', { bubbles: true }));
        }
        """
        driver.execute_script(script, symbol)
        time.sleep(0.4)
    except Exception:
        pass


def get_snapshot(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    """Captures snapshot of Market Depth quote and order book."""
    sym = oms_price.clean_symbol(symbol)
    ex = (exchange or "DSE").upper()

    script = """
    var sym = arguments[0];
    var ex = arguments[1];
    var url = '/shared/marketdepth/?symbol=' + encodeURIComponent(sym) + '&exchange=' + encodeURIComponent(ex);
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url, false);
    try {
        xhr.send();
        if (xhr.status === 200) {
            return xhr.responseText;
        }
    } catch(e) {}
    return null;
    """
    snapshot = {
        "symbol": sym,
        "exchange": ex,
        "ltp": "0.00",
        "ycp": "0.00",
        "high": "0.00",
        "low": "0.00",
        "total_buy_qty": "0",
        "total_sell_qty": "0",
    }

    try:
        resp = driver.execute_script(script, sym, ex)
        if resp:
            data = json.loads(resp)
            if isinstance(data, dict):
                snapshot["ltp"] = str(data.get("ltp") or data.get("LTP") or "0.00")
                snapshot["ycp"] = str(data.get("ycp") or data.get("close") or "0.00")
                snapshot["high"] = str(data.get("high") or "0.00")
                snapshot["low"] = str(data.get("low") or "0.00")
                snapshot["total_buy_qty"] = str(data.get("total_buy_qty") or data.get("buy_volume") or "0")
                snapshot["total_sell_qty"] = str(data.get("total_sell_qty") or data.get("sell_volume") or "0")
    except Exception:
        pass

    return snapshot
