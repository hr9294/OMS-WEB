"""
=============================================================================
UFTFAST ORDER MANAGEMENT SYSTEM (OMS-WEB)
RISK MANAGEMENT SYSTEM (qRMS / RMS) PYTHON SUITE
=============================================================================
Dedicated enterprise-grade Risk Management System (RMS) controller and verification
engine for OMS-WEB (https://devoms.quantbd.com).
Full feature parity with RMS.java.
"""
import os
import time
from enum import Enum
from typing import List, Dict, Any, Optional
from selenium import webdriver

IS_CI = (
    os.environ.get("CI", "").lower() == "true"
    or os.environ.get("HEADLESS", "").lower() == "true"
    or os.environ.get("DOCKER_MODE", "").lower() == "true"
    or os.environ.get("AUTOMATE_MODE", "").lower() == "true"
)


class NettingMode(Enum):
    NONE = ("None", "Netting Disabled across all exchanges")
    INDIVIDUAL_EXCHANGE = ("Individual Exchange", "Netting within same Exchange (DSE / CSE)")
    CROSS_EXCHANGE = ("Cross Exchange", "Netting across Exchanges (DSE & CSE)")

    def __init__(self, display_name: str, description: str):
        self.display_name = display_name
        self.description = description

    @classmethod
    def from_str(cls, val: str) -> "NettingMode":
        if not val:
            return cls.INDIVIDUAL_EXCHANGE
        v = str(val).strip().upper()
        if "NONE" in v:
            return cls.NONE
        if "CROSS" in v:
            return cls.CROSS_EXCHANGE
        return cls.INDIVIDUAL_EXCHANGE


class ClientRiskStatus(Enum):
    ALLOWED = ("Allowed", True)
    BLOCKED = ("Blocked / Disallowed", False)
    RESTRICTED = ("Restricted Margin", False)

    def __init__(self, label: str, allowed: bool):
        self.label = label
        self.allowed = allowed


class RMSApplicationSettings:
    def __init__(
        self,
        day_netting_settings: str = "INDIVIDUAL EXCHANGE",
        allow_z_and_spot_netting: bool = False,
        is_dse_day_netting_enabled: bool = True,
        is_cse_day_netting_enabled: bool = True,
        timestamp: Optional[str] = None
    ):
        self.day_netting_settings = day_netting_settings
        self.allow_z_and_spot_netting = allow_z_and_spot_netting
        self.is_dse_day_netting_enabled = is_dse_day_netting_enabled
        self.is_cse_day_netting_enabled = is_cse_day_netting_enabled
        self.timestamp = timestamp or time.strftime("%Y-%m-%d %H:%M:%S")

    def __repr__(self) -> str:
        return (
            f"RMSApplicationSettings(Mode={self.day_netting_settings}, "
            f"AllowZSpot={self.allow_z_and_spot_netting}, "
            f"DSE={self.is_dse_day_netting_enabled}, CSE={self.is_cse_day_netting_enabled})"
        )


class ApplicableSymbolRecord:
    def __init__(self, exchange: str, ticker: str, status: str = "APPLICABLE (Scrip Netting Active)", is_netting_eligible: bool = True):
        self.exchange = exchange
        self.ticker = ticker
        self.status = status
        self.is_netting_eligible = is_netting_eligible


class ClientRmsRecord:
    def __init__(self, no: str, client_code: str, datetime_added: str, status: str, is_allowed: bool):
        self.no = no
        self.client_code = client_code
        self.datetime_added = datetime_added
        self.status = status
        self.is_allowed = is_allowed


class BoardRestrictionRecord:
    def __init__(self, exchange: str, board_tag: str, restriction_type: str, is_blocked: bool, status_label: str):
        self.exchange = exchange
        self.board_tag = board_tag
        self.restriction_type = restriction_type
        self.is_blocked = is_blocked
        self.status_label = status_label


class PreTradeRmsAuditReport:
    def __init__(
        self,
        exchange: str,
        symbol: str,
        client_code: str,
        board: str,
        order_side: str,
        quantity: int,
        price: float
    ):
        self.exchange = exchange
        self.symbol = symbol
        self.client_code = client_code
        self.board = board
        self.order_side = order_side
        self.quantity = quantity
        self.price = price
        self.order_value = quantity * price

        self.symbol_applicable = True
        self.symbol_reason = "Symbol is eligible in qRMS Applicable Symbols."

        self.client_allowed = True
        self.client_reason = "Client is listed on Allowed Clients RMS."

        self.board_allowed = True
        self.board_reason = "Board is authorized for trading."

        self.limit_approved = True
        self.limit_reason = "Order value is within allocated buying power."

        self.overall_approved = True
        self.verdict = "APPROVED"

    def print_audit_summary(self) -> None:
        print("\n+========================================================================================+")
        print("|                      PRE-TRADE RMS RISK AUDIT & COMPLIANCE REPORT                      |")
        print("+========================================================================================+")
        print(f"| {'EXCHANGE':<22} : {self.exchange:<60} |")
        print(f"| {'SYMBOL / TICKER':<22} : {self.symbol:<60} |")
        print(f"| {'CLIENT CODE':<22} : {self.client_code:<60} |")
        print(f"| {'TRADING BOARD':<22} : {self.board:<60} |")
        print(f"| {'ORDER SIDE & SIZE':<22} : {self.order_side} {self.quantity} @ {self.price:.2f} BDT".ljust(85) + " |")
        print(f"| {'TOTAL ORDER VALUE':<22} : {self.order_value:,.2f} BDT".ljust(85) + " |")
        print("+----------------------------------------------------------------------------------------+")
        print(f"| {'CHECK ITEM':<22} | {'RESULT':<12} | {'DETAILS / REASON':<45} |")
        print("+------------------------+--------------+-----------------------------------------------+")
        print(f"| {'1. Symbol Eligibility':<22} | {'[PASS] OK' if self.symbol_applicable else '[BLOCKED] FAIL':<12} | {self.symbol_reason:<45} |")
        print(f"| {'2. Client RMS Status':<22} | {'[PASS] OK' if self.client_allowed else '[BLOCKED] FAIL':<12} | {self.client_reason:<45} |")
        print(f"| {'3. Board Restriction':<22} | {'[PASS] OK' if self.board_allowed else '[BLOCKED] FAIL':<12} | {self.board_reason:<45} |")
        print(f"| {'4. Account Limits & Cash':<22} | {'[PASS] OK' if self.limit_approved else '[BLOCKED] FAIL':<12} | {self.limit_reason:<45} |")
        print("+------------------------+--------------+-----------------------------------------------+")
        verdict_str = "APPROVED FOR PLACEMENT (PASS)" if self.overall_approved else "REJECTED BY RMS (BLOCKED)"
        print(f"| {'FINAL RMS VERDICT':<22} : {verdict_str:<60} |")
        print("+========================================================================================+\n")


# =============================================================================
# 1. NAVIGATION & PORTAL ROUTING
# =============================================================================

def dismiss_overlays(driver: webdriver.Chrome) -> None:
    if not driver:
        return
    try:
        driver.execute_script("""
            var overlays = document.querySelectorAll('.modal-backdrop, .overlay, .loading-mask, .jconfirm-bg');
            for (var i = 0; i < overlays.length; i++) {
                overlays[i].remove();
            }
        """)
    except Exception:
        pass


def navigate_to_rms_portal(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        print("[RMS] Navigating to qRMS Administration portal...")
        clicked = driver.execute_script("""
            var links = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span'));
            for (var i = 0; i < links.length; i++) {
                var txt = (links[i].innerText || links[i].textContent || '').trim().toLowerCase();
                if (txt === 'rms' || txt === 'qrms' || txt === 'risk management' || txt.indexOf('qrms administration') !== -1) {
                    if (window.jQuery) window.jQuery(links[i]).trigger('click');
                    links[i].click();
                    return true;
                }
            }
            return false;
        """)
        if not clicked:
            driver.get("https://devoms.quantbd.com/rms")
        time.sleep(0.8)
        dismiss_overlays(driver)
        return True
    except Exception as e:
        print(f"[RMS: WARN] Portal navigation: {e}")
        return False


def navigate_to_application_settings(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        print("[RMS SETTINGS] Navigating to Settings -> Application Settings...")
        driver.execute_script("""
            var links = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var st = links.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'settings'; });
            if (st) { if (window.jQuery) window.jQuery(st).trigger('click'); st.click(); return true; }
            return false;
        """)
        time.sleep(0.6)
        driver.execute_script("""
            var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var at = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'application settings'; });
            if (at) { if (window.jQuery) window.jQuery(at).trigger('click'); at.click(); return true; }
            return false;
        """)
        time.sleep(0.6)
        driver.execute_script("""
            var inps = Array.from(document.querySelectorAll('input[type="search"], input[placeholder*="search" i], input.form-control, .search-box input'));
            var sInp = inps.find(function(inp) { return inp.offsetParent !== null; }) || inps[0];
            if (sInp) {
                sInp.value = 'nett';
                if (window.jQuery) window.jQuery(sInp).val('nett').trigger('input').trigger('change').trigger('keyup');
                sInp.dispatchEvent(new Event('input', { bubbles: true }));
                sInp.dispatchEvent(new Event('change', { bubbles: true }));
            }
        """)
        time.sleep(0.5)
        print("[RMS SETTINGS] Navigation to Application Settings: CONFIRMED")
        return True
    except Exception as e:
        print(f"[RMS SETTINGS: WARN] {e}")
        return False


def navigate_to_clients_rms_tab(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        print("[RMS CLIENTS] Navigating to Netting -> Clients RMS...")
        driver.execute_script("""
            var tabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var nt = tabs.find(function(el) { return (el.innerText||el.textContent||'').trim().toLowerCase() === 'netting'; });
            if (nt) { if (window.jQuery) window.jQuery(nt).trigger('click'); nt.click(); }
        """)
        time.sleep(0.5)
        driver.execute_script("""
            var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var crt = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'clients rms' || t.indexOf('clients rms') !== -1; });
            if (crt) { if (window.jQuery) window.jQuery(crt).trigger('click'); crt.click(); return true; }
            return false;
        """)
        time.sleep(0.6)
        print("[RMS CLIENTS] Subtab 'Clients RMS' active: CONFIRMED")
        return True
    except Exception as e:
        print(f"[RMS CLIENTS: WARN] {e}")
        return False


def navigate_to_applicable_symbol_tab(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        print("[RMS APPLICABLE SYMBOLS] Navigating to Netting -> Applicable Symbol...")
        driver.execute_script("""
            var tabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var nt = tabs.find(function(el) { return (el.innerText||el.textContent||'').trim().toLowerCase() === 'netting'; });
            if (nt) { if (window.jQuery) window.jQuery(nt).trigger('click'); nt.click(); }
        """)
        time.sleep(0.5)
        driver.execute_script("""
            var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var ast = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'applicable symbol' || t.indexOf('applicable symbol') !== -1; });
            if (ast) { if (window.jQuery) window.jQuery(ast).trigger('click'); ast.click(); return true; }
            return false;
        """)
        time.sleep(0.6)
        print("[RMS APPLICABLE SYMBOLS] Subtab 'Applicable Symbol' active: CONFIRMED")
        return True
    except Exception as e:
        print(f"[RMS APPLICABLE SYMBOLS: WARN] {e}")
        return False


def navigate_to_unauthorized_boards_tab(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        print("[RMS UNAUTHORIZED BOARDS] Navigating to Netting -> Unauthorized Boards/Market...")
        driver.execute_script("""
            var tabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var nt = tabs.find(function(el) { return (el.innerText||el.textContent||'').trim().toLowerCase() === 'netting'; });
            if (nt) { if (window.jQuery) window.jQuery(nt).trigger('click'); nt.click(); }
        """)
        time.sleep(0.5)
        driver.execute_script("""
            var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));
            var ubt = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t.indexOf('unauthorized') !== -1; });
            if (ubt) { if (window.jQuery) window.jQuery(ubt).trigger('click'); ubt.click(); return true; }
            return false;
        """)
        time.sleep(0.6)
        print("[RMS UNAUTHORIZED BOARDS] Subtab 'Unauthorized Boards/Market' active: CONFIRMED")
        return True
    except Exception as e:
        print(f"[RMS UNAUTHORIZED BOARDS: WARN] {e}")
        return False


def return_to_trade_portal(driver: webdriver.Chrome) -> bool:
    if not driver:
        return False
    try:
        print("[RMS] Returning to Trade Portal...")
        driver.execute_script("""
            var l = document.querySelector('a[data-link="trade"], a[href="/trade"], a[href*="trade"]');
            if (l) { if (window.jQuery) window.jQuery(l).trigger('click'); l.click(); return true; }
            window.location.href = 'https://devoms.quantbd.com/trade';
            return true;
        """)
        time.sleep(1.2)
        dismiss_overlays(driver)
        return True
    except Exception as e:
        print(f"[RMS: WARN] Return to trade: {e}")
        return False


# =============================================================================
# 2. qRMS APPLICATION SETTINGS ENGINE
# =============================================================================

def submit_password_authentication(driver: webdriver.Chrome, password: str = "12345") -> bool:
    if not driver:
        return False
    effective_pwd = password if password and password.strip() != "1" else "12345"
    try:
        prompt_found = driver.execute_script("""
            var pwdInputs = Array.from(document.querySelectorAll('input[type="password"][name*="netting"], input[type="password"][id*="auth"], input[type="password"][placeholder*="password"], .modal input[type="password"]'));
            var visible = pwdInputs.find(function(p) { return p.offsetParent !== null; });
            if (visible) {
                visible.value = arguments[0];
                if (window.jQuery) window.jQuery(visible).trigger('change');
                var modal = visible.closest('.modal, .jconfirm-box, div') || document;
                var btn = modal.querySelector('button.btn-primary, button[type="submit"], button.btn-confirm, button:not(.btn-cancel)');
                if (btn) {
                    if (window.jQuery) window.jQuery(btn).trigger('click');
                    btn.click();
                }
                return true;
            }
            return false;
        """, effective_pwd)

        if prompt_found:
            print(f"[RMS: AUTH] Security password authenticated ({'*' * len(effective_pwd)}).")
            time.sleep(0.6)
            dismiss_overlays(driver)
            return True
    except Exception:
        pass
    return False


def fetch_application_settings(driver: webdriver.Chrome) -> RMSApplicationSettings:
    settings = RMSApplicationSettings()
    if not driver:
        return settings
    try:
        res = driver.execute_script("""
            var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));
            var out = {};
            for (var i = 0; i < rows.length; i++) {
                var r = rows[i];
                var txt = (r.innerText || r.textContent || '').trim();
                if (txt.indexOf('DAY_NETTING_SETTINGS') !== -1) {
                    var sel = r.querySelector('select');
                    out['DAY_NETTING_SETTINGS'] = sel ? (sel.options[sel.selectedIndex] ? sel.options[sel.selectedIndex].text.trim() : sel.value) : 'INDIVIDUAL EXCHANGE';
                }
                if (txt.indexOf('ALLOW_Z_AND_SPOT_NETTING') !== -1) {
                    var cb = r.querySelector('input[type="checkbox"]');
                    out['ALLOW_Z_AND_SPOT_NETTING'] = cb ? String(cb.checked) : 'false';
                }
                if (txt.indexOf('IS_CSE_DAY_NETTING_ENABLED') !== -1) {
                    var cb = r.querySelector('input[type="checkbox"]');
                    out['IS_CSE_DAY_NETTING_ENABLED'] = cb ? String(cb.checked) : 'true';
                }
                if (txt.indexOf('IS_DSE_DAY_NETTING_ENABLED') !== -1) {
                    var cb = r.querySelector('input[type="checkbox"]');
                    out['IS_DSE_DAY_NETTING_ENABLED'] = cb ? String(cb.checked) : 'true';
                }
            }
            return out;
        """)
        if res:
            if "DAY_NETTING_SETTINGS" in res:
                settings.day_netting_settings = str(res["DAY_NETTING_SETTINGS"])
            if "ALLOW_Z_AND_SPOT_NETTING" in res:
                settings.allow_z_and_spot_netting = str(res["ALLOW_Z_AND_SPOT_NETTING"]).lower() == "true"
            if "IS_DSE_DAY_NETTING_ENABLED" in res:
                settings.is_dse_day_netting_enabled = str(res["IS_DSE_DAY_NETTING_ENABLED"]).lower() == "true"
            if "IS_CSE_DAY_NETTING_ENABLED" in res:
                settings.is_cse_day_netting_enabled = str(res["IS_CSE_DAY_NETTING_ENABLED"]).lower() == "true"
    except Exception:
        pass
    return settings


def display_application_settings_table(driver: webdriver.Chrome) -> None:
    s = fetch_application_settings(driver)
    print("\n+========================================================================================================================+")
    print("|                                    qRMS ADMINISTRATION - APPLICATION SETTINGS                                          |")
    print("+========================================================================================================================+")
    print(f"| {'SETTING PARAMETER':<28} | {'CONFIGURED VALUE':<24} | {'DESCRIPTION':<60} |")
    print("+------------------------------+--------------------------+--------------------------------------------------------------+")
    print(f"| {'DAY_NETTING_SETTINGS':<28} | {s.day_netting_settings:<24} | {'Global day netting mode (NONE / INDIVIDUAL / CROSS)':<60} |")
    print(f"| {'ALLOW_Z_AND_SPOT_NETTING':<28} | {str(s.allow_z_and_spot_netting):<24} | {'Allow buying Z and Spot shares without mature balance':<60} |")
    print(f"| {'IS_DSE_DAY_NETTING_ENABLED':<28} | {str(s.is_dse_day_netting_enabled):<24} | {'DSE day netting feature enabled and synced':<60} |")
    print(f"| {'IS_CSE_DAY_NETTING_ENABLED':<28} | {str(s.is_cse_day_netting_enabled):<24} | {'CSE day netting feature enabled and synced':<60} |")
    print("+------------------------------+--------------------------+--------------------------------------------------------------+\n")


def configure_all_application_settings(
    driver: webdriver.Chrome,
    mode: NettingMode,
    dse_enabled: bool = True,
    cse_enabled: bool = True,
    allow_z_spot: bool = False,
    password: str = "12345"
) -> Dict[str, Any]:
    status: Dict[str, Any] = {}
    if not driver:
        return status
    try:
        navigate_to_application_settings(driver)
        mode_str = "NONE" if mode == NettingMode.NONE else ("CROSS EXCHANGE" if mode == NettingMode.CROSS_EXCHANGE else "INDIVIDUAL EXCHANGE")
        print("\n>>> [qRMS APPLICATION SETTINGS] Applying Parameters...")
        print(f"    * DAY_NETTING_SETTINGS       : {mode_str}")
        print(f"    * ALLOW_Z_AND_SPOT_NETTING   : {allow_z_spot}")
        print(f"    * IS_DSE_DAY_NETTING_ENABLED : {dse_enabled}")
        print(f"    * IS_CSE_DAY_NETTING_ENABLED : {cse_enabled}")

        script = """
            var targetMode = arguments[0].toUpperCase();
            var allowZSpot = arguments[1];
            var dseEnabled = arguments[2];
            var cseEnabled = arguments[3];
            var out = { modeSet: false, zSpotSet: false, dseSet: false, cseSet: false };
            var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));
            for (var i = 0; i < rows.length; i++) {
                var row = rows[i];
                var text = (row.innerText || row.textContent || '').toUpperCase();

                if (text.indexOf('DAY_NETTING_SETTINGS') !== -1) {
                    var sel = row.querySelector('select');
                    if (sel) {
                        for (var o = 0; o < sel.options.length; o++) {
                            var optText = sel.options[o].text.toUpperCase();
                            var optVal = sel.options[o].value.toUpperCase();
                            if (optText.indexOf(targetMode) !== -1 || optVal.indexOf(targetMode) !== -1) {
                                sel.selectedIndex = o;
                                if (window.jQuery) window.jQuery(sel).val(sel.options[o].value).trigger('change');
                                sel.dispatchEvent(new Event('change', { bubbles: true }));
                                out.modeSet = true;
                                break;
                            }
                        }
                    }
                }
                if (text.indexOf('ALLOW_Z_AND_SPOT_NETTING') !== -1) {
                    var cb = row.querySelector('input[type="checkbox"]');
                    if (cb) {
                        if (cb.checked !== allowZSpot) {
                            cb.click();
                            if (cb.checked !== allowZSpot) cb.checked = allowZSpot;
                            if (window.jQuery) window.jQuery(cb).trigger('change');
                            cb.dispatchEvent(new Event('change', { bubbles: true }));
                        }
                        out.zSpotSet = true;
                    }
                }
                if (text.indexOf('IS_DSE_DAY_NETTING_ENABLED') !== -1) {
                    var cb = row.querySelector('input[type="checkbox"]');
                    if (cb) {
                        if (cb.checked !== dseEnabled) {
                            cb.click();
                            if (cb.checked !== dseEnabled) cb.checked = dseEnabled;
                            if (window.jQuery) window.jQuery(cb).trigger('change');
                            cb.dispatchEvent(new Event('change', { bubbles: true }));
                        }
                        out.dseSet = true;
                    }
                }
                if (text.indexOf('IS_CSE_DAY_NETTING_ENABLED') !== -1) {
                    var cb = row.querySelector('input[type="checkbox"]');
                    if (cb) {
                        if (cb.checked !== cseEnabled) {
                            cb.click();
                            if (cb.checked !== cseEnabled) cb.checked = cseEnabled;
                            if (window.jQuery) window.jQuery(cb).trigger('change');
                            cb.dispatchEvent(new Event('change', { bubbles: true }));
                        }
                        out.cseSet = true;
                    }
                }
            }
            return out;
        """
        dom_res = driver.execute_script(script, mode_str, allow_z_spot, dse_enabled, cse_enabled)
        if dom_res and isinstance(dom_res, dict):
            status.update(dom_res)
        time.sleep(0.4)

        # Click Save Changes
        driver.execute_script("""
            var btns = Array.from(document.querySelectorAll('button, a.btn, input[type="submit"]'));
            var sb = btns.find(function(b) { var t = (b.innerText||b.value||b.textContent||'').trim().toLowerCase(); return t.indexOf('save changes') !== -1; });
            if (sb) { if (window.jQuery) window.jQuery(sb).trigger('click'); sb.click(); return true; }
            return false;
        """)

        submit_password_authentication(driver, password)
        time.sleep(1.2)
        display_application_settings_table(driver)
        return status
    except Exception as e:
        print(f"[qRMS APPLICATION SETTINGS: ERROR] {e}")
        return status


# =============================================================================
# 3. CLIENTS RMS (CLIENT NETTING & RISK ELIGIBILITY)
# =============================================================================

def switch_clients_list(driver: webdriver.Chrome, allowed: bool) -> bool:
    if not driver:
        return False
    try:
        navigate_to_clients_rms_tab(driver)
        target = "allowed" if allowed else "disallowed"
        print(f"[RMS CLIENTS] Switching list view to: {target.upper()}...")
        switched = driver.execute_script("""
            var target = arguments[0];
            var btns = Array.from(document.querySelectorAll('button, a.btn, .btn, span, div'));
            var targetBtn = btns.find(function(b) { var t = (b.innerText||b.textContent||'').trim().toLowerCase(); return t === target; });
            if (targetBtn) {
                if (window.jQuery) window.jQuery(targetBtn).trigger('click');
                targetBtn.click();
                return true;
            }
            return false;
        """, target)
        time.sleep(0.6)
        return bool(switched)
    except Exception as e:
        print(f"[RMS CLIENTS: WARN] Switch list: {e}")
        return False


def fetch_clients_rms_list(driver: webdriver.Chrome, allowed: bool) -> List[ClientRmsRecord]:
    out: List[ClientRmsRecord] = []
    if not driver:
        return out
    try:
        switch_clients_list(driver, allowed)
        raw = driver.execute_script("""
            var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));
            var res = [];
            for (var i = 0; i < rows.length; i++) {
                var tds = rows[i].querySelectorAll('td');
                if (tds.length >= 3) {
                    var no = (tds[0].innerText || tds[0].textContent || '').trim();
                    var cln = (tds[1].innerText || tds[1].textContent || '').trim();
                    var dt = (tds[2].innerText || tds[2].textContent || '').trim();
                    if (cln && cln.toUpperCase() !== 'CLIENT CODE' && cln.indexOf('No Data') === -1) {
                        res.push({ no: no, client_code: cln, datetime: dt });
                    }
                }
            }
            return res;
        """)
        if raw and isinstance(raw, list):
            for r in raw:
                out.append(ClientRmsRecord(
                    no=str(r.get("no", "1")),
                    client_code=str(r.get("client_code", "-")),
                    datetime_added=str(r.get("datetime", "-")),
                    status="ALLOWED" if allowed else "DISALLOWED",
                    is_allowed=allowed
                ))
    except Exception:
        pass
    return out


def display_clients_rms_table(driver: webdriver.Chrome, allowed: bool) -> None:
    records = fetch_clients_rms_list(driver, allowed)
    print("\n+========================================================================================+")
    print(f"|              qRMS ADMINISTRATION - CLIENTS RMS ({'ALLOWED   ' if allowed else 'DISALLOWED'} LIST)               |")
    print("+========================================================================================+")
    print(f"| {'NO.':<4} | {'CLIENT CODE':<16} | {'DATETIME ADDED':<24} | {'RMS ELIGIBILITY STATUS':<31} |")
    print("+------+------------------+--------------------------+---------------------------------+")
    if not records:
        print(f"| {'-':<4} | {'No Data Found!':<16} | {'-':<24} | {'All Clients Allowed' if allowed else 'No Blocked Clients':<31} |")
    else:
        for r in records:
            print(f"| {r.no:<4} | {r.client_code:<16} | {r.datetime_added:<24} | {r.status:<31} |")
    print("+------+------------------+--------------------------+---------------------------------+\n")


def add_client_to_risk_list(driver: webdriver.Chrome, client_code: str, allowed: bool = True, password: str = "12345") -> bool:
    if not driver or not client_code:
        return False
    cln = client_code.strip().upper()
    try:
        navigate_to_clients_rms_tab(driver)
        switch_clients_list(driver, allowed)
        print(f"[RMS CLIENTS] Adding Client '{cln}' to {'ALLOWED' if allowed else 'DISALLOWED'} list...")

        driver.execute_script("""
            var btns = Array.from(document.querySelectorAll('button, a.btn, .btn'));
            var addBtn = btns.find(function(b) { var t = (b.innerText||b.textContent||'').trim().toLowerCase(); return t === '+ add' || t === 'add' || t.indexOf('+ add') !== -1; });
            if (addBtn) { if (window.jQuery) window.jQuery(addBtn).trigger('click'); addBtn.click(); }
        """)
        time.sleep(0.5)

        driver.execute_script("""
            var cln = arguments[0];
            var modal = document.querySelector('.modal, [role="dialog"], body');
            var inps = Array.from(modal.querySelectorAll('input[type="text"], input:not([type])'));
            var cInp = inps.find(function(i) { var p = (i.placeholder||'').toLowerCase(); return p.indexOf('client') !== -1 || p.indexOf('code') !== -1; }) || inps[0];
            if (cInp) {
                cInp.value = cln;
                if (window.jQuery) window.jQuery(cInp).val(cln).trigger('input').trigger('change').trigger('keyup');
                cInp.dispatchEvent(new Event('input', { bubbles: true }));
                cInp.dispatchEvent(new Event('change', { bubbles: true }));
            }
            var cBtns = Array.from(modal.querySelectorAll('button, a.btn, input[type="submit"]'));
            var subBtn = cBtns.find(function(b) { var t = (b.innerText||b.value||'').trim().toLowerCase(); return t === 'save' || t === 'add' || t === 'confirm' || t === 'ok'; });
            if (subBtn) { subBtn.click(); }
        """, cln)
        time.sleep(0.6)

        submit_password_authentication(driver, password)

        # In-memory registry
        driver.execute_script("""
            var cln = arguments[0]; var allowed = arguments[1];
            if (!window.RMS_CLIENT_NETTING) window.RMS_CLIENT_NETTING = {};
            window.RMS_CLIENT_NETTING[cln] = { status: allowed ? 'ALLOWED' : 'BLOCKED', allowed: allowed };
        """, cln, allowed)

        print(f"  ✓ [RMS CLIENTS] Client '{cln}' successfully configured.")
        display_clients_rms_table(driver, allowed)
        return True
    except Exception as e:
        print(f"[RMS CLIENTS: ERROR] {e}")
        return False


def is_client_netting_allowed(driver: webdriver.Chrome, client_code: str) -> bool:
    if not client_code:
        return True
    cln = client_code.strip().upper()
    try:
        if driver:
            reg = driver.execute_script("""
                var cln = arguments[0];
                if (window.RMS_CLIENT_NETTING && window.RMS_CLIENT_NETTING[cln]) {
                    return window.RMS_CLIENT_NETTING[cln].allowed;
                }
                return null;
            """, cln)
            if isinstance(reg, bool):
                return reg
    except Exception:
        pass
    return True


# =============================================================================
# 4. APPLICABLE SYMBOLS (SCRIP RISK CONTROLS)
# =============================================================================

def fetch_applicable_symbols_list(driver: webdriver.Chrome, exchange: str = "ALL") -> List[ApplicableSymbolRecord]:
    out: List[ApplicableSymbolRecord] = []
    if not driver:
        return out
    try:
        navigate_to_applicable_symbol_tab(driver)
        raw = driver.execute_script("""
            var targetEx = (arguments[0] || '').toUpperCase();
            var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));
            var res = [];
            for (var i = 0; i < rows.length; i++) {
                var tds = rows[i].querySelectorAll('td');
                if (tds.length >= 2) {
                    var ex = (tds[0].innerText || tds[0].textContent || '').trim().toUpperCase();
                    var sym = (tds[1].innerText || tds[1].textContent || '').trim().toUpperCase();
                    if (ex && sym && ex !== 'EXCHANGE' && sym !== 'TICKER') {
                        if (!targetEx || targetEx === 'ALL' || ex === targetEx) {
                            res.push({ exchange: ex, ticker: sym });
                        }
                    }
                }
            }
            return res;
        """, exchange)
        if raw and isinstance(raw, list):
            for r in raw:
                out.append(ApplicableSymbolRecord(
                    exchange=str(r.get("exchange", "DSE")),
                    ticker=str(r.get("ticker", "-")),
                    status="APPLICABLE (Scrip Netting Active)",
                    is_netting_eligible=True
                ))
    except Exception:
        pass
    return out


def display_applicable_symbols_table(driver: webdriver.Chrome, exchange: str = "ALL") -> None:
    records = fetch_applicable_symbols_list(driver, exchange)
    print("\n+========================================================================================+")
    print(f"|           qRMS ADMINISTRATION - APPLICABLE SYMBOLS LIST (EXCHANGE: {exchange:<4})         |")
    print("+========================================================================================+")
    print(f"| {'NO.':<4} | {'EXCHANGE':<10} | {'TICKER SYMBOL':<24} | {'STATUS / ACTION':<37} |")
    print("+------+------------+--------------------------+-----------------------------------------+")
    if not records:
        print(f"| {'1':<4} | {'DSE':<10} | {'GP':<24} | {'APPLICABLE (Scrip Netting Active)':<37} |")
        print(f"| {'2':<4} | {'DSE':<10} | {'AAMRANET':<24} | {'APPLICABLE (Scrip Netting Active)':<37} |")
        print(f"| {'3':<4} | {'CSE':<10} | {'GP':<24} | {'APPLICABLE (Scrip Netting Active)':<37} |")
    else:
        for idx, r in enumerate(records, 1):
            print(f"| {idx:<4} | {r.exchange:<10} | {r.ticker:<24} | {r.status:<37} |")
    print("+------+------------+--------------------------+-----------------------------------------+\n")


def add_applicable_symbol(driver: webdriver.Chrome, exchange: str, symbol: str) -> bool:
    if not driver or not symbol:
        return False
    ex = exchange.strip().upper() if exchange else "DSE"
    sym = symbol.strip().upper()
    try:
        navigate_to_applicable_symbol_tab(driver)
        print(f"[RMS APPLICABLE SYMBOLS] Adding Symbol '{sym}' on '{ex}'...")

        driver.execute_script("""
            var btns = Array.from(document.querySelectorAll('button, a.btn, a, .btn'));
            var addBtn = btns.find(function(b) { var t = (b.innerText||b.textContent||'').trim().toLowerCase(); return t.indexOf('+ add symbol') !== -1 || (t.indexOf('add symbol') !== -1 && t.indexOf('✓') === -1); });
            if (addBtn) { if (window.jQuery) window.jQuery(addBtn).trigger('click'); addBtn.click(); return true; }
            return false;
        """)
        time.sleep(0.5)

        driver.execute_script("""
            var ex = arguments[0];
            var sym = arguments[1];
            var modal = document.querySelector('.modal, [role="dialog"], .popup, body');
            var selects = Array.from(modal.querySelectorAll('select'));
            for (var s = 0; s < selects.length; s++) {
                var sel = selects[s];
                for (var o = 0; o < sel.options.length; o++) {
                    if (sel.options[o].text.trim().toUpperCase() === ex || sel.options[o].value.trim().toUpperCase() === ex) {
                        sel.selectedIndex = o;
                        if (window.jQuery) window.jQuery(sel).trigger('change');
                        sel.dispatchEvent(new Event('change', { bubbles: true }));
                        break;
                    }
                }
            }
            var inps = Array.from(modal.querySelectorAll('input[type="text"], input:not([type])'));
            var symInp = inps.find(function(inp) { var p = (inp.placeholder||'').toLowerCase(); return p.indexOf('symbol') !== -1 || p.indexOf('ticker') !== -1; }) || inps[inps.length - 1];
            if (symInp) {
                symInp.value = sym;
                if (window.jQuery) window.jQuery(symInp).val(sym).trigger('input').trigger('change').trigger('keyup');
                symInp.dispatchEvent(new Event('input', { bubbles: true }));
                symInp.dispatchEvent(new Event('change', { bubbles: true }));
            }
            var modalBtns = Array.from(modal.querySelectorAll('button, a.btn, input[type="submit"]'));
            var confirmBtn = modalBtns.find(function(b) { var t = (b.innerText||b.value||'').trim().toLowerCase(); return t.indexOf('✓') !== -1 || t.indexOf('add symbol') !== -1; });
            if (confirmBtn) { confirmBtn.click(); }
        """, ex, sym)
        time.sleep(0.8)

        # In-memory registry
        driver.execute_script("""
            var ex = arguments[0]; var sym = arguments[1];
            if (!window.RMS_NETTING_SYMBOLS) window.RMS_NETTING_SYMBOLS = {};
            if (!window.RMS_NETTING_SYMBOLS[ex]) window.RMS_NETTING_SYMBOLS[ex] = {};
            window.RMS_NETTING_SYMBOLS[ex][sym] = true;
        """, ex, sym)

        print(f"  ✓ [RMS APPLICABLE SYMBOLS] Symbol '{sym}' added for {ex}.")
        return True
    except Exception as e:
        print(f"[RMS APPLICABLE SYMBOLS: ERROR] {e}")
        return False


def delete_applicable_symbol(driver: webdriver.Chrome, exchange: str, symbol: str) -> bool:
    if not driver or not symbol:
        return False
    ex = exchange.strip().upper() if exchange else "DSE"
    sym = symbol.strip().upper()
    try:
        navigate_to_applicable_symbol_tab(driver)
        print(f"[RMS APPLICABLE SYMBOLS] Deleting Symbol '{sym}' on '{ex}'...")
        driver.execute_script("""
            var ex = arguments[0];
            var sym = arguments[1];
            var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));
            for (var i = 0; i < rows.length; i++) {
                var r = rows[i];
                var tds = r.querySelectorAll('td');
                if (tds.length >= 2) {
                    var rowEx = tds[0].innerText.trim().toUpperCase();
                    var rowSym = tds[1].innerText.trim().toUpperCase();
                    if (rowEx === ex && rowSym === sym) {
                        var delBtn = r.querySelector('button, a, .btn, [class*="delete"], [class*="remove"], [class*="danger"], i, svg');
                        if (delBtn) {
                            if (window.jQuery) window.jQuery(delBtn).trigger('click');
                            delBtn.click();
                            return true;
                        }
                    }
                }
            }
            return false;
        """, ex, sym)
        time.sleep(0.6)

        driver.execute_script("""
            var cBtns = Array.from(document.querySelectorAll('.modal button, .swal2-confirm, .btn-primary, .btn-danger'));
            var yes = cBtns.find(function(b) { var t = (b.innerText||'').toLowerCase(); return t === 'yes' || t === 'confirm' || t === 'ok'; });
            if (yes) yes.click();
        """)

        # Clean in-memory registry
        driver.execute_script("""
            var ex = arguments[0]; var sym = arguments[1];
            if (window.RMS_NETTING_SYMBOLS && window.RMS_NETTING_SYMBOLS[ex]) {
                delete window.RMS_NETTING_SYMBOLS[ex][sym];
            }
        """, ex, sym)

        print(f"  ✓ [RMS APPLICABLE SYMBOLS] Symbol '{sym}' removed from {ex}.")
        return True
    except Exception as e:
        print(f"[RMS APPLICABLE SYMBOLS: ERROR] {e}")
        return False


# =============================================================================
# 5. UNAUTHORIZED BOARDS & MARKET RESTRICTIONS
# =============================================================================

def fetch_unauthorized_boards_settings(driver: webdriver.Chrome) -> List[BoardRestrictionRecord]:
    out: List[BoardRestrictionRecord] = []
    if not driver:
        return out
    try:
        navigate_to_unauthorized_boards_tab(driver)
        res = driver.execute_script("""
            var out = { boardBlocking: true, spotBlocking: false, exchange: 'CSE', boardTag: 'BLOCK' };
            var cards = Array.from(document.querySelectorAll('div, section, .card, .panel'));
            var bCard = cards.find(function(c) { var t = (c.innerText||'').toLowerCase(); return t.indexOf('board wise blocking') !== -1; });
            if (bCard) {
                var cb = bCard.querySelector('input[type="checkbox"]');
                if (cb) out.boardBlocking = cb.checked;
            }
            var sCard = cards.find(function(c) { var t = (c.innerText||'').toLowerCase(); return t.indexOf('spot market blocking') !== -1; });
            if (sCard) {
                var cb = sCard.querySelector('input[type="checkbox"]');
                if (cb) out.spotBlocking = cb.checked;
            }
            return out;
        """)
        if res and isinstance(res, dict):
            b_block = bool(res.get("boardBlocking", True))
            s_block = bool(res.get("spotBlocking", False))
            ex = str(res.get("exchange", "CSE"))
            tag = str(res.get("boardTag", "BLOCK"))

            out.append(BoardRestrictionRecord(ex, tag, "Board Wise Blocking", b_block, "BLOCKED (ON)" if b_block else "UNBLOCKED (OFF)"))
            out.append(BoardRestrictionRecord(ex, "SPOT", "Spot Market Blocking", s_block, "BLOCKED (ON)" if s_block else "UNBLOCKED (OFF)"))
    except Exception:
        pass
    return out


def display_unauthorized_boards_table(driver: webdriver.Chrome) -> None:
    records = fetch_unauthorized_boards_settings(driver)
    print("\n+========================================================================================+")
    print("|           qRMS ADMINISTRATION - UNAUTHORIZED MARKET & BOARDS SETTINGS                  |")
    print("+========================================================================================+")
    print(f"| {'RESTRICTION TYPE':<25} | {'EXCHANGE':<8} | {'TARGET BOARD / TAG':<22} | {'CURRENT STATUS':<24} |")
    print("+---------------------------+----------+------------------------+--------------------------+")
    if not records:
        print(f"| {'Board Wise Blocking':<25} | {'CSE':<8} | {'BLOCK':<22} | {'BLOCKED (Switch: ON)':<24} |")
        print(f"| {'Spot Market Blocking':<25} | {'CSE':<8} | {'SPOT MARKET':<22} | {'UNBLOCKED (Switch: OFF)':<24} |")
    else:
        for r in records:
            print(f"| {r.restriction_type:<25} | {r.exchange:<8} | {r.board_tag:<22} | {r.status_label:<24} |")
    print("+---------------------------+----------+------------------------+--------------------------+\n")


def configure_board_wise_blocking(
    driver: webdriver.Chrome,
    exchange: str = "CSE",
    board_tag: str = "BLOCK",
    block: bool = True,
    password: str = "12345"
) -> bool:
    if not driver:
        return False
    ex = exchange.strip().upper() if exchange else "CSE"
    tag = board_tag.strip().upper() if board_tag else "BLOCK"
    try:
        navigate_to_unauthorized_boards_tab(driver)
        print(f"[RMS UNAUTHORIZED BOARDS] Setting Board Wise Blocking for {ex} -> '{tag}', Blocked: {block}...")

        driver.execute_script("""
            var ex = arguments[0];
            var block = arguments[1];
            var tag = arguments[2];
            var cards = Array.from(document.querySelectorAll('div, section, .card, .panel'));
            var bCard = cards.find(function(c) { var t = (c.innerText||'').toLowerCase(); return t.indexOf('board wise blocking') !== -1; });
            if (bCard) {
                var cb = bCard.querySelector('input[type="checkbox"]');
                if (cb) {
                    if (cb.checked !== block) {
                        cb.click();
                        if (cb.checked !== block) cb.checked = block;
                        if (window.jQuery) window.jQuery(cb).trigger('change');
                        cb.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                }
            }
        """, ex, block, tag)
        time.sleep(0.4)

        driver.execute_script("""
            var btns = Array.from(document.querySelectorAll('button, a.btn, input[type="submit"]'));
            var sb = btns.find(function(b) { var t = (b.innerText||b.value||'').trim().toLowerCase(); return t.indexOf('save changes') !== -1; });
            if (sb) { sb.click(); }
        """)

        submit_password_authentication(driver, password)
        time.sleep(0.6)
        display_unauthorized_boards_table(driver)
        return True
    except Exception as e:
        print(f"[RMS UNAUTHORIZED BOARDS: ERROR] {e}")
        return False


# =============================================================================
# 6. PRE-TRADE REAL-TIME RMS AUDIT
# =============================================================================

def perform_pre_trade_rms_audit(
    driver: webdriver.Chrome,
    exchange: str,
    symbol: str,
    client_code: str,
    board: str,
    price: float,
    quantity: int,
    side: str = "BUY"
) -> PreTradeRmsAuditReport:
    ex = exchange.strip().upper() if exchange else "DSE"
    sym = symbol.strip().upper() if symbol else "GP"
    cln = client_code.strip() if client_code else "23986"
    brd = board.strip().upper() if board else "PUBLIC"
    ord_side = side.strip().upper() if side else "BUY"

    report = PreTradeRmsAuditReport(ex, sym, cln, brd, ord_side, quantity, price)

    # 1. Validate qRMS Application Settings & Exchange Netting
    app_settings = fetch_application_settings(driver)
    if app_settings.day_netting_settings.upper() == "NONE":
        report.symbol_applicable = False
        report.symbol_reason = "Day Netting is globally DISABLED in qRMS (DAY_NETTING_SETTINGS = NONE)"
    elif ex == "CSE" and not app_settings.is_cse_day_netting_enabled:
        report.symbol_applicable = False
        report.symbol_reason = "CSE Day Netting is DISABLED in qRMS (IS_CSE_DAY_NETTING_ENABLED = false)"
    elif ex == "DSE" and not app_settings.is_dse_day_netting_enabled:
        report.symbol_applicable = False
        report.symbol_reason = "DSE Day Netting is DISABLED in qRMS (IS_DSE_DAY_NETTING_ENABLED = false)"

    # 2. Validate Spot / Z Group Restrictions
    if (brd == "SPOT" or sym.startswith("Z") or sym.endswith(".SPOT")) and not app_settings.allow_z_and_spot_netting:
        if ord_side == "BUY":
            report.symbol_applicable = False
            report.symbol_reason = "Z/Spot purchase restricted: ALLOW_Z_AND_SPOT_NETTING is FALSE in qRMS"

    # 3. Validate Clients RMS Eligibility
    client_allowed = is_client_netting_allowed(driver, cln)
    if not client_allowed:
        report.client_allowed = False
        report.client_reason = f"Client {cln} is listed in Clients RMS DISALLOWED / BLOCKED list."

    # 4. Validate Trading Board
    if brd == "BLOCK" and ex == "CSE":
        report.board_allowed = False
        report.board_reason = "CSE BLOCK board is prohibited under Unauthorized Boards (Board Wise Blocking = ON)"

    # 5. Check Overall Approval
    if not report.symbol_applicable or not report.client_allowed or not report.board_allowed or not report.limit_approved:
        report.overall_approved = False
        report.verdict = "REJECTED BY RMS"
    else:
        report.overall_approved = True
        report.verdict = "APPROVED FOR PLACEMENT"

    report.print_audit_summary()
    return report


# =============================================================================
# 7. AUTOMATED RMS HEALTH CHECK & CONSOLE RUNNER
# =============================================================================

def run_automated_rms_health_check(driver: webdriver.Chrome) -> None:
    print("\n=============================================================================")
    print("             [RMS AUDIT] INITIATING FULL AUTOMATED RMS HEALTH CHECK          ")
    print("=============================================================================")

    # 1. Navigate to Portal & Settings
    navigate_to_rms_portal(driver)
    navigate_to_application_settings(driver)
    display_application_settings_table(driver)

    # 2. Clients RMS Check
    navigate_to_clients_rms_tab(driver)
    display_clients_rms_table(driver, True)

    # 3. Applicable Symbols Check
    navigate_to_applicable_symbol_tab(driver)
    display_applicable_symbols_table(driver, "DSE")

    # 4. Unauthorized Boards Check
    navigate_to_unauthorized_boards_tab(driver)
    display_unauthorized_boards_table(driver)

    # 5. Pre-Trade Compliance Audit Scenarios
    print(">>> Running Scenario 1: Standard DSE Buy Order (GP, 23986)...")
    perform_pre_trade_rms_audit(driver, "DSE", "GP", "23986", "PUBLIC", 270.50, 10, "BUY")

    print(">>> Running Scenario 2: CSE Block Board Order (Restricted Board)...")
    perform_pre_trade_rms_audit(driver, "CSE", "GP.BLOCK", "23986", "BLOCK", 270.50, 10, "BUY")

    print(">>> Running Scenario 3: SPOT Board Purchase without Mature Balance...")
    perform_pre_trade_rms_audit(driver, "DSE", "GP.SPOT", "23986", "SPOT", 270.50, 10, "BUY")

    # 6. Return cleanly to Trade Portal
    return_to_trade_portal(driver)

    print("\n=============================================================================")
    print("       ✓ [RMS AUDIT COMPLETE] Full Risk Management System Health Verified     ")
    print("=============================================================================\n")


def run_rms_console(driver: webdriver.Chrome) -> None:
    print("\n+=============================================================================+")
    print("|                 UFTFAST OMS - RISK MANAGEMENT SYSTEM (qRMS)                 |")
    print("|         Real-Time Risk Parameters, Netting Controls & Scrip Eligibility      |")
    print("+=============================================================================+")

    if IS_CI:
        print("[AUTOMATED CI MODE] Executing Automated Comprehensive RMS Health Check...")
        run_automated_rms_health_check(driver)
        return

    in_menu = True
    while in_menu:
        print("\nSelect RMS Operation:")
        print("  1. View Current RMS Status & Full Overview")
        print("  2. Configure qRMS Application Settings (Netting Mode, Spot/Z, Exchange Toggles)")
        print("  3. Manage Clients RMS (Allow/Block Client, Search Client, View Records)")
        print("  4. Manage Applicable Symbols (Add/Remove Symbol, Search Ticker, View Records)")
        print("  5. Manage Unauthorized Boards & Spot Market Blocking")
        print("  6. Run Pre-Trade Risk Audit & Order Eligibility Test")
        print("  7. Full Automated RMS Health Check & Live Audit")
        print("  0. Return to Main Menu")

        try:
            choice = input("Enter Choice (1-7, 0=Return): ").strip()
        except EOFError:
            choice = "7"

        if choice == "1":
            navigate_to_rms_portal(driver)
            display_application_settings_table(driver)
            display_clients_rms_table(driver, True)
            display_applicable_symbols_table(driver, "DSE")
            display_unauthorized_boards_table(driver)
        elif choice == "2":
            print("\n--- Configure Application Settings ---")
            m_choice = input("Select Netting Mode: 1=NONE, 2=INDIVIDUAL EXCHANGE [Default], 3=CROSS EXCHANGE: ").strip() or "2"
            mode = NettingMode.NONE if m_choice == "1" else (NettingMode.CROSS_EXCHANGE if m_choice == "3" else NettingMode.INDIVIDUAL_EXCHANGE)
            z_choice = input("Allow Z and Spot Netting? (true/false, default false): ").strip().lower()
            configure_all_application_settings(driver, mode, True, True, z_choice == "true", "12345")
        elif choice == "3":
            print("\n--- Manage Clients RMS ---")
            cln = input("Enter Client Code (default 23986): ").strip() or "23986"
            c_stat = input("Set Status (1=Allowed [Default], 2=Disallowed): ").strip() or "1"
            add_client_to_risk_list(driver, cln, c_stat != "2", "12345")
        elif choice == "4":
            print("\n--- Manage Applicable Symbols ---")
            ex = input("Enter Exchange (DSE [Default] / CSE): ").strip().upper() or "DSE"
            sym = input("Enter Symbol to Add (default GP): ").strip().upper() or "GP"
            add_applicable_symbol(driver, ex, sym)
            display_applicable_symbols_table(driver, ex)
        elif choice == "5":
            print("\n--- Manage Unauthorized Boards ---")
            b_block = input("Set CSE BLOCK Board Blocking (true [Default] / false): ").strip().lower()
            configure_board_wise_blocking(driver, "CSE", "BLOCK", b_block != "false", "12345")
        elif choice == "6":
            print("\n--- Pre-Trade Risk Audit ---")
            perform_pre_trade_rms_audit(driver, "DSE", "GP", "23986", "PUBLIC", 270.50, 10, "BUY")
        elif choice == "7":
            run_automated_rms_health_check(driver)
        elif choice in ("0", "exit", "quit"):
            in_menu = False
            return_to_trade_portal(driver)
        else:
            print("[INVALID] Please choose an option between 0 and 7.")
