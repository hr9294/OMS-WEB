"""
OMS-WEB Widget Color Linking Module
Synchronizes all 6 GoldenLayout widgets (Trade, Marketwatch, Market Depth,
Order Terminal, Position, Client Portfolio) to the same linked color channel.
MUST BE EXECUTED FIRST UPON LOADING TRADE PAGE!
"""
import time
from typing import List, Dict, Any, Union
from selenium import webdriver

from config import COLOR_NAMES, COLOR_HEXES, COLOR_MAP


def apply_color_to_all_widgets(
    driver: webdriver.Chrome,
    color_choice: Union[int, str] = 1
) -> bool:
    """
    Applies the chosen color to every .chooseColor widget dropdown on the page.
    color_choice can be an int (1-6), color name ('Turquoise'), or hex ('#42EADDFF').
    """
    chosen_color_name = "Turquoise"
    target_hex = "#42EADDFF"

    if isinstance(color_choice, int):
        if 1 <= color_choice <= len(COLOR_NAMES):
            chosen_color_name = COLOR_NAMES[color_choice - 1]
            target_hex = COLOR_HEXES[color_choice - 1]
    elif isinstance(color_choice, str):
        val = color_choice.strip()
        for idx, (name, hex_val) in enumerate(zip(COLOR_NAMES, COLOR_HEXES)):
            if val.lower() in (name.lower(), hex_val.lower()):
                chosen_color_name = name
                target_hex = hex_val
                break
        else:
            target_hex = val

    print(f"\n[WIDGET COLOR] Applying {chosen_color_name} ({target_hex}) to all widgets...")

    script = """
    var targetHex = arguments[0];
    var chooseColors = Array.from(document.querySelectorAll('.chooseColor'));
    var results = [];
    chooseColors.forEach(function(chooseDiv, index) {
        var stack = chooseDiv.closest('.lm_stack');
        var posTab = stack ? stack.querySelector('.lm_tab[title*=Position], .lm_tab .lm_title') : null;
        var activeTab = stack ? stack.querySelector('.lm_tab.lm_active .lm_title, .lm_tab .lm_title') : null;
        var title = activeTab ? activeTab.innerText.trim() : (posTab ? 'Position' : ('Widget ' + (index + 1)));
        var items = Array.from(chooseDiv.querySelectorAll('ul.color-card li.color-item'));
        var targetLi = items.find(function(li) {
            var colorDiv = li.querySelector('[data-link-color]');
            return colorDiv && colorDiv.getAttribute('data-link-color').toLowerCase() === targetHex.toLowerCase();
        });
        if (targetLi) {
            if (window.$) {
                window.$(targetLi).trigger('click');
            } else {
                targetLi.click();
            }
            var selectedDiv = chooseDiv.querySelector('.selectedColor');
            results.push({ index: index + 1, title: title, success: true, color: selectedDiv ? selectedDiv.style.backgroundColor : '' });
        } else {
            results.push({ index: index + 1, title: title, success: false, color: '' });
        }
    });
    return results;
    """

    try:
        results = driver.execute_script(script, target_hex)
        success_count = 0
        if results:
            for res in results:
                title = res.get("title", "Widget")
                ok = res.get("success", False)
                if ok:
                    print(f"  ✓ {title:<18} -> {chosen_color_name} SELECTED")
                    success_count += 1
                else:
                    print(f"  ✗ {title:<18} -> FAILED")
            print(f"[WIDGET COLOR] Done: {success_count}/{len(results)} widgets colored with {chosen_color_name}.")
            time.sleep(0.5)
            return success_count > 0
        else:
            print("[WIDGET COLOR] No widgets found on page.")
            return False
    except Exception as e:
        print(f"[WIDGET COLOR: ERROR] {e}")
        return False


def run_widget_color_selection(driver: webdriver.Chrome) -> None:
    """Interactive prompt to select widget color link (1-6, or Enter to skip)."""
    print("\n=== Widget Color Link ===")
    print("1.Turquoise 2.Yellow 3.Orange")
    print("4.Green 5.Red 6.Pink")
    user_inp = input("Enter choice (1-6, or Enter to skip): ").strip()
    if not user_inp:
        print("[WIDGET COLOR] Color selection skipped by user.")
        return

    try:
        choice = int(user_inp)
        if 1 <= choice <= 6:
            apply_color_to_all_widgets(driver, choice)
        else:
            print("[WIDGET COLOR] Invalid choice. Skipped.")
    except ValueError:
        print("[WIDGET COLOR] Invalid number. Skipped.")
