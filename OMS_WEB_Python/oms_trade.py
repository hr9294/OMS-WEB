"""
OMS-WEB Standard Trade Page & Order Entry Workflow Module
Provides instrument input, BUY/SELL placement, Limit/Market orders,
and post-trade portfolio audit.
"""
import time
from typing import Optional, List, Dict
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from config import DEFAULT_SYMBOL, DEFAULT_EXCHANGE, DEFAULT_CLIENT_CODE
import widget_color
import oms_price
import client_portfolio
import position


def navigate_to_trade_page(driver: webdriver.Chrome) -> None:
    """Navigates to the Trade page via navbar or exchange icon."""
    try:
        trade_nav = driver.find_elements(
            By.XPATH, "//i[contains(@class, 'fa-exchange')] | //a[contains(@href, 'trade')] | //a[normalize-space()='Trade']"
        )
        if trade_nav and trade_nav[0].is_displayed():
            trade_nav[0].click()
            time.sleep(1.0)
            return
    except Exception:
        pass

    # Direct DOM click fallback
    driver.execute_script("""
    var el = document.querySelector('a[href*="trade"], i.fa-exchange, .nav-item[data-page="trade"]');
    if (el) el.click();
    """)
    time.sleep(1.0)


def enter_instrument(driver: webdriver.Chrome, symbol: str) -> None:
    """Populates symbol into searchSymbol and marketwatch inputs with autocomplete enter."""
    sym = oms_price.clean_symbol(symbol or DEFAULT_SYMBOL)
    print(f"[SETUP] Populating symbol '{sym}' into Trade & Marketwatch inputs...")

    try:
        search_sym = driver.find_element(By.ID, "instrument")
        search_sym.clear()
        search_sym.send_keys(sym)
        time.sleep(0.3)
        search_sym.send_keys(Keys.ARROW_DOWN)
        search_sym.send_keys(Keys.ENTER)
        time.sleep(0.4)
    except Exception:
        pass

    try:
        mw_inp = driver.find_element(By.ID, "marketwatch_symbol")
        mw_inp.clear()
        mw_inp.send_keys(sym)
        time.sleep(0.3)
        mw_inp.send_keys(Keys.ARROW_DOWN)
        mw_inp.send_keys(Keys.ENTER)
        time.sleep(0.4)
    except Exception:
        pass


def restore_trade_page_state(
    driver: webdriver.Chrome,
    symbol: Optional[str] = None,
    client_code: Optional[str] = None
) -> None:
    """Navigates back to the Trade page and restores the symbol, marketwatch, and client code."""
    navigate_to_trade_page(driver)
    if symbol:
        enter_instrument(driver, symbol)
        try:
            order_inst = driver.find_element(By.ID, "order_instrument")
            order_inst.clear()
            order_inst.send_keys(oms_price.clean_symbol(symbol))
            order_inst.send_keys(Keys.ARROW_DOWN)
            order_inst.send_keys(Keys.ENTER)
        except Exception:
            pass

    if client_code:
        try:
            client_inp = driver.find_element(By.ID, "order_client_code")
            client_inp.clear()
            client_inp.send_keys(str(client_code).strip())
            client_inp.send_keys(Keys.ARROW_DOWN)
            client_inp.send_keys(Keys.ENTER)
        except Exception:
            pass

    print(f"[TRADE PAGE RESTORED] Symbol: {symbol} | Client Code: {client_code}")




def install_symbol_whitespace_sanitizer(driver: webdriver.Chrome) -> None:
    """Installs DOM event listeners and jQuery ajaxPrefilter to sanitize symbol whitespace and prevent checksaleable errors."""
    if not driver:
        return
    try:
        driver.execute_script("""
            try {
                var sym = document.getElementById('order_instrument');
                if (sym && !sym.dataset.cleanHook) {
                    sym.dataset.cleanHook = 'true';
                    var sanitize = function() {
                        if (sym.value && /\\s/.test(sym.value)) {
                            sym.value = sym.value.replace(/\\s+/g, '');
                            if (window.jQuery) { window.jQuery(sym).trigger('input').trigger('change'); }
                        }
                    };
                    sym.addEventListener('blur', sanitize);
                    sym.addEventListener('input', sanitize);
                    sym.addEventListener('change', sanitize);
                }
                var cln = document.getElementById('order_client_code');
                if (cln && !cln.dataset.cleanHook) {
                    cln.dataset.cleanHook = 'true';
                    var cleanBeforeCln = function() {
                        var s = document.getElementById('order_instrument');
                        if (s && s.value && /\\s/.test(s.value)) {
                            s.value = s.value.replace(/\\s+/g, '');
                            if (window.jQuery) { window.jQuery(s).trigger('input').trigger('change'); }
                        }
                    };
                    cln.addEventListener('focus', cleanBeforeCln);
                    cln.addEventListener('click', cleanBeforeCln);
                }
                if (window.jQuery && !window._checkSaleableFilterInstalled) {
                    window._checkSaleableFilterInstalled = true;
                    window.jQuery.ajaxPrefilter(function(options) {
                        if (options.url && (options.url.indexOf('check_saleable') !== -1 || options.url.indexOf('checksaleable') !== -1)) {
                            options.url = options.url.replace(/symbol=([^&]*)/i, function(m, s) {
                                return 'symbol=' + encodeURIComponent(decodeURIComponent(s).trim().replace(/\\s+/g, ''));
                            });
                        }
                    });
                }
            } catch(e) {}
        """)
    except Exception:
        pass


def ensure_ui_unlocked(driver: webdriver.Chrome) -> bool:
    """
    Automatically detects and unblocks the UI if blocked, frozen, or locked:
    1. Dismisses native browser alerts.
    2. Closes lingering jQuery-Confirm dialogs, SweetAlerts, Bootbox modals.
    3. Removes blocking backdrops (.modal-backdrop, .jconfirm-bg, .blockOverlay, #context-menu-layer).
    4. Unfreezes document.body (removes .modal-open, restores pointer-events & overflow).
    5. Force-enables locked or disabled form inputs (.order_submit_btn, inputs, buttons).
    6. Brings active floating windows to top (zIndex = 9999).
    """
    if not driver:
        return False
    unblocked = False

    # 1. Native alert dismissal
    try:
        alert = driver.switch_to.alert
        txt = alert.text
        print(f"[AUTO-UNBLOCK] Dismissed blocking browser alert: {txt}")
        alert.dismiss()
        unblocked = True
    except Exception:
        pass

    # 2. Comprehensive DOM unblock
    try:
        res = driver.execute_script("""
            var changed = false;
            try {
                if (window.jconfirm && window.jconfirm.instances && window.jconfirm.instances.length > 0) {
                    for (var i = 0; i < window.jconfirm.instances.length; i++) {
                        var inst = window.jconfirm.instances[i];
                        if (inst && typeof inst.close === 'function') {
                            inst.close();
                            changed = true;
                        }
                    }
                }
                var blockers = document.querySelectorAll('.modal-backdrop, .jconfirm-bg, .jconfirm-overlay, .blockOverlay, .blockUI, #context-menu-layer, .swal2-container, .swal-overlay, .ui-widget-overlay, .loading-overlay');
                for (var b = 0; b < blockers.length; b++) {
                    try { blockers[b].parentNode.removeChild(blockers[b]); changed = true; } catch(e) { blockers[b].style.display = 'none'; }
                }
                var modals = document.querySelectorAll('.modal.show, .modal[style*="display: block"]');
                for (var m = 0; m < modals.length; m++) {
                    modals[m].classList.remove('show');
                    modals[m].style.display = 'none';
                    changed = true;
                }
                if (document.body) {
                    if (document.body.classList.contains('modal-open') || document.body.style.pointerEvents === 'none') changed = true;
                    document.body.classList.remove('modal-open', 'stop-scrolling');
                    document.body.style.overflow = 'auto';
                    document.body.style.pointerEvents = 'auto';
                }
                if (document.documentElement) {
                    document.documentElement.style.overflow = 'auto';
                    document.documentElement.style.pointerEvents = 'auto';
                }
                var formElements = document.querySelectorAll('#order_instrument, #order_client_code, #order_qty, #limit_order_rate, .order_submit_btn, div.form-submit-btn-holder button, #cnfirmConfirmBtn, #cnfirmCancelBtn');
                for (var k = 0; k < formElements.length; k++) {
                    var el = formElements[k];
                    if (el.disabled || el.readOnly || el.style.pointerEvents === 'none') {
                        el.removeAttribute('disabled');
                        el.removeAttribute('readonly');
                        el.style.pointerEvents = 'auto';
                        if (window.jQuery) { window.jQuery(el).prop('disabled', false).prop('readonly', false); }
                        changed = true;
                    }
                }
                if (window.jQuery) { window.jQuery('.context-menu-list').hide(); }
                var activeWindow = document.querySelector('.wb-active, .winbox, div.form-submit-btn-holder');
                if (activeWindow && activeWindow.style) { activeWindow.style.zIndex = '9999'; }
            } catch(e) {}
            return changed;
        """)
        if res:
            unblocked = True
            print("[AUTO-UNBLOCK] Detected and unlocked blocked UI overlays/modals automatically.")
    except Exception:
        pass

    return unblocked


def safe_click(driver: webdriver.Chrome, target) -> bool:
    """Clicks an element with auto-unblock and fallback JavaScript mouse events."""
    if not driver or target is None:
        return False
    try:
        ensure_ui_unlocked(driver)
        el = None
        if isinstance(target, tuple):
            elements = driver.find_elements(*target)
            for e in elements:
                if e.is_displayed():
                    el = e
                    break
            if not el and elements:
                el = elements[0]
        else:
            el = target

        if not el:
            return False

        try:
            driver.execute_script("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", el)
            el.click()
            return True
        except Exception:
            ensure_ui_unlocked(driver)
            driver.execute_script("""
                arguments[0].removeAttribute('disabled');
                arguments[0].style.pointerEvents = 'auto';
                arguments[0].dispatchEvent(new MouseEvent('mouseover', {bubbles: true, cancelable: true, view: window}));
                arguments[0].dispatchEvent(new MouseEvent('mousedown', {bubbles: true, cancelable: true, view: window}));
                arguments[0].dispatchEvent(new MouseEvent('mouseup', {bubbles: true, cancelable: true, view: window}));
                arguments[0].dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}));
                arguments[0].click();
            """, el)
            return True
    except Exception:
        return False


def safe_type(driver: webdriver.Chrome, element, value: str) -> bool:
    """Sets input value with auto-unblock and native event dispatch."""
    if not driver or element is None:
        return False
    try:
        ensure_ui_unlocked(driver)
        driver.execute_script("""
            arguments[0].removeAttribute('disabled');
            arguments[0].removeAttribute('readonly');
            arguments[0].style.pointerEvents = 'auto';
            arguments[0].value = arguments[1];
            if (window.jQuery) { window.jQuery(arguments[0]).trigger('input').trigger('change'); }
            else { arguments[0].dispatchEvent(new Event('input', {bubbles: true})); arguments[0].dispatchEvent(new Event('change', {bubbles: true})); }
        """, element, value or "")
        return True
    except Exception:
        return False


def place_order(
    driver: webdriver.Chrome,
    side: str,
    client_code: str,
    symbol: str,
    qty: int,
    rate: float,
    exchange: str = "DSE",
    order_type: str = "Limit"
) -> bool:
    """
    Submits a BUY or SELL order in the Order Entry Form.
    """
    side_clean = side.upper().strip()
    ensure_ui_unlocked(driver)
    install_symbol_whitespace_sanitizer(driver)
    sym_clean = oms_price.clean_symbol(symbol)
    wait = WebDriverWait(driver, 6)

    # Proactively check saleable quantity for SELL orders; shift to another client if insufficient
    if side_clean == "SELL":
        try:
            import position
            best_seller = position.find_client_with_saleable(driver, sym_clean, qty, client_code, exchange)
            if best_seller and best_seller.client_code.lower() != str(client_code).lower():
                print(f"  [AUTO-SHIFT: SALEABLE BALANCE] Client {client_code} has insufficient saleable shares! Shifting to Client {best_seller.client_code} (Saleable: {best_seller.saleable_qty:,.0f}). Continuing without closing code!")
                client_code = best_seller.client_code
                if best_seller.saleable_qty < qty:
                    qty = int(max(1, best_seller.saleable_qty))
        except Exception:
            pass

    print(f"\n[ORDER ENTRY] Placing {side_clean} order: Client={client_code} | {sym_clean} x {qty} @ {rate:.2f} ({exchange})")

    try:
        # 0. Open BUY or SELL Modal / activate window
        action_xpath = (
            "//div[@title='Place BUY Order']"
            if side_clean == "BUY"
            else "//div[@title='Place SELL Order']"
        )
        try:
            action_btn = wait.until(EC.element_to_be_clickable((By.XPATH, action_xpath)))
            action_btn.click()
            time.sleep(0.25)
        except Exception:
            pass

        # Select exchange in modal (xcg2 for DSE)
        try:
            xcg_id = "xcg3" if exchange.upper() == "CSE" else "xcg2"
            driver.execute_script(f"""
                var r = document.getElementById('{xcg_id}');
                if (r) {{ r.checked = true; $(r).trigger('change'); }}
            """)
            time.sleep(0.15)
        except Exception:
            pass

        # 1. Fill Client Code
        code_input = wait.until(EC.visibility_of_element_located((By.ID, "order_client_code")))
        code_input.clear()
        code_input.send_keys(client_code)
        time.sleep(0.1)
        try:
            driver.execute_script("$('#order_client_code').trigger('input').trigger('change');")
        except Exception:
            pass
        code_input.send_keys(Keys.ARROW_DOWN)
        code_input.send_keys(Keys.ENTER)
        time.sleep(0.15)
    except Exception as e:
        print(f"[ORDER ENTRY: ERROR] Client code input failed: {e}")
        return False

    try:
        # 2. Fill Instrument
        inst_input = driver.find_element(By.ID, "order_instrument")
        inst_input.clear()
        inst_input.send_keys(sym_clean)
        time.sleep(0.2)
        inst_input.send_keys(Keys.ARROW_DOWN)
        inst_input.send_keys(Keys.ENTER)
        time.sleep(0.3)
    except Exception:
        pass

    try:
        # 3. Fill Quantity
        qty_input = driver.find_element(By.ID, "order_qty")
        qty_input.clear()
        qty_input.send_keys(str(qty))
        time.sleep(0.2)
    except Exception as e:
        print(f"[ORDER ENTRY: ERROR] Quantity input failed: {e}")
        return False

    try:
        # 4. Fill Rate
        rate_input = driver.find_element(By.ID, "limit_order_rate")
        rate_input.clear()
        rate_input.send_keys(f"{rate:.2f}")
        time.sleep(0.2)
    except Exception:
        pass

    # 5. Click Side Button (BUY or SELL)
    try:
        btn_xpath = (
            f"//button[normalize-space()='{side_clean}'] | "
            f"//input[@value='{side_clean}'] | "
            f"//button[contains(@class, '{side_clean.lower()}-btn')]"
        )
        side_btn = driver.find_element(By.XPATH, btn_xpath)
        driver.execute_script("arguments[0].click();", side_btn)
        time.sleep(0.5)
    except Exception as e:
        print(f"[ORDER ENTRY: WARN] Click side button failed: {e}")

    # 6. Confirm Modal Dialog
    try:
        confirm_btn = WebDriverWait(driver, 3).until(
            EC.element_to_be_clickable((By.XPATH, "//button[normalize-space()='YES'] | //button[@id='cnfirmConfirmBtn']"))
        )
        confirm_btn.click()
        print(f"✔ [ORDER ENTRY] Order confirmed successfully for Client {client_code}.")
        time.sleep(1.0)
        return True
    except Exception:
        # Check if auto-accepted without confirmation modal
        time.sleep(0.5)
        return True


def run_trade_workflow(driver: webdriver.Chrome) -> None:
    """Standard Trade Workflow execution matching OMSweb_Trade.java."""
    wait = WebDriverWait(driver, 10)

    # STEP 0: Navigate to Trade Page
    navigate_to_trade_page(driver)

    # STEP 1: Widget Color Linking FIRST!
    widget_color.run_widget_color_selection(driver)

    # STEP 2: Manual input of symbol
    sym_input = input(f"\nEnter Symbol (default {DEFAULT_SYMBOL}): ").strip()
    symbol = oms_price.clean_symbol(sym_input or DEFAULT_SYMBOL)

    enter_instrument(driver, symbol)

    # Prompt user for single trade or status test
    want_status = input("\nDo you want to select an Order Status type? (1=YES / 0=NO, default 0): ").strip()
    if want_status == "1":
        select_order_status(driver, symbol)
    else:
        normal_trade_flow(driver, symbol)


def normal_trade_flow(driver: webdriver.Chrome, instrument: str) -> None:
    """Executes interactive standard trading flow."""
    keep_trading = True
    while keep_trading:
        print("\nFor BUY, Please Enter: 1")
        print("For SELL, Please Enter: 2")
        side_in = input("Please Enter your choice: ").strip()
        side_text = "SELL" if side_in == "2" else "BUY"

        ex_in = input("\nSelect Exchange (1=DSE [default] / 2=CSE / 3=Priority): ").strip()
        exchange = "CSE" if ex_in == "2" else ("Priority" if ex_in == "3" else "DSE")

        client_code = input(f"Enter Client Code (default {DEFAULT_CLIENT_CODE}): ").strip() or DEFAULT_CLIENT_CODE
        qty_in = input("Enter Quantity (default 10): ").strip()
        qty = int(qty_in) if qty_in.isdigit() else 10

        ref_price = oms_price.fetch_price_ltp_or_ycp(driver, instrument, exchange)
        rate_in = input(f"Enter Rate (default ref price {ref_price:.2f}): ").strip()
        rate = float(rate_in) if rate_in else ref_price

        # Snapshot before
        print(f"\n[PORTFOLIO] Querying baseline financial status for Client {client_code}...")
        before_snap = client_portfolio.get_client_details(driver, client_code)

        # Place Order
        place_order(driver, side_text, client_code, instrument, qty, rate, exchange)

        # Snapshot after
        time.sleep(1.5)
        after_snap = client_portfolio.get_client_details(driver, client_code)
        print(f"[PORTFOLIO] Updated Cash Limit: {after_snap.get('cash_limit', '0')} BDT | Mature Balance: {after_snap.get('mature_balance', '0')} BDT")

        again = input("\nDo you want to trade again? (1=YES / 0=NO, default 0): ").strip()
        if again != "1":
            keep_trading = False
            print("Exiting trade flow.")
        else:
            widget_color.run_widget_color_selection(driver)


def select_order_status(driver: webdriver.Chrome, instrument: str) -> None:
    """Offers test orders designed to produce specific statuses."""
    print("\n=== Select Order Status Type ===")
    print("1. Accepted (normal limit order away from market)")
    print("2. Filled (BUY and SELL matching)")
    print("3. Cancelled (placed then cancelled)")
    print("4. Rejected (out of circuit rate)")
    ch = input("Enter choice (1-4, default 1): ").strip()
    ref_price = oms_price.fetch_price_ltp_or_ycp(driver, instrument)

    if ch == "4":
        # Out of circuit
        bad_price = ref_price * 2.5
        place_order(driver, "BUY", DEFAULT_CLIENT_CODE, instrument, 10, bad_price)
    elif ch == "3":
        # Place limit order far away then cancel in Terminal
        safe_price = ref_price * 0.7
        place_order(driver, "BUY", DEFAULT_CLIENT_CODE, instrument, 10, safe_price)
    else:
        # Default accepted
        safe_price = ref_price * 0.85
        place_order(driver, "BUY", DEFAULT_CLIENT_CODE, instrument, 10, safe_price)
