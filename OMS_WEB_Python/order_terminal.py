"""
OMS-WEB Order Terminal Module
Handles Order Terminal inspection, order selection, right-click context menu
'Execute at Market' trigger, confirmation modal handling, and live status verification.
"""
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def navigate_to_order_terminal(driver: webdriver.Chrome) -> None:
    """Navigates to Order Terminal widget in GoldenLayout."""
    try:
        script = """
        var tabs = Array.from(document.querySelectorAll('.lm_tab'));
        var termTab = tabs.find(function(t) {
            var txt = (t.innerText || '').toLowerCase();
            return txt.includes('order terminal') || txt.includes('terminal');
        });
        if (termTab) { termTab.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.5)
    except Exception:
        pass


def refresh(driver: webdriver.Chrome) -> None:
    """Clicks the refresh button inside the Order Terminal widget."""
    try:
        script = """
        var btn = document.querySelector('#order_terminal_refresh, button[title*="Refresh" i], .fa-refresh, .fa-sync');
        if (btn) { btn.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.5)
    except Exception:
        pass


def get_snapshot(
    driver: webdriver.Chrome,
    client_code: Optional[str] = None,
    symbol: Optional[str] = None,
    exchange: str = "DSE"
) -> Dict[str, str]:
    """Captures summary snapshot of Order Terminal."""
    navigate_to_order_terminal(driver)
    script = """
    var rows = Array.from(document.querySelectorAll('#order_terminal_table tbody tr, table.order-terminal tbody tr'));
    var total = rows.length;
    var accepted = 0;
    var filled = 0;
    var cancelled = 0;

    rows.forEach(function(r) {
        var txt = (r.innerText || '').toLowerCase();
        if (txt.includes('accepted')) accepted++;
        else if (txt.includes('filled') || txt.includes('executed')) filled++;
        else if (txt.includes('cancelled')) cancelled++;
    });

    return {
        total_rows: total.toString(),
        accepted_count: accepted.toString(),
        filled_count: filled.toString(),
        cancelled_count: cancelled.toString()
    };
    """
    try:
        data = driver.execute_script(script) or {}
        return {
            "total_rows": str(data.get("total_rows", "0")),
            "accepted_count": str(data.get("accepted_count", "0")),
            "filled_count": str(data.get("filled_count", "0")),
            "cancelled_count": str(data.get("cancelled_count", "0")),
        }
    except Exception:
        return {
            "total_rows": "0",
            "accepted_count": "0",
            "filled_count": "0",
            "cancelled_count": "0",
        }


def select_and_execute_at_market(
    driver: webdriver.Chrome,
    order_ids: Optional[List[str]] = None,
    symbol: Optional[str] = None,
    count: int = 3
) -> bool:
    """
    Selects pending orders in Order Terminal, right-clicks to open context menu,
    and clicks 'Execute at Market', then confirms modal dialog.
    """
    navigate_to_order_terminal(driver)
    refresh(driver)
    time.sleep(0.8)

    print(f"\n[TERMINAL] Locating target rows in Order Terminal for 'Execute at Market'...")

    # 1. Select checkboxes for target orders
    select_script = """
    var targetSymbol = (arguments[0] || '').toLowerCase();
    var maxCount = arguments[1] || 3;
    var rows = Array.from(document.querySelectorAll('#order_terminal_table tbody tr, table tbody tr'));
    var selectedRows = [];

    for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var txt = (r.innerText || '').toLowerCase();
        if ((!targetSymbol || txt.includes(targetSymbol)) && (txt.includes('accepted') || txt.includes('pending') || txt.includes('0%'))) {
            var chk = r.querySelector('input[type="checkbox"]');
            if (chk && !chk.checked) {
                chk.click();
            }
            selectedRows.push(r);
            if (selectedRows.length >= maxCount) break;
        }
    }
    return selectedRows.length;
    """
    selected_count = driver.execute_script(select_script, symbol or "", count)
    print(f"[TERMINAL] Selected {selected_count} pending order rows.")

    if not selected_count:
        print("[TERMINAL: WARN] No pending orders matched for selection.")
        return False

    # 2. Trigger Right-Click (Context Menu) on selected row
    time.sleep(0.5)
    context_script = """
    var rows = Array.from(document.querySelectorAll('#order_terminal_table tbody tr, table tbody tr'));
    var anchor = null;
    for (var i = 0; i < rows.length; i++) {
        var chk = rows[i].querySelector('input[type="checkbox"]');
        if (chk && chk.checked) {
            anchor = rows[i];
            break;
        }
    }
    if (!anchor && rows.length > 0) anchor = rows[0];
    if (!anchor) return false;

    var rect = anchor.getBoundingClientRect();
    var evt = new MouseEvent('contextmenu', {
        bubbles: true,
        cancelable: true,
        view: window,
        clientX: rect.left + rect.width / 2,
        clientY: rect.top + rect.height / 2,
        button: 2,
        buttons: 2
    });
    anchor.dispatchEvent(evt);
    if (window.jQuery) { window.jQuery(anchor).trigger('contextmenu'); }
    return true;
    """
    try:
        driver.execute_script(context_script)
        print("[TERMINAL] Dispatched right-click (contextmenu) event on selected order row.")
    except Exception as e:
        print(f"[TERMINAL: WARN] Action context click: {e}")

    time.sleep(0.6)

    # 3. Click 'Execute at Market' in context menu
    clicked = False
    try:
        menu_items = driver.find_elements(
            By.XPATH,
            "//*[contains(@class,'context-menu') or contains(@class,'dropdown') or contains(@class,'menu') or self::li or self::a or self::span]"
            "[contains(translate(text(), 'EXECUTE AT MARKET', 'execute at market'), 'execute at market')]"
        )
        for item in menu_items:
            if item.is_displayed():
                item.click()
                clicked = True
                print("✔ [CONTEXT MENU] Clicked 'Execute at Market' option.")
                break
    except Exception:
        pass

    if not clicked:
        # Fallback to JavaScript search across DOM
        js_click_script = """
        var items = Array.from(document.querySelectorAll('.context-menu-item, .dropdown-item, .menu-item, ul.context-menu-list li, ul.dropdown-menu li, .vakata-context li, li, a, span, div'));
        var target = items.find(function(el) {
            var txt = (el.innerText || el.textContent || '').trim().toLowerCase();
            return txt === 'execute at market' || txt.includes('execute at market') || txt === 'execute market' || txt === 'market execute';
        });
        if (target) { target.click(); return true; }
        return false;
        """
        clicked = bool(driver.execute_script(js_click_script))
        if clicked:
            print("✔ [CONTEXT MENU: JS] Clicked 'Execute at Market' via DOM.")

    time.sleep(0.6)

    # 4. Handle confirmation modal dialog
    try:
        confirm_btn = driver.find_elements(
            By.XPATH,
            "//button[@id='cnfirmConfirmBtn'] | //button[normalize-space()='YES'] | //button[normalize-space()='Yes'] | //button[normalize-space()='Confirm'] | //button[normalize-space()='OK']"
        )
        for btn in confirm_btn:
            if btn.is_displayed():
                btn.click()
                print("✔ [CONFIRMATION] Confirmed market execution dialog [YES].")
                break
    except Exception:
        pass

    time.sleep(1.5)
    refresh(driver)
    return clicked
