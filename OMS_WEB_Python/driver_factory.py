"""
OMS-WEB WebDriver Factory & Browser Session Initializer
Configures Chrome with anti-detection flags, privacy settings, and automated driver resolution.
"""
import os
import sys
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

from config import (
    PAGE_LOAD_TIMEOUT,
    IMPLICIT_WAIT,
    LOCAL_CHROMEDRIVER_PATH,
)


def get_chromedriver_service() -> Service:
    """Resolves ChromeDriver using webdriver-manager with local fallback."""
    try:
        from webdriver_manager.chrome import ChromeDriverManager
        driver_path = ChromeDriverManager().install()
        # webdriver-manager sometimes returns THIRD_PARTY_NOTICES or folder path on Windows
        if driver_path and not driver_path.endswith(".exe"):
            parent_dir = os.path.dirname(driver_path)
            potential_exe = os.path.join(parent_dir, "chromedriver.exe")
            if os.path.isfile(potential_exe):
                driver_path = potential_exe
        if driver_path and os.path.isfile(driver_path):
            print(f"[BROWSER] Using ChromeDriver from webdriver-manager: {driver_path}")
            return Service(driver_path)
    except Exception as e:
        print(f"[BROWSER: WARN] ChromeDriverManager resolution failed ({e}). Checking local fallback...")

    # Local fallback path check
    if os.path.isfile(LOCAL_CHROMEDRIVER_PATH):
        print(f"[BROWSER] Using local fallback ChromeDriver: {LOCAL_CHROMEDRIVER_PATH}")
        return Service(LOCAL_CHROMEDRIVER_PATH)

    # Let Selenium Manager automatically resolve driver
    print("[BROWSER] Falling back to Selenium built-in Selenium Manager...")
    return Service()


def create_driver(headless: bool = False) -> webdriver.Chrome:
    """Creates a configured Chrome WebDriver session with anti-detection tweaks."""
    options = Options()

    # Automation & Detection evasion
    options.add_experimental_option("excludeSwitches", ["enable-automation"])
    options.add_experimental_option("useAutomationExtension", False)
    options.add_argument("--disable-blink-features=AutomationControlled")
    options.add_argument("--disable-infobars")

    # Privacy tweaks: disable password manager, leak detection, notifications
    prefs = {
        "credentials_enable_service": False,
        "profile.password_manager_enabled": False,
        "profile.password_manager_leak_detection": False,
        "password_manager_leak_detection": False,
        "safebrowsing.enabled": False,
        "safebrowsing.enhanced": False,
        "profile.default_content_setting_values.notifications": 2,
    }
    options.add_experimental_option("prefs", prefs)

    # Suppress Chrome popups
    options.add_argument(
        "--disable-features=PasswordLeakDetection,PasswordCheck,PasswordLeakToggleMove,SafeBrowsing"
    )
    options.set_capability("acceptInsecureCerts", True)

    if headless:
        options.add_argument("--headless=new")
        options.add_argument("--window-size=1920,1080")

    service = get_chromedriver_service()
    driver = webdriver.Chrome(service=service, options=options)

    # Browser window and timeouts
    driver.maximize_window()
    try:
        driver.delete_all_cookies()
    except Exception:
        pass

    driver.set_page_load_timeout(PAGE_LOAD_TIMEOUT)
    driver.implicitly_wait(IMPLICIT_WAIT)

    # Stealth override for navigator.webdriver
    try:
        driver.execute_script(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        )
    except Exception:
        pass

    return driver
