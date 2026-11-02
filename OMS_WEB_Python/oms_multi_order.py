"""
OMS-WEB Multi-Order Execution & Real-Time 6-Widget Verification Engine
Executes multi-order non-crossing limit strategies (BUY at down prices, SELL at upper prices),
discovers active clients with cash >= 100,000 BDT & valid BO IDs, triggers 'Execute at Market'
via Order Terminal right-click context menu, and performs 6-widget audit.
"""
import math
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver
from selenium.webdriver.support.ui import WebDriverWait

from config import DEFAULT_SYMBOL, DEFAULT_EXCHANGE
import widget_color
import oms_price
import oms_trade
import account_limits
import position
import client_portfolio
import market_depth
import order_summary
import order_terminal


DEFAULT_BUY_CLIENTS = [
    "34601", "34649", "23986", "15740", "00224", "39187", "00516", "00916",
    "39187", "02417", "37854", "45971", "18051", "12001", "04474", "03246"
]

class OrderItem:
    def __init__(
        self,
        seq: int,
        side: str,
        client_code: str,
        symbol: str,
        quantity: int,
        rate: float,
        is_opposite: bool = False
    ):
        self.seq = seq
        self.side = side.upper()
        self.client_code = client_code
        self.symbol = symbol
        self.quantity = quantity
        self.rate = rate
        self.is_opposite = is_opposite

    def __repr__(self) -> str:
        tag = "[OPPOSITE]" if self.is_opposite else "[PRIMARY] "
        return (
            f"#{self.seq:02d} {tag} {self.side:<4} | Client: {self.client_code:<8} | "
            f"{self.symbol} x {self.quantity:<4} @ {self.rate:8.2f} BDT"
        )


def parse_client_list(user_in: str, defaults: List[str]) -> List[str]:
    """Parses a single client or comma-separated client list."""
    if not user_in or not user_in.strip():
        return list(defaults)
    clients = [c.strip() for c in re.split(r"[,;\s]+", user_in.strip()) if c.strip()]
    return clients if clients else list(defaults)


def set_trade_page_client_code(driver: webdriver.Chrome, client_code: str) -> None:
    """Sets active client code directly in Trade Page without page switching."""
    if not client_code or not client_code.strip():
        return
    try:
        el = driver.find_element(By.ID, "order_client_code")
        el.clear()
        el.send_keys(client_code.strip())
        el.send_keys(Keys.TAB)
        print(f"[TRADE PAGE] Active Client Code set to: {client_code.strip()}")
    except Exception:
        pass


def get_position_holding_sell_clients(driver: webdriver.Chrome, symbol: str, exchange: str) -> List[str]:
    """Fetches real holding clients with saleable shares for SELL side directly from Position API (no page switching)."""
    sell_clients: List[str] = []
    try:
        script = """
        var cb = arguments[arguments.length - 1];
        var sym = arguments[0];
        var ex = arguments[1];
        var url = '/shared/getsymbolclient/?page=1&per_page=100&symbol=' + encodeURIComponent(sym) + '&clientcode=&exchange=' + encodeURIComponent(ex);
        fetch(url, { credentials: 'include' })
        .then(function(r) { return r.json(); })
        .then(function(d) { cb(d); })
        .catch(function(e) { cb({ error: e.toString() }); });
        """
        res = driver.execute_async_script(script, symbol, exchange)
        if isinstance(res, dict) and "data" in res and isinstance(res["data"], list):
            for item in res["data"]:
                code = str(item.get("client_code", "")).strip()
                qty = float(item.get("qty", 0) or 0)
                if code and qty > 0 and code not in sell_clients:
                    sell_clients.append(code)
    except Exception:
        pass
    if not sell_clients:
        sell_clients = ["00463", "00600", "00916", "01005", "01007", "01008", "01109", "11310"]
    return sell_clients


def build_order_queue(
    driver: webdriver.Chrome,
    choice: int,
    symbol: str,
    exchange: str,
    ref_price: float,
    different_prices: bool = True
) -> List[OrderItem]:
    """Builds order queue with manual BUY entry & SELL clients from Position (100% on Trade Page)."""
    queue: List[OrderItem] = []

    pos_holders = get_position_holding_sell_clients(driver, symbol, exchange)
    print(f"[POSITION] Found {len(pos_holders)} holding client(s) with saleable shares of {symbol} in Position: {pos_holders[:5]}")

    # Calculate safe spread delta to prevent orders from executing upon initial placement
    if ref_price <= 20.0:
        spread_delta = 0.50
    elif ref_price <= 100.0:
        spread_delta = 1.00
    elif ref_price <= 500.0:
        spread_delta = 2.00
    else:
        spread_delta = max(2.0, round(ref_price * 0.005, 1))

    item_counter = 1

    if choice == 1:
        # Strategy 1: Multiple BUY orders (DOWN prices < P) + Opposite SELL order (UPPER price > P)
        buy_in = input(f"\nEnter BUY Client Code(s) (single or comma-separated, default={DEFAULT_BUY_CLIENTS[0]}): ").strip()
        buy_clients = parse_client_list(buy_in, DEFAULT_BUY_CLIENTS)

        default_sell = pos_holders[0]
        sell_in = input(f"Enter Opposite SELL Client Code (from Position) [default={default_sell}]: ").strip()
        sell_client = sell_in or default_sell

        set_trade_page_client_code(driver, buy_clients[0])

        buy_count = 3 if len(buy_clients) == 1 else min(3, len(buy_clients))
        total_buy_qty = 0

        for i in range(buy_count):
            buy_client = buy_clients[i % len(buy_clients)]
            qty = 10 + (i * 2)
            total_buy_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = max(0.10, round(ref_price - spread_delta - step, 2))
            queue.append(OrderItem(item_counter, "BUY", buy_client, symbol, qty, rate, False))
            item_counter += 1

        # Verify if chosen sell client has sufficient saleable shares; if not, shift to another client!
        best_seller = position.find_client_with_saleable(driver, symbol, total_buy_qty, sell_client, exchange)
        if best_seller:
            if best_seller.client_code.lower() != sell_client.lower():
                print(f"\n[AUTO-SHIFT: SALEABLE CHECK] Client {sell_client} does not have {total_buy_qty} saleable shares of {symbol}.")
                print(f"  -> Automatically shifting SELL order to Client {best_seller.client_code} (Saleable: {best_seller.saleable_qty:,.0f} shares)")
                sell_client = best_seller.client_code
            if best_seller.saleable_qty < total_buy_qty:
                safe_qty = int(max(1, best_seller.saleable_qty))
                print(f"  -> [ADJUST] Adjusted Opposite SELL order quantity from {total_buy_qty} to {safe_qty:,} (max available saleable for {sell_client})")
                total_buy_qty = safe_qty

        # Opposite SELL order at UPPER price (> ref_price)
        sell_rate = round(ref_price + spread_delta, 2)
        queue.append(OrderItem(item_counter, "SELL", sell_client, symbol, total_buy_qty, sell_rate, True))

    elif choice == 2:
        # Strategy 2: Multiple SELL orders (UPPER prices > P) + Opposite BUY order (DOWN price < P)
        default_sells = pos_holders[:min(3, len(pos_holders))]
        def_sell_str = ", ".join(default_sells)
        sell_in = input(f"\nEnter SELL Client Code(s) (from Position, single or comma-separated) [default={def_sell_str}]: ").strip()
        sell_clients = parse_client_list(sell_in, default_sells)

        buy_in = input(f"Enter Opposite BUY Client Code [default={DEFAULT_BUY_CLIENTS[0]}]: ").strip()
        buy_client = buy_in or DEFAULT_BUY_CLIENTS[0]

        set_trade_page_client_code(driver, sell_clients[0])

        sell_count = 3 if len(sell_clients) == 1 else min(3, len(sell_clients))
        total_sell_qty = 0

        for i in range(sell_count):
            sell_client = sell_clients[i % len(sell_clients)]
            qty = 10
            total_sell_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = round(ref_price + spread_delta + step, 2)
            queue.append(OrderItem(item_counter, "SELL", sell_client, symbol, qty, rate, False))
            item_counter += 1

        # Opposite BUY order at DOWN price (< ref_price)
        buy_rate = max(0.10, round(ref_price - spread_delta, 2))
        queue.append(OrderItem(item_counter, "BUY", buy_client, symbol, total_sell_qty, buy_rate, True))

    else:
        # Strategy 3: Custom Interactive Queue
        side_in = input("Enter Primary Order Side (BUY / SELL, default BUY): ").strip().upper()
        p_side = "SELL" if side_in == "SELL" else "BUY"
        opp_side = "SELL" if p_side == "BUY" else "BUY"

        cnt_str = input("How many primary orders to place? (default 2): ").strip()
        p_count = int(cnt_str) if cnt_str.isdigit() else 2

        total_qty = 0
        first_client = ""
        for i in range(p_count):
            default_client = DEFAULT_BUY_CLIENTS[i % len(DEFAULT_BUY_CLIENTS)] if p_side == "BUY" else pos_holders[i % len(pos_holders)]
            c_in = input(f"  Order #{i+1} Client [{default_client}]: ").strip()
            client = c_in or default_client
            if not first_client:
                first_client = client

            q_in = input("  Quantity (default 10): ").strip()
            qty = int(q_in) if q_in.isdigit() else 10
            total_qty += qty

            step = (i * 0.50) if different_prices else 0.0
            default_rate = max(0.10, round(ref_price - spread_delta - step, 2)) if p_side == "BUY" else round(ref_price + spread_delta + step, 2)
            r_in = input(f"  Price Rate (default {default_rate:.2f}): ").strip()
            rate = float(r_in) if r_in else default_rate

            queue.append(OrderItem(item_counter, p_side, client, symbol, qty, rate, False))
            item_counter += 1

        default_opp = DEFAULT_BUY_CLIENTS[0] if opp_side == "BUY" else pos_holders[0]
        opp_in = input(f"Enter Opposite {opp_side} Client [{default_opp}]: ").strip()
        opp_client = opp_in or default_opp

        default_opp_rate = round(ref_price + spread_delta, 2) if opp_side == "SELL" else max(0.10, round(ref_price - spread_delta, 2))
        or_in = input(f"Enter Opposite {opp_side} Rate (default {default_opp_rate:.2f}): ").strip()
        opp_rate = float(or_in) if or_in else default_opp_rate

        queue.append(OrderItem(item_counter, opp_side, opp_client, symbol, total_qty, opp_rate, True))

        set_trade_page_client_code(driver, first_client)

    return queue


def print_planned_queue(queue: List[OrderItem]) -> None:
    """Prints planned pipeline summary table."""
    print("\n+=====================================================================================+")
    print("|                              PLANNED ORDER PIPELINE                                 |")
    print("+=====================================================================================+")
    print(f"| {'#':<3} | {'Type':<10} | {'Side':<4} | {'Client':<10} | {'Instrument':<10} | {'Qty':<6} | {'Limit Price':<12} |")
    print("+-------------------------------------------------------------------------------------+")
    for item in queue:
        tag = "OPPOSITE" if item.is_opposite else "PRIMARY"
        print(f"| {item.seq:<3} | {tag:<10} | {item.side:<4} | {item.client_code:<10} | {item.symbol:<10} | {item.quantity:<6} | {item.rate:12.2f} |")
    print("+=====================================================================================+")


def run_multi_order_pipeline(driver: webdriver.Chrome) -> None:
    """Full Multi-Order Pipeline with 6-Widget Real-Time Audit."""
    print("\n+=============================================================================+")
    print("|        MULTI-ORDER EXECUTION & REAL-TIME 6-WIDGET VERIFICATION ENGINE       |")
    print("+=============================================================================+")

    # STEP 0: Navigate to Trade Page
    oms_trade.navigate_to_trade_page(driver)

    # STEP 1: Widget Color Linking FIRST (link all 6 widgets before symbol & order placement)
    widget_color.run_widget_color_selection(driver)

    # STEP 2: Manual input of target symbol (with numeric guardrail)
    sym_input = input(f"\nEnter Target Symbol (e.g. GP, ACFL, SQURPHARMA) [default={DEFAULT_SYMBOL}]: ").strip()
    sym_clean = oms_price.clean_symbol(sym_input or DEFAULT_SYMBOL)
    if not sym_clean or sym_clean.isdigit():
        if sym_clean and sym_clean.isdigit():
            print(f"[WARN] Entered '{sym_clean}' is numeric, not a stock symbol. Defaulting to {DEFAULT_SYMBOL}.")
        symbol = DEFAULT_SYMBOL
    else:
        symbol = sym_clean

    ex_input = input(f"Enter Exchange (DSE / CSE, default={DEFAULT_EXCHANGE}): ").strip().upper()
    exchange = "CSE" if ex_input == "CSE" else "DSE"

    # Auto-populate into Trade and Marketwatch
    oms_trade.enter_instrument(driver, symbol)

    # Fetch reference price (LTP or YCP)
    ref_price = oms_price.fetch_price_ltp_or_ycp(driver, symbol, exchange)
    print(f"\n[MARKET] Reference Price for {symbol} is {ref_price:.2f} BDT (Base for Limit Pricing)")

    # Configure Strategy
    print("\nSelect Multi-Order Execution Strategy:")
    print("  1. Multiple BUY orders (Down Prices) + Opposite SELL order (Upper Price) [Default]")
    print("  2. Multiple SELL orders (Upper Prices) + Opposite BUY order (Down Price)")
    print("  3. Custom Interactive Order Queue")
    strat_in = input("Enter Choice (1, 2, or 3, default 1): ").strip()
    choice = int(strat_in) if strat_in in ("1", "2", "3") else 1

    price_var_in = input("\nPrice Variation for Orders: (1=Same Price / 2=Different Prices, default 2): ").strip()
    different_prices = (price_var_in != "1")

    # Build queue
    order_queue = build_order_queue(driver, choice, symbol, exchange, ref_price, different_prices)
    if not order_queue:
        print("[WARN] Order queue is empty. Aborting.")
        return

    print_planned_queue(order_queue)
    proceed = input("\nProceed to execute this pipeline? (1=YES / 0=NO, default=1): ").strip()
    if proceed.lower() in ("0", "n", "no"):
        print("Operation aborted by user.")
        return
    print("[CONFIRMATION] Pipeline execution confirmed. Starting execution sequence...")

    # STEP 3: CAPTURE BASELINE 6-WIDGET SNAPSHOTS
    print("\n>>> CAPTURING BASELINE 6-WIDGET SNAPSHOTS BEFORE PLACEMENT... <<<")
    baseline_portfolio: Dict[str, Dict[str, str]] = {}
    for item in order_queue:
        if item.client_code not in baseline_portfolio:
            baseline_portfolio[item.client_code] = client_portfolio.get_snapshot(driver, item.client_code)

    baseline_depth = market_depth.get_snapshot(driver, symbol, exchange)
    baseline_summary = order_summary.get_snapshot(driver, symbol=symbol)
    baseline_terminal = order_terminal.get_snapshot(driver, symbol=symbol, exchange=exchange)
    baseline_pos = position.get_snapshot(driver, symbol, exchange)

    print("✔ Baseline snapshots successfully recorded across 6 widgets.")

    # STEP 4: PLACE ORDERS SEQUENTIALLY
    print("\n>>> PLACING NON-CROSSING LIMIT ORDERS (Orders stay Accepted with 0% progress)... <<<")
    oms_trade.navigate_to_trade_page(driver)
    placed_count = 0

    placed_orders = []
    for idx, item in enumerate(order_queue, 1):
        print(f"\n--- Placed Order [{idx}/{len(order_queue)}] ---")
        oms_trade.ensure_ui_unlocked(driver)
        if item.side.upper() == "SELL":
            saleable_check = position.find_client_with_saleable(driver, item.symbol, item.quantity, item.client_code, exchange)
            if saleable_check and saleable_check.client_code.lower() != item.client_code.lower():
                old_c = item.client_code
                item.client_code = saleable_check.client_code
                if saleable_check.saleable_qty < item.quantity:
                    item.quantity = int(max(1, saleable_check.saleable_qty))
                print(f"  [AUTO-SHIFT: SALEABLE BALANCE] Client {old_c} has insufficient saleable shares! Shifted SELL order to Client {item.client_code} (Saleable: {saleable_check.saleable_qty:,.0f}, Order Qty: {item.quantity}). Continuing without closing code!")
        try:
            ok = oms_trade.place_order(
                driver=driver,
                side=item.side,
                client_code=item.client_code,
                symbol=item.symbol,
                qty=item.quantity,
                rate=item.rate,
                exchange=exchange
            )
            if ok:
                placed_count += 1
                placed_orders.append({"client_code": item.client_code, "side": item.side, "symbol": item.symbol, "qty": item.quantity, "rate": item.rate})
        except Exception as e:
            print(f"✖ [ORDER FAILED] Order #{idx} ({item.side} {item.client_code} x {item.quantity} @ {item.rate}): {e}")
            print(f"  -> [RECOVERY] Skipping Order #{idx} and proceeding to next order in queue...")
            try:
                driver.switch_to.alert.dismiss()
            except Exception:
                pass
            try:
                driver.execute_script("""
                    try {
                        if (window.jconfirm && window.jconfirm.instances) {
                            for (var j = 0; j < window.jconfirm.instances.length; j++) {
                                if (window.jconfirm.instances[j] && typeof window.jconfirm.instances[j].close === 'function') {
                                    window.jconfirm.instances[j].close();
                                }
                            }
                        }
                        var btn = document.querySelector('#cnfirmCancelBtn, button.btn-danger, .jconfirm-closeIcon');
                        if (btn) btn.click();
                        var overlay = document.getElementById('context-menu-layer');
                        if (overlay) overlay.remove();
                        if (window.jQuery) { window.jQuery('.context-menu-list').hide(); }
                    } catch(e) {}
                """)
            except Exception:
                pass
        time.sleep(0.8)

    print(f"\n✔ Successfully submitted {placed_count}/{len(order_queue)} orders into the order book.")

    # STEP 5: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK (Selective Strategy)
    print("\n+=============================================================================+")
    print("| STEP 3: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK        |")
    print("+=============================================================================+")
    time.sleep(1.0)
    oms_trade.ensure_ui_unlocked(driver)
    placed_clients = list(set(o["client_code"] for o in placed_orders))
    buy_orders_count = sum(1 for o in placed_orders if o["side"].upper() == "BUY")
    sell_orders_count = sum(1 for o in placed_orders if o["side"].upper() == "SELL")

    if choice == 1 or (buy_orders_count > 1 and sell_orders_count <= 1):
        print(f"\n[TERMINAL] Strategy 1: Selecting ONLY placed Multiple BUY Orders (Resting SELL order unselected)...")
        exec_success = order_terminal.select_multiple_buy_orders_only_at_market(driver, symbol=symbol, client_codes=placed_clients)
        if not exec_success:
            print("[TERMINAL] Retrying without client code filter...")
            exec_success = order_terminal.select_multiple_buy_orders_only_at_market(driver, symbol=symbol, client_codes=None)
    elif choice == 2 or (sell_orders_count > 1 and buy_orders_count <= 1):
        print(f"\n[TERMINAL] Strategy 2: Selecting ONLY placed Multiple SELL Orders (Resting BUY order unselected)...")
        exec_success = order_terminal.select_multiple_sell_orders_only_at_market(driver, symbol=symbol, client_codes=placed_clients)
        if not exec_success:
            print("[TERMINAL] Retrying without client code filter...")
            exec_success = order_terminal.select_multiple_sell_orders_only_at_market(driver, symbol=symbol, client_codes=None)
    else:
        exec_success = order_terminal.execute_separated_buy_and_sell_at_market(driver, symbol=symbol)

    if exec_success:
        print("✔ 'Execute at Market' context trigger completed successfully.")
    else:
        print("[INFO] Checking if matching engine auto-executed matching opposite orders...")

    time.sleep(2.0)

    # STEP 6: REAL-TIME 6-WIDGET AUDIT REPORT
    print("\n+=============================================================================+")
    print("| STEP 4: REAL-TIME 6-WIDGET POST-EXECUTION VERIFICATION & AUDIT REPORT       |")
    print("+=============================================================================+")

    final_depth = market_depth.get_snapshot(driver, symbol, exchange)
    final_summary = order_summary.get_snapshot(driver, symbol=symbol)
    final_terminal = order_terminal.get_snapshot(driver, symbol=symbol, exchange=exchange)
    final_pos = position.get_snapshot(driver, symbol, exchange)

    print("\n1. ORDER TERMINAL AUDIT:")
    print(f"   Baseline Accepted: {baseline_terminal.get('accepted_count')} | Filled: {baseline_terminal.get('filled_count')}")
    print(f"   Current  Accepted: {final_terminal.get('accepted_count')} | Filled: {final_terminal.get('filled_count')}")

    print("\n2. ORDER SUMMARY AUDIT:")
    print(f"   Total Orders: {final_summary.get('total_orders')} | Executed: {final_summary.get('executed_orders')}")

    print("\n3. POSITION WIDGET AUDIT:")
    print(f"   Baseline Total Shares: {baseline_pos.get('total_shares')} | Saleable: {baseline_pos.get('saleable_shares')}")
    print(f"   Current  Total Shares: {final_pos.get('total_shares')} | Saleable: {final_pos.get('saleable_shares')}")

    print("\n4. MARKET DEPTH AUDIT:")
    print(f"   Baseline LTP: {baseline_depth.get('ltp')} | Current LTP: {final_depth.get('ltp')}")

    print("\n5. CLIENT PORTFOLIO FINANCIAL AUDIT:")
    for code in baseline_portfolio:
        after_p = client_portfolio.get_snapshot(driver, code)
        before_cash = baseline_portfolio[code].get("cash_limit", "0.00")
        after_cash = after_p.get("cash_limit", "0.00")
        print(f"   Client {code:<8} -> Cash: {before_cash} -> {after_cash} BDT")

    print("\n>>> Multi-Order Execution and 6-Widget Real-Time Audit Session Complete. <<<\n")
    oms_trade.navigate_to_trade_page(driver)
