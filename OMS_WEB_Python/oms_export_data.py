"""OMS-WEB Export Data Engine (Safe-Guarded DOM Inspection & Export Console).

Ports OMSweb_ExportData.java cleanly in compact form with zero live-export risk by default.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
import time
from typing import List, Optional
from selenium import webdriver
import config
from oms_upload_data import dismiss_overlays

DEFAULT_NO_EXPORT: bool = True

class ExportSubMenu(Enum):
    XML_TRADE_ALL = (1, "XML Trade File (All)", "xml_trade_all", "XML")
    XML_TRADE_DSE = (2, "XML Trade File (DSE)", "xml_trade_dse", "XML")
    XML_TRADE_CSE = (3, "XML Trade File (CSE)", "xml_trade_cse", "XML")
    CSV_TRADE_ALL = (4, "CSV Trade File (All)", "csv_trade_all", "CSV")
    CSV_TRADE_DSE = (5, "CSV Trade File (DSE)", "csv_trade_dse", "CSV")
    CSV_TRADE_CSE = (6, "CSV Trade File (CSE)", "csv_trade_cse", "CSV")
    TXT_TRADE_CSE = (7, "TXT Trade File (CSE)", "txt_trade_cse", "TXT")
    EOD_PRICE_FILE = (8, "EOD Price File", "eod_price_file", "DAT/CSV")
    ASSOCIATES_FILE = (9, "Associates File", "associates_file", "XML/CSV")
    CLIENT_COMMISSION_FILE = (10, "Client Commission File", "client_commission_file", "XML/CSV")
    CLIENT_RATING_FILE = (11, "Client Rating File", "client_rating_file", "XML/CSV")
    DEALER_TERMINAL_FILE = (12, "Dealer Terminal File", "dealer_terminal_file", "XML/CSV")
    ALL_REPORTS_FILES_ZIP = (13, "All Reports & Files Zip", "all_reports_files_zip", "ZIP")

    def __init__(self, index: int, display_name: str, key: str, file_type: str):
        self.index = index
        self.display_name = display_name
        self.key = key
        self.file_type = file_type

    @classmethod
    def from_index(cls, idx: int) -> Optional["ExportSubMenu"]:
        return next((m for m in cls if m.index == idx), None)

    @classmethod
    def from_name(cls, name: str) -> Optional["ExportSubMenu"]:
        clean = "".join(ch for ch in (name or "").lower() if ch.isalnum())
        return next((m for m in cls if clean and clean in "".join(c for c in m.display_name.lower() if c.isalnum())), None)

@dataclass
class ExportMenuData:
    menu_button_found: bool = False
    dropdown_opened: bool = False
    sub_menus: List[str] = field(default_factory=list)
    page_timestamp: str = ""

def is_export_files_button_visible(driver: Optional[webdriver.Chrome]) -> bool:
    if not driver:
        return False
    try:
        return bool(driver.execute_script(
            "return Array.from(document.querySelectorAll('button, a, .btn, [id*=\"export\"]')).some(function(b) {"
            "  var t = (b.innerText || b.textContent || '').trim().toLowerCase();"
            "  return t.indexOf('export files') !== -1 || (t.indexOf('export') !== -1 && t.indexOf('file') !== -1);"
            "});"
        ))
    except Exception:
        return False

def navigate_to_export_files_page(driver: Optional[webdriver.Chrome], timeout: float = 5.0) -> bool:
    if not driver:
        return False
    dismiss_overlays(driver)
    if is_export_files_button_visible(driver):
        return True
    try:
        driver.execute_script("""
            var link = document.querySelector('[data-link*="file_manager"], [data-link*="export"], [href*="export"]');
            if (link) { link.click(); return; }
            var sb = document.querySelector('ul.sidebar, ul.nav, .sidebar ul, nav ul');
            if (sb) {
                var items = sb.querySelectorAll('li, a');
                if (items.length >= 8) { items[7].click(); return; }
            }
            var folder = document.querySelector('i.fa-folder, [class*="folder"]');
            if (folder) (folder.closest('a, li, button') || folder).click();
        """)
        deadline = time.time() + timeout
        while time.time() < deadline:
            if is_export_files_button_visible(driver):
                return True
            time.sleep(0.3)
    except Exception:
        pass
    return is_export_files_button_visible(driver)

def open_export_files_menu(driver: Optional[webdriver.Chrome]) -> bool:
    if not driver:
        return False
    try:
        ok = driver.execute_script("""
            var items = Array.from(document.querySelectorAll('ul.dropdown-menu, .dropdown-content, li, a'));
            if (items.some(function(el){ return (el.innerText || '').indexOf('XML Trade File') !== -1 && el.offsetParent !== null; })) return true;
            var btn = Array.from(document.querySelectorAll('button, a, .btn, [id*="export"]')).find(function(b) {
                var t = (b.innerText || b.textContent || '').trim().toLowerCase();
                return t === 'export files' || t.indexOf('export files') !== -1;
            });
            if (btn) { if (window.jQuery) window.jQuery(btn).trigger('click'); btn.click(); return true; }
            return false;
        """)
        time.sleep(0.3)
        return bool(ok)
    except Exception:
        return False

def verify_sub_menu_present(driver: Optional[webdriver.Chrome], sub_menu: Optional[ExportSubMenu]) -> bool:
    if not driver or not sub_menu:
        return False
    try:
        return bool(driver.execute_script(
            "var target = arguments[0].toLowerCase();"
            "return Array.from(document.querySelectorAll('a, li, button, span')).some(function(el) {"
            "  return (el.innerText || '').trim().toLowerCase() === target;"
            "});",
            sub_menu.display_name,
        ))
    except Exception:
        return False

def capture_export_menu_and_submenu_data(driver: Optional[webdriver.Chrome]) -> ExportMenuData:
    data = ExportMenuData()
    if not driver:
        return data
    data.dropdown_opened = open_export_files_menu(driver)
    data.menu_button_found = is_export_files_button_visible(driver)
    data.sub_menus = [m.display_name for m in ExportSubMenu if verify_sub_menu_present(driver, m)]
    return data

def export_file_safely(
    driver: Optional[webdriver.Chrome],
    sub_menu: Optional[ExportSubMenu],
    dry_run: bool = DEFAULT_NO_EXPORT,
) -> bool:
    if not driver or not sub_menu:
        return False
    open_export_files_menu(driver)
    present = verify_sub_menu_present(driver, sub_menu)
    if dry_run:
        print(f"[SAFE GUARD] Dry-run only for '{sub_menu.display_name}' [{sub_menu.file_type}]: present={present}")
        return present
    try:
        clicked = driver.execute_script(
            "var target = arguments[0].toLowerCase();"
            "var el = Array.from(document.querySelectorAll('ul.dropdown-menu li, .dropdown-item, a, li, button')).find(function(x){"
            "  var t = (x.innerText || '').trim().toLowerCase(); return t === target || t.indexOf(target) !== -1;"
            "});"
            "if (el) { if (window.jQuery) window.jQuery(el).trigger('click'); el.click(); return true; } return false;",
            sub_menu.display_name,
        )
        time.sleep(0.5)
        dismiss_overlays(driver)
        return bool(clicked)
    except Exception:
        return False

def run_export_console(driver: webdriver.Chrome) -> None:
    navigate_to_export_files_page(driver)
    data = capture_export_menu_and_submenu_data(driver)
    print(f"\n[EXPORT DATA] Button={data.menu_button_found} | Dropdown={data.dropdown_opened} | SubMenus={len(data.sub_menus)}/{len(ExportSubMenu)}")
    for m in ExportSubMenu:
        print(f"  {m.index:2d}. {m.display_name:<25} [{m.file_type}]")
    choice = config.read_line_with_timeout("Select export option (1-13) or Enter for Dry-Run audit [1]: ", "1", 5.0)
    sub = ExportSubMenu.from_index(int(choice)) if choice.isdigit() else ExportSubMenu.XML_TRADE_ALL
    export_file_safely(driver, sub or ExportSubMenu.XML_TRADE_ALL, dry_run=DEFAULT_NO_EXPORT)
