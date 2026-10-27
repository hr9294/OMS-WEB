"""
OMS-WEB Position Widget Module
Manages Position inspections, holding clients discovery, saleable balances,
and Position-specific widget color linking.
"""
import re
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver

from config import COLOR_NAMES, COLOR_HEXES


class ClientPosition:
    def __init__(
        self,
        client_code: str,
        symbol: str,
        exchange: str,
        total_qty: float,
        saleable_qty: float,
        avg_rate: float,
        total_cost: float,
        market_val: float,
        unrealized_pnl: float,
    ):
        self.client_code = client_code
        self.symbol = symbol
        self.exchange = exchange
        self.total_qty = total_qty
        self.saleable_qty = saleable_qty
        self.avg_rate = avg_rate
        self.total_cost = total_cost
        self.market_val = market_val
        self.unrealized_pnl = unrealized_pnl

    def __repr__(self) -> str:
        return (
            f"Client: {self.client_code:<8} | {self.symbol} ({self.exchange}) | "
            f"Total: {self.total_qty:,.0f} | Saleable: {self.saleable_qty:,.0f} | "
            f"AvgRate: {self.avg_rate:.2f} | P/L: {self.unrealized_pnl:,.2f}"
        )


def parse_float(val: Any) -> float:
    if val is None:
        return 0.0
    try:
        clean = re.sub(r"[^\d.-]", "", str(val).strip())
        return float(clean) if clean else 0.0
    except (ValueError, TypeError):
        return 0.0


def navigate_to_position(driver: webdriver.Chrome) -> None:
    """Navigates to Position widget in GoldenLayout."""
    try:
        script = """
        var tabs = Array.from(document.querySelectorAll('.lm_tab'));
        var posTab = tabs.find(function(t) { return (t.innerText || '').includes('Position'); });
        if (posTab) { posTab.click(); return true; }
        return false;
        """
        driver.execute_script(script)
        time.sleep(0.4)
    except Exception:
        pass


def search_symbol_in_position(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> None:
    """Inputs target symbol in Position widget filter."""
    try:
        script = """
        var sym = arguments[0];
        var inp = document.querySelector('#position_symbol_search, #pos_symbol, input[placeholder*="symbol" i]');
        if (inp) {
            inp.value = sym;
            inp.dispatchEvent(new Event('input', { bubbles: true }));
            inp.dispatchEvent(new Event('change', { bubbles: true }));
        }
        """
        driver.execute_script(script, symbol)
        time.sleep(0.5)
    except Exception:
        pass


def get_positions(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    """Scrapes all client position rows for the symbol from the position table."""
    navigate_to_position(driver)
    search_symbol_in_position(driver, symbol, exchange)

    script = """
    var sym = arguments[0].toLowerCase();
    var table = document.getElementById('symbol_position_table') || document.querySelector('table[id*="position"]');
    if (!table) {
        var tables = Array.from(document.querySelectorAll('table'));
        table = tables.find(function(t) { return (t.innerText || '').toLowerCase().includes('saleable'); });
    }
    if (!table) return [];

    var rows = Array.from(table.querySelectorAll('tbody tr'));
    var results = [];
    rows.forEach(function(r) {
        var cells = Array.from(r.querySelectorAll('td'));
        if (cells.length >= 5) {
            var rowText = cells.map(function(c) { return c.innerText.trim(); });
            results.push(rowText);
        }
    });
    return results;
    """
    rows_data = driver.execute_script(script, symbol) or []
    positions: List[ClientPosition] = []

    for r in rows_data:
        if len(r) >= 5:
            code = r[0]
            if not code or code == "-" or "client" in code.lower():
                continue
            total_qty = parse_float(r[1] if len(r) > 1 else 0)
            saleable = parse_float(r[2] if len(r) > 2 else 0)
            avg_rate = parse_float(r[3] if len(r) > 3 else 0)
            cost = parse_float(r[4] if len(r) > 4 else 0)
            mkt_val = parse_float(r[5] if len(r) > 5 else 0)
            pnl = parse_float(r[6] if len(r) > 6 else 0)

            positions.append(ClientPosition(
                client_code=code,
                symbol=symbol,
                exchange=exchange,
                total_qty=total_qty,
                saleable_qty=saleable,
                avg_rate=avg_rate,
                total_cost=cost,
                market_val=mkt_val,
                unrealized_pnl=pnl,
            ))

    return positions


def get_clients_with_saleable(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    """Returns holding clients who have saleable_qty > 0."""
    all_pos = get_positions(driver, symbol, exchange)
    return [p for p in all_pos if p.saleable_qty > 0]


def prompt_or_select_clients_for_sell(
    driver: webdriver.Chrome,
    symbol: str,
    exchange: str = "DSE",
    default_client: str = "23986"
) -> List[ClientPosition]:
    """Finds real holding clients with saleable shares for SELL orders."""
    holdings = get_clients_with_saleable(driver, symbol, exchange)
    if holdings:
        print(f"\n[POSITION] Found {len(holdings)} client(s) holding saleable shares of {symbol}:")
        for idx, h in enumerate(holdings, 1):
            print(f"  {idx}. Client {h.client_code:<8} | Saleable: {h.saleable_qty:,.0f} shares | Avg Rate: {h.avg_rate:.2f}")
        return holdings
    else:
        print(f"[POSITION] No clients found with saleable shares of {symbol}. Fallback to {default_client}.")
        return []


def get_snapshot(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    """Captures summary snapshot of position holdings for symbol."""
    positions = get_positions(driver, symbol, exchange)
    total_shares = sum(p.total_qty for p in positions)
    saleable_shares = sum(p.saleable_qty for p in positions)
    return {
        "symbol": symbol,
        "exchange": exchange,
        "total_holding_clients": str(len(positions)),
        "total_shares": f"{total_shares:,.0f}",
        "saleable_shares": f"{saleable_shares:,.0f}",
    }
