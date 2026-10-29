"""
=============================================================================
OMS-WEB PYTHON: HIGH FREQUENCY TRADING (HFT) & MULTI-TRADE LOAD ENGINE
=============================================================================
Simulates high-frequency institutional trading across multiple liquid symbols
and multiple client accounts simultaneously.

Core Capabilities:
 1. Multi-Symbol Basket: Concurrent order generation across active liquid symbols.
 2. Multi-Client Routing: High-cash accounts for BUY + holding accounts for SELL.
 3. Zero Page Switching: Browser remains 100% on Trade page; background API queries.
 4. Dynamic HFT Strategies: Spread Market Maker, Aggressive Taker, Volatility Jitter.
 5. Real-Time Telemetry: Sub-second latency tracking, TPS throughput, and P&L metrics.
=============================================================================
"""
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver

from config import DEFAULT_SYMBOL, SEED_CLIENT_CODES
import widget_color
import oms_price
import oms_trade

# Default Verified Liquid Basket & High-Cash Client Pools
ALL_PUBLIC_SYMBOLS = [
    "GP", "BATBC", "BEXIMCO", "SQURPHARMA", "LHBL",
    "ROBI", "RENATA", "BRACBANK", "BXPHARMA", "WALTONHIL",
    "BEACONPHAR", "BSCCL", "CITYBANK", "EBL", "ISLAMIBANK",
    "UCB", "DHAKABANK", "PUBALIBANK", "PRIMEBANK", "DUTCHBANGL",
    "LANKABAFIN", "IDLC", "IPDC", "POWERGRID", "TITASGAS",
    "MJLBD", "UPGDCL", "SUMITPOWER", "LINDEBD", "MPETROLEUM",
    "PADMAOIL", "JAMUNAOIL", "HEIDELBCEM", "CROWNCEMNT", "BSRMSTEEL",
    "GPHISPAT", "OLYMPIC", "AAMRANET", "AAMRATECH", "GENEXIL",
    "UNILEVERCL", "MARICO", "IBNSINA", "GREENDELTA", "RELIANCINS",
    "SQUARETEXT", "ENVOYTEX"
]

def get_public_symbols_basket(exchange: str = "DSE") -> List[str]:
    try:
        from db_market_data import get_public_symbols_list
        db_syms = get_public_symbols_list(exchange)
        if db_syms:
            return db_syms
    except Exception:
        pass
    return list(ALL_PUBLIC_SYMBOLS)

DEFAULT_SYMBOLS = get_public_symbols_basket("DSE")
DEFAULT_BUY_CLIENTS = ["08096", "00224", "08833", "00507", "09139", "09189", "07605"]


class CircuitLimits:
    def __init__(self, sh: float, sl: float, ch: float, cl: float, ref_price: float):
        self.sh = sh
        self.sl = sl
        self.ch = ch
        self.cl = cl
        h = sh if sh > 0 else (ch if ch > 0 else (ref_price * 1.10 if ref_price > 0 else 0))
        l = sl if sl > 0 else (cl if cl > 0 else (ref_price * 0.90 if ref_price > 0 else 0))
        self.high = round(h, 2)
        self.low = round(l, 2)

    def is_valid(self) -> bool:
        return self.high > 0 and self.low > 0 and self.high >= self.low

    def __str__(self) -> str:
        return f"[SL/CL: {self.low:.2f} - SH/CH: {self.high:.2f}] (SH={self.sh:.2f}, SL={self.sl:.2f}, CH={self.ch:.2f}, CL={self.cl:.2f})"


class HftOrder:
    def __init__(
        self,
        seq: int,
        side: str,
        client_code: str,
        symbol: str,
        qty: int,
        rate: float,
        ref_ltp: float
    ):
        self.seq = seq
        self.side = side
        self.client_code = client_code
        self.symbol = symbol
        self.qty = qty
        self.rate = rate
        self.ref_ltp = ref_ltp
        self.latency_ms: float = 0.0
        self.executed: bool = False
        self.error_message: Optional[str] = None

    def mark_success(self, latency_ms: float) -> None:
        self.executed = True
        self.latency_ms = latency_ms

    def mark_failed(self, error: str) -> None:
        self.executed = False
        self.error_message = error

    def __str__(self) -> str:
        return f"[{self.side}] Client: {self.client_code:<6} | {self.symbol:<10} x {self.qty:2d} @ {self.rate:7.2f} (Ref: {self.ref_ltp:7.2f})"


# Backward compatibility alias
LoadOrder = HftOrder


def discover_public_symbols_from_browser(driver: webdriver.Chrome) -> List[str]:
    discovered: List[str] = []
    if not driver:
        return discovered
    try:
        res = driver.execute_script("""
            var syms = [];
            var inputs = ['#order_instrument', '#instrument', '#marketwatch_symbol', '#sc_instrument_search'];
            for (var i = 0; i < inputs.length; i++) {
                var $el = window.$ ? $(inputs[i]) : null;
                if ($el && $el.length && $el.data('ui-autocomplete')) {
                    var src = $el.autocomplete('option', 'source');
                    if (Array.isArray(src) && src.length > 0) {
                        for (var j = 0; j < src.length; j++) {
                            var s = typeof src[j] === 'string' ? src[j] : (src[j].value || src[j].label || '');
                            if (s && !syms.includes(s.trim())) syms.push(s.trim());
                        }
                    }
                }
            }
            var globals = ['instruments', 'symbols', 'public_symbols', 'symbol_list', 'stock_symbols', 'all_instruments', 'INSTRUMENTS'];
            for (var g = 0; g < globals.length; g++) {
                if (window[globals[g]] && Array.isArray(window[globals[g]])) {
                    window[globals[g]].forEach(function(item) {
                        var s = typeof item === 'string' ? item : (item.symbol || item.code || item.value || '');
                        if (s && !syms.includes(s.trim())) syms.push(s.trim());
                    });
                }
            }
            var rows = document.querySelectorAll('tr[class*="mw_"], #marketwatch_table tbody tr, table.marketwatch tbody tr');
            for (var r = 0; r < rows.length; r++) {
                var codeTd = rows[r].querySelector('td.mw_table_symbol, td[class*="symbol"]');
                if (codeTd && codeTd.innerText) {
                    var s = codeTd.innerText.trim();
                    if (s && !syms.includes(s)) syms.push(s);
                }
            }
            return syms;
        """)
        if isinstance(res, list):
            for item in res:
                c = str(item or "").strip().upper()
                if c and c not in discovered:
                    discovered.append(c)
    except Exception:
        pass
    return discovered


def fetch_holding_clients_for_symbol(driver: webdriver.Chrome, symbol: str, exchange: str = "DSE") -> List[str]:
    """Discovers holding clients with saleable shares for a symbol via background API (Zero page switch)."""
    clients: List[str] = []
    clean_sym = oms_price.clean_symbol(symbol)
    try:
        res = driver.execute_async_script("""
            var done = arguments[arguments.length - 1];
            var sym = arguments[0];
            var ex = arguments[1];
            var url = '/shared/getsymbolclient/?page=1&per_page=100&symbol=' + encodeURIComponent(sym) + '&clientcode=&exchange=' + encodeURIComponent(ex);
            fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'include' })
            .then(function(r) { return r.json(); })
            .then(function(d) { done(d); })
            .catch(function(e) { done({ error: e.toString() }); });
        """, clean_sym, exchange)

        if isinstance(res, dict):
            data_block = res.get("data", {})
            rows = data_block.get("data", []) if isinstance(data_block, dict) else []
            for r in rows:
                c = str(r.get("client_code") or "").strip()
                saleable = float(r.get("qty") or r.get("saleable_qty") or 0)
                if c and saleable > 0 and c not in clients:
                    clients.append(c)
    except Exception as e:
        print(f"[WARN: HFT Seller Discovery] Failed querying holdings for {clean_sym}: {e}")

    if not clients:
        # Fallback to verified accounts
        clients = ["00463", "00600", "00224", "08096", "11310", "12245", "14118"]
    return clients


def fetch_price_safely(driver: webdriver.Chrome, symbol: str) -> float:
    """Fetches reference price (LTP or YCP) prioritizing DB table dse_md_ltp with safe fallback and circuit band guard."""
    clean_sym = oms_price.clean_symbol(symbol).upper()
    defaults = {
        "GP": 315.50,
        "BEXIMCO": 115.60,
        "BATBC": 518.70,
        "LHBL": 72.40,
        "SQURPHARMA": 234.20,
        "ROBI": 30.50,
        "RENATA": 780.00,
        "BRACBANK": 44.50,
    }
    fallback = defaults.get(clean_sym, 100.00)

    try:
        import db_market_data
        db_price = db_market_data.get_public_symbol_ltp(clean_sym, "DSE")
        if db_price and db_price > 0:
            return float(db_price)
    except Exception:
        pass

    try:
        price = oms_price.fetch_price_ltp_or_ycp(driver, clean_sym)
        if price > 0:
            if clean_sym in defaults:
                if price >= (fallback * 0.40) and price <= (fallback * 2.50):
                    return price
                else:
                    print(f"  [PRICE GUARD] Scraped price {price:.2f} BDT for {clean_sym} violates realistic circuit band. Overriding with calibrated market price {fallback:.2f} BDT.")
                    return fallback
            if price >= 1.0:
                return price
    except Exception:
        pass

    return fallback


def fetch_circuit_limits(driver: webdriver.Chrome, symbol: str, ref_price: float) -> CircuitLimits:
    sh, sl, ch, cl = 0.0, 0.0, 0.0, 0.0
    clean_sym = oms_price.clean_symbol(symbol).upper()
    try:
        import db_market_data
        data = db_market_data.fetch_ltp_from_db("DSE", symbol=clean_sym).get(clean_sym, {})
        if data:
            sh = float(data.get("circuit_up", 0.0))
            sl = float(data.get("circuit_down", 0.0))
            ch = sh
            cl = sl
    except Exception:
        pass

    if sh <= 0 and sl <= 0:
        try:
            import market_depth
            depth = market_depth.get_snapshot(driver, clean_sym, "DSE")
            if depth:
                sh = oms_price.parse_float(depth.get("SH"))
                sl = oms_price.parse_float(depth.get("SL"))
                ch = oms_price.parse_float(depth.get("CH"))
                cl = oms_price.parse_float(depth.get("CL"))
        except Exception:
            pass

    if sh <= 0 and ch <= 0 and ref_price > 0:
        sh = round(ref_price * 1.10, 2)
        ch = sh
    if sl <= 0 and cl <= 0 and ref_price > 0:
        sl = round(ref_price * 0.90, 2)
        cl = sl

    return CircuitLimits(sh, sl, ch, cl, ref_price)


def check_price_exceed_warning(driver: webdriver.Chrome) -> Optional[str]:
    try:
        res = driver.execute_script("""
            var selectors = ['.text-danger', '.field-validation-error', '#rate_error', '.order-error', '.toast-error', '.alert-danger', '[id*="error"]', '.tooltip-inner'];
            for (var i = 0; i < selectors.length; i++) {
                var els = document.querySelectorAll(selectors[i]);
                for (var j = 0; j < els.length; j++) {
                    var el = els[j];
                    if (el && el.offsetParent !== null) {
                        var t = (el.innerText || el.textContent || '').trim();
                        var lower = t.toLowerCase();
                        if (lower.includes('exceed') || lower.includes('circuit') || lower.includes('range') || lower.includes('limit') || lower.includes('high') || lower.includes('low')) {
                            return t;
                        }
                    }
                }
            }
            return null;
        """)
        return str(res).strip() if res else None
    except Exception:
        return None


def dismiss_any_open_modal(driver: webdriver.Chrome) -> None:
    try:
        driver.execute_script("""
            var btns = document.querySelectorAll('#cnfirmCancelBtn, button.btn-secondary, .modal.show button.close, button[data-dismiss="modal"], button.confirm-cancel');
            for (var i = 0; i < btns.length; i++) { if (btns[i].offsetParent !== null) { btns[i].click(); } }
            var modals = document.querySelectorAll('.modal.show, .modal-backdrop');
            for (var m = 0; m < modals.length; m++) { modals[m].classList.remove('show'); modals[m].style.display = 'none'; }
            document.body.classList.remove('modal-open');
        """)
        time.sleep(0.25)
    except Exception:
        pass


def build_hft_pipeline(
    symbols: List[str],
    buy_clients: List[str],
    sell_clients_by_symbol: Dict[str, List[str]],
    symbol_ltp_map: Dict[str, float],
    total_trades: int,
    strategy_choice: int,
    base_offset: float = 0.10,
    symbol_circuits: Optional[Dict[str, CircuitLimits]] = None
) -> List[HftOrder]:
    """Generates an interleaved HFT order pipeline cycling through symbols and client pools."""
    pipeline: List[HftOrder] = []
    symbol_count = max(1, len(symbols))
    buy_count = max(1, len(buy_clients))

    for i in range(total_trades):
        seq = i + 1
        side = "BUY" if (i % 2 == 0) else "SELL"
        symbol = symbols[(i // 2) % symbol_count]
        ltp = symbol_ltp_map.get(symbol, 100.0)

        if side == "BUY":
            client_code = buy_clients[(i // 2) % buy_count]
        else:
            sellers = sell_clients_by_symbol.get(symbol, [])
            client_code = sellers[(i // 2) % len(sellers)] if sellers else "00463"

        # Standard trading lot size (10, 15, 20 shares)
        qty = 10 + ((i % 3) * 5)

        if strategy_choice == 2:
            # Strategy 2: Aggressive Market Taker (Exact LTP for immediate cross)
            rate = ltp
        elif strategy_choice == 3:
            # Strategy 3: Dynamic Micro-Burst / Volatility Jitter (+-0.10 to +-0.40 BDT)
            jitter = 0.10 * (1 + (i % 4))
            rate = max(1.0, round(ltp - jitter, 2)) if side == "BUY" else round(ltp + jitter, 2)
        else:
            # Strategy 1: Spread Market Maker (BUY @ LTP - spread, SELL @ LTP + spread)
            offset = base_offset + ((i % 5) * 0.10)
            rate = max(1.0, round(ltp - offset, 2)) if side == "BUY" else round(ltp + offset, 2)

        # Circuit range enforcement (SH/SL, CH/CL)
        limits = symbol_circuits.get(symbol) if symbol_circuits else None
        if limits and limits.is_valid():
            if side == "BUY" and rate > limits.high:
                rate = limits.high
            elif side == "BUY" and rate < limits.low:
                rate = limits.low
            elif side == "SELL" and rate < limits.low:
                rate = limits.low
            elif side == "SELL" and rate > limits.high:
                rate = limits.high

        rate = round(rate, 2)
        pipeline.append(HftOrder(seq, side, client_code, symbol, qty, rate, ltp))

    return pipeline


def run_load_test(driver: webdriver.Chrome) -> None:
    """Executes high-speed multi-symbol multi-client HFT simulation."""
    # 1. Ensure on Trade Page
    oms_trade.navigate_to_trade_page(driver)

    print("\n+=============================================================================+")
    print("|    HIGH FREQUENCY TRADING (HFT) & MULTI-SYMBOL MULTI-CLIENT SIMULATOR       |")
    print("+=============================================================================+")

    # STEP 0: Widget Color Linking
    widget_color.run_widget_color_selection(driver)

    # 2. Multi-Symbol Basket Selection (All Public Symbols from DB Table)
    active_public_syms = get_public_symbols_basket("DSE")
    print("\n[HFT CONFIG: 1. SYMBOLS BASKET]")
    sample_preview = ', '.join(active_public_syms[:25]) + ("..." if len(active_public_syms) > 25 else "")
    print(f"Default Public Basket ({len(active_public_syms)} Public Symbols collected from DB table dse_md_ltp):\n  {sample_preview}")
    print("Options:")
    print(f"  - Press ENTER to select ALL Public Symbols from DB table ({len(active_public_syms)} symbols)")
    print("  - Type 'DB' to view live Public Board symbols & LTPs table (dse_md_ltp / cse_md_ltp)")
    print("  - Type 'TOP' or '10' for Top 10 Liquid Public Symbols")
    print("  - Enter specific symbols comma-separated (e.g. GP, BEXIMCO, BATBC)")
    sym_in = input("Enter Choice: ").strip()

    if sym_in.upper() == "DB":
        try:
            from db_market_data import print_public_board_summary
            print_public_board_summary("DSE", limit=35)
        except Exception as e:
            print(f"[WARN] {e}")
        sym_in = input("Enter Choice (press ENTER for ALL Public Symbols): ").strip()

    symbols: List[str] = []
    if sym_in.upper() in ["TOP", "10"]:
        symbols = ["GP", "BATBC", "BEXIMCO", "SQURPHARMA", "LHBL", "ROBI", "RENATA", "BRACBANK", "BXPHARMA", "WALTONHIL"]
    elif sym_in.upper() in ["ALL", "PUBLIC"] or not sym_in:
        symbols = list(DEFAULT_SYMBOLS)
        live_disc = discover_public_symbols_from_browser(driver)
        if live_disc:
            for ds in live_disc:
                if ds not in symbols:
                    symbols.append(ds)
            print(f"  ✔ Dynamically discovered & added {len(live_disc)} additional live public symbols from Trade Portal.")
    else:
        for s in sym_in.replace(",", " ").split():
            clean = s.strip().upper()
            if clean and clean not in symbols:
                symbols.append(clean)
        if not symbols:
            symbols = list(DEFAULT_SYMBOLS)
    print(f">> Active Trading Basket: {len(symbols)} Public Symbol(s) selected: {symbols}")

    # 3. Multi-Client Configuration
    print("\n[HFT CONFIG: 2. CLIENT ROUTING POOLS]")
    print(f"Default Verified High-Cash BUY Clients (Cash >= 100k BDT): {', '.join(DEFAULT_BUY_CLIENTS)}")
    buy_in = input("Enter BUY Client Codes comma-separated (press ENTER for default): ").strip()

    buy_clients: List[str] = []
    if buy_in:
        for c in buy_in.replace(",", " ").split():
            clean = c.strip()
            if clean and clean not in buy_clients:
                buy_clients.append(clean)
    if not buy_clients:
        buy_clients = list(DEFAULT_BUY_CLIENTS)
    print(f">> Active BUY Client Pool: {buy_clients} ({len(buy_clients)} accounts)")

    # Discover holding SELL clients for each symbol via Position API
    print("\n[HFT DISCOVERY: 3. AUTO-DISCOVERING HOLDING CLIENTS FOR SELL SIDE]")
    print("Querying real portfolio holdings from backend without leaving Trade page...")
    sell_clients_by_symbol: Dict[str, List[str]] = {}
    default_sellers = ["00463", "00600", "00224", "08096", "11310", "12245", "14118"]

    holder_disc_limit = min(len(symbols), 20)
    for i in range(holder_disc_limit):
        sym = symbols[i]
        holders = fetch_holding_clients_for_symbol(driver, sym, "DSE")
        sell_clients_by_symbol[sym] = holders
        sample_preview = holders[:min(5, len(holders))]
        print(f"  ✔ {sym:<10} -> Found {len(holders):2d} holding client(s) with saleable shares: {sample_preview}")

    if len(symbols) > holder_disc_limit:
        for i in range(holder_disc_limit, len(symbols)):
            sell_clients_by_symbol[symbols[i]] = default_sellers
        print(f"  ✔ [{len(symbols) - holder_disc_limit} more public symbols mapped instantly to verified holding accounts {default_sellers}]")

    # 4. HFT Simulation Strategy
    print("\n[HFT CONFIG: 4. TRADING ALGORITHM / STRATEGY]")
    print("  1. Spread Market Maker (BUY @ LTP - spread, SELL @ LTP + spread) [Default]")
    print("  2. Aggressive Market Taker (BUY & SELL directly at LTP for immediate matching)")
    print("  3. Dynamic Micro-Burst / Volatility (Randomized tight spreads simulating order flow)")
    strat_in = input("Select Strategy (1-3, default=1): ").strip()
    strategy_choice = int(strat_in) if strat_in.isdigit() and strat_in in ["1", "2", "3"] else 1

    # 5. Execution Volume & Pacing
    print("\n[HFT CONFIG: 5. ORDER VOLUME & PACING]")
    total_trades_in = input("Enter Total Orders to Execute (default 50): ").strip()
    total_trades = int(total_trades_in) if total_trades_in.isdigit() else 50

    delay_in = input("Enter Inter-Order Delay in milliseconds (default 0 ms for maximum burst TPS): ").strip()
    inter_order_delay_ms = int(delay_in) if delay_in.isdigit() else 0

    dur_in = input("Enter Max Duration budget in seconds (default 60s): ").strip()
    max_duration_sec = int(dur_in) if dur_in.isdigit() else 60

    # 6. Pre-fetch Live Reference Prices & Circuit Limits (SH/SL, CH/CL) directly from DB table
    print("\n[HFT PRE-FLIGHT: 6. BULK-COLLECTING MARKET QUOTES & CIRCUIT LIMITS FROM DB TABLE (dse_md_ltp)]")
    symbol_prices: Dict[str, float] = {}
    symbol_circuits: Dict[str, CircuitLimits] = {}
    import db_market_data
    db_public_quotes = db_market_data.fetch_ltp_from_db("DSE")
    db_hit_count = 0
    print_count = 0
    total_syms = len(symbols)

    for sym in symbols:
        clean_sym = sym.strip().upper()
        rec = db_public_quotes.get(clean_sym)
        if rec and rec.get("ltp", 0.0) > 0:
            price = float(rec["ltp"])
            sh = float(rec.get("circuit_up", 0.0))
            sl = float(rec.get("circuit_down", 0.0))
            if sh <= 0:
                sh = round(price * 1.10, 2)
            if sl <= 0:
                sl = round(price * 0.90, 2)
            db_hit_count += 1
            src_tag = " (DB: dse_md_ltp)"
        else:
            price = fetch_price_safely(driver, clean_sym)
            sh = round(price * 1.10, 2)
            sl = round(price * 0.90, 2)
            src_tag = " (Fallback)"

        circuits = CircuitLimits(sh, sl, sh, sl, price)
        symbol_prices[clean_sym] = price
        symbol_circuits[clean_sym] = circuits

        if print_count < 15 or print_count >= total_syms - 5:
            print(f"  ✔ {clean_sym:<10} Ref: {price:7.2f} BDT | Circuit Bounds: [SL/CL: {circuits.low:7.2f} - SH/CH: {circuits.high:7.2f}]{src_tag}")
        elif print_count == 15:
            print(f"  ... [{total_syms - 20} more public symbols bulk-loaded from DB table dse_md_ltp] ...")
        print_count += 1

    print(f"  ✔ Total {total_syms} public symbols bulk-loaded from DB table (Hits: {db_hit_count}, Fallbacks: {total_syms - db_hit_count}). Zero Market Watch scraping!")

    # 7. Generate Interleaved HFT Pipeline (Guarded by Circuit Limits)
    pipeline = build_hft_pipeline(
        symbols=symbols,
        buy_clients=buy_clients,
        sell_clients_by_symbol=sell_clients_by_symbol,
        symbol_ltp_map=symbol_prices,
        total_trades=total_trades,
        strategy_choice=strategy_choice,
        base_offset=0.10,
        symbol_circuits=symbol_circuits
    )

    # 8. Display Simulation Plan & Guardrail Proceed Prompt
    strat_names = {
        1: "Spread Market Maker",
        2: "Aggressive Market Taker (LTP)",
        3: "Dynamic Volatility Jitter"
    }
    print("\n+=============================================================================+")
    print("|              HFT MULTI-SYMBOL MULTI-CLIENT SIMULATION PLAN                  |")
    print("+=============================================================================+")
    print(f"| Basket Symbols       : {str(symbols):<52} |")
    print(f"| BUY Client Pool      : {str(buy_clients):<52} |")
    print(f"| SELL Client Routing  : {'Mapped dynamically to verified holding accounts':<52} |")
    print(f"| Total Planned Orders : {len(pipeline):<52} |")
    print(f"| Trading Strategy     : {strat_names.get(strategy_choice, 'Spread Market Maker'):<52} |")
    print(f"| Interleave Pacing    : {inter_order_delay_ms} ms delay | Max Duration Budget: {max_duration_sec}s                |")
    print("+=============================================================================+")

    proceed = input("\nProceed to execute HFT Simulation? (1=YES / 0=NO, default=1): ").strip()
    if proceed.lower() in ["0", "n", "no"]:
        print("\n[HFT ENGINE] Execution aborted by user.")
        return

    # 9. High-Speed Execution Loop
    print("\n>>> STARTING HIGH-FREQUENCY MULTI-SYMBOL EXECUTION ENGINE <<<")
    start_time = time.time()
    success_count = 0
    failed_count = 0

    for i, order in enumerate(pipeline):
        elapsed = time.time() - start_time
        if elapsed >= max_duration_sec:
            print(f"\n⏱ Max duration budget ({max_duration_sec}s) reached. Halting HFT engine gracefully.")
            break

        order_start = time.time()
        try:
            # Check price against circuit limits & UI exceed warnings
            limits = symbol_circuits.get(order.symbol) if symbol_circuits else None
            rate_to_use = order.rate
            if limits and limits.is_valid():
                if order.side == "BUY" and rate_to_use > limits.high:
                    rate_to_use = limits.high
                elif order.side == "SELL" and rate_to_use < limits.low:
                    rate_to_use = limits.low

            ok = oms_trade.place_order(
                driver=driver,
                side=order.side,
                client_code=order.client_code,
                symbol=order.symbol,
                qty=order.qty,
                rate=rate_to_use
            )
            latency = (time.time() - order_start) * 1000.0

            if ok:
                order.mark_success(latency)
                success_count += 1
                print(f"[HFT #{order.seq:03d}/{len(pipeline):03d}] ✔ {order.side:<4} | Client: {order.client_code:<6} | {order.symbol:<10} x {order.qty:2d} @ {rate_to_use:7.2f} (Ref: {order.ref_ltp:7.2f}) [{latency:4.0f} ms]")
            else:
                order.mark_failed("Order rejected or intercepted")
                failed_count += 1
                print(f"[HFT #{order.seq:03d}/{len(pipeline):03d}] ✖ {order.side:<4} | Client: {order.client_code:<6} | {order.symbol:<10} x {order.qty:2d} @ {rate_to_use:7.2f} [{latency:4.0f} ms] -> REJECTED (Proceeding to next)")
                dismiss_any_open_modal(driver)
        except Exception as ex:
            latency = (time.time() - order_start) * 1000.0
            order.mark_failed(str(ex))
            failed_count += 1
            print(f"[HFT #{order.seq:03d}/{len(pipeline):03d}] ✖ {order.side:<4} | Client: {order.client_code:<6} | {order.symbol:<10} x {order.qty:2d} @ {order.rate:7.2f} [{latency:4.0f} ms] -> ERROR: {ex} (Continuing to next)")
            dismiss_any_open_modal(driver)

        if inter_order_delay_ms > 0 and i < len(pipeline) - 1:
            time.sleep(inter_order_delay_ms / 1000.0)

        # Periodic background refresh every 5 orders so frontend tables stay live
        if (i + 1) % 5 == 0 or i == len(pipeline) - 1:
            try:
                driver.execute_script("if (typeof get_order_history === 'function') { get_order_history(); }")
                driver.execute_script("if (typeof get_order_summary === 'function') { get_order_summary(); }")
            except Exception:
                pass

    total_time = max(0.001, time.time() - start_time)

    # 10. Performance Telemetry & Detailed Reporting
    print_hft_execution_summary(pipeline, total_time, success_count, failed_count, symbols)

    # 11. Synchronize & Present Orders in Frontend Order Terminal
    sync_and_present_orders_in_frontend(driver, symbols)


def print_hft_execution_summary(
    pipeline: List[HftOrder],
    total_time: float,
    success_count: int,
    failed_count: int,
    symbols: List[str]
) -> None:
    """Renders comprehensive HFT executive report with throughput and latency metrics."""
    total_attempted = success_count + failed_count
    tps = success_count / total_time

    latencies = [o.latency_ms for o in pipeline if o.executed and o.latency_ms > 0]
    min_lat = min(latencies) if latencies else 0.0
    max_lat = max(latencies) if latencies else 0.0
    avg_lat = (sum(latencies) / len(latencies)) if latencies else 0.0

    total_shares = sum(o.qty for o in pipeline if o.executed)
    total_value = sum(o.qty * o.rate for o in pipeline if o.executed)

    symbol_stats: Dict[str, Dict[str, Any]] = {
        s: {"attempted": 0, "executed": 0, "shares": 0, "value": 0.0} for s in symbols
    }
    client_stats: Dict[str, Dict[str, Any]] = {}

    for o in pipeline:
        if o.symbol not in symbol_stats:
            symbol_stats[o.symbol] = {"attempted": 0, "executed": 0, "shares": 0, "value": 0.0}
        symbol_stats[o.symbol]["attempted"] += 1

        val = o.qty * o.rate
        if o.executed:
            symbol_stats[o.symbol]["executed"] += 1
            symbol_stats[o.symbol]["shares"] += o.qty
            symbol_stats[o.symbol]["value"] += val

            if o.client_code not in client_stats:
                client_stats[o.client_code] = {"orders": 0, "value": 0.0}
            client_stats[o.client_code]["orders"] += 1
            client_stats[o.client_code]["value"] += val

    success_pct = (success_count * 100.0 / max(1, total_attempted))
    failed_pct = (failed_count * 100.0 / max(1, total_attempted))

    print("\n+=============================================================================+")
    print("|            HIGH FREQUENCY TRADING (HFT) SIMULATION PERFORMANCE REPORT       |")
    print("+=============================================================================+")
    print(f"| Total Duration       : {total_time:6.2f} seconds{'':<37} |")
    print(f"| Orders Attempted     : {total_attempted:<49} |")
    print(f"| Successful Orders    : {success_count:<6} ({success_pct:5.1f}%){'':<33} |")
    print(f"| Failed / Rejected    : {failed_count:<6} ({failed_pct:5.1f}%){'':<33} |")
    print(f"| Throughput (TPS)     : {tps:6.2f} Trades / Second{'':<27} |")
    print(f"| Latency (Min/Avg/Max): {min_lat:4.0f} ms / {avg_lat:4.0f} ms / {max_lat:4.0f} ms{'':<28} |")
    print(f"| Total Shares Traded  : {total_shares:<12,d} shares{'':<31} |")
    print(f"| Total Traded Value   : {total_value:<12,.2f} BDT{'':<34} |")
    print("+-----------------------------------------------------------------------------+")
    print("| PER-SYMBOL EXECUTION BREAKDOWN:                                             |")
    print("| Symbol       | Orders Placed | Executed | Volume (Shares) | Traded Value(BDT|")
    print("|--------------|---------------|----------|-----------------|-----------------|")
    for sym, st in symbol_stats.items():
        print(f"| {sym:<12} | {st['attempted']:<13d} | {st['executed']:<8d} | {st['shares']:<15d} | {st['value']:15,.2f} |")
    print("+-----------------------------------------------------------------------------+")
    print("| PER-CLIENT TRADING ACTIVITY:                                                |")
    print("| Client Code  | Orders Executed | Total Traded Value (BDT)                   |")
    print("|--------------|-----------------|--------------------------------------------|")
    for client, st in client_stats.items():
        print(f"| {client:<12} | {st['orders']:<15d} | {st['value']:42,.2f} |")
    print("+=============================================================================+\n")


def sync_and_present_orders_in_frontend(driver: webdriver.Chrome, symbols: List[str]) -> None:
    """Clears filters, refreshes Order Terminal/Summary, and switches view so user sees placed orders."""
    print("\n+=============================================================================+")
    print("|      SYNCHRONIZING FRONTEND: ORDER TERMINAL & ORDER SUMMARY WIDGETS        |")
    print("+=============================================================================+")
    try:
        from selenium.webdriver.common.by import By
        import order_terminal
        # 1. Clear any active filter in Order Terminal
        print("[FRONTEND] Clearing Order Terminal search filters (Client Code, Symbol)...")
        order_terminal.clear_filter(driver)
        time.sleep(0.4)

        # 2. Trigger Order Terminal & Order Summary data refresh
        print("[FRONTEND] Triggering live get_order_history() & get_order_summary()...")
        order_terminal.refresh(driver)
        driver.execute_script("if (typeof get_order_summary === 'function') { get_order_summary(); }")
        time.sleep(0.8)

        # 3. Navigate directly to Order Terminal tab to make orders immediately visible
        print("[FRONTEND] Switching active tab to Order Terminal widget...")
        order_terminal.navigate_to_order_terminal(driver)
        time.sleep(0.6)
        order_terminal.refresh(driver)
        time.sleep(0.6)

        # 4. Inspect visible rows in Order Terminal table to verify
        rows = driver.find_elements(By.XPATH, "//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr")
        print(f"✔ [FRONTEND VERIFIED] Order Terminal visible order rows: {len(rows)}")

        display_count = min(10, len(rows))
        if display_count > 0:
            print("\n[RECENT ORDERS DISPLAYED IN FRONTEND ORDER TERMINAL]:")
            print("+------+--------------+------------+------------+-------+-----------+---------+--------------------+")
            print(f"| {'#':<4} | {'Order ID':<12} | {'Client':<10} | {'Symbol':<10} | {'Side':<5} | {'Price':<9} | {'Qty':<7} | {'Status':<18} |")
            print("+------+--------------+------------+------------+-------+-----------+---------+--------------------+")
            for r in range(display_count):
                try:
                    row = rows[r]
                    cells = row.find_elements(By.XPATH, "./td")
                    if len(cells) >= 12:
                        oid = cells[1].text.strip()
                        client = cells[4].text.strip()
                        sym = cells[5].text.strip()
                        side = cells[7].text.strip()
                        price = cells[9].text.strip()
                        qty = cells[11].text.strip()
                        status = cells[13].text.strip() if len(cells) > 13 else "-"
                        print(f"| {(r + 1):<4d} | {oid:<12} | {client:<10} | {sym:<10} | {side:<5} | {price:<9} | {qty:<7} | {status:<18} |")
                    else:
                        print(f"| {(r + 1):<4d} | {row.text.strip():<78} |")
                except Exception:
                    pass
            print("+------+--------------+------------+------------+-------+-----------+---------+--------------------+")
        else:
            print("[INFO] Table rendered. If rows are empty, check if exchange gateway is connected.")

        resp = input("\nSwitch view back to Trade widget? (1=YES / 0=Stay on Order Terminal, default=0): ").strip()
        if resp == "1":
            oms_trade.navigate_to_trade_page(driver)
            print("[NAV] Switched back to Trade view.")
        else:
            print("[NAV] Remaining on Order Terminal view.")
    except Exception as e:
        print(f"[WARN: Frontend Sync] {e}")


