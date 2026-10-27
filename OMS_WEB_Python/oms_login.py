"""
OMS-WEB Login & Session Authentication Module
Handles credentials entry, concurrent/active session overrides, and system modal dismissals.
"""
import time
from typing import Optional
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from config import BASE_URL, DEFAULT_USERNAME, DEFAULT_PASSWORD


def press_ok_automatically(driver: webdriver.Chrome) -> None:
    """Dismisses browser alerts, breach popups, or web modal OK buttons automatically."""
    time.sleep(0.8)

    # 1. Check DOM modal buttons (e.g. OK, Confirm, Dismiss)
    try:
        xpath_query = (
            "//button[translate(normalize-space(), 'ok', 'OK')='OK'] | "
            "//a[translate(normalize-space(), 'ok', 'OK')='OK'] | "
            "//button[contains(translate(text(), 'ok', 'OK'), 'OK')] | "
            "//div[contains(@class,'modal') or contains(@class,'dialog') or contains(@class,'popup')]//button"
        )
        ok_buttons = driver.find_elements(By.XPATH, xpath_query)
        for btn in ok_buttons:
            if btn.is_displayed():
                driver.execute_script("arguments[0].click();", btn)
                print("[POPUP] Automatically clicked OK button in DOM.")
                time.sleep(0.3)
                return
    except Exception:
        pass

    # 2. Check JavaScript browser alert
    try:
        alert = driver.switch_to.alert
        alert.accept()
        print("[POPUP] Automatically accepted browser alert.")
        return
    except Exception:
        pass


def login(
    driver: webdriver.Chrome,
    username: Optional[str] = None,
    password: Optional[str] = None,
    auto_continue_active_session: bool = True,
) -> bool:
    """
    Performs full authentication to OMS-WEB.
    """
    usr = username or DEFAULT_USERNAME
    pwd = password or DEFAULT_PASSWORD

    print(f"\n[LOGIN] Navigating to {BASE_URL}...")
    driver.get(BASE_URL)
    time.sleep(1.0)

    try:
        driver.execute_script(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        )
    except Exception:
        pass

    wait = WebDriverWait(driver, 10)

    # Fill username
    try:
        user_input = wait.until(EC.visibility_of_element_located((By.XPATH, "//input[@id='username']")))
        user_input.clear()
        user_input.send_keys(usr)
        time.sleep(0.5)
    except Exception as e:
        print(f"[LOGIN: ERROR] Username field not found: {e}")
        return False

    # Fill password
    try:
        pass_input = driver.find_element(By.XPATH, "//input[@id='password']")
        pass_input.clear()
        pass_input.send_keys(pwd)
        time.sleep(0.5)
    except Exception as e:
        print(f"[LOGIN: ERROR] Password field not found: {e}")
        return False

    # Submit
    try:
        submit_btn = driver.find_element(By.XPATH, "//button[normalize-space()='Login']")
        submit_btn.click()
        print(f"[LOGIN] Submitted credentials for user '{usr}'.")
        time.sleep(1.2)
    except Exception as e:
        print(f"[LOGIN: ERROR] Login submit button failed: {e}")
        return False

    # Handle Active Session popup
    force_login_btns = driver.find_elements(By.ID, "continue_to_force_login")
    if force_login_btns and force_login_btns[0].is_displayed():
        print("[LOGIN] Active session popup detected.")
        if auto_continue_active_session:
            print("[LOGIN] Auto-continuing active session...")
            force_login_btns[0].click()
            time.sleep(1.0)
        else:
            ch = input("Active session exists. Continue session? (1=YES / 0=NO, default 1): ").strip()
            if ch == "0":
                cancel_btn = driver.find_element(By.ID, "cancel_to_force_login")
                cancel_btn.click()
                print("[LOGIN] Active session cancelled.")
                return False
            else:
                force_login_btns[0].click()
                print("[LOGIN] Continuing active session...")
                time.sleep(1.0)
    else:
        print("[LOGIN] No active session popup detected. Proceeding...")

    # Dismiss any leftover breach alerts or info modals
    press_ok_automatically(driver)
    time.sleep(1.0)

    print("✔ [LOGIN] Authentication successful. Logged in as: " + usr)
    return True
