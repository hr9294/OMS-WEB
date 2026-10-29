"""
db_market_data.py
-----------------
Collects Market Data (Symbols, LTP, YCP, Circuit Limits) directly from the
PostgreSQL database table (marketdb -> dse_md_ltp / cse_md_ltp) filtering
specifically for the Public Board ('PUBLIC').

Configured for UFTFAST OMS / UFTCL UAT environment (192.168.155.22:5445).
"""

import os
import sys
import json
import subprocess
from typing import Dict, List, Optional, Any

# Ensure stdout handles UTF-8 characters cleanly on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# Database Connection Settings
DB_CONFIG = {
    "host": os.environ.get("MKT_DB_HOST", "192.168.155.22"),
    "port": int(os.environ.get("MKT_DB_PORT", "5445")),
    "dbname": os.environ.get("MKT_DB_NAME", "marketdb"),
    "user": os.environ.get("MKT_DB_USER", "marketdata"),
    "password": os.environ.get("MKT_DB_PASS", "pass4321"),
    "timeout": 5,
}

# Cache file location for fast offline/cross-process access
CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "public_board_ltp.json")


def fetch_public_board_from_db(exchange: str = "DSE") -> Dict[str, Dict[str, Any]]:
    """
    Queries the database table (dse_md_ltp / cse_md_ltp) directly for all
    public board symbols where board = 'PUBLIC'.

    Returns a dict mapping symbol -> {
        'symbol': str,
        'company_name': str,
        'board': 'PUBLIC',
        'ltp': float,
        'ycp': float,
        'circuit_up': float,
        'circuit_down': float,
        'floor_price': float,
        'open': float,
        'high': float,
        'low': float,
        'close': float,
        'total_qty': int,
        'last_update': str
    }
    """
    results: Dict[str, Dict[str, Any]] = {}
    ex_clean = (exchange or "DSE").strip().upper()
    table_ltp = "cse_md_ltp" if ex_clean == "CSE" else "dse_md_ltp"
    table_sym = "cse_md_symbols" if ex_clean == "CSE" else "dse_md_symbols"

    # 1. Try direct psycopg2 connection first
    try:
        import psycopg2

        conn = psycopg2.connect(
            host=DB_CONFIG["host"],
            port=DB_CONFIG["port"],
            dbname=DB_CONFIG["dbname"],
            user=DB_CONFIG["user"],
            password=DB_CONFIG["password"],
            connect_timeout=DB_CONFIG["timeout"],
        )
        cur = conn.cursor()
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
                l.last_update
            FROM {table_ltp} l
            LEFT JOIN {table_sym} s ON l.symbol = s.symbol AND l.board = s.board
            WHERE l.board = 'PUBLIC'
              AND l.ltp IS NOT NULL
              AND l.ltp > 0
            ORDER BY l.symbol ASC;
        """
        cur.execute(query)
        rows = cur.fetchall()
        for r in rows:
            sym = str(r[0] or "").strip().upper()
            if not sym:
                continue
            results[sym] = {
                "symbol": sym,
                "company_name": str(r[1] or sym).strip(),
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
        if results:
            save_public_board_cache(results, CACHE_FILE)
            return results
    except Exception as e:
        print(f"[WARN: DB MarketData psycopg2] {e}")

    # 2. Fallback: Try psql command-line client if available
    try:
        psql_paths = [
            r"C:\Program Files\PostgreSQL\16\bin\psql.exe",
            r"C:\Program Files\PostgreSQL\15\bin\psql.exe",
            "psql",
        ]
        psql_exe = None
        for p in psql_paths:
            if os.path.exists(p) or p == "psql":
                psql_exe = p
                break

        if psql_exe:
            env = dict(os.environ)
            env["PGPASSWORD"] = DB_CONFIG["password"]
            sql_cmd = (
                f"SELECT l.symbol, COALESCE(s.company_name, l.symbol), l.ltp, l.ycp, "
                f"l.circuit_up, l.circuit_down, l.floor_price, l.open, l.high, l.low, l.close, l.total_qty, l.last_update "
                f"FROM {table_ltp} l LEFT JOIN {table_sym} s ON l.symbol = s.symbol AND l.board = s.board "
                f"WHERE l.board = 'PUBLIC' AND l.ltp IS NOT NULL AND l.ltp > 0 ORDER BY l.symbol ASC;"
            )
            cmd = [
                psql_exe,
                "-h", DB_CONFIG["host"],
                "-p", str(DB_CONFIG["port"]),
                "-U", DB_CONFIG["user"],
                "-d", DB_CONFIG["dbname"],
                "-t", "-A", "-F", "\t",
                "-c", sql_cmd,
            ]
            proc = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=10)
            if proc.returncode == 0 and proc.stdout:
                for line in proc.stdout.splitlines():
                    parts = line.split("\t")
                    if len(parts) >= 3:
                        sym = parts[0].strip().upper()
                        if sym:
                            results[sym] = {
                                "symbol": sym,
                                "company_name": parts[1].strip() if len(parts) > 1 else sym,
                                "board": "PUBLIC",
                                "ltp": float(parts[2]) if parts[2] else 0.0,
                                "ycp": float(parts[3]) if len(parts) > 3 and parts[3] else 0.0,
                                "circuit_up": float(parts[4]) if len(parts) > 4 and parts[4] else 0.0,
                                "circuit_down": float(parts[5]) if len(parts) > 5 and parts[5] else 0.0,
                                "floor_price": float(parts[6]) if len(parts) > 6 and parts[6] else 0.0,
                                "open": float(parts[7]) if len(parts) > 7 and parts[7] else 0.0,
                                "high": float(parts[8]) if len(parts) > 8 and parts[8] else 0.0,
                                "low": float(parts[9]) if len(parts) > 9 and parts[9] else 0.0,
                                "close": float(parts[10]) if len(parts) > 10 and parts[10] else 0.0,
                                "total_qty": int(parts[11]) if len(parts) > 11 and parts[11] else 0,
                                "last_update": parts[12].strip() if len(parts) > 12 else "",
                            }
                if results:
                    save_public_board_cache(results, CACHE_FILE)
                    return results
    except Exception as e:
        print(f"[WARN: DB MarketData psql CLI] {e}")

    # 3. Fallback: Load from local JSON cache if DB is temporarily unreachable
    cached = load_public_board_cache(CACHE_FILE)
    if cached:
        print(f"[INFO] Loaded {len(cached)} Public Board symbols from local cache ({CACHE_FILE}).")
        return cached

    return results


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


def get_public_symbols_list(exchange: str = "DSE") -> List[str]:
    """Returns sorted list of all public board symbols available in the database."""
    data = fetch_public_board_from_db(exchange)
    return sorted(list(data.keys()))


def get_public_symbol_ltp(symbol: str, exchange: str = "DSE") -> Optional[float]:
    """Returns the live LTP for a given public board symbol, or None if not found."""
    if not symbol:
        return None
    sym_clean = symbol.strip().upper()
    data = fetch_public_board_from_db(exchange)
    if sym_clean in data:
        return data[sym_clean].get("ltp")
    return None


def print_public_board_summary(exchange: str = "DSE", limit: int = 25) -> None:
    """Prints a formatted summary table of collected public board symbols & LTPs."""
    data = fetch_public_board_from_db(exchange)
    print("\n" + "=" * 95)
    print(f"  DATABASE TABLE: {exchange.upper()} PUBLIC BOARD MARKET DATA (dse_md_ltp WHERE board = 'PUBLIC')")
    print(f"  Target: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['dbname']} | Total Symbols Collected: {len(data)}")
    print("=" * 95)
    header = f"{'#':<4} | {'Symbol':<12} | {'Company Name':<28} | {'LTP (BDT)':>10} | {'YCP':>8} | {'SH/CH':>9} | {'SL/CL':>9}"
    print(header)
    print("-" * 95)

    symbols = sorted(list(data.keys()))
    for idx, sym in enumerate(symbols[:limit], 1):
        item = data[sym]
        cname = item.get("company_name", sym)
        if len(cname) > 28:
            cname = cname[:25] + "..."
        ltp_val = item.get("ltp", 0.0)
        ycp_val = item.get("ycp", 0.0)
        up_val = item.get("circuit_up", 0.0)
        down_val = item.get("circuit_down", 0.0)
        print(f"{idx:<4} | {sym:<12} | {cname:<28} | {ltp_val:>10.2f} | {ycp_val:>8.2f} | {up_val:>9.2f} | {down_val:>9.2f}")

    if len(symbols) > limit:
        print(f"... and {len(symbols) - limit} more Public Board symbols collected from DB table.")
    print("=" * 95 + "\n")


if __name__ == "__main__":
    ex = sys.argv[1] if len(sys.argv) > 1 else "DSE"
    print_public_board_summary(ex, limit=30)
