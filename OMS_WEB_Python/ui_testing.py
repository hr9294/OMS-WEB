"""Cross-Browser Automated UI & Functional Tester (UI_testing.java Python Port).

Connects existing OMS-WEB modules:
  driver_factory, oms_login, oms_dashboard, rms,
  oms_upload_data, oms_export_data, day_netting
Target Modules: Admin Dashboard, RMS, File Manager, Account, Trade Logs, Setting.
Run `python ui_testing.py --test` for offline verification or `python ui_testing.py` for live Chrome/Firefox audit.
"""
from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime
import json
import os
import platform
import re
import sys
import tempfile
import time
from typing import Any, Callable, Dict, List, Optional
from selenium import webdriver
from selenium.webdriver.common.by import By
import config
import day_netting
from driver_factory import create_driver
import oms_dashboard
import oms_export_data
import oms_login
import oms_upload_data
import rms

OUT_DIR = os.environ.get("UI_OUT_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "ui_reports"))

HOOK_JS = """
if (!window.__uiTesterHooked) {
  window.__uiTesterHooked = true;
  window.__uiErrs = [];
  window.__uiConsoleErrs = [];
  window.__uiHttpFails = [];
  window.addEventListener('error', function(e) {
    window.__uiErrs.push((e.message || 'JS Error') + ' @ ' + (e.filename || '') + ':' + (e.lineno || 0));
  });
  window.addEventListener('unhandledrejection', function(e) {
    window.__uiErrs.push('PromiseRejection: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason)));
  });
  var origConsoleErr = console.error;
  console.error = function() {
    try { window.__uiConsoleErrs.push(Array.prototype.slice.call(arguments).map(String).join(' ').slice(0, 200)); } catch(ex) {}
    return origConsoleErr.apply(console, arguments);
  };
  if (window.fetch) {
    var origFetch = window.fetch;
    window.fetch = function() {
      var u = (arguments[0] && arguments[0].url) ? arguments[0].url : String(arguments[0]);
      return origFetch.apply(this, arguments).then(function(r) {
        if (r && r.status >= 400) window.__uiHttpFails.push('FETCH ' + r.status + ' ' + u);
        return r;
      }).catch(function(err) {
        window.__uiHttpFails.push('FETCH_ERR ' + u + ' -> ' + err);
        throw err;
      });
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(m, url) {
    this.__reqUrl = m + ' ' + url;
    return origOpen.apply(this, arguments);
  };
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function() {
    var self = this;
    this.addEventListener('load', function() {
      if (self.status >= 400) window.__uiHttpFails.push('XHR ' + self.status + ' ' + (self.__reqUrl || ''));
    });
    this.addEventListener('error', function() {
      window.__uiHttpFails.push('XHR_ERR ' + (self.__reqUrl || ''));
    });
    return origSend.apply(this, arguments);
  };
}
"""

@dataclass(frozen=True)
class TargetPage:
    key: str
    label: str
    link_keywords: tuple[str, ...]
    text_keywords: tuple[str, ...]

TARGET_PAGES: tuple[TargetPage, ...] = (
    TargetPage("admin_dashboard", "Admin Dashboard", ("home",), ("home", "admin dashboard", "dashboard")),
    TargetPage("rms", "RMS", ("rms/rms_ccd", "rms"), ("rms",)),
    TargetPage("file_manager", "File Manager", ("broker_it/file_manager", "file_manager"), ("file manager",)),
    TargetPage("account", "Account", ("account/",), ("account",)),
    TargetPage("trade_logs", "Trade Logs (Order Logs)", ("live_orders/fix_order_logs", "fix_order_logs"), ("order logs", "trade logs")),
    TargetPage("setting", "Setting", ("settings/edit", "settings"), ("settings", "setting")),
)

def _save_shot(driver: Any, out_dir: str, browser_name: str, step_name: str) -> None:
    try:
        shots_dir = os.path.join(out_dir, "ui_shots")
        os.makedirs(shots_dir, exist_ok=True)
        driver.save_screenshot(os.path.join(shots_dir, f"{browser_name.lower()}_{step_name}.png"))
    except Exception:
        pass

def _navigate_to_page(driver: Any, tp: TargetPage) -> str:
    try:
        if not driver.execute_script("return document.querySelectorAll('[data-link]').length;"):
            driver.get(f"{config.BASE_URL}/home")
            time.sleep(1.8)
            oms_upload_data.dismiss_overlays(driver)
    except Exception:
        pass
    if tp.key == "admin_dashboard" and oms_dashboard.navigate_to_dashboard(driver):
        return "CONNECTED:oms_dashboard"
    if tp.key == "file_manager" and oms_upload_data.navigate_to_upload_data_page(driver, timeout=10.0):
        return "CONNECTED:oms_upload_data"
    clicked = driver.execute_script(
        """
        var linkKws = arguments[0], textKws = arguments[1];
        var els = Array.from(document.querySelectorAll('[data-link], aside a, .sidebar a, nav a, .nav-sidebar a, .menu-item a, .left-sidebar a, .sidebar li a'));
        for (var k = 0; k < linkKws.length; k++) {
          var kw = linkKws[k].toLowerCase();
          for (var i = 0; i < els.length; i++) {
            var dl = (els[i].getAttribute('data-link') || '').toLowerCase();
            if (dl === kw) { try { els[i].click(); return 'SIDEBAR_EXACT:' + dl; } catch(e) {} }
          }
        }
        for (var k = 0; k < textKws.length; k++) {
          var kw = textKws[k].toLowerCase();
          for (var i = 0; i < els.length; i++) {
            var el = els[i];
            var title = (el.getAttribute('title') || el.getAttribute('data-original-title') || el.getAttribute('data-bs-original-title') || '').toLowerCase().trim();
            var txt = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();
            if (title === kw || txt === kw) { try { el.click(); return 'SIDEBAR_TEXT:' + kw; } catch(e) {} }
          }
        }
        return 'NOT_FOUND';
        """,
        list(tp.link_keywords),
        list(tp.text_keywords),
    )
    return str(clicked)

def _verify_chg_sign(today_str: str, yest_str: str, chg_str: str) -> str:
    t = oms_dashboard.parse_num_safe(today_str)
    y = oms_dashboard.parse_num_safe(yest_str)
    c = oms_dashboard.parse_num_safe(chg_str)
    if t > y:
        return "PASS(+)" if c >= 0 else f"FAIL(expected +, got {chg_str})"
    if t < y:
        return "PASS(-)" if c <= 0 else f"FAIL(expected -, got {chg_str})"
    return "PASS(0)"


def _test_admin_dashboard(driver: Any, browser_name: str) -> Dict[str, Any]:
    oms_dashboard.wait_for_dashboard_ready(driver, timeout_seconds=8)
    snap = oms_dashboard.capture_snapshot(driver, browser_name)
    tv = oms_dashboard.parse_num_safe(snap["turnover"]["big_value"])
    buy = oms_dashboard.parse_num_safe(snap["buy_value"]["big_value"])
    sell = oms_dashboard.parse_num_safe(snap["sell_value"]["big_value"])
    tr = oms_dashboard.parse_num_safe(snap["trades"]["big_count"])
    bt = oms_dashboard.parse_num_safe(snap["buy_trades"]["big_count"])
    st = oms_dashboard.parse_num_safe(snap["sell_trades"]["big_count"])
    active = snap["active_users"]
    tab_test = driver.execute_script(
        "var tabs = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link')).filter(function(t){ return t.offsetParent !== null; });"
        "if (tabs.length < 2) return 'NO_TABS(' + tabs.length + ')';"
        "var orig = tabs[0]; tabs[1].click(); var txt = (tabs[1].innerText || '').trim(); orig.click(); return 'SWITCHED_OK:' + txt;"
    )
    return {
        "turnover": snap["turnover"]["big_value"],
        "buyValue": snap["buy_value"]["big_value"],
        "sellValue": snap["sell_value"]["big_value"],
        "turnoverMatchesBuyPlusSell": abs(tv - (buy + sell)) <= 1.0 or tv == 0.0,
        "tradesCount": snap["trades"]["big_count"],
        "buyTradesCount": snap["buy_trades"]["big_count"],
        "sellTradesCount": snap["sell_trades"]["big_count"],
        "tradesMatchesBuyPlusSell": abs(tr - (bt + st)) <= 0.1,
        "brokerLimitDseUsed": snap["broker_limit"]["dse_used"],
        "brokerLimitCseUsed": snap["broker_limit"]["cse_used"],
        "activeUsersOnline": active["users_online"],
        "activeUsersOffline": active["users_offline"],
        "activeUsersTotal": active["users_total"],
        "activeUsersSumOk": (active["users_online"] + active["users_offline"]) == active["users_total"],
        "roleTotalsSum": active["dealers_total"] + active["clients_total"] + active["associates_total"],
        "chgSignTurnoverDse": _verify_chg_sign(snap["turnover"]["dse_today"], snap["turnover"]["dse_yesterday"], snap["turnover"]["dse_change"]),
        "chgSignBuyValDse": _verify_chg_sign(snap["buy_value"]["dse_today"], snap["buy_value"]["dse_yesterday"], snap["buy_value"]["dse_change"]),
        "chgSignSellValDse": _verify_chg_sign(snap["sell_value"]["dse_today"], snap["sell_value"]["dse_yesterday"], snap["sell_value"]["dse_change"]),
        "chgSignTradesDse": _verify_chg_sign(snap["trades"]["dse_today"], snap["trades"]["dse_yesterday"], snap["trades"]["dse_change"]),
        "chgSignBuyTradesDse": _verify_chg_sign(snap["buy_trades"]["dse_today"], snap["buy_trades"]["dse_yesterday"], snap["buy_trades"]["dse_change"]),
        "chgSignSellTradesDse": _verify_chg_sign(snap["sell_trades"]["dse_today"], snap["sell_trades"]["dse_yesterday"], snap["sell_trades"]["dse_change"]),
        "mostTradedStocksRows": len(snap["most_traded_stocks"]),
        "exchangeContributionRows": len(snap["exchange_contribution"]),
        "topDealersRows": len(snap["top_dealers"]),
        "topAssociatesRows": len(snap["top_associates"]),
        "mostTradedClientsRows": len(snap["most_traded_clients"]),
        "tabSwitchTest": str(tab_test),
    }

def _test_rms_module(driver: Any, _browser_name: str) -> Dict[str, Any]:
    settings = rms.fetch_application_settings(driver)
    visible_tabs = driver.execute_script(
        "return Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link'))"
        ".filter(function(t){ return t.offsetParent !== null; }).map(function(t){ return (t.innerText || '').replace(/\\s+/g,' ').trim(); });"
    )
    clients_nav = rms.navigate_to_clients_rms_tab(driver)
    time.sleep(0.5)
    save_state = driver.execute_script(
        "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return /save changes/i.test(b.innerText || ''); });"
        "if (!btn) return 'NOT_FOUND'; return btn.disabled || btn.classList.contains('disabled') ? 'DISABLED_SAFE' : 'ENABLED_WITHOUT_CLIENT';"
    )
    search_res = driver.execute_script(
        "var inp = Array.from(document.querySelectorAll('input[type=text], input[type=search], input[placeholder*=\"client\" i], input[placeholder*=\"search\" i]')).find(function(el){ return el.offsetParent !== null; });"
        "if (!inp) return 'NO_SEARCH_INPUT';"
        "inp.value = '23986'; inp.dispatchEvent(new Event('input', {bubbles:true})); inp.dispatchEvent(new Event('change', {bubbles:true}));"
        "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /search/i.test(b.innerText || ''); });"
        "if (btn) btn.click(); return 'SEARCH_TRIGGERED';"
    )
    time.sleep(0.9)
    rows_after = driver.execute_script(
        "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });"
        "return tb ? tb.querySelectorAll('tbody tr').length : -1;"
    )
    applicable_nav = rms.navigate_to_applicable_symbol_tab(driver)
    time.sleep(0.4)
    boards_nav = rms.navigate_to_unauthorized_boards_tab(driver)
    time.sleep(0.4)
    app_nav = rms.navigate_to_application_settings(driver)
    return {
        "apiDayNettingMode": settings.day_netting_settings,
        "apiAllowZAndSpot": settings.allow_z_and_spot_netting,
        "apiDseNettingEnabled": settings.is_dse_day_netting_enabled,
        "apiCseNettingEnabled": settings.is_cse_day_netting_enabled,
        "visibleTabs": visible_tabs,
        "clientsTabLoaded": clients_nav,
        "saveChangesWithoutClientState": str(save_state),
        "clientSearch23986": f"{search_res}|rows={rows_after}",
        "applicableSymbolTabNav": applicable_nav,
        "unauthorizedBoardsTabNav": boards_nav,
        "appSettingsTabNav": app_nav,
    }

def _test_file_manager_module(driver: Any, _browser_name: str) -> Dict[str, Any]:
    upload_open = oms_upload_data.open_upload_data_menu(driver)
    missing_upload = [m.name for m in oms_upload_data.UploadSubMenu if not oms_upload_data.verify_sub_menu_present(driver, m)]
    export_open = oms_export_data.open_export_files_menu(driver)
    missing_export = [m.name for m in oms_export_data.ExportSubMenu if not oms_export_data.verify_sub_menu_present(driver, m)]
    try:
        driver.execute_script("document.body.click();")
    except Exception:
        pass
    time.sleep(0.3)
    history = driver.execute_script(
        "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });"
        "if (!tb) return { found: false };"
        "var headers = Array.from(tb.querySelectorAll('thead th')).map(function(th){ return (th.innerText || '').trim(); });"
        "var rows = tb.querySelectorAll('tbody tr');"
        "var hasSearch = Array.from(document.querySelectorAll('input[type=search], input[placeholder*=\"search\" i]')).some(function(i){ return i.offsetParent !== null; });"
        "var hasPagination = !!document.querySelector('.pagination, .dataTables_paginate');"
        "var actions = rows.length > 0 ? rows[0].querySelectorAll('a, button, i, svg').length : 0;"
        "return { found: true, headers: headers, rowCount: rows.length, hasSearch: hasSearch, hasPagination: hasPagination, firstRowActionControls: actions };"
    )
    return {
        "uploadMenuOpened": upload_open,
        "uploadSubMenusExpected": len(oms_upload_data.UploadSubMenu),
        "uploadSubMenusMissing": missing_upload,
        "exportMenuOpened": export_open,
        "exportSubMenusExpected": len(oms_export_data.ExportSubMenu),
        "exportSubMenusMissing": missing_export,
        "historyTableAudit": history,
    }

def _test_account_module(driver: Any, _browser_name: str) -> Dict[str, Any]:
    profile = driver.execute_script(
        "var txt = (document.body ? document.body.innerText : '');"
        "var pwds = Array.from(document.querySelectorAll('input[type=password]')).filter(function(i){ return i.offsetParent !== null; });"
        "var eyes = Array.from(document.querySelectorAll('i.fa-eye, i.fa-eye-slash, .password-toggle, [data-toggle=password]')).filter(function(i){ return i.offsetParent !== null; });"
        "var badge = Array.from(document.querySelectorAll('.badge, span')).find(function(b){ return /^active$/i.test((b.innerText || '').trim()); });"
        "return { hasUsernameLabel: /username/i.test(txt), hasUserRoleLabel: /user role/i.test(txt), hasAccountStatusLabel: /account status/i.test(txt), hasActiveBadge: !!badge, passwordInputCount: pwds.length, passwordEyeToggleCount: eyes.length };"
    )
    before_url = driver.current_url
    submit_res = driver.execute_script(
        "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /(change|update|save).*password/i.test(b.innerText || ''); });"
        "if (!btn) return 'NO_SUBMIT_BTN'; btn.click(); return 'CLICKED:' + (btn.innerText || '').trim();"
    )
    time.sleep(0.5)
    eye_res = driver.execute_script(
        "var inp = document.querySelector('input[type=password]');"
        "var eye = document.querySelector('i.fa-eye, i.fa-eye-slash, .password-toggle');"
        "if (!inp || !eye) return 'NO_TOGGLE_OR_INPUT';"
        "var t1 = inp.type; eye.click(); var t2 = inp.type; if (t2 !== t1) eye.click();"
        "return 'before=' + t1 + ',after=' + t2;"
    )
    return {
        "profileAndForm": profile,
        "emptySubmitTest": f"{submit_res}|stayedOnPage={before_url == driver.current_url}",
        "passwordVisibilityToggle": str(eye_res),
    }

def _test_trade_logs_module(driver: Any, _browser_name: str) -> Dict[str, Any]:
    overview = driver.execute_script(
        "var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]), select')).filter(function(el){ return el.offsetParent !== null; });"
        "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });"
        "var ths = tb ? tb.querySelectorAll('thead th').length : 0, rows = tb ? tb.querySelectorAll('tbody tr').length : 0;"
        "var rej = 0, rejMsg = 0;"
        "if (tb) tb.querySelectorAll('tbody tr').forEach(function(tr){ if (/rejected/i.test(tr.innerText || '')) { rej++; var tds = tr.querySelectorAll('td'); if (tds.length && (tds[tds.length - 1].innerText || '').trim()) rejMsg++; } });"
        "var txt = (document.body ? document.body.innerText : ''), cm = txt.match(/(\\d+)\\s+of\\s+(\\d+)\\s+logs/i);"
        "return { filterControlCount: inputs.length, tableColumns: ths, initialRowCount: rows, rejectedRows: rej, rejectedWithErrorMsg: rejMsg, logBadgeText: cm ? cm[0] : 'NOT_FOUND', hasStatusLegend: /success/i.test(txt) && /pending/i.test(txt) && /failed/i.test(txt) };"
    )
    flt = driver.execute_script(
        "var inp = Array.from(document.querySelectorAll('input[type=text], input[type=search]')).find(function(i){ return i.offsetParent !== null; });"
        "var sBtn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /^search$/i.test((b.innerText || '').trim()); });"
        "var cBtn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /^clear$/i.test((b.innerText || '').trim()); });"
        "if (!inp || !sBtn) return 'MISSING_CONTROLS';"
        "var before = document.querySelectorAll('table tbody tr').length;"
        "inp.value = 'ZZZ_NO_MATCH'; inp.dispatchEvent(new Event('input', {bubbles:true})); sBtn.click();"
        "return { beforeRows: before, hasClearBtn: !!cBtn };"
    )
    time.sleep(0.8)
    after_rows = driver.execute_script(
        "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });"
        "var after = tb ? tb.querySelectorAll('tbody tr').length : -1;"
        "var cBtn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /^clear$/i.test((b.innerText || '').trim()); });"
        "if (cBtn) cBtn.click(); return after;"
    )
    time.sleep(0.6)
    return {"logsOverview": overview, "filterSearchAndClearTest": f"{flt}|afterFilterRows={after_rows}"}

def _test_setting_module(driver: Any, _browser_name: str) -> Dict[str, Any]:
    meta = driver.execute_script(
        "var txt = (document.body ? document.body.innerText : '');"
        "var tabs = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link')).filter(function(t){ return t.offsetParent !== null; }).map(function(t){ return (t.innerText || '').trim(); });"
        "var fontInp = Array.from(document.querySelectorAll('input')).find(function(i){ return i.offsetParent !== null && /^\\.?\\d+(\\.\\d+)?$/.test((i.value || '').trim()); });"
        "return { tabs: tabs, hasProductUftfast: /UFTFAST OMS/i.test(txt), hasVersion: /v\\d+\\.\\d+\\.\\d+/i.test(txt), hasFixVersion: /5\\.0\\s*SP1/i.test(txt), fontSizeValue: fontInp ? fontInp.value : 'NOT_FOUND', fontSizeLeadingZeroMissing: fontInp ? /^\\.\\d+$/.test((fontInp.value || '').trim()) : false };"
    )
    driver.set_window_size(1366, 768)
    time.sleep(0.35)
    save_1366 = driver.execute_script(
        "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /save settings/i.test(b.innerText || ''); });"
        "if (!btn) return 'NOT_FOUND'; var r = btn.getBoundingClientRect();"
        "return { top: Math.round(r.top), bottom: Math.round(r.bottom), innerHeight: window.innerHeight, obscuredByBottomBar: r.bottom > window.innerHeight - 28 };"
    )
    driver.set_window_size(1920, 1080)
    time.sleep(0.25)
    act_switch = driver.execute_script(
        "var tab = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link, a, button')).find(function(el){ return el.offsetParent !== null && /activity logs/i.test(el.innerText || ''); });"
        "if (!tab) return 'NO_ACTIVITY_LOGS_TAB'; tab.click(); return 'CLICKED_ACTIVITY_LOGS';"
    )
    time.sleep(0.7)
    act_content = driver.execute_script(
        "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });"
        "return { hasTable: !!tb, rows: tb ? tb.querySelectorAll('tbody tr').length : 0 };"
    )
    try:
        driver.execute_script(
            "var tab = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link, a, button')).find(function(el){ return el.offsetParent !== null && /^settings$/i.test((el.innerText || '').trim()); });"
            "if (tab) tab.click();"
        )
    except Exception:
        pass
    return {"settingsMetadata": meta, "saveSettingsAt1366": save_1366, "activityLogsTabTest": f"{act_switch}|{act_content}"}

_MODULE_HANDLERS: Dict[str, Callable[[Any, str], Dict[str, Any]]] = {
    "admin_dashboard": _test_admin_dashboard,
    "rms": _test_rms_module,
    "file_manager": _test_file_manager_module,
    "account": _test_account_module,
    "trade_logs": _test_trade_logs_module,
    "setting": _test_setting_module,
}

def _test_target_page(driver: Any, browser_name: str, tp: TargetPage, out_dir: str) -> Dict[str, Any]:
    out: Dict[str, Any] = {"key": tp.key, "label": tp.label}
    t0 = time.perf_counter()
    try:
        driver.execute_script(HOOK_JS + "window.__uiErrs=[]; window.__uiConsoleErrs=[]; window.__uiHttpFails=[];")
    except Exception:
        pass
    nav_method = _navigate_to_page(driver, tp)
    time.sleep(2.0)
    oms_upload_data.dismiss_overlays(driver)
    try:
        driver.execute_script(HOOK_JS)
    except Exception:
        pass
    out.update({
        "navMethod": nav_method,
        "finalUrl": driver.current_url,
        "pageTitle": driver.title,
        "loadMs": int((time.perf_counter() - t0) * 1000),
    })
    _save_shot(driver, out_dir, browser_name, f"{tp.key}_1920")
    dom_audit = driver.execute_script("""
        var res = {}, bodyText = (document.body ? document.body.innerText : '').trim();
        res.bodyTextLength = bodyText.length;
        var main = document.querySelector('#main-content, #content, .content-wrapper, .page-content, main') || document.body;
        var mainText = (main ? main.innerText : bodyText).trim();
        res.mainContentSample = mainText.slice(0, 500).replace(/\\s+/g, ' ');
        var h = (main || document).querySelector('h1, h2, h3, h4, .page-title, .card-title, .header-title');
        res.primaryHeading = h ? (h.innerText || '').trim() : '';
        res.tableCount = document.querySelectorAll('table').length;
        var vis = 0, maxR = 0, totR = 0, emp = 0;
        document.querySelectorAll('table').forEach(function(tb){
          if (tb.offsetParent !== null) vis++;
          var rc = tb.querySelectorAll('tbody tr').length; totR += rc; if (rc > maxR) maxR = rc; if (rc === 0) emp++;
        });
        res.visibleTables = vis; res.totalTableRows = totR; res.maxTableRows = maxR; res.emptyTables = emp;
        res.buttonCount = Array.from(document.querySelectorAll('button:not([disabled]), a.btn')).filter(function(b){ return b.offsetParent !== null; }).length;
        res.inputCount = Array.from(document.querySelectorAll('input:not([type=hidden]), select, textarea')).filter(function(i){ return i.offsetParent !== null; }).length;
        res.tabCount = document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link').length;
        var broken = [];
        document.querySelectorAll('img').forEach(function(img){ if (img.offsetParent !== null && (!img.complete || img.naturalWidth === 0)) broken.push(img.getAttribute('src') || 'img'); });
        res.brokenImages = broken; res.brokenImageCount = broken.length;
        res.hasNaNText = /\\b(NaN|undefined|null)\\b/.test(mainText);
        res.hasServerErrorText = /\\b(404 Not Found|500 Internal Server|Whoops|Fatal error|SQLSTATE|Exception in)\\b/i.test(mainText);
        res.horizontalOverflow1920 = document.documentElement.scrollWidth > document.documentElement.clientWidth;
        res.scrollWidth1920 = document.documentElement.scrollWidth; res.clientWidth1920 = document.documentElement.clientWidth;
        return res;
    """)
    if isinstance(dom_audit, dict):
        out.update(dom_audit)
    try:
        health = day_netting.verify_ui_layout_health(driver)
        out["dayNettingUiHealth"] = re.sub(r"\s+", " ", str(health)).strip()
    except Exception as exc:
        out["dayNettingUiHealth"] = f"ERR: {exc}"
    handler = _MODULE_HANDLERS.get(tp.key)
    try:
        out["functionalTests"] = handler(driver, browser_name) if handler else {}
    except Exception as exc:
        out["functionalTests"] = {"moduleError": str(exc)}
    try:
        out["bestPracticeUiAudit"] = _run_best_practice_ui_audit(driver)
    except Exception as exc:
        out["bestPracticeUiAudit"] = {"auditError": str(exc)}
    try:
        driver.set_window_size(1366, 768)
        time.sleep(0.45)
        resp = driver.execute_script(
            "var c=0; document.querySelectorAll('table,.card,.btn').forEach(function(e){ var r=e.getBoundingClientRect(); if (r.width>0 && r.right>window.innerWidth+4) c++; });"
            "return { overflowAt1366: document.documentElement.scrollWidth > document.documentElement.clientWidth, scrollWidth1366: document.documentElement.scrollWidth, clientWidth1366: document.documentElement.clientWidth, clipped1366: c };"
        )
        if isinstance(resp, dict):
            out.update(resp)
        _save_shot(driver, out_dir, browser_name, f"{tp.key}_1366")
        driver.set_window_size(1920, 1080)
        time.sleep(0.25)
    except Exception:
        out["overflowAt1366"] = "ERR"
    js_errs = driver.execute_script("return window.__uiErrs || [];") or []
    con_errs = driver.execute_script("return window.__uiConsoleErrs || [];") or []
    http_fails = driver.execute_script("return window.__uiHttpFails || [];") or []
    out.update({
        "jsErrors": js_errs, "jsErrorCount": len(js_errs),
        "consoleErrors": con_errs, "consoleErrorCount": len(con_errs),
        "httpFailures": http_fails, "httpFailureCount": len(http_fails),
    })
    return out

def execute_suite_on_driver(driver: Any, browser_name: str, out_dir: str = OUT_DIR, perform_login: bool = True) -> Dict[str, Any]:
    res: Dict[str, Any] = {"status": "EXECUTED"}
    t0 = time.perf_counter()
    if perform_login:
        driver.get(config.BASE_URL)
        time.sleep(1.5)
        try:
            driver.execute_script(HOOK_JS)
        except Exception:
            pass
        user = os.environ.get("OMS_USERNAME") or "Testadmin"
        pwd = os.environ.get("OMS_PASSWORD") or "12345"
        logged_in = False
        try:
            u_inputs = driver.find_elements(By.ID, "username")
            if u_inputs:
                u_inputs[0].clear()
                u_inputs[0].send_keys(user)
                p_input = driver.find_element(By.ID, "password")
                p_input.clear()
                p_input.send_keys(pwd)
                driver.find_element(By.XPATH, "//button[normalize-space()='Login' or @type='submit']").click()
                time.sleep(2.5)
                oms_login.press_ok_automatically(driver)
                oms_upload_data.dismiss_overlays(driver)
            logged_in = not driver.find_elements(By.ID, "username")
        except Exception as exc:
            res["loginError"] = str(exc)
        res["loginSuccess"] = logged_in
    else:
        res["loginSuccess"] = True
    res["loginMs"] = int((time.perf_counter() - t0) * 1000)
    res["postLoginUrl"] = driver.current_url
    _save_shot(driver, out_dir, browser_name, "00_post_login")
    try:
        driver.execute_script(HOOK_JS)
    except Exception:
        pass
    res["webkitCompatibilityAudit"] = driver.execute_script("""
        var h = { unprefixedBackdropFilter: 0, overflowOverlay: 0, cssGridSubgrid: 0 };
        try {
          for (var s = 0; s < document.styleSheets.length; s++) {
            var rules; try { rules = document.styleSheets[s].cssRules; } catch(ex) { continue; }
            if (!rules) continue;
            for (var r = 0; r < rules.length; r++) {
              var ct = rules[r].cssText || '';
              if (ct.indexOf('backdrop-filter') !== -1 && ct.indexOf('-webkit-backdrop-filter') === -1) h.unprefixedBackdropFilter++;
              if (ct.indexOf('overflow: overlay') !== -1 || ct.indexOf('overflow-y: overlay') !== -1) h.overflowOverlay++;
              if (ct.indexOf('subgrid') !== -1) h.cssGridSubgrid++;
            }
          }
        } catch(e) {}
        return h;
    """)

    pages = []
    for tp in TARGET_PAGES:
        p_res = _test_target_page(driver, browser_name, tp, out_dir)
        pages.append(p_res)
        print(f"  [{browser_name} | {tp.label:<23}] nav={p_res.get('navMethod')} | loadMs={p_res.get('loadMs')} | httpFails={p_res.get('httpFailureCount')} | jsErr={p_res.get('jsErrorCount')} | overflow1366={p_res.get('overflowAt1366')}")
    res["pages"] = pages
    return res

def probe_safari_availability() -> Dict[str, Any]:
    if platform.system().lower() != "darwin":
        return {"status": "BLOCKED_ON_WINDOWS" if platform.system().lower() == "windows" else "BLOCKED_NON_MAC", "reason": f"Safari requires macOS (current OS: {platform.system()})"}
    drv = None
    try:
        drv = webdriver.Safari()
        return execute_suite_on_driver(drv, "Safari")
    except Exception as exc:
        return {"status": "FAILED", "reason": str(exc)}
    finally:
        if drv:
            try:
                drv.quit()
            except Exception:
                pass

def run_browser_suite(browser_name: str, headless: bool = False, out_dir: str = OUT_DIR) -> Dict[str, Any]:
    print(f"\n>>> Starting Browser Suite: {browser_name}")
    drv = None
    try:
        if browser_name == "Chrome":
            drv = create_driver(headless=headless)
            drv.implicitly_wait(4)
        else:
            opts = webdriver.FirefoxOptions()
            opts.accept_insecure_certs = True
            for k, v in (("dom.webnotifications.enabled", False), ("dom.push.enabled", False), ("signon.rememberSignons", False)):
                opts.set_preference(k, v)
            if headless:
                opts.add_argument("-headless")
            opts.add_argument("--width=1920")
            opts.add_argument("--height=1080")
            drv = webdriver.Firefox(options=opts)
            drv.set_window_size(1920, 1080)
            drv.set_page_load_timeout(25)
            drv.implicitly_wait(4)
        return execute_suite_on_driver(drv, browser_name, out_dir=out_dir)
    except Exception as exc:
        return {"status": "ERROR", "reason": str(exc)}
    finally:
        if drv:
            try:
                drv.quit()
            except Exception:
                pass

def _click_rms_tab(driver: Any, name: str) -> str:
    return str(driver.execute_script(
        "var name=arguments[0].toLowerCase();"
        "var el=Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link,a,button'))"
        ".find(function(t){return t.offsetParent!==null && (t.innerText||'').replace(/\\s+/g,' ').trim().toLowerCase()===name;});"
        "if(!el) return 'TAB_NOT_FOUND:'+name; el.click(); return 'CLICKED:'+name;", name
    ))


def _verify_rms_subtab(driver: Any, sub_name: str) -> str:
    clk = _click_rms_tab(driver, sub_name)
    time.sleep(0.7)
    stats = driver.execute_script(
        "var tbl=Array.from(document.querySelectorAll('table')).filter(function(t){return t.offsetParent!==null;}).length;"
        "var inp=Array.from(document.querySelectorAll('input:not([type=hidden]),select')).filter(function(i){return i.offsetParent!==null;}).length;"
        "var btn=Array.from(document.querySelectorAll('button')).filter(function(b){return b.offsetParent!==null;}).length;"
        "return 'tables='+tbl+',inputs='+inp+',buttons='+btn;"
    )
    return f"{clk} | {stats} | uiHealth={day_netting.verify_ui_layout_health(driver)}"


def _run_rms_sub_menu(driver: Any, parent: str, opts: tuple[str, ...]) -> None:
    while True:
        print(f"\n--- [RMS -> {parent}] Select Sub-Option ---")
        for i, name in enumerate(opts, 1):
            print(f"  {i}. {name}")
        all_choice = len(opts) + 1
        print(f"  {all_choice}. All {parent} Options")
        print("  0. Back to RMS Menu")
        s = config.read_line_with_timeout(f"Enter Choice (0-{all_choice}, ENTER={all_choice}): ", str(all_choice))
        if s in ("0", "back", "exit"):
            break
        if s == str(all_choice):
            for sub in opts:
                print(f"  -> {sub}: {_verify_rms_subtab(driver, sub)}")
        elif s.isdigit() and 1 <= int(s) <= len(opts):
            sub = opts[int(s) - 1]
            print(f"  -> {sub}: {_verify_rms_subtab(driver, sub)}")
        else:
            print(f"[INVALID] Choose 0-{all_choice}.")


def _run_rms_menu(driver: Any) -> None:
    rms_sections: tuple[tuple[str, tuple[str, ...]], ...] = (
        ("Client", ("Limits", "Identity")),
        ("User", ("Identity", "Limits", "Connections")),
        ("Branch", ("Limits", "Identity", "Teams")),
        ("Margin", ("Boardwise Setup", "Categorywise Setup", "Instrumentwise Setup", "Clientwise Setup",
                    "FDR PP Adjustment", "Force Sell", "Marginable Securities", "Applicable Marginable Securities",
                    "Marginable Client", "Settings")),
        ("Broker", ("Limits", "Ticker Restriction", "Ticker Group", "Client Group", "User Group",
                    "Assigned Watchlist", "Reports", "Circuit Breaker Rules", "Password Policy", "Offline Order Settings")),
        ("Netting", ("Applicable Symbol", "Clients RMS", "Unauthorized Boards/Market")),
        ("Settings", ("Application Settings", "Email Template Settings", "SMS Template Settings", "Trade File Settings")),
    )
    while True:
        print("\n--- [RMS UI Testing] Select RMS Section ---")
        for i, (sec, subs) in enumerate(rms_sections, 1):
            print(f"  {i}. {sec} ({len(subs)} options)")
        print("  8. Run ALL RMS Options")
        print("  0. Back to UI Testing Menu")
        choice = config.read_line_with_timeout("Enter Choice (0-8, ENTER=8): ", "8")
        if choice in ("0", "back", "exit"):
            break
        _navigate_to_page(driver, TARGET_PAGES[1])
        time.sleep(1.5)
        if choice == "8":
            print("[RMS Full Audit]", json.dumps(_test_rms_module(driver, "Chrome"), indent=2))
            continue
        if choice.isdigit() and 1 <= int(choice) <= len(rms_sections):
            sec, subs = rms_sections[int(choice) - 1]
            _click_rms_tab(driver, sec)
            time.sleep(0.8)
            _run_rms_sub_menu(driver, sec, subs)
        else:
            print("[INVALID] Choose 0-8.")


def _run_best_practice_ui_audit(driver: Any) -> Dict[str, Any]:
    return driver.execute_script(
        "var imgsNoAlt=0, inputsNoLabel=0, btnsNoText=0, tinyFonts=0, noPointer=0, focusable=0, safeClicks=0, fixedOverlap=0;"
        "document.querySelectorAll('img').forEach(function(i){ if(i.offsetParent!==null && !(i.getAttribute('alt')||'').trim()) imgsNoAlt++; });"
        "document.querySelectorAll('input:not([type=hidden]),select,textarea').forEach(function(el){ if(el.offsetParent!==null){ var id=el.id, lbl=id?document.querySelector('label[for=\"'+id+'\"]'):null; if(!lbl && !el.getAttribute('aria-label') && !el.getAttribute('placeholder')) inputsNoLabel++; } });"
        "document.querySelectorAll('button,a.btn').forEach(function(b){ if(b.offsetParent!==null){ var t=(b.innerText||b.getAttribute('aria-label')||b.getAttribute('title')||'').trim(); if(!t) btnsNoText++; var cs=getComputedStyle(b); if(cs.cursor!=='pointer' && !b.disabled) noPointer++; } });"
        "document.querySelectorAll('p,span,td,th,label,a,button').forEach(function(el){ if(el.offsetParent!==null && (el.innerText||'').trim()){ var fs=parseFloat(getComputedStyle(el).fontSize||'14'); if(fs<11) tinyFonts++; } });"
        "focusable = Array.from(document.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]):not([type=hidden]),select:not([disabled]),textarea:not([disabled]),[tabindex]')).filter(function(e){return e.offsetParent!==null;}).length;"
        "var fixedBars = Array.from(document.querySelectorAll('*')).filter(function(e){ var cs=getComputedStyle(e); return (cs.position==='fixed'||cs.position==='sticky') && e.offsetHeight>0 && e.offsetHeight<120; });"
        "document.querySelectorAll('button.btn, .btn-primary').forEach(function(b){ if(b.offsetParent!==null){ var r=b.getBoundingClientRect(); fixedBars.forEach(function(fb){ var fr=fb.getBoundingClientRect(); if(r.left<fr.right && r.right>fr.left && r.top<fr.bottom && r.bottom>fr.top && !fb.contains(b)) fixedOverlap++; }); } });"
        "var tabs = Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link')).filter(function(t){return t.offsetParent!==null;});"
        "tabs.forEach(function(t){ try{ t.click(); safeClicks++; }catch(e){} });"
        "if (tabs.length > 0) try{ tabs[0].click(); }catch(e){}"
        "var safeBtns = Array.from(document.querySelectorAll('.dropdown-toggle,[data-bs-toggle=collapse],[data-toggle=collapse],th[data-sort],th.sorting')).filter(function(b){ return b.offsetParent!==null && !/(delete|remove|logout|sign out|save|upload|export|confirm|submit)/i.test(b.innerText||''); });"
        "safeBtns.slice(0,12).forEach(function(b){ try{ b.click(); safeClicks++; }catch(e){} });"
        "try{ document.body.click(); }catch(e){}"
        "return { imgsMissingAlt:imgsNoAlt, inputsMissingLabel:inputsNoLabel, buttonsWithoutText:btnsNoText, tinyFontsUnder11px:tinyFonts, missingPointerCursor:noPointer, focusableControls:focusable, fixedBarOverlaps:fixedOverlap, safeClicksExecuted:safeClicks };"
    )


def _run_browser_selection_menu(driver: Any, out_dir: str) -> None:
    print("\n--- [Browser Target] Select Browser ---")
    print("  1. Chrome (Fresh Browser)")
    print("  2. Firefox (Fresh Browser)")
    print("  3. Safari (macOS check / WebKit Audit)")
    print("  4. All Browsers (Chrome + Firefox + Safari)")
    print("  5. Current Active Chrome Session")
    print("  0. Back")
    b = config.read_line_with_timeout("Enter Choice (0-5, ENTER=4): ", "4")
    if b == "0":
        return
    if b == "1":
        run_ui_testing(browsers=["Chrome"], out_dir=out_dir)
    elif b == "2":
        run_ui_testing(browsers=["Firefox"], out_dir=out_dir)
    elif b == "3":
        print(json.dumps(probe_safari_availability(), indent=2))
    elif b == "5":
        run_ui_testing(existing_driver=driver, out_dir=out_dir)
    else:
        run_ui_testing(out_dir=out_dir)


def run_ui_testing_menu(driver: Any, out_dir: str = OUT_DIR) -> None:
    while True:
        print("\n=== [UI TESTING MENU] Select Page / Module ===")
        print("  1. Admin Dashboard (All Cards, Chg% Sign, Active/Inactive Users & Live Trade)")
        print("  2. RMS (Select RMS Option: Client, User, Branch, Margin, Broker, Netting, Settings)")
        print("  3. File Manager")
        print("  4. Account")
        print("  5. Trade Logs (FIX Order Logs)")
        print("  6. Setting")
        print("  7. Run ALL 6 Pages (Choose Browser: Chrome, Firefox, Safari, or All)")
        print("  8. Select Browser Target (Chrome / Firefox / Safari / All / Current)")
        print("  0. Return to Main Menu")
        choice = config.read_line_with_timeout("Enter Choice (0-8, ENTER=7): ", "7")
        if choice in ("0", "back", "exit"):
            break
        if choice == "1":
            res = test_target_page(driver, "Chrome", TARGET_PAGES[0], out_dir=out_dir)
            print("[Admin Dashboard Audit]", json.dumps(res.get("functionalTests"), indent=2))
            print("[Best-Practice UI Audit]", json.dumps(res.get("bestPracticeUiAudit"), indent=2))
            run_trade = config.read_line_with_timeout("Run live trade + pre/post Dashboard Reflection Audit? (1=YES / 0=NO, ENTER=0): ", "0", 10.0)
            if run_trade == "1":
                oms_dashboard.run_live_reflection_audit(driver)
        elif choice == "2":
            _run_rms_menu(driver)
        elif choice in ("3", "4", "5", "6"):
            tp = TARGET_PAGES[int(choice) - 1]
            res = test_target_page(driver, "Chrome", tp, out_dir=out_dir)
            print(f"[ui_testing | {tp.label}] nav={res.get('navMethod')} | loadMs={res.get('loadMs')} | jsErr={res.get('jsErrorCount')} | overflow1366={res.get('overflowAt1366')}")
            print("  Functional:", json.dumps(res.get("functionalTests"), indent=2))
            print("  UI Best-Practice:", json.dumps(res.get("bestPracticeUiAudit"), indent=2))
        elif choice == "7":
            run_ui_testing(out_dir=out_dir)
        elif choice == "8":
            _run_browser_selection_menu(driver, out_dir)
        else:
            print("[INVALID] Choose 0-8.")


def run_ui_testing(browsers: Optional[List[str]] = None, headless: bool = False, out_dir: str = OUT_DIR, existing_driver: Any = None) -> Dict[str, Any]:
    print("=========================================================================")
    print("  ui_testing | Full Cross-Browser Suite (Connected to OMS_WEB_Python)  ")
    print("=========================================================================")
    report: Dict[str, Any] = {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "baseUrl": config.BASE_URL,
        "osName": platform.system(),
        "pythonVersion": platform.python_version(),
        "browsers": {},
    }
    if existing_driver is not None:
        report["browsers"]["Chrome"] = execute_suite_on_driver(existing_driver, "Chrome", out_dir=out_dir, perform_login=False)
    else:
        for b in (browsers or ["Chrome", "Firefox"]):
            report["browsers"][b] = run_browser_suite(b, headless=headless or config.IS_HEADLESS, out_dir=out_dir)
        report["browsers"]["Safari"] = probe_safari_availability()
    os.makedirs(os.path.join(out_dir, "ui_shots"), exist_ok=True)
    json_path = os.path.join(out_dir, "ui_testing_report_data.json")
    with open(json_path, "w", encoding="utf-8") as fh:
        json.dump(report, fh, indent=2)
    print(f"\n[ui_testing] Report JSON saved -> {json_path}")
    return report

def test_ui_testing_offline_suite() -> bool:
    """Offline self-test verifying JS hook, 6 target pages, 9+13 menu enums, Safari guard & JSON write."""
    assert len(TARGET_PAGES) == 6 and set(_MODULE_HANDLERS.keys()) == {tp.key for tp in TARGET_PAGES}
    assert "__uiTesterHooked" in HOOK_JS and "XMLHttpRequest.prototype.send" in HOOK_JS
    assert len(oms_upload_data.UploadSubMenu) == 9 and len(oms_export_data.ExportSubMenu) == 13
    assert oms_export_data.DEFAULT_NO_EXPORT is True
    safari_res = probe_safari_availability()
    if platform.system().lower() != "darwin":
        assert "BLOCKED" in safari_res["status"]
    with tempfile.TemporaryDirectory() as tmp:
        p = os.path.join(tmp, "ui_testing_report_data.json")
        with open(p, "w", encoding="utf-8") as fh:
            json.dump({"pages": [tp.key for tp in TARGET_PAGES], "safari": safari_res}, fh)
        loaded = json.loads(open(p, encoding="utf-8").read())
        assert len(loaded["pages"]) == 6
    print("[UI_TESTING OFFLINE PASS] 6/6 target modules, 9 Upload + 13 Export enums, HOOK_JS & Safari guard OK.")
    return True

if __name__ == "__main__":
    if "--test" in sys.argv:
        sys.exit(0 if test_ui_testing_offline_suite() else 1)
    run_ui_testing(headless="--headless" in sys.argv or config.IS_HEADLESS)
