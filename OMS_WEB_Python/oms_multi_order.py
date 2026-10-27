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


def build_order_queue(
    driver: webdriver.Chrome,
    choice: int,
    symbol: str,
    exchange: str,
    ref_price: float,
    different_prices: bool = True
) -> List[OrderItem]:
    """Builds the non-crossing order queue with dynamic high-cash client discovery."""
    queue: List[OrderItem] = []

    # 1. Discover Active BUY Clients with Cash >= 100,000 BDT & Valid BO ID
    real_buy_clients = account_limits.get_active_buy_clients_with_cash(driver, 100000.0, 5)
    if not real_buy_clients:
        real_buy_clients = ["10009", "10004", "10102"]

    # 2. Query Real Holding Clients for SELL side from Position widget
    real_sell_clients: List[str] = []
    try:
        holdings = position.get_clients_with_saleable(driver, symbol, exchange)
        if holdings:
            real_sell_clients = [h.client_code for h in holdings]
            print(f"[POSITION] Found {len(real_sell_clients)} real holding clients with saleable shares of {symbol}.")
    except Exception as e:
        print(f"[POSITION: WARN] {e}")

    if not real_sell_clients:
        real_sell_clients = ["11310", "12245", "14118", "19363", "26687"]

    # 3. Calculate safe spread delta to prevent orders from executing upon initial placement
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
        buy_count = min(3, len(real_buy_clients))
        total_buy_qty = 0

        for i in range(buy_count):
            buy_client = real_buy_clients[i % len(real_buy_clients)]
            qty = 10 + (i * 2)
            total_buy_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = max(0.10, round(ref_price - spread_delta - step, 2))
            queue.append(OrderItem(item_counter, "BUY", buy_client, symbol, qty, rate, False))
            item_counter += 1

        # Opposite SELL order at UPPER price (> ref_price)
        sell_client = real_sell_clients[0]
        sell_rate = round(ref_price + spread_delta, 2)
        queue.append(OrderItem(item_counter, "SELL", sell_client, symbol, total_buy_qty, sell_rate, True))

    elif choice == 2:
        # Strategy 2: Multiple SELL orders (UPPER prices > P) + Opposite BUY order (DOWN price < P)
        sell_count = min(3, len(real_sell_clients))
        total_sell_qty = 0

        for i in range(sell_count):
            sell_client = real_sell_clients[i % len(real_sell_clients)]
            qty = 10
            total_sell_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = round(ref_price + spread_delta + step, 2)
            queue.append(OrderItem(item_counter, "SELL", sell_client, symbol, qty, rate, False))
            item_counter += 1

        # Opposite BUY order at DOWN price (< ref_price)
        buy_client = real_buy_clients[0]
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
        for i in range(p_count):
            client = real_buy_clients[i % len(real_buy_clients)] if p_side == "BUY" else real_sell_clients[i % len(real_sell_clients)]
            qty = 10
            total_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = max(0.10, round(ref_price - spread_delta - step, 2)) if p_side == "BUY" else round(ref_price + spread_delta + step, 2)
            queue.append(OrderItem(item_counter, p_side, client, symbol, qty, rate, False))
            item_counter += 1

        opp_client = real_sell_clients[0] if p_side == "BUY" else real_buy_clients[0]
        opp_rate = round(ref_price + spread_delta, 2) if opp_side == "SELL" else max(0.10, round(ref_price - spread_delta, 2))
        queue.append(OrderItem(item_counter, opp_side, opp_client, symbol, total_qty, opp_rate, True))

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

    # STEP 2: Manual input of target symbol
    sym_input = input(f"\nEnter Target Symbol (e.g. GP, ACFL, SQURPHARMA) [default={DEFAULT_SYMBOL}]: ").strip()
    symbol = oms_price.clean_symbol(sym_input or DEFAULT_SYMBOL)

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
    proceed = input("\nProceed to execute this pipeline? (1=YES / 0=NO, default 1): ").strip()
    if proceed == "0":
        print("Operation cancelled by user.")
        return

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

    for idx, item in enumerate(order_queue, 1):
        print(f"\n--- Placed Order [{idx}/{len(order_queue)}] ---")
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
        time.sleep(0.8)

    print(f"\n✔ Successfully submitted {placed_count}/{len(order_queue)} orders into the order book.")

    # STEP 5: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK
    print("\n+=============================================================================+")
    print("| STEP 3: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK        |")
    print("+=============================================================================+")
    time.sleep(1.0)
    exec_success = order_terminal.select_and_execute_at_market(driver, symbol=symbol, count=len(order_queue))
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
