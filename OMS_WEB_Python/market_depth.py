"""
OMS-WEB Market Depth & Quote Viewer Module
Inspects top 5 Bid & Ask market depth, order book queue, and live quote snapshots.
"""
import json
import time
from typing import Dict
from selenium import webdriver

import oms_price


def navigate_to_market_depth(driver: webdriver.Chrome) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var tabs = Array.from(document.querySelectorAll('.lm_tab'));
            var d = tabs.find(function(t) { return (t.innerText || '').includes('Depth'); });
            if (d) d.click();
        """)
        time.sleep(0.4)
    except Exception:
        pass


def search_symbol_in_depth(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> None:
    """Retains Public symbol format (e.g. GP.PUBLIC) and triggers getmktdepth."""
    if not driver or not symbol:
        return
    pub_sym = oms_price.to_public_symbol(symbol)
    base_sym = oms_price.clean_symbol(symbol)
    ex = (exchange or "DSE").strip().upper()
    try:
        driver.execute_script("""
            var pubSym = (arguments[0] || '').trim().toUpperCase();
            var baseSym = (arguments[1] || '').trim().toUpperCase();
            var ex = (arguments[2] || 'DSE').trim().toUpperCase();
            document.querySelectorAll('select[id*="exchange_market_depth"], select[id*="exchange_mktdpt"], select.md_exch').forEach(function(s) {
                for (var o = 0; o < s.options.length; o++) {
                    if (s.options[o].text.trim().toUpperCase() === ex || s.options[o].value.trim().toUpperCase() === ex) {
                        s.selectedIndex = o;
                        if (window.jQuery) $(s).trigger('change');
                        try { change_mkt_exchange(ex, s.id); } catch(e) {}
                        break;
                    }
                }
            });
            document.querySelectorAll('input[id*="symbol_market_depth"], input[id*="symbol_mktdpt"], input.mktdpt_symbol_name, #depth_symbol').forEach(function(i) {
                i.value = pubSym;
                if (window.jQuery) $(i).val(pubSym).trigger('input').trigger('change');
                else { i.dispatchEvent(new Event('input', {bubbles:true})); i.dispatchEvent(new Event('change', {bubbles:true})); }
                if (typeof getmktdepth === 'function') {
                    try { getmktdepth(pubSym, i.id); } catch(e) { try { getmktdepth(baseSym, i.id); } catch(e2){} }
                }
            });
        """, pub_sym, base_sym, ex)
        time.sleep(0.4)
    except Exception:
        pass


def get_api_snapshot(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    sym = oms_price.clean_symbol(symbol)
    ex = (exchange or "DSE").upper()
    snapshot = {
        "symbol": sym, "exchange": ex, "ltp": "0.00", "ycp": "0.00",
        "high": "0.00", "low": "0.00", "total_buy_qty": "0", "total_sell_qty": "0",
    }
    if not driver:
        return snapshot
    try:
        resp = driver.execute_script("""
            var xhr = new XMLHttpRequest();
            xhr.open('GET', '/shared/marketdepth/?symbol=' + encodeURIComponent(arguments[0]) + '&exchange=' + encodeURIComponent(arguments[1]), false);
            try { xhr.send(); if (xhr.status === 200) return xhr.responseText; } catch(e) {}
            return null;
        """, sym, ex)
        if resp:
            data = json.loads(resp)
            if isinstance(data, dict):
                sd = data.get("symbol_data") if isinstance(data.get("symbol_data"), dict) else data
                snapshot["ltp"] = str(sd.get("ltp") or sd.get("LTP") or "0.00")
                snapshot["LTP"] = snapshot["ltp"]
                snapshot["ycp"] = str(sd.get("ycp") or sd.get("close") or "0.00")
                snapshot["YCP"] = snapshot["ycp"]
                snapshot["high"] = str(sd.get("high") or "0.00")
                snapshot["low"] = str(sd.get("low") or "0.00")
                snapshot["SH"] = str(sd.get("circuit_up") or sd.get("SH") or "0.00")
                snapshot["SL"] = str(sd.get("circuit_down") or sd.get("SL") or "0.00")
                snapshot["total_buy_qty"] = str(sd.get("total_buy_qty") or sd.get("buy_volume") or "0")
                snapshot["total_sell_qty"] = str(sd.get("total_sell_qty") or sd.get("sell_volume") or "0")
    except Exception:
        pass
    return snapshot


def get_ui_snapshot(driver: webdriver.Chrome) -> Dict[str, str]:
    if not driver:
        return {}
    try:
        return driver.execute_script("""
            var res = { LTP: '-', HIGH: '-', LOW: '-', 'BUY%': '-', 'SELL%': '-' };
            var symTables = document.querySelectorAll('div[id^="symdata_"] table, table.marketdepth-table');
            if (symTables.length > 0) {
                var rows = symTables[0].querySelectorAll('tr');
                if (rows.length >= 2) {
                    var h1 = rows[0].querySelectorAll('td,th'), v1 = rows[1].querySelectorAll('td');
                    for (var i = 0; i < h1.length && i < v1.length; i++) {
                        var k = h1[i].innerText.trim().toUpperCase();
                        if (k === 'LTP') res.LTP = v1[i].innerText.trim();
                        if (k === 'HIGH') res.HIGH = v1[i].innerText.trim();
                        if (k === 'LOW') res.LOW = v1[i].innerText.trim();
                    }
                }
            }
            var bs = document.querySelector('span[class*="buypercent"]'), ss = document.querySelector('span[class*="sellpercent"]');
            if (bs) res['BUY%'] = bs.innerText.trim();
            if (ss) res['SELL%'] = ss.innerText.trim();
            return res;
        """) or {}
    except Exception:
        return {}


def get_snapshot(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    sym = oms_price.clean_symbol(symbol)
    ex = (exchange or "DSE").upper()
    try:
        from db_market_data import fetch_complete_market_depth
        db_depth = fetch_complete_market_depth(sym, ex)
        if db_depth and (db_depth.get("LTP") or db_depth.get("Best_Bid_Price")):
            return db_depth
    except Exception:
        pass
    return get_api_snapshot(driver, sym, ex)
