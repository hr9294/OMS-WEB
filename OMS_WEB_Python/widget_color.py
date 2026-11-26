"""OMS-WEB Widget Color Linking Module.

Synchronizes all GoldenLayout widgets (including inactive tabs inside multi-tab
stacks such as [Order Summary | Order Execution | Account Limits]) to the same
linked color channel and syncs Account Limits with the linked client code.
"""
import time
from typing import Any, Dict, List, Union
from selenium import webdriver
import config
from config import COLOR_NAMES, COLOR_HEXES

_MULTI_TAB_COLOR_JS = """
var targetHex = arguments[0];
var disabled = {
    'market sentiment': 1, 'advance decline': 1, 'top sectors': 1, 'sector gain': 1,
    'corp. events': 1, 'index impact': 1, 'index chart': 1, 'time & sales ticker': 1,
    'block vs public volume chart': 1, 'bulk order': 1, 'order execution': 1,
    'stock info': 1, 'indexes': 1
};
function normTitle(t) {
    var clean = (t || '').replace(/[\\u2715\\u274c\\u00d7]/g, '').replace(/\\s+/g, ' ').trim();
    return clean === 'Order Terminal' ? 'Order Terminal 1' : clean;
}
function lsPrefix() {
    var u = typeof system_username !== 'undefined' ? system_username : '';
    var pg = typeof profile_page !== 'undefined' ? profile_page : '';
    var pidEl = document.querySelector('#profile li.profile-selected-li');
    var pid = (pidEl && pidEl.getAttribute('data-value')) || (typeof profile_pid !== 'undefined' ? profile_pid : '');
    return (u && pg && pid) ? (u + '_' + pg + '_' + pid + '_') : null;
}
function pickColorOnStack(stack, hex) {
    var cc = stack ? stack.querySelector('.chooseColor') : null;
    if (!cc) return false;
    var items = Array.from(cc.querySelectorAll('ul.color-card li.color-item'));
    var li = items.find(function(item) {
        var d = item.querySelector('[data-link-color]');
        return d && (d.getAttribute('data-link-color') || '').toLowerCase() === hex.toLowerCase();
    });
    if (!li) return false;
    if (window.$) window.$(li).trigger('click'); else li.click();
    var sel = cc.querySelector('.selectedColor');
    if (sel) {
        sel.style.backgroundColor = hex;
        var noLink = sel.querySelector('.selected-no-link');
        if (noLink) noLink.style.display = 'none';
    }
    cc.style.display = '';
    return true;
}

var results = [];
var seenTitles = {};
var prefix = lsPrefix();
var stacks = Array.from(document.querySelectorAll('.lm_stack'));

stacks.forEach(function(stack, sIdx) {
    var tabs = Array.from(stack.querySelectorAll('.lm_header .lm_tab, .lm_tab'));
    var origTab = stack.querySelector('.lm_header .lm_tab.lm_active, .lm_tab.lm_active') || tabs[0] || null;
    var colorableTabs = tabs.filter(function(tab) {
        var title = normTitle((tab.querySelector('.lm_title') || tab).innerText || tab.getAttribute('title') || '');
        return title && !disabled[title.toLowerCase()];
    });

    colorableTabs.forEach(function(tab) {
        var titleEl = tab.querySelector('.lm_title') || tab;
        var title = normTitle(titleEl.innerText || tab.getAttribute('title') || ('Widget ' + (sIdx + 1)));
        try { titleEl.click(); } catch(e) { try { tab.click(); } catch(e2) {} }
        var ok = pickColorOnStack(stack, targetHex);
        if (ok && prefix) {
            try { localStorage.setItem(prefix + title + '_color', targetHex); } catch(e) {}
        }
        seenTitles[title] = true;
        results.push({ title: title, success: ok });
    });

    if (origTab && !origTab.classList.contains('lm_active')) {
        var origTitle = normTitle((origTab.querySelector('.lm_title') || origTab).innerText || '');
        if (!disabled[origTitle.toLowerCase()]) {
            try { (origTab.querySelector('.lm_title') || origTab).click(); } catch(e) {}
        }
    }
    var cc = stack.querySelector('.chooseColor');
    if (cc) {
        var sel = cc.querySelector('.selectedColor');
        if (sel) {
            sel.style.backgroundColor = targetHex;
            var nl = sel.querySelector('.selected-no-link');
            if (nl) nl.style.display = 'none';
        }
    }
});

// Fallback for any standalone .chooseColor outside .lm_stack
if (results.length === 0) {
    Array.from(document.querySelectorAll('.chooseColor')).forEach(function(cc, idx) {
        var stack = cc.closest('.lm_stack');
        var ok = pickColorOnStack(stack || cc.parentElement, targetHex);
        results.push({ title: 'Widget ' + (idx + 1), success: ok });
    });
}

// Refresh Account Limits whenever a color-linked client code exists
try {
    if (typeof show_hide_no_link === 'function') show_hide_no_link();
    var u = typeof system_username !== 'undefined' ? system_username : '';
    var savedCode = u ? (localStorage.getItem(u + '_last_selected_client_code') || '') : '';
    var pInp = document.getElementById('portfolio_code_input');
    var oInp = document.getElementById('order_client_code');
    var alInp = document.getElementById('account_limit_code');
    var targetCode = (alInp && alInp.value) || (pInp && pInp.value) || (oInp && oInp.value) || savedCode;
    if (alInp && targetCode) {
        if (!alInp.value) alInp.value = targetCode;
        if (window.jQuery) window.jQuery(alInp).val(targetCode);
        if (typeof get_account_limit === 'function') get_account_limit();
    }
} catch(e) {}

return results;
"""


def _resolve_color(color_choice: Union[int, str]) -> tuple[str, str]:
    if isinstance(color_choice, int) and 1 <= color_choice <= len(COLOR_NAMES):
        return COLOR_NAMES[color_choice - 1], COLOR_HEXES[color_choice - 1]
    val = str(color_choice or "").strip()
    for name, hex_val in zip(COLOR_NAMES, COLOR_HEXES):
        if val.lower() in (name.lower(), hex_val.lower()):
            return name, hex_val
    return (val or "Turquoise"), (val if val.startswith("#") else "#42EADDFF")


def apply_color_to_all_widgets(
    driver: webdriver.Chrome,
    color_choice: Union[int, str] = 1,
) -> bool:
    """Applies the chosen color across every colorable tab in every GoldenLayout stack."""
    chosen_color_name, target_hex = _resolve_color(color_choice)
    print(f"\n[WIDGET COLOR] Applying {chosen_color_name} ({target_hex}) to all widgets (including stacked tabs)...")
    try:
        results: List[Dict[str, Any]] = driver.execute_script(_MULTI_TAB_COLOR_JS, target_hex) or []
        if not results:
            print("[WIDGET COLOR] No widgets found on page.")
            return False
        success_count = 0
        for res in results:
            title = res.get("title", "Widget")
            if res.get("success"):
                print(f"  ✓ {title:<20} -> {chosen_color_name} SELECTED")
                success_count += 1
            else:
                print(f"  ✗ {title:<20} -> FAILED")
        print(f"[WIDGET COLOR] Done: {success_count}/{len(results)} widget tabs colored with {chosen_color_name}.")
        if success_count > 0:
            try:
                import oms_trade
                oms_trade.active_widget_color_name = chosen_color_name
                oms_trade.active_widget_color_hex = target_hex
            except Exception:
                pass
        time.sleep(0.5)
        return success_count > 0
    except Exception as exc:
        print(f"[WIDGET COLOR: ERROR] {exc}")
        return False


def run_widget_color_selection(driver: webdriver.Chrome) -> None:
    """Interactive prompt to select widget color link (1-6, default 1 [Turquoise])."""
    print("\n=== Widget Color Link ===")
    print("1.Turquoise (Default) 2.Yellow 3.Orange")
    print("4.Green 5.Red 6.Pink")
    user_inp = config.read_line_with_timeout("Enter choice (1-6, default 1 [Turquoise]): ", "1", 5.0)
    choice = int(user_inp) if user_inp.isdigit() and 1 <= int(user_inp) <= 6 else 1
    apply_color_to_all_widgets(driver, choice)
