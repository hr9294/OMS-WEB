"""
OMS-WEB Automation Suite - Master Entry Point
Unified Python runner matching OMSwebmain.java architecture.
"""
import argparse
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

import config
from driver_factory import create_driver
import oms_login
import oms_trade
import oms_multi_order
import oms_multiple_trade
import oms_upload_data
import oms_export_data
import account_limits
import position
import client_portfolio
import market_depth
import order_terminal
import widget_color
import oms_dashboard
import rms
import day_netting
import ui_testing
import oms_test_runner

def print_banner() -> None:
    print("+=============================================================================+")
    print("|          WELCOME TO THE UFTFAST ORDER MANAGEMENT SYSTEM (OMS-WEB)           |")
    print("|                    PYTHON AUTOMATION & LOAD TEST SUITE                      |")
    print("+=============================================================================+")

def _dispatch_mode(driver, mode: str) -> None:
    if mode == "db-ltp":
        from db_market_data import print_public_board_summary
        print_public_board_summary("DSE", limit=50)
    elif mode == "multi-order":
        oms_multi_order.run_multi_order_pipeline(driver)
    elif mode == "load-test":
        oms_multiple_trade.run_load_test(driver)
    elif mode == "upload":
        oms_upload_data.run_upload_console(driver)
    elif mode == "export":
        oms_export_data.run_export_console(driver)
    elif mode == "day-netting":
        day_netting.run_day_netting_console(driver)
    elif mode == "account-limits":
        account_limits.get_active_buy_clients_with_cash(driver, 100000.0, 5)
    elif mode == "position":
        sym = config.read_line_with_timeout("Enter Symbol for Position check (default GP): ", "GP", 4.0) or "GP"
        position.prompt_or_select_clients_for_sell(driver, sym)
    elif mode == "portfolio":
        code = config.read_line_with_timeout("Enter Client Code (default 23986): ", "23986", 4.0) or "23986"
        client_portfolio.print_portfolio(code, client_portfolio.get_snapshot(driver, code))
    elif mode == "depth":
        sym = config.read_line_with_timeout("Enter Symbol for Market Depth (default GP): ", "GP", 4.0) or "GP"
        print(f"\nMarket Depth for {sym}: {market_depth.get_snapshot(driver, sym)}")
    elif mode == "terminal":
        print(f"\nOrder Terminal Status: {order_terminal.get_snapshot(driver)}")
    elif mode == "dashboard":
        oms_dashboard.run_live_reflection_audit(driver)
    elif mode == "rms":
        rms.run_rms_console(driver)
    elif mode == "ui-test":
        ui_testing.run_ui_testing(existing_driver=driver)
    else:
        oms_trade.run_trade_workflow(driver)

def run_session_loop(driver, initial_mode: str = "") -> None:
    mode_map = {
        "1": "trade", "2": "multi-order", "3": "load-test", "4": "upload",
        "5": "export", "6": "day-netting", "7": "dashboard", "8": "rms",
        "9": "ui-test", "10": "account-limits", "11": "db-ltp",
        "12": "position", "13": "portfolio", "14": "depth", "15": "terminal",
    }
    first_pass = bool(initial_mode)
    while True:
        if first_pass:
            first_pass = False
            _dispatch_mode(driver, initial_mode)
            if config.IS_CI:
                break
            continue
        print("\n=== [UFTFAST OMS ACTIVE SESSION] Select Action ===")
        print("  1. Standard Trade & 6-Widget Verification")
        print("  2. Multi-Order Execution & Real-time 6-Widget Audit")
        print("  3. High-Speed Multi-Trade & Load Testing Engine")
        print("  4. Upload File Engine (Client, Stock, Cash - Safe Guarded)")
        print("  5. Export File Engine (Trade Files, EOD Price, Commission & Reports)")
        print("  6. Day Netting Engine (Scrip Netting, RMS Controls & 9-Scenario) [Default]")
        print("  7. Dashboard 12-Model Reflection & Live Audit")
        print("  8. RMS Risk Management Engine (qRMS + Connected Trade Gate)")
        print("  9. Cross-Browser UI Testing Audit (6 Modules)")
        print("  10. Account Limits | 11. DB LTPs | 12-15. Position/Portfolio/Depth/Terminal")
        print("  0. Exit Application")
        choice = config.read_line_with_timeout("Enter Choice (0-15, default 6 in 5s): ", "6", 5.0)
        if choice in ("0", "exit", "quit"):
            break
        try:
            oms_trade.ensure_trade_settings_restored(driver)
        except Exception:
            pass
        _dispatch_mode(driver, mode_map.get(choice, "day-netting"))
        if config.IS_CI:
            break

def main() -> None:
    parser = argparse.ArgumentParser(description="OMS-WEB Python Automation Suite")
    parser.add_argument(
        "--mode",
        choices=[
            "trade", "multi-order", "load-test", "upload", "export", "day-netting",
            "account-limits", "position", "portfolio", "depth", "terminal",
            "db-ltp", "dashboard", "rms", "ui-test", "test-runner",
        ],
        help="Directly launch a specific engine without prompt",
    )
    parser.add_argument("--headless", action="store_true", help="Run Chrome in headless mode")
    parser.add_argument("--username", help="OMS username override")
    parser.add_argument("--password", help="OMS password override")
    args = parser.parse_args()
    print_banner()
    if args.mode == "test-runner":
        sys.exit(0 if oms_test_runner.run_unified_tests("all") else 1)
    driver = create_driver(headless=args.headless or config.IS_HEADLESS)
    try:
        if not oms_login.login(driver=driver, username=args.username, password=args.password):
            print("[ERROR] Login failed. Exiting suite.")
            return
        if not args.mode:
            oms_trade.navigate_to_trade_page(driver, force=True)
            widget_color.run_widget_color_selection(driver)
        run_session_loop(driver, initial_mode=args.mode or "")
    except KeyboardInterrupt:
        print("\n[OMS-WEB] Execution interrupted by user.")
    finally:
        keep_open = config.read_line_with_timeout("Close browser window now? (1=YES / 0=NO, default 1): ", "1", 4.0)
        if keep_open != "0":
            try:
                driver.quit()
            except Exception:
                pass

if __name__ == "__main__":
    main()
