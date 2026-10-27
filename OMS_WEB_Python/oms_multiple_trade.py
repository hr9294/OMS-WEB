"""
OMS-WEB High-Speed Multi-Trade & Load Testing Engine
Benchmarks OMS trade execution capacity, measuring TPS (Trades Per Second),
throughput, latency, and success/failure ratios under load.
"""
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver

from config import DEFAULT_SYMBOL, SEED_CLIENT_CODES
import widget_color
import oms_price
import oms_trade


class LoadOrder:
    def __init__(
        self,
        seq: int,
        side: str,
        client_code: str,
        symbol: str,
        qty: int,
        rate: float
    ):
        self.seq = seq
        self.side = side
        self.client_code = client_code
        self.symbol = symbol
        self.qty = qty
        self.rate = rate


def run_load_test(driver: webdriver.Chrome) -> None:
    """Executes high-speed multi-trade load test session."""
    # 1. Navigate to Trade Page
    oms_trade.navigate_to_trade_page(driver)

    print("\n=======================================================")
    print("       HIGH-SPEED MULTI-TRADE & LOAD TESTING ENGINE    ")
    print("=======================================================")

    # STEP 0: Widget Color Linking FIRST
    widget_color.run_widget_color_selection(driver)

    # 2. Configure Symbol
    sym_in = input(f"\nSearch Symbol (default {DEFAULT_SYMBOL}): ").strip()
    symbol = oms_price.clean_symbol(sym_in or DEFAULT_SYMBOL)
    oms_trade.enter_instrument(driver, symbol)

    # 3. Load Testing Configuration
    total_trades_in = input("Enter Total Trades to Execute (default 50): ").strip()
    total_trades = int(total_trades_in) if total_trades_in.isdigit() else 50

    dur_in = input("Enter Max Duration budget in seconds (default 60s): ").strip()
    max_duration_sec = int(dur_in) if dur_in.isdigit() else 60

    ref_price = oms_price.fetch_price_ltp_or_ycp(driver, symbol)
    print(f"\n[LOAD TEST ENGINE] Configuring pipeline for {total_trades} trades of {symbol} (Ref: {ref_price:.2f} BDT, Max: {max_duration_sec}s)...")

    # Build Trade Pipeline
    clients = SEED_CLIENT_CODES[:5]
    pipeline: List[LoadOrder] = []
    for i in range(total_trades):
        side = "BUY" if (i % 2 == 0) else "SELL"
        client = clients[i % len(clients)]
        qty = 10
        # Small price offset
        offset = (i % 5) * 0.10
        rate = round(ref_price - 1.0 - offset, 2) if side == "BUY" else round(ref_price + 1.0 + offset, 2)
        pipeline.append(LoadOrder(i + 1, side, client, symbol, qty, rate))

    # 4. Rapid Execution Loop
    start_time = time.time()
    executed_count = 0
    failed_count = 0

    print("\n>>> STARTING HIGH-SPEED LOAD TEST LOOP <<<")
    for order in pipeline:
        elapsed = time.time() - start_time
        if elapsed >= max_duration_sec:
            print(f"[LOAD TEST] Max duration ({max_duration_sec}s) reached. Stopping loop.")
            break

        ok = oms_trade.place_order(
            driver=driver,
            side=order.side,
            client_code=order.client_code,
            symbol=order.symbol,
            qty=order.qty,
            rate=order.rate
        )
        if ok:
            executed_count += 1
        else:
            failed_count += 1

        print(f"  [Progress] {executed_count + failed_count}/{total_trades} | Time: {time.time() - start_time:.1f}s", end="\r")

    total_time = max(0.001, time.time() - start_time)
    tps = executed_count / total_time

    # 5. Metrics & Benchmark Summary
    print("\n\n+=============================================================================+")
    print("|                      LOAD TESTING BENCHMARK METRICS                         |")
    print("+=============================================================================+")
    print(f"| Total Trades Attempted: {executed_count + failed_count:<49} |")
    print(f"| Successfully Executed:  {executed_count:<49} |")
    print(f"| Failed / Rejected:      {failed_count:<49} |")
    print(f"| Total Duration:         {total_time:6.2f} seconds{'':<37} |")
    print(f"| Average Throughput:     {tps:6.2f} Trades / Sec (TPS){'':<27} |")
    print(f"| Average Latency:        {(total_time / max(1, executed_count)) * 1000:6.1f} ms / order{'':<33} |")
    print("+=============================================================================+\n")
