"""
OMS-WEB Position Widget Module
Manages Position inspections, holding clients discovery, saleable balances,
and DB/API/DOM position resolution.
"""
import json
import time
from typing import List, Dict, Any, Optional, Iterable, Set
from selenium import webdriver
import oms_price
from config import get_qtrader_db_connection

class ClientPosition:
    def __init__(
        self,
        client_code: str,
        symbol: str,
        exchange: str,
        total_qty: float,
        saleable_qty: float,
        avg_rate: float = 0.0,
        total_cost: float = 0.0,
        market_val: float = 0.0,
        unrealized_pnl: float = 0.0,
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
            f"Total: {self.total_qty:,.0f} | Saleable: {self.saleable_qty:,.0f} | AvgRate: {self.avg_rate:.2f}"
        )

parse_float = oms_price.parse_float

def navigate_to_position(driver: webdriver.Chrome) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var tabs = Array.from(document.querySelectorAll('.lm_tab'));
            var posTab = tabs.find(function(t) { return (t.innerText || '').includes('Position'); });
            if (posTab) posTab.click();
        """)
        time.sleep(0.4)
    except Exception:
        pass

def search_symbol_in_position(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var inp = document.querySelector('#position_symbol_search, #pos_symbol, input[placeholder*="symbol" i]');
            if (inp) {
                inp.value = arguments[0];
                inp.dispatchEvent(new Event('input', { bubbles: true }));
                inp.dispatchEvent(new Event('change', { bubbles: true }));
            }
        """, oms_price.to_public_symbol(symbol))
        time.sleep(0.5)
    except Exception:
        pass

def fetch_positions_from_api(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    if not driver or not symbol:
        return []
    clean_sym = oms_price.clean_symbol(symbol)
    ex = (exchange or "DSE").strip().upper()
    out: List[ClientPosition] = []
    try:
        raw = driver.execute_async_script("""
            var cb = arguments[arguments.length - 1];
            var url = '/shared/getsymbolclient/?page=1&per_page=100&symbol=' + encodeURIComponent(arguments[0]) + '&clientcode=&exchange=' + encodeURIComponent(arguments[1]);
            fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'include' })
            .then(function(r){ return r.text(); })
            .then(function(t){ cb(t); })
            .catch(function(){ cb(null); });
        """, clean_sym, ex)
        if not raw:
            return out
        parsed = json.loads(raw)
        rows = parsed.get("data", {}).get("data", []) if isinstance(parsed.get("data"), dict) else (parsed if isinstance(parsed, list) else [])
        for r in rows:
            if not isinstance(r, dict):
                continue
            code = str(r.get("client_code") or "").strip()
            if not code:
                continue
            tot = parse_float(r.get("total_qty") or r.get("qty"))
            sal = parse_float(r.get("saleable_qty") or r.get("qty"))
            out.append(ClientPosition(
                client_code=code, symbol=clean_sym, exchange=ex,
                total_qty=tot, saleable_qty=sal,
                avg_rate=parse_float(r.get("avg_rate") or r.get("avg_cost")),
                total_cost=parse_float(r.get("total_cost")),
                market_val=parse_float(r.get("value")),
                unrealized_pnl=parse_float(r.get("gain")),
            ))
    except Exception:
        pass
    return out

def extract_from_dom_table(driver: webdriver.Chrome, clean_sym: str, exchange: str = "DSE") -> List[ClientPosition]:
    if not driver:
        return []
    rows_data = driver.execute_script("""
        var table = document.getElementById('symbol_position_table') || document.querySelector('table[id*="position"]');
        if (!table) {
            table = Array.from(document.querySelectorAll('table')).find(function(t) { return (t.innerText || '').toLowerCase().includes('saleable'); });
        }
        if (!table) return [];
        return Array.from(table.querySelectorAll('tbody tr')).map(function(r) {
            return Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });
        }).filter(function(a) { return a.length >= 5; });
    """) or []
    positions: List[ClientPosition] = []
    for r in rows_data:
        code = str(r[0]).strip()
        if not code or code == "-" or "client" in code.lower():
            continue
        positions.append(ClientPosition(
            client_code=code, symbol=clean_sym, exchange=exchange,
            total_qty=parse_float(r[1]), saleable_qty=parse_float(r[2]),
            avg_rate=parse_float(r[3]), total_cost=parse_float(r[4]),
            market_val=parse_float(r[5] if len(r) > 5 else 0),
            unrealized_pnl=parse_float(r[6] if len(r) > 6 else 0),
        ))
    return positions

def get_positions(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    clean_sym = oms_price.clean_symbol(symbol)
    api = fetch_positions_from_api(driver, clean_sym, exchange)
    if api:
        return api
    navigate_to_position(driver)
    search_symbol_in_position(driver, clean_sym, exchange)
    return extract_from_dom_table(driver, clean_sym, exchange)

def get_api_snapshot(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    return fetch_positions_from_api(driver, symbol, exchange)

def get_ui_snapshot(driver: webdriver.Chrome, client_code: str, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    res = get_positions(driver, symbol, exchange)
    if client_code:
        filtered = [p for p in res if p.client_code.strip().lower() == client_code.strip().lower()]
        return filtered or res
    return res

def get_clients_with_saleable(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[ClientPosition]:
    return [p for p in get_positions(driver, symbol, exchange) if p.saleable_qty > 0]

def prompt_or_select_clients_for_sell(
    driver: webdriver.Chrome, symbol: str, exchange: str = "DSE", default_client: str = "23986",
    exclude_client: Optional[str] = None
) -> List[ClientPosition]:
    excl = exclude_client.strip().lower() if exclude_client else ""
    holdings = [p for p in get_clients_with_saleable(driver, symbol, exchange)
                if not excl or p.client_code.strip().lower() != excl]
    if holdings:
        print(f"\n[POSITION] Found {len(holdings)} client(s) holding saleable {symbol}:")
        for idx, h in enumerate(holdings, 1):
            print(f"  {idx}. {h}")
        return holdings
    db_holders = get_holders_from_db(symbol, 1.0, [exclude_client] if exclude_client else None)
    if db_holders:
        print(f"[Client_Position DB] Found holding client(s) for {symbol}: {db_holders[:5]}")
        return [ClientPosition(c, oms_price.clean_symbol(symbol), exchange, 1, 1) for c in db_holders]
    fb = resolve_sell_client_excluding(driver, symbol, 1.0, [exclude_client] if exclude_client else None, exchange) or default_client
    print(f"[POSITION] No saleable holders for {symbol}. Fallback: {fb}.")
    return []

def get_snapshot(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    positions = get_positions(driver, symbol, exchange)
    return {
        "symbol": symbol,
        "exchange": exchange,
        "total_holding_clients": str(len(positions)),
        "total_shares": f"{sum(p.total_qty for p in positions):,.0f}",
        "saleable_shares": f"{sum(p.saleable_qty for p in positions):,.0f}",
    }

def _normalize_exclusions(exclude_client: Optional[str] = None, exclude_clients: Optional[Iterable[str]] = None) -> Set[str]:
    excl: Set[str] = set()
    if exclude_client and exclude_client.strip():
        excl.add(exclude_client.strip().lower())
    if exclude_clients:
        for c in exclude_clients:
            if c and str(c).strip():
                excl.add(str(c).strip().lower())
    return excl

def _select_best_holder(
    positions: List[ClientPosition], required_qty: float, preferred_client: Optional[str] = None,
    exclude_client: Optional[str] = None, exclude_clients: Optional[Iterable[str]] = None
) -> Optional[ClientPosition]:
    excl = _normalize_exclusions(exclude_client, exclude_clients)
    pref = preferred_client.strip().lower() if preferred_client else ""
    candidates = [p for p in positions if p.saleable_qty > 0 and p.client_code.strip().lower() not in excl]
    if not candidates:
        return None
    for cp in candidates:
        if pref and cp.client_code.strip().lower() == pref and cp.saleable_qty >= required_qty:
            return cp
    for cp in candidates:
        if cp.saleable_qty >= required_qty:
            return cp
    return max(candidates, key=lambda c: c.saleable_qty)

def find_client_with_saleable(
    driver: webdriver.Chrome, symbol: str, required_qty: float, preferred_client: Optional[str] = None, exchange: str = "DSE"
) -> Optional[ClientPosition]:
    return _select_best_holder(get_positions(driver, symbol, exchange), required_qty, preferred_client=preferred_client)

def get_holders_from_db(
    symbol: str, required_qty: float = 1.0, exclude_clients: Optional[Iterable[str]] = None, limit: int = 20
) -> List[str]:
    """Queries Client_Position and legacy portfolio tables in PostgreSQL, excluding given clients."""
    clean_sym = oms_price.clean_symbol(symbol)
    if not clean_sym:
        return []
    excl = _normalize_exclusions(exclude_clients=exclude_clients)
    conn = get_qtrader_db_connection()
    if not conn:
        return []
    candidate_sqls = [
        'SELECT "ClientCode" FROM public."Client_Position" WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable_qty, total_qty, qty, 0) >= %s ORDER BY COALESCE(saleable_qty, total_qty, qty, 0) DESC LIMIT %s;',
        'SELECT client_code FROM public."Client_Position" WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable_qty, saleable, total_qty, qty, 0) >= %s ORDER BY COALESCE(saleable_qty, saleable, total_qty, qty, 0) DESC LIMIT %s;',
        'SELECT "ClientCode" FROM public.client_position WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable_qty, total_qty, qty, 0) >= %s ORDER BY COALESCE(saleable_qty, total_qty, qty, 0) DESC LIMIT %s;',
        'SELECT client_code FROM public.client_position WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable_qty, saleable, total_qty, qty, 0) >= %s ORDER BY COALESCE(saleable_qty, saleable, total_qty, qty, 0) DESC LIMIT %s;',
        'SELECT "ClientCode" FROM public.clients_portfolio WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable_qty, total_qty, 0) >= %s ORDER BY COALESCE(saleable_qty, total_qty, 0) DESC LIMIT %s;',
        'SELECT "ClientCode" FROM public.clients_share WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable, qty, 0) >= %s ORDER BY COALESCE(saleable, qty, 0) DESC LIMIT %s;',
        'SELECT client_code FROM public.client_portfolio WHERE UPPER(symbol) = UPPER(%s) AND COALESCE(saleable_qty, total_qty, 0) >= %s ORDER BY COALESCE(saleable_qty, total_qty, 0) DESC LIMIT %s;',
    ]
    holders: List[str] = []
    try:
        for sql in candidate_sqls:
            try:
                with conn.cursor() as cur:
                    cur.execute(sql, (clean_sym, required_qty, limit))
                    for row in cur.fetchall() or []:
                        code = str(row[0]).strip() if row and row[0] else ""
                        if code and code.lower() not in excl and code not in holders:
                            holders.append(code)
                if holders:
                    return holders
            except Exception:
                try:
                    conn.rollback()
                except Exception:
                    pass
        return holders
    finally:
        try:
            conn.close()
        except Exception:
            pass

def find_holder_with_sufficient_shares_from_db(symbol: str, required_qty: float, exclude_client: Optional[str] = None) -> Optional[str]:
    excl = [exclude_client] if exclude_client else None
    holders = get_holders_from_db(symbol, required_qty, exclude_clients=excl, limit=20)
    if not holders and required_qty > 1.0:
        holders = get_holders_from_db(symbol, 1.0, exclude_clients=excl, limit=20)
    return holders[0] if holders else None

def find_client_with_saleable_excluding(
    driver: Optional[webdriver.Chrome], symbol: str, required_qty: float,
    exclude_client: Optional[str] = None, exchange: str = "DSE",
    exclude_clients: Optional[Iterable[str]] = None
) -> Optional[ClientPosition]:
    positions = get_positions(driver, symbol, exchange) if driver else []
    best = _select_best_holder(positions, required_qty, exclude_client=exclude_client, exclude_clients=exclude_clients)
    if best:
        return best
    excl = _normalize_exclusions(exclude_client, exclude_clients)
    db_holders = get_holders_from_db(symbol, required_qty, exclude_clients=excl, limit=10)
    if not db_holders and required_qty > 1.0:
        db_holders = get_holders_from_db(symbol, 1.0, exclude_clients=excl, limit=10)
    if db_holders:
        return ClientPosition(db_holders[0], oms_price.clean_symbol(symbol), exchange, required_qty, required_qty)
    return None

def resolve_sell_client_excluding(
    driver: Optional[webdriver.Chrome], symbol: str, required_qty: float = 1.0,
    exclude_clients: Optional[Any] = None, exchange: str = "DSE"
) -> str:
    excl_list = [exclude_clients] if isinstance(exclude_clients, str) else list(exclude_clients or [])
    excl = _normalize_exclusions(exclude_clients=excl_list)
    cp = find_client_with_saleable_excluding(driver, symbol, required_qty, exchange=exchange, exclude_clients=excl)
    if cp and cp.client_code and cp.client_code.strip().lower() not in excl:
        return cp.client_code.strip()
    for fb in ("11310", "00128", "00463", "00600", "00224", "34601"):
        if fb.lower() not in excl:
            return fb
    return "11310"
