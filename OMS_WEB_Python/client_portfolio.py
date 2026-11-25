"""
OMS-WEB Client Portfolio Module
Fetches client financial summary, cash limits, BO IDs, and equity holdings
via /shared/getsaleable/ and /shared/getclientdata/ APIs and UI DOM table.
"""
import json
import re
import time
from typing import Dict, Optional
from selenium import webdriver

import oms_price


def default_zero_portfolio(symbol: str = "GP") -> Dict[str, str]:
    return {
        "symbol": oms_price.clean_symbol(symbol) or "GP",
        "total_qty": "0",
        "saleable_qty": "0",
        "avg_cost": "0.00",
        "total_cost": "0.00",
        "ltp": "0.00",
        "value": "0.00",
        "gain": "0.00",
        "gain_percent": "0.00",
    }


def navigate_to_portfolio(driver: webdriver.Chrome) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var tab = document.querySelector('.lm_tab[title*="Portfolio"], .lm_tab[title*="portfolio"]');
            if (tab && !tab.classList.contains('lm_active')) tab.click();
        """)
        time.sleep(0.2)
    except Exception:
        pass


def get_client_details(driver: webdriver.Chrome, client_code: str) -> Dict[str, str]:
    code = (client_code or "").strip()
    if not code or not driver:
        return {"client_code": code, "cash_limit": "0.00", "mature_balance": "0.00", "bo_id": "-", "status": "Active", "client_name": f"Client {code}"}

    script = """
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/shared/getclientdata/?cln_code=' + encodeURIComponent(arguments[0]), false);
    try { xhr.send(); if (xhr.status === 200) return xhr.responseText; } catch(e) {}
    return null;
    """
    try:
        raw_resp = driver.execute_script(script, code)
        if raw_resp:
            data = json.loads(raw_resp)
            row = data[0] if isinstance(data, list) and data and isinstance(data[0], dict) else (data if isinstance(data, dict) else None)
            if row:
                out = {str(k).lower(): (str(v) if v is not None else "") for k, v in row.items()}
                out["client_code"] = code
                out.setdefault("client_name", out.get("cln_name") or out.get("name") or f"Client {code}")
                out.setdefault("bo_id", out.get("cln_bo") or out.get("boid") or "-")
                out.setdefault("cash_limit", out.get("b_limit") or out.get("limit") or out.get("cash") or "0.00")
                out.setdefault("mature_balance", out.get("rem_mature_balance") or out.get("mature_balance") or "0.00")
                out.setdefault("status", out.get("cln_status") or out.get("trade_status") or "Active")
                return out
    except Exception as e:
        print(f"[PORTFOLIO API: WARN] Error fetching details for {code}: {e}")

    return {"client_code": code, "cash_limit": "0.00", "mature_balance": "0.00", "bo_id": "-", "status": "Active", "client_name": f"Client {code}"}


def _extract_portfolio_row_from_json(raw_json: str, clean_sym: str) -> Dict[str, str]:
    if not raw_json or raw_json.strip() in ("", "[]"):
        return {}
    try:
        parsed = json.loads(raw_json)
        items = parsed if isinstance(parsed, list) else (parsed.get("data", []) if isinstance(parsed, dict) else [])
        for it in items:
            if not isinstance(it, dict):
                continue
            row_sym = oms_price.clean_symbol(str(it.get("symbol", "")))
            if row_sym == clean_sym:
                return {
                    "symbol": clean_sym,
                    "total_qty": str(it.get("total_qty") or it.get("totalqty") or "0"),
                    "saleable_qty": str(it.get("saleable_qty") or it.get("qty") or "0"),
                    "avg_cost": str(it.get("avg_cost") or it.get("avgcost") or "0.00"),
                    "total_cost": str(it.get("total_cost") or it.get("totalcost") or "0.00"),
                    "ltp": str(it.get("ltp") or "0.00"),
                    "value": str(it.get("value") or "0.00"),
                    "gain": str(it.get("gain") or "0.00"),
                    "gain_percent": str(it.get("gain_percent") or it.get("gain_per") or "0.00"),
                }
        if any(k in raw_json for k in ("sum_totalcost", "data", "success")) or raw_json.strip().startswith("[["):
            return default_zero_portfolio(clean_sym)
    except Exception:
        m = re.search(r'\{[^{}]*"symbol"\s*:\s*"' + re.escape(clean_sym) + r'"[^{}]*\}', raw_json, re.I)
        if m:
            blk = m.group(0)
            def _gv(*keys):
                for k in keys:
                    km = re.search(rf'"{k}"\s*:\s*"?([^",}}]+)"?', blk, re.I)
                    if km:
                        return km.group(1).strip()
                return "0"
            return {
                "symbol": clean_sym,
                "total_qty": _gv("total_qty", "totalqty"),
                "saleable_qty": _gv("saleable_qty", "qty"),
                "avg_cost": _gv("avg_cost", "avgcost"),
                "total_cost": _gv("total_cost", "totalcost"),
                "ltp": _gv("ltp"),
                "value": _gv("value"),
                "gain": _gv("gain"),
                "gain_percent": _gv("gain_percent", "gain_per"),
            }
    return {}


def fetch_from_admin_shared_api(driver: webdriver.Chrome, client_code: str, target_symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    if not driver or not client_code:
        return {}
    clean_sym = oms_price.clean_symbol(target_symbol)
    ex = (exchange or "DSE").strip().upper()
    try:
        raw = driver.execute_async_script("""
            var cb = arguments[arguments.length - 1];
            fetch('/shared/getsaleable/?clientcode=' + encodeURIComponent(arguments[0]) + '&exchange=' + encodeURIComponent(arguments[1]),
                  { headers: { 'Accept': 'application/json' }, credentials: 'include' })
            .then(function(r){ return r.text(); })
            .then(function(t){ cb(t); })
            .catch(function(){ cb(null); });
        """, client_code.strip(), ex)
        if raw:
            res = _extract_portfolio_row_from_json(raw, clean_sym)
            if res:
                res["DATA_SOURCE"] = f"API ({ex})"
            return res
    except Exception:
        pass
    return {}


def get_api_snapshot(driver: webdriver.Chrome, client_code: str, symbol: str = "GP", preferred_exchange: str = "DSE") -> Dict[str, str]:
    ex = (preferred_exchange or "DSE").strip().upper()
    return fetch_from_admin_shared_api(driver, client_code, oms_price.clean_symbol(symbol), ex)


def _fetch_from_ui_table(driver: webdriver.Chrome, symbol: str) -> Dict[str, str]:
    clean_sym = oms_price.clean_symbol(symbol)
    if not driver:
        return default_zero_portfolio(clean_sym)
    try:
        res = driver.execute_script("""
            var sym = arguments[0].trim().toUpperCase();
            var tbl = document.getElementById('symbol_position_table') || document.querySelector('div[id*="portfolio"] table, #account-portfolio table');
            if (!tbl) return null;
            var headers = Array.from(tbl.querySelectorAll('thead th, tr:first-child th')).map(function(h){ return h.innerText.trim(); });
            var rows = tbl.querySelectorAll('tbody tr');
            for (var i = 0; i < rows.length; i++) {
                var cells = rows[i].querySelectorAll('td');
                if (!cells.length) continue;
                var rSym = (cells[0].innerText || '').trim().toUpperCase().replace(/\\[.*?\\]/g,'').split('.')[0].trim();
                if (rSym === sym || (sym && rSym.indexOf(sym) !== -1)) {
                    var obj = {};
                    for (var k = 0; k < headers.length && k < cells.length; k++) {
                        var ht = headers[k].toUpperCase(), v = cells[k].innerText.trim();
                        if (ht.indexOf('TOTAL') !== -1 && ht.indexOf('QTY') !== -1) obj['total_qty'] = v;
                        else if (ht.indexOf('SALE') !== -1) obj['saleable_qty'] = v;
                        else if (ht.indexOf('AVG') !== -1) obj['avg_cost'] = v;
                        else if (ht.indexOf('TOTAL') !== -1 && ht.indexOf('COST') !== -1) obj['total_cost'] = v;
                        else if (ht === 'LTP' || ht.indexOf('RATE') !== -1) obj['ltp'] = v;
                        else if (ht.indexOf('VALUE') !== -1) obj['value'] = v;
                    }
                    return obj;
                }
            }
            return null;
        """, clean_sym)
        if isinstance(res, dict) and res:
            res.setdefault("symbol", clean_sym)
            return res
    except Exception:
        pass
    return default_zero_portfolio(clean_sym)


def get_ui_snapshot(driver: webdriver.Chrome, client_code: str, symbol: str = "GP", exchange: str = "DSE") -> Dict[str, str]:
    clean_sym = oms_price.clean_symbol(symbol)
    ex = (exchange or "DSE").strip().upper()
    ensure_client_loaded_in_ui(driver, client_code)
    api = fetch_from_admin_shared_api(driver, client_code, clean_sym, ex)
    if api:
        return api
    ui = _fetch_from_ui_table(driver, clean_sym)
    ui["DATA_SOURCE"] = f"UI Table DOM ({ex})"
    return ui


def get_snapshot(driver: webdriver.Chrome, client_code: str, symbol: Optional[str] = None, preferred_exchange: str = "DSE") -> Dict[str, str]:
    if symbol:
        ex_order = [preferred_exchange.upper()] + [e for e in ("DSE", "CSE") if e != preferred_exchange.upper()]
        for ex in ex_order:
            api = get_api_snapshot(driver, client_code, symbol, ex)
            if api:
                return api
        return _fetch_from_ui_table(driver, symbol)
    return get_client_details(driver, client_code)


def ensure_client_loaded_in_ui(driver: webdriver.Chrome, client_code: str) -> None:
    if not driver or not client_code:
        return
    try:
        driver.execute_script("""
            var code = arguments[0];
            var inp = document.getElementById('portfolio_code_input') || document.querySelector('input[id*="portfolio"], #account-portfolio input');
            if (inp) { inp.value = code; try { $(inp).trigger('input').trigger('change'); } catch(e){} }
            if (typeof selectSaleable === 'function') selectSaleable(code);
        """, str(client_code).strip())
        time.sleep(0.4)
    except Exception:
        pass


def verify(
    before: Optional[Dict[str, str]],
    after: Optional[Dict[str, str]],
    symbol: str = "GP",
    side: str = "BUY",
    trade_qty: int = 1,
    trade_rate: float = 0.0,
    exchange: str = "DSE",
    client_before: Optional[Dict[str, str]] = None,
    client_after: Optional[Dict[str, str]] = None,
    executed_qty: int = 0,
    due_qty: int = 0,
) -> bool:
    before = before or {}
    after = after or {}
    is_buy = (side or "BUY").upper() == "BUY"
    old_tot = oms_price.parse_float(before.get("total_qty", before.get("totalqty")))
    new_tot = oms_price.parse_float(after.get("total_qty", after.get("totalqty")))
    old_sal = oms_price.parse_float(before.get("saleable_qty", before.get("saleable", before.get("qty"))))
    new_sal = oms_price.parse_float(after.get("saleable_qty", after.get("saleable", after.get("qty"))))

    eff_exec = executed_qty if executed_qty > 0 else (int(abs(new_tot - old_tot)) or trade_qty)
    expected_tot = (old_tot + eff_exec) if is_buy else (old_tot - eff_exec)
    qty_pass = abs(new_tot - expected_tot) < 0.001
    sal_pass = (
        (abs(new_sal - old_sal) < 0.001 or abs(new_sal - (old_sal + eff_exec)) < 0.001)
        if is_buy
        else abs(new_sal - (old_sal - eff_exec)) < 0.001
    )

    old_cost = oms_price.parse_float(before.get("total_cost", before.get("totalcost")))
    new_cost = oms_price.parse_float(after.get("total_cost", after.get("totalcost")))
    cost_pass = (new_cost >= old_cost or eff_exec == 0) if is_buy else (new_cost <= old_cost or eff_exec == 0)

    print(f"[PORTFOLIO AUDIT] {side.upper()} {eff_exec} {symbol} @ {trade_rate:.2f} ({exchange}) | "
          f"Tot: {old_tot:.0f}->{new_tot:.0f} ({'PASS' if qty_pass else 'FAIL'}) | "
          f"Sal: {old_sal:.0f}->{new_sal:.0f} ({'PASS' if sal_pass else 'FAIL'}) | "
          f"Cost: {old_cost:.2f}->{new_cost:.2f} ({'PASS' if cost_pass else 'FAIL'})")
    return qty_pass and sal_pass and cost_pass


def print_portfolio(client_code: str, snapshot: Dict[str, str]) -> None:
    print(f"\n+--- CLIENT PORTFOLIO SNAPSHOT: {client_code} ---+")
    for k, v in sorted(snapshot.items()):
        print(f"  {k:<20}: {v}")
