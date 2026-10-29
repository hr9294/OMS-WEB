"""
OMS-WEB Automation Suite - Configuration & Settings
"""
import os

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

# Known Active Seed Clients across platform
# Known Active Verified High-Cash Seed Clients (>= 100,000 BDT & Valid BO IDs)
SEED_CLIENT_CODES = [
    "08096", "00224", "08833", "00507", "09139", "09189",
    "07605", "09185", "08383", "08231", "08570", "08739",
    "08425", "00495", "08345"
]

# GoldenLayout Widget Color Link Palette
COLOR_NAMES = ["Turquoise", "Yellow", "Orange", "Green", "Red", "Pink"]
COLOR_HEXES = ["#42EADDFF", "#FEE715FF", "#F95700ff", "#06d001", "#EB0506", "#FF3EA5FF"]
COLOR_MAP = dict(zip(COLOR_NAMES, COLOR_HEXES))

# Local ChromeDriver Fallback Path
LOCAL_CHROMEDRIVER_PATH = (
    r"D:\Automation & Load Testing\Selenium\Software\152.0.7977.64 chromedriver-win64\chromedriver-win64\chromedriver.exe"
)
