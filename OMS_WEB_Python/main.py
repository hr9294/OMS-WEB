"""
OMS-WEB Automation Suite - Master Entry Point
Unified Python runner matching OMSwebmain.java architecture.
"""
import argparse
import sys
import time

from driver_factory import create_driver
import oms_login
import oms_trade
import oms_multi_order
import oms_multiple_trade
import account_limits
import position
import client_portfolio
import market_depth
import order_terminal


def print_banner() -> None:
    print("+=============================================================================+")
    print("|          WELCOME TO THE UFTFAST ORDER MANAGEMENT SYSTEM (OMS-WEB)           |")
    print("|                    PYTHON AUTOMATION & LOAD TEST SUITE                      |")
    print("+=============================================================================+")


def main() -> None:
    parser = argparse.ArgumentParser(description="OMS-WEB Python Automation Suite")
    parser.add_argument("--mode", choices=["trade", "multi-order", "load-test", "account-limits", "position", "portfolio", "depth", "terminal"],
                        help="Directly launch a specific engine without prompt")
    parser.add_argument("--headless", action="store_true", help="Run Chrome in headless mode")
    parser.add_argument("--username", help="OMS username override")
    parser.add_argument("--password", help="OMS password override")
    args = parser.parse_args()

    print_banner()

    print("[SETUP] Initializing Chrome browser session...")
    driver = create_driver(headless=args.headless)

    try:
        # Perform Login
        logged_in = oms_login.login(
            driver=driver,
            username=args.username,
            password=args.password
        )
        if not logged_in:
            print("[ERROR] Login failed. Exiting suite.")
            return

        mode = args.mode
        if not mode:
            # 1. FIRST OPEN THE TRADE PAGE
            print("\n[UFTFAST OMS] 1. Opening Trade Page...")
            oms_trade.navigate_to_trade_page(driver)

            # 2. THEN SELECT WIDGET COLOR LINK
            print("[UFTFAST OMS] 2. Configuring Widget Color Link...")
            widget_color.run_widget_color_selection(driver)

            # 3. THEN TRADE EXECUTION OPTION
            print("\n[UFTFAST OMS] 3. Select Trading Execution Option:")
            print("  1. Standard Trade & 6-Widget Verification [Default]")
            print("  2. Multi-Order Execution, Right-Click 'Execute at Market' & Real-time 6-Widget Audit")
            print("  3. High-Speed Multi-Trade & Load Testing Engine (Throughput / TPS)")
            print("  4. Account Limits & High-Cash Client Discovery")
            print("  5. Position & Saleable Holdings Viewer")
            print("  6. Client Portfolio Viewer")
            print("  7. Market Depth & Quotes")
            print("  8. Order Terminal Viewer")
            choice = input("Enter Choice (1-8, default 1): ").strip()

            mode_map = {
                "1": "trade",
                "2": "multi-order",
                "3": "load-test",
                "4": "account-limits",
                "5": "position",
                "6": "portfolio",
                "7": "depth",
                "8": "terminal"
            }
            mode = mode_map.get(choice, "trade")

        # Launch selected workflow
        if mode == "multi-order":
            oms_multi_order.run_multi_order_pipeline(driver)
        elif mode == "load-test":
            oms_multiple_trade.run_load_test(driver)
        elif mode == "account-limits":
            account_limits.get_active_buy_clients_with_cash(driver, 100000.0, 5)
        elif mode == "position":
            sym = input("Enter Symbol for Position check (default GP): ").strip() or "GP"
            position.prompt_or_select_clients_for_sell(driver, sym)
        elif mode == "portfolio":
            code = input("Enter Client Code (default 23986): ").strip() or "23986"
            client_portfolio.print_portfolio(code, client_portfolio.get_snapshot(driver, code))
        elif mode == "depth":
            sym = input("Enter Symbol for Market Depth (default GP): ").strip() or "GP"
            snap = market_depth.get_snapshot(driver, sym)
            print(f"\nMarket Depth for {sym}: {snap}")
        elif mode == "terminal":
            snap = order_terminal.get_snapshot(driver)
            print(f"\nOrder Terminal Status: {snap}")
        else:
            oms_trade.run_trade_workflow(driver)

    except KeyboardInterrupt:
        print("\n[OMS-WEB] Execution interrupted by user.")
    except Exception as e:
        print(f"\n[OMS-WEB: ERROR] An unexpected exception occurred: {e}")
        import traceback
        traceback.print_exc()
    finally:
        print("\n[OMS-WEB] Session finished.")
        keep_open = input("Close browser window now? (1=YES / 0=NO, default 1): ").strip()
        if keep_open != "0":
            try:
                driver.quit()
                print("[BROWSER] Session closed.")
            except Exception:
                pass


if __name__ == "__main__":
    main()
