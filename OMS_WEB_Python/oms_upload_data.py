"""
OMS-WEB Upload Data Engine (7th Sidebar Folder Icon)
Manages navigation, 9 sub-menu inspection, uploaded XML table scraping,
Zip report controls, and guarded Stock File upload (DEFAULT_NO_UPLOAD = True).
"""
from enum import Enum
import os
import time
from typing import List, Dict, Optional
from selenium import webdriver
from selenium.webdriver.common.by import By

import config


DEFAULT_NO_UPLOAD = config.DEFAULT_NO_UPLOAD

SUB_MENU_NAMES = [
    "Client File",
    "Stock File",
    "Cash File",
    "Associates File",
    "Client Rating File",
    "Client Commission File",
    "User Active / Inactive File",
    "Dealer Terminal File",
    "CSE Circuit Breaker File",
]


class UploadSubMenu(Enum):
    CLIENT_FILE = (1, "Client File", "client_file")
    STOCK_FILE = (2, "Stock File", "stock_file")
    CASH_FILE = (3, "Cash File", "cash_file")
    ASSOCIATES_FILE = (4, "Associates File", "associates_file")
    CLIENT_RATING_FILE = (5, "Client Rating File", "client_rating_file")
    CLIENT_COMMISSION_FILE = (6, "Client Commission File", "client_commission_file")
    USER_ACTIVE_INACTIVE_FILE = (7, "User Active / Inactive File", "user_active_inactive_file")
    DEALER_TERMINAL_FILE = (8, "Dealer Terminal File", "dealer_terminal_file")
    CSE_CIRCUIT_BREAKER_FILE = (9, "CSE Circuit Breaker File", "cse_circuit_breaker_file")

    def __init__(self, index: int, display_name: str, identifier_key: str):
        self.index = index
        self.display_name = display_name
        self.identifier_key = identifier_key

    @classmethod
    def from_index(cls, idx: int) -> Optional["UploadSubMenu"]:
        for m in cls:
            if m.index == idx:
                return m
        return None


class SubMenuItemData:
    def __init__(self, index: int, name: str):
        self.index = index
        self.name = name
        self.dom_id = ""
        self.dom_class = ""
        self.is_displayed = False
        self.is_enabled = True


class UploadedFileRecord:
    def __init__(self, index: int, file_name: str, status: str = "Uploaded"):
        self.index = index
        self.file_name = file_name
        self.status = status
        self.raw_attributes: Dict[str, str] = {}


class ZipReportInfo:
    def __init__(self):
        self.has_download_btn = False
        self.has_delete_btn = False
        self.label = "all reports zip file"


class MenuData:
    def __init__(self):
        self.menu_button_found = False
        self.dropdown_opened = False
        self.menu_button_text = "Upload Data"
        self.sub_menus: List[SubMenuItemData] = [SubMenuItemData(i + 1, n) for i, n in enumerate(SUB_MENU_NAMES)]
        self.uploaded_files: List[UploadedFileRecord] = []
        self.zip_report_info = ZipReportInfo()


class UploadExecutionResult:
    def __init__(self, attempted: bool, uploaded: bool, dry_run: bool, resolved_path: str, status: str, message: str):
        self.attempted = attempted
        self.uploaded = uploaded
        self.dry_run = dry_run
        self.resolved_path = resolved_path or ""
        self.status = status or ""
        self.message = message or ""

    def __repr__(self) -> str:
        return (
            f"UploadExecutionResult[attempted={self.attempted}, uploaded={self.uploaded}, "
            f"dry_run={self.dry_run}, status={self.status}, path={self.resolved_path}]"
        )


def dismiss_overlays(driver: Optional[webdriver.Chrome]) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            if (window.jconfirm && window.jconfirm.instances) {
                for (var i = 0; i < window.jconfirm.instances.length; i++) {
                    if (window.jconfirm.instances[i]) window.jconfirm.instances[i].close();
                }
            }
            document.querySelectorAll('.modal-backdrop, .jconfirm-bg, .jconfirm-overlay, .blockOverlay').forEach(function(b) { b.remove(); });
        """)
        try:
            driver.switch_to.alert.dismiss()
        except Exception:
            pass
    except Exception:
        pass


def navigate_to_upload_data_page(driver: Optional[webdriver.Chrome], timeout: float = 5.0) -> bool:
    if not driver:
        return False
    try:
        dismiss_overlays(driver)
        driver.execute_script("""
            var fi = document.querySelector('i.fa-folder, i.fa-folder-open, [class*="folder"]');
            var target = fi ? (fi.closest('a, li, button, div') || fi) : document.querySelector('[data-link*="upload"], [href*="upload"]');
            if (target) { if (window.jQuery) $(target).trigger('click'); target.click(); }
        """)
        end = time.time() + timeout
        while time.time() < end:
            if driver.execute_script(
                "return Array.from(document.querySelectorAll('button, a, .btn')).some(function(b){ return (b.innerText||'').toLowerCase().indexOf('upload data') !== -1; });"
            ):
                return True
            time.sleep(0.25)
    except Exception:
        pass
    return False


def open_upload_data_menu(driver: Optional[webdriver.Chrome]) -> bool:
    if not driver:
        return False
    try:
        ok = driver.execute_script("""
            var items = Array.from(document.querySelectorAll('ul.dropdown-menu, .dropdown-item, li, a'));
            if (items.some(function(el){ return (el.innerText||'').indexOf('Client File') !== -1 && el.offsetParent !== null; })) return true;
            var btn = Array.from(document.querySelectorAll('button, a, .btn')).find(function(b){
                return (b.innerText||'').trim().toLowerCase().indexOf('upload data') !== -1;
            });
            if (btn) { if (window.jQuery) $(btn).trigger('click'); btn.click(); return true; }
            return false;
        """)
        time.sleep(0.3)
        return bool(ok)
    except Exception:
        return False


def capture_menu_and_submenu_data(driver: Optional[webdriver.Chrome]) -> MenuData:
    res = MenuData()
    if not driver:
        return res
    try:
        res.dropdown_opened = open_upload_data_menu(driver)
        dom = driver.execute_script("""
            var out = { menuButtonFound: false, subMenus: [], uploadedFiles: [], zipInfo: {} };
            var btn = Array.from(document.querySelectorAll('button, a, .btn')).find(function(b){ return (b.innerText||'').toLowerCase().indexOf('upload data') !== -1; });
            out.menuButtonFound = !!btn;
            var allEls = Array.from(document.querySelectorAll('ul.dropdown-menu li, .dropdown-item, a, li, button, span'));
            arguments[0].forEach(function(name, idx) {
                var m = allEls.find(function(el){ var t = (el.innerText||'').trim(); return t === name || (t.indexOf(name) !== -1 && t.length < name.length + 8); });
                out.subMenus.push({ index: idx + 1, name: name, domId: m ? (m.id||'') : '', domClass: m ? (m.className||'') : '', isDisplayed: m ? (m.offsetParent !== null) : false });
            });
            document.querySelectorAll('table tbody tr, table tr').forEach(function(r) {
                var txt = (r.innerText||'').trim();
                if (txt.indexOf('.xml') !== -1 || txt.indexOf('.csv') !== -1) {
                    var cells = Array.from(r.querySelectorAll('td')).map(function(c){ return c.innerText.trim(); });
                    out.uploadedFiles.push({ fileName: cells[0] || txt });
                }
            });
            out.zipInfo.hasDownloadBtn = !!document.querySelector('button.btn-success, .btn-download, i.fa-download');
            out.zipInfo.hasDeleteBtn = !!document.querySelector('button.btn-danger, .btn-delete, i.fa-trash');
            return out;
        """, SUB_MENU_NAMES) or {}
        res.menu_button_found = bool(dom.get("menuButtonFound"))
        if isinstance(dom.get("subMenus"), list) and dom["subMenus"]:
            res.sub_menus = []
            for sm in dom["subMenus"]:
                item = SubMenuItemData(int(sm.get("index", 1)), str(sm.get("name", "")))
                item.dom_id = str(sm.get("domId", ""))
                item.dom_class = str(sm.get("domClass", ""))
                item.is_displayed = bool(sm.get("isDisplayed", False))
                res.sub_menus.append(item)
        res.uploaded_files = [
            UploadedFileRecord(i + 1, str(f.get("fileName", "")))
            for i, f in enumerate(dom.get("uploadedFiles", []) or [])
        ]
        zi = dom.get("zipInfo") or {}
        res.zip_report_info.has_download_btn = bool(zi.get("hasDownloadBtn"))
        res.zip_report_info.has_delete_btn = bool(zi.get("hasDeleteBtn"))
    except Exception:
        pass
    return res


def resolve_default_sample_file_path(sub_menu: Optional[UploadSubMenu]) -> str:
    if sub_menu is None:
        return ""
    mapping = {
        UploadSubMenu.CLIENT_FILE: "sample_files/increment_active_clients.xml",
        UploadSubMenu.STOCK_FILE: "sample_files/increment_stock.xml",
        UploadSubMenu.CASH_FILE: "sample_files/increment_cash.xml",
    }
    rel = mapping.get(sub_menu, "")
    if not rel:
        return ""
    base_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        rel,
        os.path.join("OMS WEB", rel),
        os.path.join(base_dir, rel),
        os.path.join(base_dir, "..", "OMS WEB", rel),
    ]
    for cand in candidates:
        if os.path.isfile(cand):
            return os.path.normpath(cand)
    return rel


def confirm_upload_modal_if_present(driver: Optional[webdriver.Chrome]) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var btns = Array.from(document.querySelectorAll('.modal button, .jconfirm button, button.btn-primary, button.btn-success'));
            var b = btns.find(function(x){ var t = (x.innerText||'').trim().toLowerCase(); return ['upload','submit','confirm','yes','save'].indexOf(t) !== -1; });
            if (b) b.click();
        """)
    except Exception:
        pass


def upload_file_safely(
    driver: Optional[webdriver.Chrome], sub_menu: Optional[UploadSubMenu], file_path: str, dry_run: bool = True
) -> bool:
    if not driver or sub_menu is None:
        return False
    if dry_run:
        return bool(driver.execute_script(
            "var t = arguments[0].toLowerCase(); return Array.from(document.querySelectorAll('a,li,button,span')).some(function(el){ return (el.innerText||'').trim().toLowerCase() === t; });",
            sub_menu.display_name,
        ))
    if not file_path or not os.path.isfile(file_path):
        print(f"[UPLOAD: ERROR] Local file does not exist: {file_path}")
        return False
    abs_path = os.path.abspath(file_path)
    try:
        open_upload_data_menu(driver)
        driver.execute_script("""
            var target = arguments[0].toLowerCase();
            var item = Array.from(document.querySelectorAll('ul.dropdown-menu li, .dropdown-item, a, li, button')).find(function(el){
                var t = (el.innerText||'').trim().toLowerCase();
                return t === target || t.indexOf(target) !== -1;
            });
            if (item) { if (window.jQuery) $(item).trigger('click'); item.click(); }
        """, sub_menu.display_name)
        time.sleep(0.5)
        inputs = driver.find_elements(By.XPATH, "//input[@type='file']")
        if not inputs:
            return False
        inp = inputs[0]
        driver.execute_script(
            "arguments[0].style.display='block';arguments[0].style.visibility='visible';arguments[0].style.opacity='1';arguments[0].removeAttribute('hidden');",
            inp,
        )
        inp.send_keys(abs_path)
        time.sleep(0.6)
        confirm_upload_modal_if_present(driver)
        time.sleep(2.0)
        dismiss_overlays(driver)
        return True
    except Exception as e:
        print(f"[UPLOAD: ERROR] {e}")
        return False


def upload_stock_file(driver: Optional[webdriver.Chrome], file_path: str, dry_run: bool = True) -> bool:
    return upload_file_safely(driver, UploadSubMenu.STOCK_FILE, file_path, dry_run)


def upload_stock_file_with_guard(
    driver: Optional[webdriver.Chrome], explicit_path: str = "", allow_live_upload: bool = False
) -> UploadExecutionResult:
    """Enforces path precedence (explicit -> STOCK_FILE_PATH env -> sample XML) and DEFAULT_NO_UPLOAD / CI dry-run guards."""
    resolved = (explicit_path or "").strip() or os.environ.get("STOCK_FILE_PATH", "").strip()
    if not resolved:
        resolved = resolve_default_sample_file_path(UploadSubMenu.STOCK_FILE)

    exists = bool(resolved and os.path.isfile(resolved))
    final_path = os.path.abspath(resolved) if exists else resolved

    if (DEFAULT_NO_UPLOAD and not allow_live_upload) or (config.IS_CI and not allow_live_upload):
        if driver is not None:
            upload_stock_file(driver, final_path, True)
        return UploadExecutionResult(False, False, True, final_path, "DRY_RUN_GUARD", "Stock file upload skipped because dry-run guard is active.")

    if not exists:
        return UploadExecutionResult(False, False, True, final_path, "FILE_NOT_FOUND", f"Stock file does not exist: {final_path}")

    if driver is None:
        return UploadExecutionResult(False, False, True, final_path, "OFFLINE_NO_DRIVER", "WebDriver is None; live Stock File upload skipped.")

    navigate_to_upload_data_page(driver)
    uploaded = upload_stock_file(driver, final_path, False)
    try:
        import oms_trade
        oms_trade.navigate_to_trade_page(driver, force=False)
    except Exception:
        pass
    return UploadExecutionResult(
        True, uploaded, False, final_path,
        "UPLOADED" if uploaded else "UPLOAD_FAILED",
        "Stock file uploaded and returned to Trade Portal." if uploaded else "Stock file upload failed.",
    )


def print_full_report(data: MenuData) -> None:
    print(f"\n[UPLOAD DATA AUDIT] ButtonFound={data.menu_button_found} | DropdownOpen={data.dropdown_opened} | SubMenus={len(data.sub_menus)} | Files={len(data.uploaded_files)}")
    for sm in data.sub_menus:
        print(f"  {sm.index}. {sm.name:<28} | Displayed={sm.is_displayed}")


def run_upload_console(driver: webdriver.Chrome) -> None:
    navigate_to_upload_data_page(driver)
    while True:
        print("\n--- UPLOAD DATA ENGINE (DEFAULT NO UPLOAD SAFE MODE) ---")
        print("  1. Inspect Menu & 9 Sub-Menus (Safe View) [Default]")
        print("  2. Upload File (User Choice 1-9 with Confirmation)")
        print("  0. Return to Main Menu")
        ch = config.read_line_with_timeout("Enter Choice (0-2, default 1): ", "1", 4.0).strip()
        if ch in ("0", "exit", "quit"):
            break
        elif ch == "2":
            for m in UploadSubMenu:
                print(f"  {m.index}. {m.display_name}")
            idx_str = config.read_line_with_timeout("Enter Sub-Menu (1-9, default 2=Stock File): ", "2", 4.0)
            sm = UploadSubMenu.from_index(int(idx_str)) if idx_str.isdigit() else UploadSubMenu.STOCK_FILE
            def_path = resolve_default_sample_file_path(sm)
            path = config.read_line_with_timeout(f"File path (default {def_path}): ", def_path, 4.0)
            conf = config.read_line_with_timeout("Confirm live upload? (1=YES / 0=NO, default 0): ", "0", 4.0)
            if conf == "1" and path:
                upload_file_safely(driver, sm, path, dry_run=False)
            else:
                print("[CANCELLED] Dry-run guard kept active.")
        else:
            print_full_report(capture_menu_and_submenu_data(driver))
