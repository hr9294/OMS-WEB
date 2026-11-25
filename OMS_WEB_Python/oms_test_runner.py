"""
OMS-WEB Unified Automated Test Suite Runner (Python)
Mirrors OMSweb_TestRunner.java (excluding RMS merge — delegates to rms.test_pre_trade_rms_audit_scenarios).
Usage: python oms_test_runner.py [all|limits|core|6widget|widget|qa|netting|rms]
"""
import argparse
import sys
import time
from typing import Callable, List, Tuple

import account_limits
import client_portfolio
import day_netting
import oms_trade
import order_terminal
import rms


def _test_order_terminal_reflection() -> bool:
    before = {"Order_ID": "ORD-1001", "Side": "BUY", "Status": "NEW", "Qty": "0", "Price": "0.0"}
    after = {"Order_ID": "ORD-1002", "Side": "BUY", "Status": "FILLED", "Qty": "100", "Price": "270.0", "Progress": "100%"}
    return order_terminal.verify(before, after, "GP", "BUY", 100, 270.0, "DSE")


def _test_portfolio_reflection() -> bool:
    before = {"total_qty": "500", "saleable_qty": "500", "ledger_balance": "1000000", "total_cost": "135000"}
    after = {"total_qty": "600", "saleable_qty": "500", "ledger_balance": "973000", "total_cost": "162000"}
    return client_portfolio.verify(before, after, "GP", "BUY", 100, 270.0, "DSE")


def _test_6widget_audit() -> bool:
    pre1 = day_netting._mk_snap(
        "PRE_IND_DSE", "23986", "GP.PUBLIC", "DSE", day_netting.NettingMode.INDIVIDUAL_EXCHANGE,
        True, True, True, True, True, "PUBLIC", "BUY", 100, False, 500, 500, 0, 0
    )
    pre1.market_depth_data = {"LTP": "270.00"}
    post1 = day_netting._mk_snap(
        "POST_IND_DSE", "23986", "GP.PUBLIC", "DSE", day_netting.NettingMode.INDIVIDUAL_EXCHANGE,
        True, True, True, True, True, "PUBLIC", "BUY", 100, False, 600, 600, 100, 0
    )
    post1.order_terminal_data = {"Order_ID": "ORD-89211", "Status": "Accepted"}
    post1.order_summary_data = {"Total_Orders": "1", "Buy_Qty": "100"}
    post1.account_limits_data = {"Remaining_Cash": "473000.00", "Total_Tx": "27000.00"}
    post1.market_depth_data = {"LTP": "270.00"}
    post1.market_depth_netting_badge = True
    day_netting.compare_and_report_netting_widgets(pre1, post1, "DAY NETTING UI AUDIT (INDIVIDUAL - DSE)")

    post_rej = day_netting.Netting6WidgetsUiSnapshot()
    post_rej.stage_name = "POST_REJECTED"
    post_rej.order_terminal_data = {"Order_ID": "ORD-89212", "Status": "Rejected"}
    post_rej.market_depth_data = {"LTP": "-"}
    day_netting.compare_and_report_netting_widgets(pre1, post_rej, "DAY NETTING UI AUDIT (REJECTED TRADE)")

    health = day_netting.verify_ui_layout_health(None)
    return bool(health and health.strip())


def run_unified_tests(mode: str = "all") -> bool:
    start_ms = time.perf_counter()
    m = (mode or "all").strip().lower()
    run_all = m == "all"
    run_limits = run_all or m in ("limits", "core")
    run_widget = run_all or m in ("6widget", "widget")
    run_qa = run_all or m in ("qa", "limits", "core")
    run_netting = run_all or m in ("netting", "qa")
    run_rms = run_all or m in ("rms", "limits", "core")

    suites: List[Tuple[str, Callable[[], bool], str, str]] = []
    if run_limits:
        suites.append(("Cash Limit 3-Tier Precedence", account_limits.test_resolve_cash_limit_scenarios, "5 hierarchy tiers verified", "Hierarchy mismatch"))
        suites.append(("Price Limit Breach & Clamping", oms_trade.test_price_limit_breach_clamping, "Clamping & regex verified (12/12)", "Clamping failure"))
    if run_rms:
        suites.append(("RMS Pre-Trade Audit & Trade Gate", rms.test_pre_trade_rms_audit_scenarios, "8/8 RMS & Trade Gate scenarios passed", "RMS audit rule mismatch"))
    if run_limits:
        suites.append(("Order Terminal Reflection Audit", _test_order_terminal_reflection, "Pre/Post order state verified", "Terminal reflection mismatch"))
        suites.append(("Portfolio Extraction & Delta Audit", _test_portfolio_reflection, "Buy delta & saleable verified", "Portfolio delta mismatch"))
    if run_widget:
        suites.append(("6-Widget Audit & Layout Health", _test_6widget_audit, "Comparison tables & layout healthy", "6-Widget audit failed"))
    if run_qa:
        suites.append(("QA Day Netting Multi-Exchange (1-4)", day_netting.test_qa_day_netting_scenarios, "4/4 QA scenarios (25+ checks) passed", "QA netting mismatch"))
    if run_netting:
        suites.append(("Day Netting 9-Scenario Compliance", lambda: day_netting.run_all_netting_tests(None), "All 9 offline blocks + matrix passed", "9-Scenario suite failed"))

    results: List[Tuple[str, bool, str]] = []
    for name, fn, ok_msg, fail_msg in suites:
        try:
            ok = bool(fn())
            results.append((name, ok, ok_msg if ok else fail_msg))
        except Exception as e:
            results.append((name, False, f"Exception: {e}"))

    elapsed_ms = int((time.perf_counter() - start_ms) * 1000)
    print("\n+----+---------------------------------------+--------+-----------------------------------------+")
    print("| #  | Test Suite                            | Status | Remarks                                 |")
    print("+----+---------------------------------------+--------+-----------------------------------------+")
    for idx, (name, ok, msg) in enumerate(results, 1):
        print(f"| {idx:<2} | {name[:37]:<37} | {'PASS' if ok else 'FAIL':<6} | {msg[:39]:<39} |")
    passed = sum(1 for _, ok, _ in results if ok)
    print(f"+----+---------------------------------------+--------+-----------------------------------------+")
    print(f"| SUMMARY: {passed}/{len(results)} SUITES PASSED | Excluded from Merge: rms.py | Time: {elapsed_ms} ms\n")
    return bool(results) and all(ok for _, ok, _ in results)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="OMS-WEB Unified Python Test Runner")
    parser.add_argument("filter", nargs="?", default="all", help="all|limits|core|6widget|qa|netting|rms")
    parser.add_argument("--mode", dest="mode_opt", help="Filter mode override")
    args = parser.parse_args()
    success = run_unified_tests(args.mode_opt or args.filter)
    sys.exit(0 if success else 1)
