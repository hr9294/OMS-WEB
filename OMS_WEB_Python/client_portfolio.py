"""
OMS-WEB Client Portfolio Module
Fetches client financial summary, cash limits, BO IDs, and equity holdings
via the /shared/getclientdata/?cln_code= API and UI table.
"""
import json
import time
from typing import Dict, Any, Optional
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from config import SHARED_CLIENT_DATA_URL


def navigate_to_portfolio(driver: webdriver.Chrome) -> None:
    """Navigates to Client Portfolio widget."""
    try:
        script = """
        var el = document.querySelector('a[data-link*="client_portfolio"], a[href*="portfolio"], .lm_tab[title*="Portfolio"]');
        if (el) { el.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.5)
    except Exception:
        pass


def get_client_details(driver: webdriver.Chrome, client_code: str) -> Dict[str, str]:
    """
    Queries client details via the internal authenticated API.
    Returns cash_limit, mature_balance, equity, bo_id, status, client_name, etc.
    """
    code = (client_code or "").strip()
    if not code:
        return {}

    script = """
    var code = arguments[0];
    var url = '/shared/getclientdata/?cln_code=' + encodeURIComponent(code);
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
    try:
        raw_resp = driver.execute_script(script, code)
        if raw_resp:
            data = json.loads(raw_resp)
            out: Dict[str, str] = {}

            # Handle dict response
            if isinstance(data, dict):
                for k, v in data.items():
                    out[str(k).lower()] = str(v) if v is not None else ""
                
                # Normalize common fields
                out["client_code"] = code
                if "name" in out and "client_name" not in out:
                    out["client_name"] = out["name"]
                if "boid" in out and "bo_id" not in out:
                    out["bo_id"] = out["boid"]
                if "limit" in out and "cash_limit" not in out:
                    out["cash_limit"] = out["limit"]
                if "cash" in out and "cash_limit" not in out:
                    out["cash_limit"] = out["cash"]
                if "status" not in out:
                    out["status"] = "Active"
                return out

            elif isinstance(data, list) and data:
                first = data[0]
                if isinstance(first, dict):
                    for k, v in first.items():
                        out[str(k).lower()] = str(v) if v is not None else ""
                    out["client_code"] = code
                    return out
    except Exception as e:
        print(f"[PORTFOLIO API: WARN] Error fetching details for {code}: {e}")

    return {
        "client_code": code,
        "cash_limit": "0.00",
        "mature_balance": "0.00",
        "bo_id": "-",
        "status": "Active",
        "client_name": f"Client {code}",
    }


def get_snapshot(driver: webdriver.Chrome, client_code: str) -> Dict[str, str]:
    """Captures snapshot of Client Portfolio."""
    details = get_client_details(driver, client_code)
    return details


def print_portfolio(client_code: str, snapshot: Dict[str, str]) -> None:
    """Pretty prints client portfolio data."""
    print(f"\n+-------------------------------------------------------+")
    print(f"| CLIENT PORTFOLIO SNAPSHOT: {client_code:<26} |")
    print(f"+-------------------------------------------------------+")
    for k, v in sorted(snapshot.items()):
        print(f"  {k:<20}: {v}")
    print(f"+-------------------------------------------------------+")
