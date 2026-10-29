"""
db_market_data.py
-----------------
Collects Market Data directly from PostgreSQL database tables (marketdb)
for the PUBLIC Board (board = 'PUBLIC'):

  1. LTP & Statistics   -> dse_md_ltp / cse_md_ltp
  2. Bid/Ask Price(BBO) -> dse_md_bbo / cse_md_bbo
  3. Market Depth       -> dse_md_mktdepth / cse_md_mktdepth

STRICT FILTERING:
  - ONLY Public Board: board = 'PUBLIC'
  - STRICTLY EXCLUDES all other boards: DEBT, BUYIN, YIELDDBT, BUYDBT, ALTDBT,
    ATBDEBT, SDEBT, SBUYIN, ATBBUYIN, BLOCK, SBLOCK, ATBPUB, ATBX, SCPX, SPUBLIC,
    BULK, ODDLOT, FOREIGN, INDEX, etc.
  - STRICTLY EXCLUDES non-equity/derivative instruments: Bonds, Sukuk, Treasury
    Bills/Bonds (TB...), Debentures, Options (-CE, -PE), Futures, Multi-leg (-SPD, -XX).

Configured for UFTFAST OMS / UFTCL UAT environment:
  MARKETDATA_DB_URI = 'postgresql+psycopg2://marketdata:pass4321@10.20.8.29:5445/marketdb'
"""

import os
import sys
import json
import subprocess
from typing import Dict, List, Optional, Any

# Ensure stdout handles UTF-8 cleanly on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# Database Connection Settings
MARKETDATA_DB_URI = os.environ.get(
    "MARKETDATA_DB_URI",
    "postgresql+psycopg2://marketdata:pass4321@10.20.8.29:5445/marketdb"
)
DB_CONFIG = {
    "host": os.environ.get("MKT_DB_HOST", "10.20.8.29"),
    "port": int(os.environ.get("MKT_DB_PORT", "5445")),
    "dbname": os.environ.get("MKT_DB_NAME", "marketdb"),
    "user": os.environ.get("MKT_DB_USER", "marketdata"),
    "password": os.environ.get("MKT_DB_PASS", "pass4321"),
    "timeout": 5,
}

# Cache file location for fast offline/cross-process access
CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "public_board_ltp.json")

# In-memory session caches for sub-millisecond retrieval
_DSE_LTP_CACHE: Dict[str, Dict[str, Any]] = {}
_CSE_LTP_CACHE: Dict[str, Dict[str, Any]] = {}

_DSE_BBO_CACHE: Dict[str, Dict[str, Any]] = {}
_CSE_BBO_CACHE: Dict[str, Dict[str, Any]] = {}


def get_db_connection():
    """
    Creates and returns a PostgreSQL database connection using MARKETDATA_DB_URI or DB_CONFIG.
    Strips '+psycopg2' scheme prefix if present so psycopg2 connects directly.
    """
    import psycopg2
    if MARKETDATA_DB_URI:
        uri = MARKETDATA_DB_URI
        if uri.startswith("postgresql+psycopg2://"):
            uri = "postgresql://" + uri[len("postgresql+psycopg2://"):]
        try:
            return psycopg2.connect(uri, connect_timeout=DB_CONFIG.get("timeout", 5))
        except Exception:
            pass

    return psycopg2.connect(
        host=DB_CONFIG["host"],
        port=DB_CONFIG["port"],
        dbname=DB_CONFIG["dbname"],
        user=DB_CONFIG["user"],
        password=DB_CONFIG["password"],
        connect_timeout=DB_CONFIG.get("timeout", 5),
    )


def is_strictly_public_equity(symbol: str, company_name: str = "", sector: str = "", symbol_instr: str = "") -> bool:
    """
    Validates that a security is strictly a Public Board equity or mutual fund,
    filtering out any debt, bond, treasury, option contract, or futures.
    """
    if not symbol:
        return False
    s_upper = symbol.strip().upper()

    # Reject derivatives / options / futures suffixes
    if s_upper.endswith("-CE") or s_upper.endswith("-PE") or s_upper.endswith("-XX") or s_upper.endswith("-SPD"):
        return False

    # Reject Treasury bonds / bills and Sukuks
    if s_upper.startswith("TB") and len(s_upper) >= 6 and any(c.isdigit() for c in s_upper):
        return False
    if "BOND" in s_upper or "SUKUK" in s_upper:
        return False

    # Reject debt/bond sectors
    sec_upper = (sector or "").upper()
    if "BOND" in sec_upper or "DEBT" in sec_upper or "DEBENTURE" in sec_upper:
        return False

    # Reject non-equity instrument types
    instr_upper = (symbol_instr or "").upper()
    if instr_upper in ["OPT", "FUT", "MLEG", "TBILL", "TBOND", "CORP", "GOVDBT", "CORPDBT", "TB", "CB", "DB"]:
        return False

    return True


# =========================================================================
# 1. LTP & STATISTICS: dse_md_ltp / cse_md_ltp
# =========================================================================
def fetch_ltp_from_db(exchange: str = "DSE", symbol: Optional[str] = None) -> Dict[str, Dict[str, Any]]:
    """
    Queries dse_md_ltp / cse_md_ltp directly for Public Board LTP & statistics.
    Table: dse_md_ltp / cse_md_ltp WHERE board = 'PUBLIC'
    """
    results: Dict[str, Dict[str, Any]] = {}
    ex_clean = (exchange or "DSE").strip().upper()
    target_cache = _CSE_LTP_CACHE if ex_clean == "CSE" else _DSE_LTP_CACHE

    if target_cache:
        if symbol:
            s_clean = symbol.strip().upper()
            if s_clean in target_cache:
                return {s_clean: target_cache[s_clean]}
        else:
            return dict(target_cache)

    table_ltp = "cse_md_ltp" if ex_clean == "CSE" else "dse_md_ltp"
    table_sym = "cse_md_symbols" if ex_clean == "CSE" else "dse_md_symbols"

    sym_filter = f"AND l.symbol = '{symbol.strip().upper()}'" if symbol else ""

    query = f"""
        SELECT
            l.symbol,
            COALESCE(s.company_name, l.symbol) as company_name,
            l.ltp,
            l.ycp,
            l.circuit_up,
            l.circuit_down,
            l.floor_price,
            l.open,
            l.high,
            l.low,
            l.close,
            l.total_qty,
            l.last_update,
            COALESCE(s.sector, '') as sector,
            COALESCE(s.symbol_instr, '') as symbol_instr
        FROM {table_ltp} l
        LEFT JOIN {table_sym} s ON l.symbol = s.symbol AND l.board = s.board
        WHERE l.board = 'PUBLIC'
          AND (s.symbol_instr IS NULL OR s.symbol_instr IN ('EQ', 'MF', 'CS'))
          AND COALESCE(s.sector, '') NOT ILIKE '%Bond%'
          AND COALESCE(s.sector, '') NOT ILIKE '%Debt%'
          AND COALESCE(s.sector, '') NOT ILIKE '%Debenture%'
          AND l.symbol NOT LIKE '%-CE'
          AND l.symbol NOT LIKE '%-PE'
          AND l.symbol NOT LIKE '%-XX'
          AND l.symbol NOT LIKE '%-SPD'
          AND l.symbol NOT LIKE '%BOND%'
          AND l.symbol NOT LIKE 'TB%'
          AND l.symbol NOT LIKE '%SUKUK%'
          AND l.ltp IS NOT NULL
          AND l.ltp > 0
          {sym_filter}
        ORDER BY l.symbol ASC;
    """

    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute(query)
        for r in cur.fetchall():
            sym = str(r[0] or "").strip().upper()
            cname = str(r[1] or sym).strip()
            sec = str(r[13] or "").strip()
            sinstr = str(r[14] or "").strip()
            if not is_strictly_public_equity(sym, cname, sec, sinstr):
                continue

            results[sym] = {
                "symbol": sym,
                "company_name": cname,
                "board": "PUBLIC",
                "ltp": float(r[2]) if r[2] is not None else 0.0,
                "ycp": float(r[3]) if r[3] is not None else 0.0,
                "circuit_up": float(r[4]) if r[4] is not None else 0.0,
                "circuit_down": float(r[5]) if r[5] is not None else 0.0,
                "floor_price": float(r[6]) if r[6] is not None else 0.0,
                "open": float(r[7]) if r[7] is not None else 0.0,
                "high": float(r[8]) if r[8] is not None else 0.0,
                "low": float(r[9]) if r[9] is not None else 0.0,
                "close": float(r[10]) if r[10] is not None else 0.0,
                "total_qty": int(r[11]) if r[11] is not None else 0,
                "last_update": str(r[12] or "").strip(),
            }
        conn.close()
        if results and not symbol:
            target_cache.clear()
            target_cache.update(results)
            save_public_board_cache(results, CACHE_FILE)
        return results
    except Exception as e:
        print(f"[WARN: DB LTP] {e}")

    # Fallback to cache if offline
    cached = load_public_board_cache(CACHE_FILE)
    if cached:
        return {
            k: v for k, v in cached.items()
            if (not symbol or k == symbol.strip().upper()) and is_strictly_public_equity(k, v.get("company_name", ""), v.get("sector", ""), v.get("symbol_instr", ""))
        }
    return results


def collect_ltp(exchange: str = "DSE") -> Dict[str, Dict[str, Any]]:
    """
    Explicitly triggers real-time LTP collection from PostgreSQL database (marketdb)
    at MARKETDATA_DB_URI for all Public Board equity symbols, saves to JSON cache,
    and returns the updated dictionary of symbols.
    """
    ex_clean = (exchange or "DSE").strip().upper()
    if ex_clean == "CSE":
        _CSE_LTP_CACHE.clear()
    else:
        _DSE_LTP_CACHE.clear()
    ltp_map = fetch_ltp_from_db(exchange=ex_clean)
    print(f"[SUCCESS: DB LTP] Collected {len(ltp_map)} Public Board symbols from {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['dbname']} ({ex_clean})")
    return ltp_map


# =========================================================================
# 2. BID/ASK PRICE (BBO): dse_md_bbo / cse_md_bbo
# =========================================================================
def fetch_bbo_from_db(exchange: str = "DSE", symbol: Optional[str] = None) -> Dict[str, Dict[str, Any]]:
    """
    Queries dse_md_bbo / cse_md_bbo directly for Public Board Best Bid & Ask prices.
    Table: dse_md_bbo / cse_md_bbo WHERE board = 'PUBLIC'
    """
    results: Dict[str, Dict[str, Any]] = {}
    ex_clean = (exchange or "DSE").strip().upper()
    table_bbo = "cse_md_bbo" if ex_clean == "CSE" else "dse_md_bbo"
    table_sym = "cse_md_symbols" if ex_clean == "CSE" else "dse_md_symbols"

    sym_filter = f"AND b.symbol = '{symbol.strip().upper()}'" if symbol else ""

    query = f"""
        SELECT
            b.symbol,
            COALESCE(b.bid_price, 0.0) as bid_price,
            COALESCE(b.bid_qty, 0) as bid_qty,
            COALESCE(b.ask_price, 0.0) as ask_price,
            COALESCE(b.ask_qty, 0) as ask_qty,
            COALESCE(b.price_decimals, 2) as price_decimals,
            b.last_update,
            COALESCE(s.company_name, b.symbol) as company_name,
            COALESCE(s.sector, '') as sector,
            COALESCE(s.symbol_instr, '') as symbol_instr
        FROM {table_bbo} b
        LEFT JOIN {table_sym} s ON b.symbol = s.symbol AND b.board = s.board
        WHERE b.board = 'PUBLIC'
          AND (s.symbol_instr IS NULL OR s.symbol_instr IN ('EQ', 'MF', 'CS'))
          AND COALESCE(s.sector, '') NOT ILIKE '%Bond%'
          AND COALESCE(s.sector, '') NOT ILIKE '%Debt%'
          AND COALESCE(s.sector, '') NOT ILIKE '%Debenture%'
          AND b.symbol NOT LIKE '%-CE'
          AND b.symbol NOT LIKE '%-PE'
          AND b.symbol NOT LIKE '%-XX'
          AND b.symbol NOT LIKE '%-SPD'
          AND b.symbol NOT LIKE '%BOND%'
          AND b.symbol NOT LIKE 'TB%'
          AND b.symbol NOT LIKE '%SUKUK%'
          {sym_filter}
        ORDER BY b.symbol ASC;
    """

    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute(query)
        for r in cur.fetchall():
            sym = str(r[0] or "").strip().upper()
            cname = str(r[7] or sym).strip()
            sec = str(r[8] or "").strip()
            sinstr = str(r[9] or "").strip()
            if not is_strictly_public_equity(sym, cname, sec, sinstr):
                continue

            results[sym] = {
                "symbol": sym,
                "board": "PUBLIC",
                "bid_price": float(r[1]) if r[1] is not None else 0.0,
                "bid_qty": int(r[2]) if r[2] is not None else 0,
                "ask_price": float(r[3]) if r[3] is not None else 0.0,
                "ask_qty": int(r[4]) if r[4] is not None else 0,
                "price_decimals": int(r[5]) if r[5] is not None else 2,
                "last_update": str(r[6] or "").strip(),
            }
        conn.close()
        return results
    except Exception as e:
        print(f"[WARN: DB BBO] {e}")
        return results


# =========================================================================
# 3. MARKET DEPTH: dse_md_mktdepth / cse_md_mktdepth
# =========================================================================
def fetch_mktdepth_from_db(exchange: str = "DSE", symbol: Optional[str] = None) -> Dict[str, Dict[str, Any]]:
    """
    Queries dse_md_mktdepth / cse_md_mktdepth directly for Public Board Order Book Queues.
    Table: dse_md_mktdepth / cse_md_mktdepth WHERE board = 'PUBLIC'
    """
    results: Dict[str, Dict[str, Any]] = {}
    ex_clean = (exchange or "DSE").strip().upper()
    table_depth = "cse_md_mktdepth" if ex_clean == "CSE" else "dse_md_mktdepth"
    table_sym = "cse_md_symbols" if ex_clean == "CSE" else "dse_md_symbols"

    sym_filter = f"AND m.symbol = '{symbol.strip().upper()}'" if symbol else ""

    query = f"""
        SELECT
            m.symbol,
            m.bid_levels,
            m.ask_levels,
            COALESCE(m.price_decimals, 2) as price_decimals,
            m.last_update,
            COALESCE(s.company_name, m.symbol) as company_name,
            COALESCE(s.sector, '') as sector,
            COALESCE(s.symbol_instr, '') as symbol_instr
        FROM {table_depth} m
        LEFT JOIN {table_sym} s ON m.symbol = s.symbol AND m.board = s.board
        WHERE m.board = 'PUBLIC'
          AND (s.symbol_instr IS NULL OR s.symbol_instr IN ('EQ', 'MF', 'CS'))
          AND COALESCE(s.sector, '') NOT ILIKE '%Bond%'
          AND COALESCE(s.sector, '') NOT ILIKE '%Debt%'
          AND COALESCE(s.sector, '') NOT ILIKE '%Debenture%'
          AND m.symbol NOT LIKE '%-CE'
          AND m.symbol NOT LIKE '%-PE'
          AND m.symbol NOT LIKE '%-XX'
          AND m.symbol NOT LIKE '%-SPD'
          AND m.symbol NOT LIKE '%BOND%'
          AND m.symbol NOT LIKE 'TB%'
          AND m.symbol NOT LIKE '%SUKUK%'
          {sym_filter}
        ORDER BY m.symbol ASC;
    """

    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute(query)
        for r in cur.fetchall():
            sym = str(r[0] or "").strip().upper()
            b_raw = str(r[1] or "").strip()
            a_raw = str(r[2] or "").strip()
            dec = int(r[3]) if r[3] is not None else 2
            lup = str(r[4] or "").strip()
            cname = str(r[5] or sym).strip()
            sec = str(r[6] or "").strip()
            sinstr = str(r[7] or "").strip()

            if not is_strictly_public_equity(sym, cname, sec, sinstr):
                continue

            factor = 10.0 ** dec
            bid_parsed = []
            ask_parsed = []

            # Parse Bid Levels
            if b_raw:
                try:
                    b_obj = json.loads(b_raw)
                    raw_levels = b_obj.get("bid_level", [])
                    for idx, lvl in enumerate(raw_levels, 1):
                        rate = float(lvl[0]) / factor if lvl[0] else 0.0
                        qty = int(lvl[1]) if len(lvl) > 1 and lvl[1] else 0
                        ord_cnt = int(lvl[3]) if len(lvl) > 3 and lvl[3] else 1
                        bid_parsed.append({
                            "level": idx,
                            "price": rate,
                            "qty": qty,
                            "orders": ord_cnt,
                        })
                except Exception:
                    pass

            # Parse Ask Levels
            if a_raw:
                try:
                    a_obj = json.loads(a_raw)
                    raw_levels = a_obj.get("ask_level", [])
                    for idx, lvl in enumerate(raw_levels, 1):
                        rate = float(lvl[0]) / factor if lvl[0] else 0.0
                        qty = int(lvl[1]) if len(lvl) > 1 and lvl[1] else 0
                        ord_cnt = int(lvl[3]) if len(lvl) > 3 and lvl[3] else 1
                        ask_parsed.append({
                            "level": idx,
                            "price": rate,
                            "qty": qty,
                            "orders": ord_cnt,
                        })
                except Exception:
                    pass

            results[sym] = {
                "symbol": sym,
                "board": "PUBLIC",
                "price_decimals": dec,
                "last_update": lup,
                "bid_levels": bid_parsed,
                "ask_levels": ask_parsed,
            }
        conn.close()
        return results
    except Exception as e:
        print(f"[WARN: DB Depth] {e}")
        return results


# =========================================================================
# 4. UNIFIED COMPLETE MARKET SNAPSHOT
# =========================================================================
def fetch_complete_market_depth(symbol: str, exchange: str = "DSE") -> Dict[str, str]:
    """
    Combines LTP (dse_md_ltp), BBO (dse_md_bbo), and Market Depth (dse_md_mktdepth)
    into a unified dictionary matching the Market Depth audit structure.
    """
    sym = symbol.strip().upper()
    ex = (exchange or "DSE").strip().upper()
    data: Dict[str, str] = {
        "Symbol": sym,
        "Exchange": ex,
        "DATA_SOURCE": f"DB Tables ({ex} PUBLIC: ltp, bbo, mktdepth)",
    }

    # 1. Fetch LTP & limits
    ltp_data = fetch_ltp_from_db(exchange=ex, symbol=sym).get(sym, {})
    if ltp_data:
        data["LTP"] = f"{ltp_data.get('ltp', 0.0):.2f}"
        data["YCP"] = f"{ltp_data.get('ycp', 0.0):.2f}"
        data["High"] = f"{ltp_data.get('high', 0.0):.2f}"
        data["Low"] = f"{ltp_data.get('low', 0.0):.2f}"
        data["Open"] = f"{ltp_data.get('open', 0.0):.2f}"
        data["Close"] = f"{ltp_data.get('close', 0.0):.2f}"
        data["Total_Volume"] = str(ltp_data.get("total_qty", 0))
        data["SH"] = f"{ltp_data.get('circuit_up', 0.0):.2f}"
        data["SL"] = f"{ltp_data.get('circuit_down', 0.0):.2f}"
        data["CH"] = f"{ltp_data.get('circuit_up', 0.0):.2f}"
        data["CL"] = f"{ltp_data.get('circuit_down', 0.0):.2f}"

    # 2. Fetch Bid/Ask Price (BBO)
    bbo_data = fetch_bbo_from_db(exchange=ex, symbol=sym).get(sym, {})
    if bbo_data:
        data["Best_Bid_Price"] = f"{bbo_data.get('bid_price', 0.0):.2f}"
        data["Best_Bid_Qty"] = str(bbo_data.get("bid_qty", 0))
        data["Best_Ask_Price"] = f"{bbo_data.get('ask_price', 0.0):.2f}"
        data["Best_Ask_Qty"] = str(bbo_data.get("ask_qty", 0))

    # 3. Fetch Multi-level Market Depth
    depth_data = fetch_mktdepth_from_db(exchange=ex, symbol=sym).get(sym, {})
    if depth_data:
        bid_lvls = depth_data.get("bid_levels", [])
        ask_lvls = depth_data.get("ask_levels", [])

        for i in range(1, 6):
            if i <= len(bid_lvls):
                bl = bid_lvls[i - 1]
                data[f"Bid_L{i}_Price"] = f"{bl['price']:.2f}"
                data[f"Bid_L{i}_Qty"] = str(bl["qty"])
                data[f"Bid_L{i}_Ordr"] = str(bl["orders"])
            else:
                data[f"Bid_L{i}_Price"] = "-"
                data[f"Bid_L{i}_Qty"] = "-"
                data[f"Bid_L{i}_Ordr"] = "-"

            if i <= len(ask_lvls):
                al = ask_lvls[i - 1]
                data[f"Ask_L{i}_Price"] = f"{al['price']:.2f}"
                data[f"Ask_L{i}_Qty"] = str(al["qty"])
                data[f"Ask_L{i}_Ordr"] = str(al["orders"])
            else:
                data[f"Ask_L{i}_Price"] = "-"
                data[f"Ask_L{i}_Qty"] = "-"
                data[f"Ask_L{i}_Ordr"] = "-"

    return data


# Backward-compatible helper aliases
fetch_public_board_from_db = fetch_ltp_from_db


def get_public_symbols_list(exchange: str = "DSE") -> List[str]:
    """Returns sorted list of all public board symbols available in the database."""
    data = fetch_ltp_from_db(exchange)
    return sorted(list(data.keys()))


def get_public_symbol_ltp(symbol: str, exchange: str = "DSE") -> Optional[float]:
    """Returns the live LTP for a given public board symbol, or None if not found."""
    if not symbol:
        return None
    sym_clean = symbol.strip().upper()
    ex_clean = (exchange or "DSE").strip().upper()
    target_cache = _CSE_LTP_CACHE if ex_clean == "CSE" else _DSE_LTP_CACHE
    if not target_cache:
        fetch_ltp_from_db(ex_clean)
    if sym_clean in target_cache:
        return target_cache[sym_clean].get("ltp")
    data = fetch_ltp_from_db(exchange, symbol=sym_clean)
    if sym_clean in data:
        return data[sym_clean].get("ltp")
    return None


def get_public_symbol_bbo(symbol: str, exchange: str = "DSE") -> Dict[str, float]:
    """Returns Best Bid & Ask prices for a given public board symbol."""
    sym_clean = symbol.strip().upper()
    bbo = fetch_bbo_from_db(exchange, symbol=sym_clean).get(sym_clean, {})
    return {
        "bid_price": bbo.get("bid_price", 0.0),
        "bid_qty": bbo.get("bid_qty", 0),
        "ask_price": bbo.get("ask_price", 0.0),
        "ask_qty": bbo.get("ask_qty", 0),
    }


def save_public_board_cache(data: Dict[str, Dict[str, Any]], path: str) -> None:
    """Saves the collected market data dictionary to a JSON cache file."""
    try:
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
    except Exception as e:
        print(f"[WARN: save_public_board_cache] {e}")


def load_public_board_cache(path: str) -> Dict[str, Dict[str, Any]]:
    """Loads cached public board data from disk."""
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def print_public_board_summary(exchange: str = "DSE", limit: int = 30) -> None:
    """Prints a comprehensive summary table combining LTP (ltp), BBO (bbo), and Depth queues (mktdepth)."""
    ex = exchange.upper()
    ltp_map = fetch_ltp_from_db(ex)
    bbo_map = fetch_bbo_from_db(ex)

    print("\n" + "=" * 115)
    print(f"  DATABASE TABLES: {ex} ONLY PUBLIC BOARD MARKET DATA (board = 'PUBLIC')")
    print(f"    - LTP:        {ex.lower()}_md_ltp")
    print(f"    - Bid/Ask:    {ex.lower()}_md_bbo")
    print(f"    - MktDepth:   {ex.lower()}_md_mktdepth")
    print(f"  URI: {MARKETDATA_DB_URI}")
    print(f"  Target: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['dbname']} | Total Public Symbols: {len(ltp_map)}")
    print("=" * 115)
    header = f"{'#':<4} | {'Symbol':<12} | {'Company Name':<26} | {'LTP (BDT)':>10} | {'Best Bid (Qty)':>18} | {'Best Ask (Qty)':>18} | {'SH/SL Bounds':>15}"
    print(header)
    print("-" * 115)

    symbols = sorted(list(ltp_map.keys()))
    for idx, sym in enumerate(symbols[:limit], 1):
        l_item = ltp_map[sym]
        b_item = bbo_map.get(sym, {})
        cname = l_item.get("company_name", sym)
        if len(cname) > 26:
            cname = cname[:23] + "..."
        ltp_val = l_item.get("ltp", 0.0)
        up_val = l_item.get("circuit_up", 0.0)
        down_val = l_item.get("circuit_down", 0.0)

        bid_str = f"{b_item.get('bid_price', 0.0):.2f} ({b_item.get('bid_qty', 0)})" if b_item.get("bid_price", 0.0) > 0 else "-"
        ask_str = f"{b_item.get('ask_price', 0.0):.2f} ({b_item.get('ask_qty', 0)})" if b_item.get("ask_price", 0.0) > 0 else "-"
        bounds_str = f"{up_val:.1f} / {down_val:.1f}"

        print(f"{idx:<4} | {sym:<12} | {cname:<26} | {ltp_val:>10.2f} | {bid_str:>18} | {ask_str:>18} | {bounds_str:>15}")

    if len(symbols) > limit:
        print(f"... and {len(symbols) - limit} more Public Board symbols collected from DB tables.")
    print("=" * 115 + "\n")


if __name__ == "__main__":
    first_arg = sys.argv[1].strip() if len(sys.argv) > 1 else "DSE"
    if first_arg.upper() == "COLLECT":
        target_ex = sys.argv[2] if len(sys.argv) > 2 else "DSE"
        collect_ltp(target_ex)
    elif len(sys.argv) > 2:
        # If symbol specified, print full depth
        test_sym = sys.argv[2]
        snap = fetch_complete_market_depth(test_sym, first_arg)
        print(f"\n--- Complete Market Depth for {test_sym} ({first_arg}) from DB Tables ---")
        for k, v in snap.items():
            print(f"  {k:<20}: {v}")
    else:
        print_public_board_summary(first_arg, limit=25)
