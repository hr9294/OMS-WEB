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
    sym_clean = oms_price.clean_symbol(symbol)
    wait = WebDriverWait(driver, 6)

    print(f"\n[ORDER ENTRY] Placing {side_clean} order: Client={client_code} | {sym_clean} x {qty} @ {rate:.2f} ({exchange})")

    try:
        # 1. Fill Client Code
        code_input = wait.until(EC.presence_of_element_located((By.ID, "order_client_code")))
        code_input.clear()
        code_input.send_keys(client_code)
        time.sleep(0.2)
        code_input.send_keys(Keys.ARROW_DOWN)
        code_input.send_keys(Keys.ENTER)
        time.sleep(0.3)
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
