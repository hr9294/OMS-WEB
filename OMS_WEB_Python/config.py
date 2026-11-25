"""
OMS-WEB Automation Suite - Configuration & Settings
"""
import os
import socket
import sys
import time
from typing import Optional, Any

# Environment & Safety Guards
IS_CI = any(
    os.environ.get(k, "").strip().lower() == "true"
    for k in ("CI", "HEADLESS", "DOCKER_MODE", "AUTOMATE_MODE")
)
IS_HEADLESS = any(
    os.environ.get(k, "").strip().lower() == "true"
    for k in ("CI", "HEADLESS")
)
DEFAULT_NO_UPLOAD = True

# URLs & Endpoints
BASE_URL = os.environ.get("OMS_BASE_URL", "https://devoms.quantbd.com")
SHARED_CLIENT_DATA_URL = f"{BASE_URL}/shared/getclientdata/?cln_code="
SHARED_MARKET_DEPTH_URL = f"{BASE_URL}/shared/marketdepth/?symbol="

# Default Credentials
DEFAULT_USERNAME = os.environ.get("OMS_USERNAME", "UFTTRDR020")
DEFAULT_PASSWORD = os.environ.get("OMS_PASSWORD", "12345")

# Timeouts (seconds)
PAGE_LOAD_TIMEOUT = 25
IMPLICIT_WAIT = 10
EXPLICIT_WAIT = 8

# Default Trading Settings
DEFAULT_SYMBOL = "GP"
DEFAULT_EXCHANGE = "DSE"
DEFAULT_CLIENT_CODE = "23986"
DEFAULT_MIN_CASH_BDT = 100000.0

SEED_CLIENT_CODES = [
    "08096", "00224", "08833", "00507", "09139", "09189",
    "07605", "09185", "08383", "08231", "08570", "08739",
    "08425", "00495", "08345",
]

# GoldenLayout Widget Color Link Palette
COLOR_NAMES = ["Turquoise", "Yellow", "Orange", "Green", "Red", "Pink"]
COLOR_HEXES = ["#42EADDFF", "#FEE715FF", "#F95700ff", "#06d001", "#EB0506", "#FF3EA5FF"]
COLOR_MAP = dict(zip(COLOR_NAMES, COLOR_HEXES))

# Market Data Database Settings (PostgreSQL - Primary & Secondary Fallback)
PRIMARY_DB_HOST = "192.168.155.22"
SECONDARY_DB_HOST = "10.20.8.29"
MKT_DB_HOST = PRIMARY_DB_HOST
MKT_DB_PORT = int(os.environ.get("MKT_DB_PORT", "5445"))
MKT_DB_NAME = os.environ.get("MKT_DB_NAME", "marketdb")
MKT_DB_USER = os.environ.get("MKT_DB_USER", "marketdata")
MKT_DB_PASS = os.environ.get("MKT_DB_PASS", "pass4321")
PRIMARY_MARKETDATA_DB_URI = os.environ.get(
    "PRIMARY_MARKETDATA_DB_URI",
    f"postgresql+psycopg2://{MKT_DB_USER}:{MKT_DB_PASS}@{PRIMARY_DB_HOST}:{MKT_DB_PORT}/{MKT_DB_NAME}",
)
SECONDARY_MARKETDATA_DB_URI = os.environ.get(
    "SECONDARY_MARKETDATA_DB_URI",
    f"postgresql+psycopg2://{MKT_DB_USER}:{MKT_DB_PASS}@{SECONDARY_DB_HOST}:{MKT_DB_PORT}/{MKT_DB_NAME}",
)
MARKETDATA_DB_URI = PRIMARY_MARKETDATA_DB_URI

# qTrader Portfolio & Client Cash Database Settings (PostgreSQL)
QTRADER_PRIMARY_HOST = os.environ.get("QTRADER_PRIMARY_HOST", PRIMARY_DB_HOST)
QTRADER_SECONDARY_HOST = os.environ.get("QTRADER_SECONDARY_HOST", SECONDARY_DB_HOST)
QTRADER_DB_PORT = int(os.environ.get("QTRADER_DB_PORT", "5443"))
QTRADER_DB_NAME = os.environ.get("QTRADER_DB_NAME", "qtraderdb")
QTRADER_DB_USER = os.environ.get("QTRADER_DB_USER", "qtraderdata")
QTRADER_DB_PASS = os.environ.get("QTRADER_DB_PASS", "pass4321")

# Local ChromeDriver Fallback Path
LOCAL_CHROMEDRIVER_PATH = (
    r"D:\Automation & Load Testing\Selenium\Software\152.0.7977.64 chromedriver-win64\chromedriver-win64\chromedriver.exe"
)

def is_host_reachable(host: str, port: int, timeout_sec: float = 0.15) -> bool:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout_sec)
            return s.connect_ex((host, port)) == 0
    except Exception:
        return False

def _connect_pg_failover(hosts: list, port: int, dbname: str, user: str, password: str) -> Optional[Any]:
    """Connects to Primary/Secondary PostgreSQL using fast socket pre-check; returns None if unavailable."""
    try:
        import psycopg2
    except Exception:
        return None
    for host in hosts:
        if not is_host_reachable(host, port, 0.15):
            continue
        try:
            conn = psycopg2.connect(
                host=host, port=port, dbname=dbname, user=user, password=password, connect_timeout=2
            )
            if conn and not conn.closed:
                return conn
        except Exception:
            pass
    return None

def get_qtrader_db_connection() -> Optional[Any]:
    return _connect_pg_failover(
        [QTRADER_PRIMARY_HOST, QTRADER_SECONDARY_HOST],
        QTRADER_DB_PORT, QTRADER_DB_NAME, QTRADER_DB_USER, QTRADER_DB_PASS,
    )

def get_marketdata_db_connection() -> Optional[Any]:
    return _connect_pg_failover(
        [PRIMARY_DB_HOST, SECONDARY_DB_HOST],
        MKT_DB_PORT, MKT_DB_NAME, MKT_DB_USER, MKT_DB_PASS,
    )

def read_line_with_timeout(prompt: str, default_val: str = "", timeout_sec: float = 4.0) -> str:
    """
    Non-blocking console prompt that auto-defaults after timeout_sec or immediately in CI/non-TTY.
    Uses msvcrt on Windows interactive terminals so it never hangs pytest or automated sessions.
    """
    if prompt:
        print(prompt, end="", flush=True)
    if IS_CI or not sys.stdin or not sys.stdin.isatty():
        print(f" [Auto-selected default: {default_val}]")
        return default_val
    try:
        if os.name == "nt":
            import msvcrt
            buf = []
            end = time.time() + max(0.2, timeout_sec)
            while time.time() < end:
                if msvcrt.kbhit():
                    ch = msvcrt.getwch()
                    if ch in ("\r", "\n"):
                        print()
                        val = "".join(buf).strip()
                        return val if val else default_val
                    elif ch == "\x08":  # Backspace
                        if buf:
                            buf.pop()
                            print("\b \b", end="", flush=True)
                    elif ch == "\x03":
                        raise KeyboardInterrupt
                    else:
                        buf.append(ch)
                        print(ch, end="", flush=True)
                time.sleep(0.05)
            print(f"\n [Auto-selected default: {default_val}]")
            return default_val
        else:
            import select
            r, _, _ = select.select([sys.stdin], [], [], max(0.2, timeout_sec))
            if r:
                val = sys.stdin.readline().strip()
                return val if val else default_val
            print(f"\n [Auto-selected default: {default_val}]")
            return default_val
    except KeyboardInterrupt:
        raise
    except Exception:
        return default_val
