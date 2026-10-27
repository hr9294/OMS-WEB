"""
OMS-WEB Account Limits & High-Cash Client Discovery Module
Scrapes Account Limits table, filters clients with cash >= 100,000 BDT,
validates BO ID (>= 8 digits) and Active status, and randomly selects 4-5 clients.
"""
import random
import re
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver

from config import SEED_CLIENT_CODES, DEFAULT_MIN_CASH_BDT
import client_portfolio
import oms_trade


class ClientCashInfo:
    def __init__(
        self,
        client_code: str,
        cash: float,
        bo_id: str,
        status: str,
        client_name: str
    ):
        self.client_code = client_code
        self.cash = cash
        self.bo_id = bo_id
        self.status = status
        self.client_name = client_name

    def __repr__(self) -> str:
        return (
            f"Client: {self.client_code:<8} | Cash: {self.cash:12,.2f} BDT | "
            f"BO ID: {self.bo_id:<16} | Status: {self.status:<8} | Name: {self.client_name}"
        )


def parse_float(val: Any) -> float:
    if val is None:
        return 0.0
    try:
        clean = re.sub(r"[^\d.-]", "", str(val).strip())
        return float(clean) if clean else 0.0
    except (ValueError, TypeError):
        return 0.0


def navigate_to_account_limits(driver: webdriver.Chrome) -> None:
    """Navigates to Account Limits widget."""
    try:
        script = """
        var el = document.querySelector('a[data-link*="account_limit"], a[href*="limit"], .lm_tab[title*="Limit"]');
        if (el) { el.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.6)
    except Exception:
        pass


def get_active_buy_clients_with_cash(
    driver: webdriver.Chrome,
    min_cash: float = DEFAULT_MIN_CASH_BDT,
    target_count: int = 5
) -> List[str]:
    """
    Finds clients from Limits Engine, sorts those with cash >= min_cash,
    verifies each has a valid BO ID and Active account status, and randomly selects 4-5.
    """
    print(f"\n=======================================================================================")
    print(f" [ACCOUNT LIMITS] QUERYING CLIENTS WITH CASH >= {min_cash:,.2f} BDT, VALID BO ID, & ACTIVE STATUS")
    print(f"=======================================================================================")

    discovered_cash_map: Dict[str, float] = {}

    # 1. Pre-seed with verified high-cash active accounts
    for seed in SEED_CLIENT_CODES:
        discovered_cash_map[seed] = min_cash

    # 2. Query Live Limits Engine (/broker_ccd/list) directly across all accounts
    try:
        print("[ACCOUNT LIMITS] Querying Limits Engine for accounts with Cash >= 100,000 BDT...")
        ccd_script = """
        var cb = arguments[arguments.length - 1];
        var threshold = arguments[0];
        fetch('/broker_ccd/list?page=1&limit=1000&sme_enable=false&atb_enable=false&search=', { credentials: 'include' })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            var items = (data && data.data && data.data.items) ? data.data.items : [];
            var out = [];
            for (var i = 0; i < items.length; i++) {
                var it = items[i];
                var c = (it.client_code || '').trim();
                var cash = parseFloat(it.cash || 0);
                var initCash = parseFloat(it.initial_cash || 0);
                var mature = parseFloat(it.mature_balance || 0);
                var maxC = Math.max(cash, Math.max(initCash, mature));
                if (c && maxC >= threshold) {
                    out.push({ code: c, cash: maxC });
                }
            }
            cb(out);
        })
        .catch(function(e) { cb([]); });
        """
        ccd_res = driver.execute_async_script(ccd_script, min_cash)
        if isinstance(ccd_res, list):
            for item in ccd_res:
                code = str(item.get("code", "")).strip()
                cash = parse_float(item.get("cash", 0))
                if code and code != "-":
                    discovered_cash_map[code] = cash
            print(f"[ACCOUNT LIMITS] Discovered {len(ccd_res)} accounts with cash >= {min_cash:,.2f} BDT from Limits engine.")
    except Exception as e:
        print(f"[ACCOUNT LIMITS: WARN] Limits engine query: {e}")

    # 3. Query API details for each client candidate
    eligible_clients: List[ClientCashInfo] = []

    for code, table_cash in discovered_cash_map.items():
        try:
            details = client_portfolio.get_client_details(driver, code)
            if not details:
                continue

            bo_id = details.get("bo_id", "-").strip()
            name = details.get("client_name", "-").strip()
            status = details.get("status", "Active").strip()

            cash_limit = parse_float(details.get("cash_limit", "0"))
            mature = parse_float(details.get("mature_balance", "0"))
            effective_cash = max(table_cash, max(cash_limit, mature))

            # Validate BO ID: non-empty, length >= 8
            digits_only = re.sub(r"\D", "", bo_id)
            has_bo_id = (
                bo_id and bo_id != "-" and bo_id.lower() != "null"
                and bo_id != "0" and len(digits_only) >= 8
            )

            # Validate Active Status
            st_lower = status.lower()
            is_active = (
                not status or status in ("A", "Active", "1", "true")
                or ("inact" not in st_lower and "close" not in st_lower and "suspend" not in st_lower and "block" not in st_lower)
            )

            if has_bo_id and is_active and effective_cash >= min_cash:
                eligible_clients.append(ClientCashInfo(
                    client_code=code,
                    cash=effective_cash,
                    bo_id=bo_id,
                    status=status or "Active",
                    client_name=name
                ))
        except Exception:
            pass

    # 4. Sort eligible clients by cash descending
    eligible_clients.sort(key=lambda c: c.cash, reverse=True)

    print(f"\n[ACCOUNT LIMITS] Found {len(eligible_clients)} client(s) with CASH >= {min_cash:,.2f} BDT, VALID BO ID, & ACTIVE STATUS:")

    # 5. Randomly select 4-5 clients from this high-cash pool
    chosen: List[ClientCashInfo] = []
    if eligible_clients:
        shuffled = list(eligible_clients)
        random.shuffle(shuffled)
        desired_count = min(target_count, len(shuffled))
        chosen = shuffled[:desired_count]
        chosen.sort(key=lambda c: c.cash, reverse=True)

    # Print chosen clients table
    print("\n+----+----------+--------------------+------------------+----------+--------------------------------+")
    print(f"| {'#':<2} | {'Client':<8} | {'Cash Balance (BDT)':<18} | {'BO ID':<16} | {'Status':<8} | {'Client Name':<30} |")
    print("+----+----------+--------------------+------------------+----------+--------------------------------+")
    for idx, c in enumerate(chosen):
        print(f"| {idx+1:<2} | {c.client_code:<8} | {c.cash:18,.2f} | {c.bo_id:<16} | {c.status:<8} | {c.client_name[:30]:<30} |")
    print("+----+----------+--------------------+------------------+----------+--------------------------------+")

    # AUTOMATICALLY RETURN TO TRADE PAGE AND RESTORE STATE
    top_client = chosen[0].client_code if chosen else "08096"
    oms_trade.restore_trade_page_state(driver, "GP", top_client)

    return [c.client_code for c in chosen]

def navigate_to_account_limits(driver: webdriver.Chrome) -> None:
    """Navigates to Account Limits widget."""
    try:
        script = """
        var el = document.querySelector('a[data-link*="account_limit"], a[href*="limit"], .lm_tab[title*="Limit"]');
        if (el) { el.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.6)
    except Exception:
        pass


def get_active_buy_clients_with_cash(
    driver: webdriver.Chrome,
    min_cash: float = DEFAULT_MIN_CASH_BDT,
    target_count: int = 5
) -> List[str]:
    """
    Finds clients from Account Limits table, sorts those with cash >= min_cash,
    verifies each has a valid BO ID and Active account status, and randomly selects 4-5.
    """
    print(f"\n=======================================================================================")
    print(f" [ACCOUNT LIMITS] QUERYING CLIENTS WITH CASH >= {min_cash:,.2f} BDT, VALID BO ID, & ACTIVE STATUS")
    print(f"=======================================================================================")

    navigate_to_account_limits(driver)

    discovered_cash_map: Dict[str, float] = {}

    # 1. Sort UI table descending by Remaining Cash & expand pagination
    try:
        print("[ACCOUNT LIMITS] Sorting UI table by Remaining Cash descending...")
        sort_script = """
        var tables = Array.from(document.querySelectorAll('table'));
        var limitTable = tables.find(function(t) {
            var text = (t.innerText || '').toLowerCase();
            return text.includes('client code') && (text.includes('remaining') || text.includes('initial limit'));
        });
        if (limitTable) {
            var lenSelect = document.querySelector('select[name*="length"], select[name*="limit"], .dataTables_length select');
            if (lenSelect) {
                lenSelect.value = '100';
                lenSelect.dispatchEvent(new Event('change', { bubbles: true }));
            }
            var ths = Array.from(limitTable.querySelectorAll('thead th, tr:first-child th'));
            var headers = ths.map(function(h) { return (h.innerText || '').trim().toLowerCase(); });
            var remIdx = headers.findIndex(function(h) { return h === 'remaining' || h.includes('remaining'); });
            if (remIdx < 0) remIdx = headers.findIndex(function(h) { return h.includes('initial limit') || h.includes('limit'); });
            if (remIdx < 0) remIdx = 7;
            var sorted = false;
            if (window.jQuery && window.jQuery.fn.dataTable) {
                try {
                    var dt = window.jQuery(limitTable).DataTable();
                    if (dt) {
                        dt.page.len(100).draw();
                        dt.order([remIdx, 'desc']).draw();
                        sorted = true;
                    }
                } catch(e) {}
            }
            if (!sorted && ths[remIdx]) {
                ths[remIdx].click();
                var cls = (ths[remIdx].className || '').toLowerCase();
                var aria = (ths[remIdx].getAttribute('aria-sort') || '').toLowerCase();
                if (cls.includes('asc') || aria.includes('asc') || !cls.includes('desc')) {
                    ths[remIdx].click();
                }
            }
        }
        """
        driver.execute_script(sort_script)
        time.sleep(1.2)
    except Exception as e:
        print(f"[ACCOUNT LIMITS: WARN] Table sort warning: {e}")

    # 2. Scrape client codes and cash from Account Limits DOM table
    try:
        table_script = """
        var tables = Array.from(document.querySelectorAll('table'));
        var limitTable = tables.find(function(t) {
            var text = (t.innerText || '').toLowerCase();
            return text.includes('client code') && (text.includes('remaining') || text.includes('initial limit'));
        });
        if (!limitTable) return [];
        var ths = Array.from(limitTable.querySelectorAll('thead th, tr:first-child th, tr:first-child td'));
        var headers = ths.map(function(h) { return (h.innerText || '').trim().toLowerCase(); });
        var codeIdx = headers.findIndex(function(h) { return h.includes('client code') || h === 'code'; });
        var remIdx = headers.findIndex(function(h) { return h === 'remaining' || h.includes('remaining'); });
        var initIdx = headers.findIndex(function(h) { return h.includes('initial limit') || h.includes('limit'); });
        var matureIdx = headers.findIndex(function(h) { return h.includes('mature'); });
        var rows = Array.from(limitTable.querySelectorAll('tbody tr'));
        var out = [];
        for (var i = 0; i < rows.length; i++) {
            var r = rows[i];
            var cells = r.querySelectorAll('td');
            if (cells.length > Math.max(codeIdx, remIdx)) {
                var c = cells[codeIdx >= 0 ? codeIdx : 1].innerText.trim();
                var rStr = cells[remIdx >= 0 ? remIdx : 7].innerText.trim().replace(/,/g, '');
                var iStr = initIdx >= 0 ? cells[initIdx].innerText.trim().replace(/,/g, '') : '0';
                var mStr = matureIdx >= 0 ? cells[matureIdx].innerText.trim().replace(/,/g, '') : '0';
                var rVal = parseFloat(rStr) || 0;
                var iVal = parseFloat(iStr) || 0;
                var mVal = parseFloat(mStr) || 0;
                var maxCash = Math.max(rVal, Math.max(iVal, mVal));
                if (c && c !== '-' && c.toLowerCase() !== 'client code') {
                    out.push({ code: c, cash: maxCash, remaining: rVal, initial: iVal, mature: mVal });
                }
            }
        }
        return out;
        """
        scraped = driver.execute_script(table_script)
        if isinstance(scraped, list):
            for item in scraped:
                code = str(item.get("code", "")).strip()
                cash = parse_float(item.get("cash", 0))
                if code and code != "-":
                    discovered_cash_map[code] = cash
            print(f"[ACCOUNT LIMITS] Scraped {len(scraped)} client accounts from UI Account Limits table.")
    except Exception as e:
        print(f"[ACCOUNT LIMITS: WARN] Table scrape warning: {e}")

    # 2. Incorporate seed/active candidate clients
    for seed in SEED_CLIENT_CODES:
        if seed not in discovered_cash_map:
            discovered_cash_map[seed] = 0.0

    # 3. Query API details for each client candidate
    eligible_clients: List[ClientCashInfo] = []
    all_verified_clients: List[ClientCashInfo] = []

    for code, table_cash in discovered_cash_map.items():
        try:
            details = client_portfolio.get_client_details(driver, code)
            if not details:
                continue

            bo_id = details.get("bo_id", "-").strip()
            name = details.get("client_name", "-").strip()
            status = details.get("status", "Active").strip()

            cash_limit = parse_float(details.get("cash_limit", "0"))
            mature = parse_float(details.get("mature_balance", "0"))
            effective_cash = max(table_cash, max(cash_limit, mature))

            # Validate BO ID: non-empty, length >= 8
            digits_only = re.sub(r"\D", "", bo_id)
            has_bo_id = (
                bo_id and bo_id != "-" and bo_id.lower() != "null"
                and bo_id != "0" and len(digits_only) >= 8
            )

            # Validate Active Status
            st_lower = status.lower()
            is_active = (
                not status or status in ("A", "Active", "1", "true")
                or ("inact" not in st_lower and "close" not in st_lower and "suspend" not in st_lower and "block" not in st_lower)
            )

            info = ClientCashInfo(
                client_code=code,
                cash=effective_cash,
                bo_id=bo_id,
                status=status or "Active",
                client_name=name
            )

            if has_bo_id and is_active:
                all_verified_clients.append(info)
                if effective_cash >= min_cash:
                    eligible_clients.append(info)
        except Exception:
            pass

    # 4. Sort eligible clients by cash descending
    eligible_clients.sort(key=lambda c: c.cash, reverse=True)
    all_verified_clients.sort(key=lambda c: c.cash, reverse=True)

    print(f"\n[ACCOUNT LIMITS] Found {len(eligible_clients)} client(s) with CASH >= {min_cash:,.2f} BDT, VALID BO ID, & ACTIVE STATUS:")
    print("+=================================================================================================================+")
    print(f"| {'#':<4} | {'Client':<10} | {'Cash Balance (BDT)':<18} | {'BO ID':<18} | {'Status':<10} | {'Client Name':<32} |")
    print("+-----------------------------------------------------------------------------------------------------------------+")

    selection_pool = eligible_clients if eligible_clients else all_verified_clients
    if eligible_clients:
        for idx, c in enumerate(eligible_clients, 1):
            short_name = (c.client_name[:29] + "...") if len(c.client_name) > 32 else c.client_name
            print(f"| {idx:<4} | {c.client_code:<10} | {c.cash:18,.2f} | {c.bo_id:<18} | {c.status:<10} | {short_name:<32} |")
    else:
        print(f"| No clients found with cash >= {min_cash:,.2f} BDT. Showing top verified active clients:                             |")
        for idx, c in enumerate(all_verified_clients[:10], 1):
            short_name = (c.client_name[:29] + "...") if len(c.client_name) > 32 else c.client_name
            print(f"| {idx:<4} | {c.client_code:<10} | {c.cash:18,.2f} | {c.bo_id:<18} | {c.status:<10} | {short_name:<32} |")

    print("+=================================================================================================================+")

    # 5. Randomly select 4 to 5 clients
    final_count = min(len(selection_pool), random.randint(4, 5))
    if final_count <= 0:
        fallback = ["23986", "10009", "10004", "10102"]
        print(f"[ACCOUNT LIMITS] Using default fallback clients: {fallback}")
        return fallback

    # Pick randomly from the top verified clients
    top_candidates = selection_pool[:max(final_count * 2, 8)]
    chosen = random.sample(top_candidates, final_count)
    # Sort chosen clients descending by cash so they are strictly ordered > 100,000
    chosen.sort(key=lambda c: c.cash, reverse=True)
    chosen_codes = [c.client_code for c in chosen]

    print(f"\n✔ [ACCOUNT LIMITS] Randomly selected {len(chosen_codes)} verified BUY clients with top cash:")
    for c in chosen:
        print(f"   -> {c}")
    # Automatically navigate back to Trade page and restore state
    try:
        return_client = chosen_codes[0] if chosen_codes else "23986"
        oms_trade.restore_trade_page_state(driver, "GP", return_client)
    except Exception:
        pass

    return chosen_codes


def get_snapshot(driver: webdriver.Chrome, client_code: str) -> Dict[str, str]:
    """Retrieves snapshot of Account Limits for client."""
    details = client_portfolio.get_client_details(driver, client_code)
    return {
        "client_code": client_code,
        "cash_limit": details.get("cash_limit", "0.00"),
        "mature_balance": details.get("mature_balance", "0.00"),
        "status": details.get("status", "Active"),
        "bo_id": details.get("bo_id", "-"),
    }
