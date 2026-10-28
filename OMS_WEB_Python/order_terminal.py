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


def clear_filter(driver: webdriver.Chrome) -> None:
    """Clears and resets search filters in Order Terminal."""
    try:
        script = """
        var clearBtn = document.querySelector('#clear_filter, button[title*="Clear" i], #reset_filter, button[title*="Reset" i]');
        if (clearBtn) { clearBtn.click(); }
        var inputs = document.querySelectorAll('#order_terminal_code, #order_terminal_symbol');
        inputs.forEach(function(inp) {
            inp.value = '';
            inp.dispatchEvent(new Event('input', { bubbles: true }));
            inp.dispatchEvent(new Event('change', { bubbles: true }));
        });
        var statusSel = document.querySelector('#order_status_filter, select[name*="status"]');
        if (statusSel) { statusSel.selectedIndex = 0; $(statusSel).trigger('change'); }
        if (typeof get_order_history === 'function') { get_order_history(); }
        """
        driver.execute_script(script)
        time.sleep(0.4)
    except Exception:
        pass


def refresh(driver: webdriver.Chrome) -> None:
    """Clicks the refresh button inside the Order Terminal widget and invokes get_order_history."""
    try:
        script = """
        if (typeof get_order_history === 'function') { get_order_history(); }
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
    count: int = 3,
    side: Optional[str] = None
) -> bool:
    """
    Selects pending orders in Order Terminal, right-clicks to open context menu,
    and clicks 'Execute at Market', then confirms modal dialog.
    """
    navigate_to_order_terminal(driver)
    refresh(driver)
    time.sleep(0.8)

    print(f"\n[TERMINAL] Locating target rows in Order Terminal for 'Execute at Market'...")

    # 1. Select checkboxes for target orders (optionally restricted to BUY or SELL side)
    select_script = """
    var targetSymbol = (arguments[0] || '').toLowerCase();
    var maxCount = arguments[1] || 5;
    var targetSide = (arguments[2] || '').toLowerCase();
    var rows = Array.from(document.querySelectorAll('#order_terminal_table tbody tr, table tbody tr'));
    var selectedRows = [];

    // Clear previous selections first
    for (var j = 0; j < rows.length; j++) {
        var c = rows[j].querySelector('input[type="checkbox"]');
        if (c && c.checked) c.click();
        rows[j].classList.remove('selected', 'active', 'order_highlight');
    }

    for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var txt = (r.innerText || '').toLowerCase();
        var matchesStatus = txt.includes('accepted') || txt.includes('partially') || txt.includes('0%');
        var matchesSym = !targetSymbol || txt.includes(targetSymbol);
        var matchesSide = true;
        if (targetSide === 'buy') {
            matchesSide = (txt.includes('buy') || r.classList.contains('order-buy')) && !txt.includes('sell');
        } else if (targetSide === 'sell') {
            matchesSide = txt.includes('sell') || r.classList.contains('order-sell');
        }

        if (matchesSym && matchesStatus && matchesSide) {
            var chk = r.querySelector('input[type="checkbox"]');
            if (chk && !chk.checked) {
                chk.click();
            }
            r.classList.add('selected', 'active', 'order_highlight');
            selectedRows.push(r);
            if (selectedRows.length >= maxCount) break;
        }
    }
    return selectedRows.length;
    """
    target_side_str = side.upper() if side else ""
    selected_count = driver.execute_script(select_script, symbol or "", count, (side or "").lower())
    side_msg = f" {target_side_str}" if target_side_str else ""
    print(f"[TERMINAL] Selected {selected_count} pending{side_msg} order rows.")
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


def execute_buy_orders_at_market(driver: webdriver.Chrome, symbol: Optional[str] = None) -> bool:
    """Selects multiple BUY orders only and triggers 'Execute at Market'."""
    print("\n[ORDER TERMINAL] >>> SELECTING MULTIPLE BUY ORDERS ONLY <<<")
    return select_and_execute_at_market(driver, symbol=symbol, side="BUY", count=10)


def execute_sell_orders_at_market(driver: webdriver.Chrome, symbol: Optional[str] = None) -> bool:
    """Selects multiple SELL orders only and triggers 'Execute at Market'."""
    print("\n[ORDER TERMINAL] >>> SELECTING MULTIPLE SELL ORDERS ONLY <<<")
    return select_and_execute_at_market(driver, symbol=symbol, side="SELL", count=10)


def execute_separated_buy_and_sell_at_market(driver: webdriver.Chrome, symbol: Optional[str] = None) -> bool:
    """Executes multiple BUY orders only, waits 2 seconds, then executes multiple SELL orders only."""
    print("\n===============================================================================")
    print(" BATCH 1: SELECT MULTIPLE BUY ORDERS ONLY -> RIGHT-CLICK 'EXECUTE AT MARKET'")
    print("===============================================================================")
    buy_ok = execute_buy_orders_at_market(driver, symbol=symbol)

    print("\n[INFO] Waiting 2 seconds for BUY order processing...")
    time.sleep(2.0)

    print("\n===============================================================================")
    print(" BATCH 2: SELECT MULTIPLE SELL ORDERS ONLY -> RIGHT-CLICK 'EXECUTE AT MARKET'")
    print("===============================================================================")
    sell_ok = execute_sell_orders_at_market(driver, symbol=symbol)

    return buy_ok or sell_ok


def _trigger_context_menu_and_confirm(driver: webdriver.Chrome) -> bool:
    """Dispatches right-click contextmenu event on selected row, clicks 'Execute at Market', and confirms."""
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

    clicked = False
    candidate_xpaths = [
        "//li[contains(., 'Execute at Market') and not(contains(., 'Cancel Order'))]",
        "//*[contains(text(), 'Execute at Market')]/ancestor-or-self::li[1]",
        "//span[contains(normalize-space(), 'Execute at Market')]",
        "//a[contains(normalize-space(), 'Execute at Market')]",
        "//*[contains(@class,'context-menu') or contains(@class,'dropdown') or contains(@class,'menu')]//li[contains(., 'Execute at Market')]",
        "//*[(self::li or self::a or self::span or self::div) and contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'execute at market') and not(contains(., 'Cancel Order'))]"
    ]

    for xp in candidate_xpaths:
        try:
            items = driver.find_elements(By.XPATH, xp)
            for item in items:
                if item.is_displayed():
                    print(f"[CONTEXT MENU] Located 'Execute at Market' with XPath: {xp}")
                    try:
                        driver.execute_script("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", item)
                        time.sleep(0.1)
                    except Exception:
                        pass

                    try:
                        from selenium.webdriver.common.action_chains import ActionChains
                        ActionChains(driver).move_to_element(item).click().perform()
                        clicked = True
                        print("[CONTEXT MENU] Clicked 'Execute at Market' via ActionChains.")
                        break
                    except Exception:
                        pass

                    try:
                        item.click()
                        clicked = True
                        print("[CONTEXT MENU] Clicked 'Execute at Market' via element.click().")
                        break
                    except Exception:
                        pass

                    try:
                        driver.execute_script("arguments[0].click();", item)
                        clicked = True
                        print("[CONTEXT MENU] Clicked 'Execute at Market' via JS click().")
                        break
                    except Exception:
                        pass
            if clicked:
                break
        except Exception:
            pass

    if not clicked:
        js_click_script = """
        var allElements = Array.from(document.querySelectorAll('li, a, span, div, p'));
        var target = null;
        for (var i = 0; i < allElements.length; i++) {
            var el = allElements[i];
            if (el.offsetParent === null) continue;
            var txt = (el.innerText || el.textContent || '').trim();
            if (txt.toLowerCase() === 'execute at market' || (txt.toLowerCase().indexOf('execute at market') !== -1 && txt.length < 35)) {
                target = el;
                break;
            }
        }
        if (target) {
            var clickable = target.closest('li, a, div.menu-item') || target;
            var rect = clickable.getBoundingClientRect();
            var evtOpts = { bubbles: true, cancelable: true, view: window, clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2 };
            clickable.dispatchEvent(new MouseEvent('mouseover', evtOpts));
            clickable.dispatchEvent(new MouseEvent('mousedown', evtOpts));
            clickable.dispatchEvent(new MouseEvent('mouseup', evtOpts));
            clickable.dispatchEvent(new MouseEvent('click', evtOpts));
            if (typeof clickable.click === 'function') { clickable.click(); }
            return true;
        }
        return false;
        """
        clicked = bool(driver.execute_script(js_click_script))
        if clicked:
            print("[CONTEXT MENU: JS] Clicked 'Execute at Market' via DOM MouseEvents.")

    time.sleep(0.6)

    try:
        confirm_btn = driver.find_elements(
            By.XPATH,
            "//button[@id='cnfirmConfirmBtn'] | //button[normalize-space()='YES'] | //button[normalize-space()='Yes'] | //button[normalize-space()='Confirm'] | //button[normalize-space()='OK']"
        )
        for btn in confirm_btn:
            if btn.is_displayed():
                btn.click()
                print("[CONFIRMATION] Confirmed market execution dialog [YES].")
                break
    except Exception:
        pass

    time.sleep(1.5)
    refresh(driver)
    return clicked


def select_multiple_buy_and_one_sell_at_market(driver: webdriver.Chrome, symbol: Optional[str] = None, client_codes: Optional[List[str]] = None) -> bool:
    """Selects MULTIPLE BUY orders and its ONLY ONE corresponding SELL order, right-clicks, and executes at market."""
    navigate_to_order_terminal(driver)
    refresh(driver)
    time.sleep(0.8)

    print(f"\n[ORDER TERMINAL] >>> SELECTING MULTIPLE BUY ORDERS AND ONLY ONE SELL ORDER (Symbol: {symbol or 'ALL'}) <<<")

    select_script = """
    var targetSymbol = (arguments[0] || '').toLowerCase();
    var clientCodes = arguments[1] || [];
    var rows = Array.from(document.querySelectorAll('#order_terminal_table tbody tr, table tbody tr'));

    for (var j = 0; j < rows.length; j++) {
        var c = rows[j].querySelector('input[type="checkbox"]');
        if (c && c.checked) c.click();
        rows[j].classList.remove('selected', 'active', 'order_highlight');
    }

    var buyRows = [];
    var singleSellRow = null;

    for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var txt = (r.innerText || '').toLowerCase();
        if (!txt.includes('accepted') && !txt.includes('partially')) continue;
        if (targetSymbol && !txt.includes(targetSymbol)) continue;

        if (clientCodes.length > 0) {
            var matchClient = false;
            for (var k = 0; k < clientCodes.length; k++) {
                if (txt.includes(clientCodes[k].toLowerCase())) { matchClient = true; break; }
            }
            if (!matchClient) continue;
        }

        var isBuy = (txt.includes('buy') || r.classList.contains('order-buy')) && !txt.includes('sell');
        var isSell = txt.includes('sell') || r.classList.contains('order-sell');

        if (isBuy) {
            buyRows.push(r);
        } else if (isSell && !singleSellRow) {
            singleSellRow = r;
        }
    }

    var selected = buyRows.slice();
    if (singleSellRow) selected.push(singleSellRow);

    for (var s = 0; s < selected.length; s++) {
        var row = selected[s];
        var chk = row.querySelector('input[type="checkbox"]');
        if (chk && !chk.checked) chk.click();
        row.classList.add('selected', 'active', 'order_highlight');
    }
    return { buys: buyRows.length, sell: singleSellRow ? 1 : 0, total: selected.length };
    """
    res = driver.execute_script(select_script, symbol or "", client_codes or [])
    buys_cnt = res.get("buys", 0) if isinstance(res, dict) else 0
    sell_cnt = res.get("sell", 0) if isinstance(res, dict) else 0
    total_cnt = res.get("total", 0) if isinstance(res, dict) else 0

    print(f"[ORDER TERMINAL] Selected {buys_cnt} BUY order(s) + {sell_cnt} SELL order (Total: {total_cnt}).")
    if not total_cnt:
        print("[TERMINAL: WARN] No matching orders found to execute.")
        return False

    return _trigger_context_menu_and_confirm(driver)


def select_multiple_sell_and_one_buy_at_market(driver: webdriver.Chrome, symbol: Optional[str] = None, client_codes: Optional[List[str]] = None) -> bool:
    """Selects MULTIPLE SELL orders and its ONLY ONE corresponding BUY order, right-clicks, and executes at market."""
    navigate_to_order_terminal(driver)
    refresh(driver)
    time.sleep(0.8)

    print(f"\n[ORDER TERMINAL] >>> SELECTING MULTIPLE SELL ORDERS AND ONLY ONE BUY ORDER (Symbol: {symbol or 'ALL'}) <<<")

    select_script = """
    var targetSymbol = (arguments[0] || '').toLowerCase();
    var clientCodes = arguments[1] || [];
    var rows = Array.from(document.querySelectorAll('#order_terminal_table tbody tr, table tbody tr'));

    for (var j = 0; j < rows.length; j++) {
        var c = rows[j].querySelector('input[type="checkbox"]');
        if (c && c.checked) c.click();
        rows[j].classList.remove('selected', 'active', 'order_highlight');
    }

    var sellRows = [];
    var singleBuyRow = null;

    for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var txt = (r.innerText || '').toLowerCase();
        if (!txt.includes('accepted') && !txt.includes('partially')) continue;
        if (targetSymbol && !txt.includes(targetSymbol)) continue;

        if (clientCodes.length > 0) {
            var matchClient = false;
            for (var k = 0; k < clientCodes.length; k++) {
                if (txt.includes(clientCodes[k].toLowerCase())) { matchClient = true; break; }
            }
            if (!matchClient) continue;
        }

        var isBuy = (txt.includes('buy') || r.classList.contains('order-buy')) && !txt.includes('sell');
        var isSell = txt.includes('sell') || r.classList.contains('order-sell');

        if (isSell) {
            sellRows.push(r);
        } else if (isBuy && !singleBuyRow) {
            singleBuyRow = r;
        }
    }

    var selected = sellRows.slice();
    if (singleBuyRow) selected.push(singleBuyRow);

    for (var s = 0; s < selected.length; s++) {
        var row = selected[s];
        var chk = row.querySelector('input[type="checkbox"]');
        if (chk && !chk.checked) chk.click();
        row.classList.add('selected', 'active', 'order_highlight');
    }
    return { sells: sellRows.length, buy: singleBuyRow ? 1 : 0, total: selected.length };
    """
    res = driver.execute_script(select_script, symbol or "", client_codes or [])
    sells_cnt = res.get("sells", 0) if isinstance(res, dict) else 0
    buy_cnt = res.get("buy", 0) if isinstance(res, dict) else 0
    total_cnt = res.get("total", 0) if isinstance(res, dict) else 0

    print(f"[ORDER TERMINAL] Selected {sells_cnt} SELL order(s) + {buy_cnt} BUY order (Total: {total_cnt}).")
    if not total_cnt:
        print("[TERMINAL: WARN] No matching orders found to execute.")
        return False

    return _trigger_context_menu_and_confirm(driver)
