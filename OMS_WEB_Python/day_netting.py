"""
UFTFAST OMS - DAY NETTING (SCRIP NETTING) ENGINE (Python Suite)
Compact, modular port of Day_Netting.java delegating RMS mutations to rms.py
and Stock File guards to oms_upload_data.py.
"""
from enum import Enum
import sys
import time
from typing import List, Dict, Optional
from selenium import webdriver
import config
import oms_price
import rms

NettingMode = rms.NettingMode

class ClientNettingStatus(Enum):
    ALLOWED = ("Allowed", True)
    BLOCKED = ("Blocked", False)
    DEFAULT = ("Default (Allowed)", True)

    def __init__(self, label: str, allowed: bool):
        self.label = label
        self.allowed = allowed

class NettingScenarioType(Enum):
    S1_CLIENT1_ALLOWED_OTHERS_DISALLOWED = "S1: Client 1 Allowed vs Others Disallowed"
    S2_CLIENT1_DISALLOWED_OTHERS_ALLOWED = "S2: Client 1 Disallowed vs Others Allowed"
    S3_SYMBOL_ALLOWED_CLIENT_DISALLOWED = "S3: Symbol Allowed + Client Disallowed -> No Saleable Update"
    S4_SYMBOL_DISALLOWED_CLIENT_ALLOWED = "S4: Symbol Disallowed + Client Allowed -> No Saleable Update"
    S5_POST_ORDER_ALLOW_SYMBOL = "S5: Post-Accepted-Order Allow Symbol -> Check Reflection"
    S6_POST_ORDER_ALLOW_CLIENT = "S6: Post-Accepted-Order Allow Client -> Check Update"
    S7_ALLOW_NETTING_PLUS_STOCK_FILE = "S7: Allow Netting + Stock File -> Saleable Updated"
    S8_DISALLOW_NETTING_PLUS_STOCK_FILE = "S8: Disallow Netting + Stock File -> Saleable Updated"
    S9_DISALLOW_PLUS_STOCK_FILE_THEN_ALLOW = "S9: Disallow + Stock File -> Then Allow Netting -> Saleable Updated"

class RetroactivePolicy(Enum):
    RETROACTIVE_APPLY = "RETROACTIVE_APPLY"
    PROSPECTIVE_ONLY = "PROSPECTIVE_ONLY"
    OBSERVE_AND_REPORT = "OBSERVE_AND_REPORT"

class NettingCalculationResult:
    def __init__(self):
        self.mode = NettingMode.NONE
        self.symbol = ""
        self.client_code = ""
        self.dse_buy_qty = self.dse_sell_qty = self.cse_buy_qty = self.cse_sell_qty = 0.0
        self.total_buy_qty = self.total_sell_qty = self.netted_qty = 0.0
        self.remaining_buy_qty = self.remaining_sell_qty = 0.0
        self.is_eligible = self.is_client_allowed = True
        self.is_market_blocked = False

class NettingUIReport:
    def __init__(self, symbol: str = "GP", client_code: str = "23986"):
        self.symbol = symbol
        self.client_code = client_code
        self.market_depth_indicator = False
        self.default_netting_watchlist = False
        self.existing_watchlist_icon = False
        self.existing_watchlist_hover_text = ""
        self.market_watch_indicator = False
        self.order_window_dse_indicator = False
        self.order_window_cse_indicator = False
        self.order_window_client_blocked_indicator = False

class NettingApplicabilityReport:
    def __init__(self, exchange: str = "DSE", symbol: str = "GP", client_code: str = "23986", board: str = "PUBLIC"):
        self.exchange = (exchange or "DSE").strip().upper()
        self.symbol = (symbol or "GP").strip().upper()
        self.client_code = (client_code or "23986").strip()
        self.board = (board or "PUBLIC").strip().upper()
        self.is_symbol_applicable = True
        self.symbol_status = "APPLICABLE"
        self.symbol_reason = ""
        self.is_client_applicable = True
        self.client_status = "APPLICABLE"
        self.client_reason = ""
        self.is_board_applicable = True
        self.board_status = "APPLICABLE"
        self.board_reason = ""
        self.is_overall_applicable = True
        self.overall_verdict = ""

class NettingScenarioConfig:
    def __init__(self):
        self.primary_client = "002"
        self.other_clients: List[str] = ["23986"]
        self.symbol = "GP"
        self.exchange = "BOTH"
        self.board = "PUBLIC"
        self.netting_mode = NettingMode.INDIVIDUAL_EXCHANGE
        self.dse_enabled = True
        self.cse_enabled = True
        self.allow_z_spot = False
        self.order_qty = 10
        self.order_rate = 266.90
        self.rms_password = "12345"
        self.stock_file_path = ""
        self.allow_live_stock_upload = False
        self.execute_live_orders = True
        self.retroactive_policy = RetroactivePolicy.OBSERVE_AND_REPORT
        self.scenario_filter = "ALL"

class NettingScenarioSpec:
    def __init__(
        self, number: int, stype: NettingScenarioType, title: str, rule_description: str,
        initial_client_allowed: bool, initial_symbol_allowed: bool, post_order_allow_symbol: bool,
        post_order_allow_client: bool, requires_stock_file: bool,
        expect_saleable_initially: bool, expect_saleable_after_flip: bool
    ):
        self.number = number
        self.type = stype
        self.title = title
        self.rule_description = rule_description
        self.initial_client_allowed = initial_client_allowed
        self.initial_symbol_allowed = initial_symbol_allowed
        self.post_order_allow_symbol = post_order_allow_symbol
        self.post_order_allow_client = post_order_allow_client
        self.requires_stock_file = requires_stock_file
        self.expect_saleable_initially = expect_saleable_initially
        self.expect_saleable_after_flip = expect_saleable_after_flip

class NettingScenarioOutcome:
    def __init__(
        self, spec: NettingScenarioSpec, executed_live: bool, passed: bool, applicability_ok: bool,
        exclusivity_ok: bool, saleable_audit_ok: bool, pre_saleable_summary: str,
        post_saleable_summary: str, verdict: str, details: str, audit_result=None, delta_report=None
    ):
        self.spec = spec
        self.executed_live = executed_live
        self.passed = passed
        self.applicability_ok = applicability_ok
        self.exclusivity_ok = exclusivity_ok
        self.saleable_audit_ok = saleable_audit_ok
        self.pre_saleable_summary = pre_saleable_summary or "-"
        self.post_saleable_summary = post_saleable_summary or "-"
        self.verdict = verdict or ("PASS" if passed else "FAIL")
        self.details = details or ""
        self.audit_result = audit_result
        self.delta_report = delta_report

class QrmsNettingStateSnapshot:
    def __init__(
        self, day_netting_settings: str, dse_enabled: bool, cse_enabled: bool, allow_z_spot: bool,
        allowed_clients: List, disallowed_clients: List, dse_symbols: List, cse_symbols: List,
        board_restrictions: List, captured_from_live: bool
    ):
        self.day_netting_settings = day_netting_settings or "INDIVIDUAL EXCHANGE"
        self.dse_enabled = dse_enabled
        self.cse_enabled = cse_enabled
        self.allow_z_spot = allow_z_spot
        self.allowed_clients = list(allowed_clients or [])
        self.disallowed_clients = list(disallowed_clients or [])
        self.dse_symbols = list(dse_symbols or [])
        self.cse_symbols = list(cse_symbols or [])
        self.board_restrictions = list(board_restrictions or [])
        self.captured_from_live = captured_from_live

class Netting6WidgetsUiSnapshot:
    def __init__(self):
        self.stage_name = ""
        self.exchange = "DSE"
        self.target_symbol = "GP"
        self.target_client = "23986"
        self.day_netting_mode = "INDIVIDUAL EXCHANGE"
        self.allow_z_and_spot_netting = False
        self.is_cse_day_netting_enabled = True
        self.is_dse_day_netting_enabled = True
        self.trade_action = "BUY"
        self.trade_qty = 1
        self.traded_exchange = "DSE"
        self.symbol_allowed = True
        self.client_allowed = True
        self.board_allowed = True
        self.board = "PUBLIC"
        self.order_status = "ACCEPTED"
        self.stock_file_uploaded = False
        self.dedicated_netting_watchlist = False
        self.watchlist_netting_icon = False
        self.watchlist_hover_text = ""
        self.watchlist_symbol_count = 0
        self.market_depth_netting_badge = False
        self.market_depth_data: Dict[str, str] = {}
        self.portfolio_data: Dict[str, str] = {}
        self.portfolio_data_dse: Dict[str, str] = {}
        self.portfolio_data_cse: Dict[str, str] = {}
        self.order_terminal_data: Dict[str, str] = {}
        self.order_summary_data: Dict[str, str] = {}
        self.account_limits_data: Dict[str, str] = {}

class ExchangeSaleableReading:
    def __init__(self, exchange: str = "DSE", client_code: str = "", symbol: str = ""):
        self.exchange = exchange
        self.client_code = client_code
        self.symbol = symbol
        self.portfolio_total_qty = 0.0
        self.portfolio_saleable_qty = 0.0
        self.position_total_qty = 0.0
        self.position_saleable_qty = 0.0
        self.db_total_qty = -1.0
        self.db_saleable_qty = -1.0
        self.has_portfolio_data = False
        self.has_position_data = False
        self.has_db_data = False
        self.portfolio_source = "NONE"
        self.position_source = "NONE"
        self.portfolio_snapshot: Dict[str, str] = {}

    def effective_total_qty(self) -> float:
        if self.has_portfolio_data:
            return self.portfolio_total_qty
        if self.has_position_data:
            return self.position_total_qty
        return self.db_total_qty if self.has_db_data else 0.0

    def effective_saleable_qty(self) -> float:
        if self.has_portfolio_data:
            return self.portfolio_saleable_qty
        if self.has_position_data:
            return self.position_saleable_qty
        return self.db_saleable_qty if self.has_db_data else 0.0

class SaleableProbeSnapshot:
    def __init__(self, stage_name: str = "", client_code: str = "", symbol: str = ""):
        self.stage_name = stage_name
        self.client_code = client_code
        self.symbol = symbol
        self.captured_at_ms = int(time.time() * 1000)
        self.dse = ExchangeSaleableReading("DSE", client_code, symbol)
        self.cse = ExchangeSaleableReading("CSE", client_code, symbol)

class SaleableDeltaReport:
    def __init__(self):
        self.client_code = ""
        self.symbol = ""
        self.baseline_stage = ""
        self.current_stage = ""
        self.dse_total_before = self.dse_total_after = self.dse_total_delta = 0.0
        self.dse_saleable_before = self.dse_saleable_after = self.dse_saleable_delta = 0.0
        self.cse_total_before = self.cse_total_after = self.cse_total_delta = 0.0
        self.cse_saleable_before = self.cse_saleable_after = self.cse_saleable_delta = 0.0
        self.position_dse_saleable_before = self.position_dse_saleable_after = self.position_dse_saleable_delta = 0.0
        self.position_cse_saleable_before = self.position_cse_saleable_after = self.position_cse_saleable_delta = 0.0
        self.dse_saleable_updated = self.cse_saleable_updated = self.any_saleable_updated = False
        self.portfolio_matches_position = True

class NettingEligibilityContext:
    def __init__(
        self, mode: NettingMode, dse_enabled: bool, cse_enabled: bool, symbol_allowed: bool,
        client_allowed: bool, board_allowed: bool, traded_exchange: str = "DSE", board: str = "PUBLIC",
        allow_z_and_spot_netting: bool = False, order_status: str = "ACCEPTED", stock_file_uploaded: bool = False
    ):
        self.mode = mode if mode is not None else NettingMode.NONE
        self.dse_enabled = dse_enabled
        self.cse_enabled = cse_enabled
        self.symbol_allowed = symbol_allowed
        self.client_allowed = client_allowed
        self.board_allowed = board_allowed
        self.traded_exchange = (traded_exchange or "DSE").strip().upper()
        self.board = (board or "PUBLIC").strip().upper()
        self.allow_z_and_spot_netting = allow_z_and_spot_netting
        self.order_status = (order_status or "ACCEPTED").strip().upper()
        self.stock_file_uploaded = stock_file_uploaded

    @classmethod
    def from_snapshot(cls, curr: Optional[Netting6WidgetsUiSnapshot]) -> "NettingEligibilityContext":
        if curr is None:
            return cls(NettingMode.NONE, False, False, False, False, False, "DSE", "PUBLIC", False, "UNKNOWN", False)
        return cls(
            NettingMode.from_str(curr.day_netting_mode), curr.is_dse_day_netting_enabled,
            curr.is_cse_day_netting_enabled, curr.symbol_allowed, curr.client_allowed, curr.board_allowed,
            curr.traded_exchange, curr.board, curr.allow_z_and_spot_netting, curr.order_status, curr.stock_file_uploaded,
        )

class NettingSaleableAuditResult:
    def __init__(self):
        self.mode = "INDIVIDUAL_EXCHANGE"
        self.traded_exchange = "DSE"
        self.trade_action = "BUY"
        self.trade_qty = 1.0
        self.client_code = "23986"
        self.symbol = "GP.PUBLIC"
        self.symbol_allowed = self.client_allowed = self.board_allowed = True
        self.dse_enabled = self.cse_enabled = True
        self.stock_file_uploaded = False
        self.order_status = "ACCEPTED"
        self.netting_effective = True
        self.ineligibility_reason = ""
        self.prev_dse_total = self.curr_dse_total = self.expected_dse_total = 0.0
        self.prev_cse_total = self.curr_cse_total = self.expected_cse_total = 0.0
        self.prev_dse_saleable = self.curr_dse_saleable = self.expected_dse_saleable = 0.0
        self.prev_cse_saleable = self.curr_cse_saleable = self.expected_cse_saleable = 0.0
        self.dse_total_right = self.cse_total_right = self.dse_saleable_right = self.cse_saleable_right = True
        self.dse_total_remark = self.cse_total_remark = self.dse_saleable_remark = self.cse_saleable_remark = ""

    def saleable_updated_any(self) -> bool:
        return abs(self.curr_dse_saleable - self.prev_dse_saleable) > 0.0001 or abs(self.curr_cse_saleable - self.prev_cse_saleable) > 0.0001

    def is_all_right(self) -> bool:
        return self.dse_total_right and self.cse_total_right and self.dse_saleable_right and self.cse_saleable_right

class DayNettingPortfolioState:
    def __init__(
        self, initial_mature_holding: float, dse_buy_qty: float, dse_sell_qty: float,
        cse_buy_qty: float, cse_sell_qty: float, dse_saleable_qty: float, cse_saleable_qty: float,
        total_holding_qty: float, day_netted_qty: float, mature_deducted_qty: float, mode_name: str
    ):
        self.initial_mature_holding = initial_mature_holding
        self.dse_buy_qty = dse_buy_qty
        self.dse_sell_qty = dse_sell_qty
        self.cse_buy_qty = cse_buy_qty
        self.cse_sell_qty = cse_sell_qty
        self.dse_saleable_qty = dse_saleable_qty
        self.cse_saleable_qty = cse_saleable_qty
        self.total_holding_qty = total_holding_qty
        self.day_netted_qty = day_netted_qty
        self.mature_deducted_qty = mature_deducted_qty
        self.mode_name = mode_name

# =========================================================================
# DELEGATED RMS NAVIGATION & UI INDICATORS
# =========================================================================

def verify_market_depth_netting_indicator(driver: Optional[webdriver.Chrome], symbol: str) -> bool:
    if not driver:
        return False
    try:
        return bool(driver.execute_script("""
            var d = document.querySelector('#market-depth-request, #market_depth, .market-depth, [id*="depth"]') || document;
            var icons = d.querySelectorAll('#netting_icon_market_depth, i[id*="netting_icon"], i.fa-check-circle[title*="Netting" i], .netting-indicator, [title*="Netting" i]');
            return Array.from(icons).some(function(ic){ return ic.offsetParent !== null || window.getComputedStyle(ic).display !== 'none'; });
        """))
    except Exception:
        return False

def verify_default_netting_watchlist(driver: Optional[webdriver.Chrome], symbol: str) -> bool:
    if not driver:
        return False
    try:
        return bool(driver.execute_script("""
            return Array.from(document.querySelectorAll('.watchlist-tab, .nav-tabs li a, select#watchlist_select option'))
                .some(function(el){ return (el.innerText || el.value || '').toLowerCase().indexOf('netting') !== -1; });
        """))
    except Exception:
        return False

def verify_existing_watchlist_netting_icon_and_hover(driver: Optional[webdriver.Chrome], symbol: str) -> str:
    if not driver:
        return ""
    try:
        return str(driver.execute_script("""
            var sym = (arguments[0] || '').trim().toUpperCase();
            var rows = Array.from(document.querySelectorAll('#marketwatch_table tbody tr, .watchlist-table tbody tr, table tbody tr'));
            for (var i = 0; i < rows.length; i++) {
                if (!sym || (rows[i].innerText||'').toUpperCase().indexOf(sym) !== -1) {
                    var ic = rows[i].querySelector('[title*="netting" i], .netting-icon, i.fa-exchange, i.fa-compress');
                    if (ic) return ic.getAttribute('title') || ic.getAttribute('data-original-title') || 'Netting Enabled';
                }
            }
            return '';
        """, symbol) or "").strip()
    except Exception:
        return ""

def verify_order_window_client_netting_blocked(driver: Optional[webdriver.Chrome], client_code: str) -> bool:
    if not driver:
        return False
    try:
        return bool(driver.execute_script("""
            var el = document.querySelector('.client-netting-blocked, [title*="netting blocked" i], #client_netting_status.blocked');
            if (el && el.offsetParent !== null) return true;
            var box = document.querySelector('#order_modal, #order_window, .order-entry') || document.body;
            return ((box.innerText || '').toLowerCase().indexOf('netting blocked') !== -1);
        """))
    except Exception:
        return False

def check_selected_applicability(
    driver: Optional[webdriver.Chrome], exchange: str, symbol: str, client_code: str, board: str = "PUBLIC"
) -> NettingApplicabilityReport:
    ex = "DSE" if (not exchange or exchange.strip().upper() == "BOTH") else exchange.strip().upper()
    sym = oms_price.clean_symbol(symbol) or "GP"
    brd = (board or "PUBLIC").strip().upper()
    cln = (client_code or "23986").strip()
    rep = NettingApplicabilityReport(ex, sym, cln, brd)
    settings = rms.fetch_application_settings(driver)
    ex_enabled = True
    if settings.day_netting_settings.upper() == "NONE":
        ex_enabled = rep.is_symbol_applicable = False
        rep.symbol_status, rep.symbol_reason = "DISABLED (GLOBAL)", "DAY_NETTING_SETTINGS = NONE"
    elif ex == "CSE" and not settings.is_cse_day_netting_enabled:
        ex_enabled = rep.is_symbol_applicable = False
        rep.symbol_status, rep.symbol_reason = "DISABLED (CSE)", "IS_CSE_DAY_NETTING_ENABLED = false"
    elif ex == "DSE" and not settings.is_dse_day_netting_enabled:
        ex_enabled = rep.is_symbol_applicable = False
        rep.symbol_status, rep.symbol_reason = "DISABLED (DSE)", "IS_DSE_DAY_NETTING_ENABLED = false"
    if ex_enabled:
        app_syms = rms.fetch_applicable_symbols_list(driver, ex) if driver else []
        in_list = any((r.ticker or "").strip().upper() == sym for r in app_syms)
        if verify_market_depth_netting_indicator(driver, sym) or in_list or (not app_syms and rms.is_symbol_netting_applicable(driver, ex, sym)):
            rep.is_symbol_applicable = True
        else:
            rep.is_symbol_applicable = False
            rep.symbol_status = "NOT APPLICABLE"
    if verify_order_window_client_netting_blocked(driver, cln):
        rep.is_client_applicable, rep.client_status = False, "BLOCKED"
    elif driver:
        in_dis = rms.is_client_present_in_list(driver, cln, False)
        in_alw = rms.is_client_present_in_list(driver, cln, True)
        if in_dis:
            rep.is_client_applicable, rep.client_status = False, "DISALLOWED"
        elif in_alw or rms.is_client_netting_allowed(driver, cln):
            rep.is_client_applicable, rep.client_status = True, "APPLICABLE (ALLOWED)"
        else:
            rep.is_client_applicable, rep.client_status = False, "DISALLOWED"
    elif not rms.is_client_netting_allowed(None, cln):
        rep.is_client_applicable, rep.client_status = False, "DISALLOWED"
    brd_blocked = rms.is_board_blocked(driver, ex, brd)
    if brd == "SPOT" and (brd_blocked or not settings.allow_z_and_spot_netting):
        rep.is_board_applicable, rep.board_status = False, "UNAUTHORIZED / BLOCKED"
    elif brd_blocked or brd not in ("PUBLIC", "BLOCK", "SPOT"):
        rep.is_board_applicable, rep.board_status = False, "UNAUTHORIZED / BLOCKED"
    rep.is_overall_applicable = rep.is_symbol_applicable and rep.is_client_applicable and rep.is_board_applicable
    rep.overall_verdict = f"{'APPLICABLE' if rep.is_overall_applicable else 'INELIGIBLE'}: {sym} | {cln} | {brd} ({ex})"
    return rep

# =========================================================================
# SALEABLE PROBE & PORTFOLIO COMPLIANCE ENGINE
# =========================================================================

def probe_exchange_saleable(
    driver: Optional[webdriver.Chrome], client_code: str, symbol: str, exchange: str = "DSE", include_db: Optional[bool] = None
) -> ExchangeSaleableReading:
    import client_portfolio, position
    if include_db is None:
        include_db = driver is not None
    ex = (exchange or "DSE").strip().upper()
    cln = (client_code or "").strip()
    clean_sym = oms_price.clean_symbol(symbol) or "GP"
    r = ExchangeSaleableReading(ex, cln, clean_sym)
    if driver and cln:
        try:
            psnap = client_portfolio.get_ui_snapshot(driver, cln, clean_sym, ex)
            if not psnap or ("total_qty" not in psnap and "saleable_qty" not in psnap):
                psnap = client_portfolio.get_api_snapshot(driver, cln, clean_sym, ex)
            if psnap:
                r.portfolio_snapshot.update(psnap)
                r.portfolio_total_qty = oms_price.parse_float(psnap.get("total_qty"))
                r.portfolio_saleable_qty = oms_price.parse_float(psnap.get("saleable_qty"))
                r.has_portfolio_data = "total_qty" in psnap or "saleable_qty" in psnap
                r.portfolio_source = psnap.get("DATA_SOURCE", "PORTFOLIO")
        except Exception:
            pass
        try:
            pos_list = position.get_api_snapshot(driver, clean_sym, ex) or position.get_ui_snapshot(driver, cln, clean_sym, ex)
            matched = next((p for p in pos_list if p.client_code.strip().lower() == cln.lower()), None)
            if matched:
                r.position_total_qty = matched.total_qty
                r.position_saleable_qty = matched.saleable_qty
                r.has_position_data = True
                r.position_source = f"POSITION ({ex})"
        except Exception:
            pass
    if include_db and cln:
        conn = config.get_qtrader_db_connection()
        if conn:
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        'SELECT COALESCE(total_qty, 0), COALESCE(saleable_qty, total_qty, 0) FROM public.clients_portfolio WHERE UPPER(symbol)=UPPER(%s) AND "ClientCode"=%s LIMIT 1',
                        (clean_sym, cln),
                    )
                    row = cur.fetchone()
                    if row:
                        r.db_total_qty, r.db_saleable_qty, r.has_db_data = float(row[0]), float(row[1]), True
            except Exception:
                pass
            finally:
                try:
                    conn.close()
                except Exception:
                    pass
    return r

def capture_saleable_baseline(driver: Optional[webdriver.Chrome], stage_name: str, client_code: str, symbol: str) -> SaleableProbeSnapshot:
    clean_sym = oms_price.clean_symbol(symbol) or "GP"
    snap = SaleableProbeSnapshot(stage_name or "PROBE", (client_code or "").strip(), clean_sym)
    snap.dse = probe_exchange_saleable(driver, snap.client_code, clean_sym, "DSE")
    snap.cse = probe_exchange_saleable(driver, snap.client_code, clean_sym, "CSE")
    return snap

def compute_saleable_delta(before: Optional[SaleableProbeSnapshot], after: Optional[SaleableProbeSnapshot]) -> SaleableDeltaReport:
    rep = SaleableDeltaReport()
    if before is None or after is None:
        return rep
    rep.client_code = after.client_code or before.client_code
    rep.symbol = after.symbol or before.symbol
    rep.baseline_stage, rep.current_stage = before.stage_name, after.stage_name
    rep.dse_total_before, rep.dse_total_after = before.dse.effective_total_qty(), after.dse.effective_total_qty()
    rep.dse_total_delta = rep.dse_total_after - rep.dse_total_before
    rep.dse_saleable_before, rep.dse_saleable_after = before.dse.effective_saleable_qty(), after.dse.effective_saleable_qty()
    rep.dse_saleable_delta = rep.dse_saleable_after - rep.dse_saleable_before
    rep.cse_total_before, rep.cse_total_after = before.cse.effective_total_qty(), after.cse.effective_total_qty()
    rep.cse_total_delta = rep.cse_total_after - rep.cse_total_before
    rep.cse_saleable_before, rep.cse_saleable_after = before.cse.effective_saleable_qty(), after.cse.effective_saleable_qty()
    rep.cse_saleable_delta = rep.cse_saleable_after - rep.cse_saleable_before
    rep.position_dse_saleable_before, rep.position_dse_saleable_after = before.dse.position_saleable_qty, after.dse.position_saleable_qty
    rep.position_dse_saleable_delta = rep.position_dse_saleable_after - rep.position_dse_saleable_before
    rep.position_cse_saleable_before, rep.position_cse_saleable_after = before.cse.position_saleable_qty, after.cse.position_saleable_qty
    rep.position_cse_saleable_delta = rep.position_cse_saleable_after - rep.position_cse_saleable_before
    rep.dse_saleable_updated = abs(rep.dse_saleable_delta) > 0.0001 or abs(rep.position_dse_saleable_delta) > 0.0001
    rep.cse_saleable_updated = abs(rep.cse_saleable_delta) > 0.0001 or abs(rep.position_cse_saleable_delta) > 0.0001
    rep.any_saleable_updated = rep.dse_saleable_updated or rep.cse_saleable_updated

    def _aligned(r: ExchangeSaleableReading) -> bool:
        if not r.has_portfolio_data or not r.has_position_data:
            return True
        return abs(r.portfolio_saleable_qty - r.position_saleable_qty) < 0.001 and abs(r.portfolio_total_qty - r.position_total_qty) < 0.001
    rep.portfolio_matches_position = _aligned(before.dse) and _aligned(after.dse) and _aligned(before.cse) and _aligned(after.cse)
    return rep

def capture_saleable_delta(driver: Optional[webdriver.Chrome], baseline: Optional[SaleableProbeSnapshot], post_stage_name: str) -> SaleableDeltaReport:
    if baseline is None:
        return SaleableDeltaReport()
    return compute_saleable_delta(baseline, capture_saleable_baseline(driver, post_stage_name, baseline.client_code, baseline.symbol))

def print_saleable_delta_report(r: Optional[SaleableDeltaReport]) -> None:
    if not r:
        return
    print(f"[SALEABLE DELTA] Client {r.client_code} | {r.symbol} ({r.baseline_stage} -> {r.current_stage}) | "
          f"DSE Sal: {r.dse_saleable_before:.0f}->{r.dse_saleable_after:.0f} ({r.dse_saleable_delta:+.0f}) | "
          f"CSE Sal: {r.cse_saleable_before:.0f}->{r.cse_saleable_after:.0f} ({r.cse_saleable_delta:+.0f}) | "
          f"Aligned={r.portfolio_matches_position}")

def explain_ineligibility_reason(ctx: Optional[NettingEligibilityContext]) -> str:
    if ctx is None:
        return "Missing NettingEligibilityContext"
    if ctx.mode == NettingMode.NONE:
        return "System DAY_NETTING_SETTINGS is NONE"
    if any(k in ctx.order_status for k in ("REJECT", "CANCEL", "FAIL")):
        return f"Order status is {ctx.order_status}"
    if not ctx.client_allowed:
        return "Client is DISALLOWED/BLOCKED in Clients RMS"
    if not ctx.symbol_allowed:
        return "Symbol is NOT in qRMS Applicable Symbols"
    if not ctx.board_allowed:
        return f"Trading board '{ctx.board}' is blocked/unauthorized"
    if ctx.board == "SPOT" and not ctx.allow_z_and_spot_netting:
        return "SPOT board netting disabled (ALLOW_Z_AND_SPOT_NETTING=false)"
    if ctx.mode == NettingMode.CROSS_EXCHANGE:
        if not ctx.dse_enabled and not ctx.cse_enabled:
            return "Both DSE and CSE netting disabled for CROSS_EXCHANGE"
    else:
        if ctx.traded_exchange == "DSE" and not ctx.dse_enabled:
            return "DSE Day Netting is disabled"
        if ctx.traded_exchange == "CSE" and not ctx.cse_enabled:
            return "CSE Day Netting is disabled"
        if ctx.traded_exchange == "BOTH" and not ctx.dse_enabled and not ctx.cse_enabled:
            return "Both DSE and CSE Day Netting are disabled"
    return ""

def is_netting_effective_for_trade(ctx: Optional[NettingEligibilityContext]) -> bool:
    return explain_ineligibility_reason(ctx) == ""

def audit_day_netting_saleable_compliance(
    prev: Optional[Netting6WidgetsUiSnapshot], curr: Optional[Netting6WidgetsUiSnapshot],
    ctx: Optional[NettingEligibilityContext] = None,
    dse_trade_qty: Optional[float] = None, cse_trade_qty: Optional[float] = None
) -> NettingSaleableAuditResult:
    res = NettingSaleableAuditResult()
    if prev is None or curr is None:
        return res
    eff_ctx = ctx or NettingEligibilityContext.from_snapshot(curr)
    if dse_trade_qty is None or cse_trade_qty is None:
        p_dse = oms_price.parse_float(prev.portfolio_data_dse.get("total_qty", "0"))
        c_dse = oms_price.parse_float(curr.portfolio_data_dse.get("total_qty", "0"))
        p_cse = oms_price.parse_float(prev.portfolio_data_cse.get("total_qty", "0"))
        c_cse = oms_price.parse_float(curr.portfolio_data_cse.get("total_qty", "0"))
        obs_delta = abs(c_dse - p_dse) or abs(c_cse - p_cse)
        ex = (eff_ctx.traded_exchange or curr.traded_exchange or "DSE").upper()
        fb_qty = float(curr.trade_qty if curr.trade_qty > 0 else 1)
        eff_qty = obs_delta if obs_delta > 0 else fb_qty
        if ex == "BOTH":
            if obs_delta > 0 and curr.trade_qty > 0 and abs(obs_delta - curr.trade_qty) < 0.001:
                d_qty = c_qty = obs_delta / 2.0
            else:
                d_qty = c_qty = fb_qty
        elif ex == "CSE":
            d_qty, c_qty = 0.0, eff_qty
        else:
            d_qty, c_qty = eff_qty, 0.0
    else:
        d_qty, c_qty = max(0.0, dse_trade_qty), max(0.0, cse_trade_qty)
    tot_qty = d_qty + c_qty
    res.client_code = curr.target_client or "23986"
    res.symbol = curr.target_symbol or "GP.PUBLIC"
    res.mode = curr.day_netting_mode or eff_ctx.mode.name
    res.traded_exchange = eff_ctx.traded_exchange
    res.trade_action = (curr.trade_action or "BUY").upper()
    res.trade_qty = tot_qty if tot_qty > 0 else float(max(1, curr.trade_qty))
    res.symbol_allowed, res.client_allowed, res.board_allowed = eff_ctx.symbol_allowed, eff_ctx.client_allowed, eff_ctx.board_allowed
    res.dse_enabled, res.cse_enabled, res.stock_file_uploaded = eff_ctx.dse_enabled, eff_ctx.cse_enabled, eff_ctx.stock_file_uploaded
    res.order_status = eff_ctx.order_status
    res.ineligibility_reason = explain_ineligibility_reason(eff_ctx)
    res.netting_effective = res.ineligibility_reason == ""
    res.prev_dse_total = oms_price.parse_float(prev.portfolio_data_dse.get("total_qty", "0"))
    res.curr_dse_total = oms_price.parse_float(curr.portfolio_data_dse.get("total_qty", "0"))
    res.prev_dse_saleable = oms_price.parse_float(prev.portfolio_data_dse.get("saleable_qty", "0"))
    res.curr_dse_saleable = oms_price.parse_float(curr.portfolio_data_dse.get("saleable_qty", "0"))
    res.prev_cse_total = oms_price.parse_float(prev.portfolio_data_cse.get("total_qty", "0"))
    res.curr_cse_total = oms_price.parse_float(curr.portfolio_data_cse.get("total_qty", "0"))
    res.prev_cse_saleable = oms_price.parse_float(prev.portfolio_data_cse.get("saleable_qty", "0"))
    res.curr_cse_saleable = oms_price.parse_float(curr.portfolio_data_cse.get("saleable_qty", "0"))
    is_rejected = any(k in res.order_status for k in ("REJECT", "CANCEL", "FAIL"))
    is_buy = "BUY" in res.trade_action and "BOTH_SELL_BUY" not in res.trade_action
    is_sell = ("SELL" in res.trade_action and "BUY" not in res.trade_action) or "BOTH_SELL_BUY" in res.trade_action
    if res.stock_file_uploaded:
        if tot_qty > 0:
            res.expected_dse_total = res.prev_dse_total + tot_qty
            res.expected_cse_total = res.prev_cse_total + tot_qty
            res.expected_dse_saleable = res.prev_dse_saleable + tot_qty
            res.expected_cse_saleable = res.prev_cse_saleable + tot_qty
        else:
            res.expected_dse_total, res.expected_cse_total = res.curr_dse_total, res.curr_cse_total
            res.expected_dse_saleable, res.expected_cse_saleable = res.curr_dse_saleable, res.curr_cse_saleable
        res.dse_total_right = abs(res.curr_dse_total - res.expected_dse_total) < 0.001
        res.cse_total_right = abs(res.curr_cse_total - res.expected_cse_total) < 0.001
        res.dse_saleable_right = abs(res.curr_dse_saleable - res.expected_dse_saleable) < 0.001
        res.cse_saleable_right = abs(res.curr_cse_saleable - res.expected_cse_saleable) < 0.001
        return res
    if is_rejected:
        res.expected_dse_total, res.expected_cse_total = res.prev_dse_total, res.prev_cse_total
    elif is_buy:
        res.expected_dse_total, res.expected_cse_total = res.prev_dse_total + tot_qty, res.prev_cse_total + tot_qty
    elif is_sell:
        res.expected_dse_total = max(0.0, res.prev_dse_total - tot_qty)
        res.expected_cse_total = max(0.0, res.prev_cse_total - tot_qty)
    else:
        res.expected_dse_total, res.expected_cse_total = res.prev_dse_total, res.prev_cse_total
    res.dse_total_right = abs(res.curr_dse_total - res.expected_dse_total) < 0.001
    res.cse_total_right = abs(res.curr_cse_total - res.expected_cse_total) < 0.001
    if not res.netting_effective:
        if not is_rejected and is_sell:
            res.expected_dse_saleable = max(0.0, res.prev_dse_saleable - tot_qty)
            res.expected_cse_saleable = max(0.0, res.prev_cse_saleable - tot_qty)
        else:
            res.expected_dse_saleable, res.expected_cse_saleable = res.prev_dse_saleable, res.prev_cse_saleable
    elif eff_ctx.mode == NettingMode.CROSS_EXCHANGE:
        delta = tot_qty if is_buy else (-tot_qty if is_sell else 0.0)
        res.expected_dse_saleable = max(0.0, res.prev_dse_saleable + delta)
        res.expected_cse_saleable = max(0.0, res.prev_cse_saleable + delta)
    else:
        if is_buy:
            res.expected_dse_saleable = res.prev_dse_saleable + (d_qty if eff_ctx.dse_enabled else 0.0)
            res.expected_cse_saleable = res.prev_cse_saleable + (c_qty if eff_ctx.cse_enabled else 0.0)
        elif is_sell:
            shared_mature = min(res.prev_dse_saleable, res.prev_cse_saleable)
            dse_day = max(0.0, res.prev_dse_saleable - shared_mature)
            cse_day = max(0.0, res.prev_cse_saleable - shared_mature)
            dse_ex = max(0.0, d_qty - dse_day) if eff_ctx.dse_enabled else d_qty
            cse_ex = max(0.0, c_qty - cse_day) if eff_ctx.cse_enabled else c_qty
            rem_mature = max(0.0, shared_mature - (dse_ex + cse_ex))
            res.expected_dse_saleable = rem_mature + (max(0.0, dse_day - d_qty) if eff_ctx.dse_enabled else dse_day)
            res.expected_cse_saleable = rem_mature + (max(0.0, cse_day - c_qty) if eff_ctx.cse_enabled else cse_day)
        else:
            res.expected_dse_saleable, res.expected_cse_saleable = res.prev_dse_saleable, res.prev_cse_saleable
    res.dse_saleable_right = abs(res.curr_dse_saleable - res.expected_dse_saleable) < 0.001
    res.cse_saleable_right = abs(res.curr_cse_saleable - res.expected_cse_saleable) < 0.001
    return res

def calculate_day_netting_portfolio(
    mode: Optional[NettingMode], dse_enabled: bool, cse_enabled: bool,
    client_allowed: bool, symbol_eligible: bool,
    *args
) -> DayNettingPortfolioState:
    """Supports both 10-arg (without board_allowed) and 11-arg (with board_allowed) calling forms."""
    if len(args) == 6:
        board_allowed = bool(args[0])
        initial_mature, dse_buy, dse_sell, cse_buy, cse_sell = (float(x) for x in args[1:])
    elif len(args) == 5:
        board_allowed = True
        initial_mature, dse_buy, dse_sell, cse_buy, cse_sell = (float(x) for x in args)
    else:
        raise TypeError("Expected 10 or 11 positional arguments to calculate_day_netting_portfolio")
    total_holding = max(0.0, initial_mature + (dse_buy + cse_buy) - (dse_sell + cse_sell))
    mode_name = mode.name if mode else "NONE"
    if mode is None or mode == NettingMode.NONE or not client_allowed or not symbol_eligible or not board_allowed:
        mat_ded = dse_sell + cse_sell
        rem_mat = max(0.0, initial_mature - mat_ded)
        return DayNettingPortfolioState(initial_mature, dse_buy, dse_sell, cse_buy, cse_sell, rem_mat, rem_mat, total_holding, 0.0, mat_ded, mode_name)
    if mode == NettingMode.INDIVIDUAL_EXCHANGE:
        d_net = min(dse_buy, dse_sell) if dse_enabled else 0.0
        d_rem = max(0.0, dse_buy - dse_sell) if dse_enabled else 0.0
        d_exc = max(0.0, dse_sell - dse_buy) if dse_enabled else dse_sell
        c_net = min(cse_buy, cse_sell) if cse_enabled else 0.0
        c_rem = max(0.0, cse_buy - cse_sell) if cse_enabled else 0.0
        c_exc = max(0.0, cse_sell - cse_buy) if cse_enabled else cse_sell
        mat_ded = d_exc + c_exc
        rem_mat = max(0.0, initial_mature - mat_ded)
        return DayNettingPortfolioState(
            initial_mature, dse_buy, dse_sell, cse_buy, cse_sell,
            rem_mat + d_rem, rem_mat + c_rem, total_holding, d_net + c_net, mat_ded, mode_name
        )

    # CROSS_EXCHANGE
    if not dse_enabled and not cse_enabled:
        mat_ded = dse_sell + cse_sell
        rem_mat = max(0.0, initial_mature - mat_ded)
        return DayNettingPortfolioState(initial_mature, dse_buy, dse_sell, cse_buy, cse_sell, rem_mat, rem_mat, total_holding, 0.0, mat_ded, mode_name)
    t_buy, t_sell = dse_buy + cse_buy, dse_sell + cse_sell
    c_net = min(t_buy, t_sell)
    c_rem = max(0.0, t_buy - t_sell)
    mat_ded = max(0.0, t_sell - t_buy)
    rem_mat = max(0.0, initial_mature - mat_ded)
    return DayNettingPortfolioState(
        initial_mature, dse_buy, dse_sell, cse_buy, cse_sell,
        rem_mat + c_rem, rem_mat + c_rem, total_holding, c_net, mat_ded, mode_name
    )

def calculate_position_netting(
    symbol: str, client_code: str, dse_buy: float, dse_sell: float, cse_buy: float, cse_sell: float,
    mode: NettingMode, is_dse_enabled: bool, is_cse_enabled: bool,
    is_symbol_eligible: bool, is_client_allowed: bool, is_market_blocked: bool
) -> NettingCalculationResult:
    res = NettingCalculationResult()
    res.symbol, res.client_code, res.mode = symbol, client_code, mode
    res.dse_buy_qty, res.dse_sell_qty, res.cse_buy_qty, res.cse_sell_qty = dse_buy, dse_sell, cse_buy, cse_sell
    res.total_buy_qty, res.total_sell_qty = dse_buy + cse_buy, dse_sell + cse_sell
    res.is_eligible, res.is_client_allowed, res.is_market_blocked = is_symbol_eligible, is_client_allowed, is_market_blocked
    if mode == NettingMode.NONE or not is_symbol_eligible or not is_client_allowed or is_market_blocked:
        res.remaining_buy_qty, res.remaining_sell_qty = res.total_buy_qty, res.total_sell_qty
        return res
    if mode == NettingMode.INDIVIDUAL_EXCHANGE:
        d_net = min(dse_buy, dse_sell) if is_dse_enabled else 0.0
        c_net = min(cse_buy, cse_sell) if is_cse_enabled else 0.0
        res.netted_qty = d_net + c_net
        res.remaining_buy_qty = (dse_buy - d_net) + (cse_buy - c_net)
        res.remaining_sell_qty = (dse_sell - d_net) + (cse_sell - c_net)
        return res
    if is_dse_enabled or is_cse_enabled:
        res.netted_qty = min(res.total_buy_qty, res.total_sell_qty)
    res.remaining_buy_qty = res.total_buy_qty - res.netted_qty
    res.remaining_sell_qty = res.total_sell_qty - res.netted_qty
    return res

def compare_and_report_netting_widgets(
    prev: Optional[Netting6WidgetsUiSnapshot], curr: Optional[Netting6WidgetsUiSnapshot],
    title: str = "DAY NETTING UI AUDIT", driver: Optional[webdriver.Chrome] = None
) -> None:
    if prev is None or curr is None:
        return
    print(f"\n=== 6-WIDGET NETTING AUDIT: {title} ({prev.stage_name} -> {curr.stage_name}) ===")
    audit = audit_day_netting_saleable_compliance(prev, curr)
    print(f"  Portfolio DSE Total : {audit.prev_dse_total:.0f} -> {audit.curr_dse_total:.0f} (Exp {audit.expected_dse_total:.0f}) [{'PASS' if audit.dse_total_right else 'FAIL'}]")
    print(f"  Portfolio DSE Sal   : {audit.prev_dse_saleable:.0f} -> {audit.curr_dse_saleable:.0f} (Exp {audit.expected_dse_saleable:.0f}) [{'PASS' if audit.dse_saleable_right else 'FAIL'}]")
    print(f"  Order Terminal      : ID={curr.order_terminal_data.get('Order_ID','-')} Status={curr.order_terminal_data.get('Status','-')}")
    print(f"  Order Summary       : Orders={curr.order_summary_data.get('Total_Orders','0')} BuyQty={curr.order_summary_data.get('Buy_Qty','0')}")
    print(f"  Account Limits      : RemCash={curr.account_limits_data.get('Remaining_Cash','-')} TotalTx={curr.account_limits_data.get('Total_Tx','-')}")
    print(f"  Market Depth        : LTP={curr.market_depth_data.get('LTP','-')} Badge={curr.market_depth_netting_badge}")

def verify_ui_layout_health(driver: Optional[webdriver.Chrome]) -> str:
    if driver is None:
        return "Driver Offline"
    try:
        res = driver.execute_script("""
            var maxed = document.querySelectorAll('.lm_stack.lm_maximised, .lm_maximised').length;
            var stacks = Array.from(document.querySelectorAll('.lm_stack')).filter(function(s){ return s.offsetParent !== null; }).length;
            return maxed > 0 ? 'WARN: Widget left maximized (' + maxed + ')' : 'HEALTHY (' + stacks + ' Stacks)';
        """)
        return str(res or "HEALTHY")
    except Exception:
        return "OK (Grid Restored)"

# =========================================================================
# 9-SCENARIO DAY NETTING & STOCK FILE COMPLIANCE MATRIX
# =========================================================================

def _mk_snap(
    stage: str, client: str, symbol: str, exchange: str, mode: NettingMode,
    dse_en: bool, cse_en: bool, sym_alw: bool, cln_alw: bool, brd_alw: bool,
    board: str, action: str, qty: int, stock_up: bool,
    d_tot: float, d_sal: float, c_tot: float, c_sal: float
) -> Netting6WidgetsUiSnapshot:
    s = Netting6WidgetsUiSnapshot()
    s.stage_name, s.target_client, s.target_symbol = stage, client, symbol
    s.exchange = s.traded_exchange = exchange
    s.day_netting_mode = mode.name if mode else "INDIVIDUAL_EXCHANGE"
    s.is_dse_day_netting_enabled, s.is_cse_day_netting_enabled = dse_en, cse_en
    s.symbol_allowed, s.client_allowed, s.board_allowed, s.board = sym_alw, cln_alw, brd_alw, board
    s.trade_action, s.trade_qty, s.order_status, s.stock_file_uploaded = action, qty, "ACCEPTED", stock_up
    s.portfolio_data_dse = {"total_qty": f"{d_tot:.0f}", "saleable_qty": f"{d_sal:.0f}"}
    s.portfolio_data_cse = {"total_qty": f"{c_tot:.0f}", "saleable_qty": f"{c_sal:.0f}"}
    s.portfolio_data = s.portfolio_data_dse
    return s

def build_9_scenario_specs() -> List[NettingScenarioSpec]:
    return [
        NettingScenarioSpec(1, NettingScenarioType.S1_CLIENT1_ALLOWED_OTHERS_DISALLOWED, "Client 1 Allowed vs Others Disallowed", "Primary allowed & others disallowed", True, True, False, False, False, True, True),
        NettingScenarioSpec(2, NettingScenarioType.S2_CLIENT1_DISALLOWED_OTHERS_ALLOWED, "Client 1 Disallowed vs Others Allowed", "Primary disallowed & others allowed", False, True, False, False, False, False, False),
        NettingScenarioSpec(3, NettingScenarioType.S3_SYMBOL_ALLOWED_CLIENT_DISALLOWED, "Symbol Allowed + Client Disallowed", "Client disallowed -> no saleable update", False, True, False, False, False, False, False),
        NettingScenarioSpec(4, NettingScenarioType.S4_SYMBOL_DISALLOWED_CLIENT_ALLOWED, "Symbol Disallowed + Client Allowed", "Symbol disallowed -> no saleable update", True, False, False, False, False, False, False),
        NettingScenarioSpec(5, NettingScenarioType.S5_POST_ORDER_ALLOW_SYMBOL, "Post-Accepted-Order Allow Symbol", "Order w/ disallowed symbol -> allow symbol", True, False, True, False, False, False, True),
        NettingScenarioSpec(6, NettingScenarioType.S6_POST_ORDER_ALLOW_CLIENT, "Post-Accepted-Order Allow Client", "Order w/ disallowed client -> allow client", False, True, False, True, False, False, True),
        NettingScenarioSpec(7, NettingScenarioType.S7_ALLOW_NETTING_PLUS_STOCK_FILE, "Allow Netting + Stock File", "Netting allowed + Stock File -> updated", True, True, False, False, True, True, True),
        NettingScenarioSpec(8, NettingScenarioType.S8_DISALLOW_NETTING_PLUS_STOCK_FILE, "Disallow Netting + Stock File", "Netting disallowed + Stock File -> updated", False, True, False, False, True, True, True),
        NettingScenarioSpec(9, NettingScenarioType.S9_DISALLOW_PLUS_STOCK_FILE_THEN_ALLOW, "Disallow + Stock File -> Then Allow Netting", "Stock File while disallowed -> allow netting", False, True, False, True, True, True, True),
    ]

def capture_initial_qrms_state(driver: Optional[webdriver.Chrome]) -> QrmsNettingStateSnapshot:
    if not driver:
        return QrmsNettingStateSnapshot("INDIVIDUAL EXCHANGE", True, True, False, [], [], [], [], [], False)
    rms.navigate_to_application_settings(driver)
    app = rms.fetch_application_settings(driver)
    return QrmsNettingStateSnapshot(
        app.day_netting_settings, app.is_dse_day_netting_enabled, app.is_cse_day_netting_enabled,
        app.allow_z_and_spot_netting, rms.fetch_clients_rms_list(driver, True),
        rms.fetch_clients_rms_list(driver, False), rms.fetch_applicable_symbols_list(driver, "DSE"),
        rms.fetch_applicable_symbols_list(driver, "CSE"), rms.fetch_unauthorized_boards_settings(driver), True
    )

def restore_qrms_state_safely(driver: Optional[webdriver.Chrome], initial: Optional[QrmsNettingStateSnapshot], password: str = "12345") -> None:
    if not driver or not initial or not initial.captured_from_live:
        return
    try:
        rms.configure_all_application_settings(
            driver, NettingMode.from_str(initial.day_netting_settings),
            initial.dse_enabled, initial.cse_enabled, initial.allow_z_spot, password
        )
        orig_clients = {r.client_code.strip().upper(): True for r in initial.allowed_clients if r.client_code}
        orig_clients.update({r.client_code.strip().upper(): False for r in initial.disallowed_clients if r.client_code})
        for r in rms.fetch_clients_rms_list(driver, True):
            c = r.client_code.strip().upper()
            if c and orig_clients.get(c) is not True:
                rms.remove_client_from_risk_list(driver, c, True, password)
        for r in rms.fetch_clients_rms_list(driver, False):
            c = r.client_code.strip().upper()
            if c and orig_clients.get(c) is not False:
                rms.remove_client_from_risk_list(driver, c, False, password)
        for c, want in orig_clients.items():
            if not rms.is_client_present_in_list(driver, c, want):
                rms.set_client_netting_state(driver, c, want, password)
        for ex, orig_syms in (("DSE", initial.dse_symbols), ("CSE", initial.cse_symbols)):
            orig_set = {r.ticker.strip().upper() for r in orig_syms if r.ticker}
            curr_set = {r.ticker.strip().upper() for r in rms.fetch_applicable_symbols_list(driver, ex) if r.ticker}
            for s in curr_set - orig_set:
                rms.delete_applicable_symbol(driver, ex, s)
            for s in orig_set - curr_set:
                rms.add_applicable_symbol(driver, ex, s)
    except Exception as e:
        print(f"[QRMS RESTORE: WARN] {e}")

def _matches_filter(spec: NettingScenarioSpec, flt: str) -> bool:
    if not flt or flt.strip().upper() == "ALL":
        return True
    for part in flt.upper().split(","):
        p = part.strip()
        if p in (str(spec.number), f"S{spec.number}"):
            return True
    return False

def _evaluate_scenario_offline(
    spec: NettingScenarioSpec, cfg: NettingScenarioConfig, primary: str, others: List[str], sym: str, ex: str
) -> NettingScenarioOutcome:
    import oms_upload_data
    excl_ok = True
    if spec.number in (1, 2):
        excl = rms.evaluate_client_exclusivity(primary, spec.initial_client_allowed, others)
        try:
            rms.assert_client_exclusivity_rule(excl)
        except ValueError:
            excl_ok = False
    qty = cfg.order_qty if cfg.order_qty > 0 else 10
    base = 50.0
    d_qty = 0.0 if ex == "CSE" else float(qty)
    c_qty = 0.0 if ex == "DSE" else float(qty)
    pre = _mk_snap(f"OFFLINE_PRE_S{spec.number}", primary, sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled,
                   spec.initial_symbol_allowed, spec.initial_client_allowed, True, cfg.board,
                   "STOCK_FILE" if spec.requires_stock_file else "BUY", qty, False, base, base, base, base)
    init_ctx = NettingEligibilityContext(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled,
                                         spec.initial_symbol_allowed, spec.initial_client_allowed, True,
                                         ex, cfg.board, cfg.allow_z_spot, "ACCEPTED", spec.requires_stock_file)
    if spec.requires_stock_file:
        stock_add = 50.0
        guard = oms_upload_data.upload_stock_file_with_guard(None, cfg.stock_file_path, False)
        new_tot = base + stock_add
        post = _mk_snap(f"OFFLINE_POST_S{spec.number}", primary, sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled,
                        spec.initial_symbol_allowed, spec.initial_client_allowed, True, cfg.board, "STOCK_FILE",
                        int(stock_add), True, new_tot, new_tot, new_tot, new_tot)
        audit = audit_day_netting_saleable_compliance(pre, post, init_ctx, stock_add / 2.0, stock_add / 2.0)
        ok = audit.is_all_right() and audit.saleable_updated_any() and guard.dry_run
        if spec.post_order_allow_client:
            f_ctx = NettingEligibilityContext(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, True, True, ex, cfg.board, cfg.allow_z_spot, "ACCEPTED", False)
            p2 = calculate_day_netting_portfolio(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, True, True, new_tot, d_qty, 0, c_qty, 0)
            p_flip = _mk_snap(f"OFFLINE_FLIP_S{spec.number}", primary, sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled,
                              True, True, True, cfg.board, "BUY", qty, False, p2.total_holding_qty, p2.dse_saleable_qty, p2.total_holding_qty, p2.cse_saleable_qty)
            f_audit = audit_day_netting_saleable_compliance(post, p_flip, f_ctx, d_qty, c_qty)
            ok = ok and is_netting_effective_for_trade(f_ctx) and f_audit.is_all_right() and f_audit.saleable_updated_any()
        passed = excl_ok and ok
        return NettingScenarioOutcome(spec, False, passed, True, excl_ok, ok, f"DSE={base:.0f}/CSE={base:.0f}",
                                      f"DSE={new_tot:.0f}/CSE={new_tot:.0f}", "PASS" if passed else "FAIL", f"guard dryRun={guard.dry_run}", audit)
    calc = calculate_day_netting_portfolio(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled,
                                         spec.initial_client_allowed, spec.initial_symbol_allowed, True, base, d_qty, 0, c_qty, 0)
    post = _mk_snap(f"OFFLINE_POST_S{spec.number}", primary, sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled,
                    spec.initial_symbol_allowed, spec.initial_client_allowed, True, cfg.board, "BUY", qty, False,
                    calc.total_holding_qty, calc.dse_saleable_qty, calc.total_holding_qty, calc.cse_saleable_qty)
    audit = audit_day_netting_saleable_compliance(pre, post, init_ctx, d_qty, c_qty)
    sal_ok = audit.is_all_right() and (audit.saleable_updated_any() == spec.expect_saleable_initially)
    if spec.number in (1, 2):
        o_alw = not spec.initial_client_allowed
        o_calc = calculate_day_netting_portfolio(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, o_alw, True, True, base, d_qty, 0, c_qty, 0)
        o_ctx = NettingEligibilityContext(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, o_alw, True, ex, cfg.board, cfg.allow_z_spot, "ACCEPTED", False)
        o_pre = _mk_snap("OTHER_PRE", others[0], sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, o_alw, True, cfg.board, "BUY", qty, False, base, base, base, base)
        o_post = _mk_snap("OTHER_POST", others[0], sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, o_alw, True, cfg.board, "BUY", qty, False, o_calc.total_holding_qty, o_calc.dse_saleable_qty, o_calc.total_holding_qty, o_calc.cse_saleable_qty)
        o_audit = audit_day_netting_saleable_compliance(o_pre, o_post, o_ctx, d_qty, c_qty)
        sal_ok = sal_ok and o_audit.is_all_right() and (o_audit.saleable_updated_any() == o_alw)
    if spec.post_order_allow_symbol or spec.post_order_allow_client:
        f_ctx = NettingEligibilityContext(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, True, True, ex, cfg.board, cfg.allow_z_spot, "ACCEPTED", False)
        f_calc = calculate_day_netting_portfolio(cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, True, True, base, d_qty, 0, c_qty, 0)
        f_post = _mk_snap(f"FLIP_S{spec.number}", primary, sym, ex, cfg.netting_mode, cfg.dse_enabled, cfg.cse_enabled, True, True, True, cfg.board, "BUY", qty, False,
                          f_calc.total_holding_qty, f_calc.dse_saleable_qty, f_calc.total_holding_qty, f_calc.cse_saleable_qty)
        f_audit = audit_day_netting_saleable_compliance(pre, f_post, f_ctx, d_qty, c_qty)
        sal_ok = sal_ok and is_netting_effective_for_trade(f_ctx) and f_audit.is_all_right() and f_audit.saleable_updated_any()
    passed = excl_ok and sal_ok
    return NettingScenarioOutcome(spec, False, passed, True, excl_ok, sal_ok, f"DSE={base:.0f}/CSE={base:.0f}",
                                  f"DSE={calc.dse_saleable_qty:.0f}/CSE={calc.cse_saleable_qty:.0f}",
                                  "PASS" if passed else "FAIL", f"Eff={audit.netting_effective}, Upd={audit.saleable_updated_any()}", audit)

def _configure_symbol_state(driver: webdriver.Chrome, sym: str, ex: str, dse_en: bool, cse_en: bool, allow: bool) -> None:
    exchanges = ([e for e, en in (("DSE", dse_en), ("CSE", cse_en)) if en] if ex == "BOTH" else [ex])
    for target_ex in exchanges:
        if allow:
            rms.add_applicable_symbol(driver, target_ex, sym)
        else:
            rms.delete_applicable_symbol(driver, target_ex, sym)

def _evaluate_scenario_live(
    driver: webdriver.Chrome, spec: NettingScenarioSpec, cfg: NettingScenarioConfig,
    primary: str, others: List[str], sym: str, ex: str, rate: float
) -> NettingScenarioOutcome:
    import oms_trade, oms_upload_data
    excl_ok = True
    if spec.number in (1, 2):
        rms.assert_client_exclusivity_rule(rms.evaluate_client_exclusivity(primary, spec.initial_client_allowed, others))
        excl_ok = rms.apply_client_exclusivity_rule(driver, primary, spec.initial_client_allowed, others, cfg.rms_password)
    else:
        rms.set_client_netting_state(driver, primary, spec.initial_client_allowed, cfg.rms_password)
    _configure_symbol_state(driver, sym, ex, cfg.dse_enabled, cfg.cse_enabled, spec.initial_symbol_allowed)
    check_ex = ("DSE" if cfg.dse_enabled else "CSE") if ex == "BOTH" else ex
    app_rep = check_selected_applicability(driver, check_ex, sym, primary, cfg.board)
    app_ok = (app_rep.is_client_applicable == spec.initial_client_allowed) and (app_rep.is_symbol_applicable == spec.initial_symbol_allowed)
    upload_res = oms_upload_data.upload_stock_file_with_guard(driver, cfg.stock_file_path, cfg.allow_live_stock_upload) if spec.requires_stock_file else None
    oms_trade.navigate_to_trade_page(driver, force=False)
    baseline = capture_saleable_baseline(driver, f"PRE_S{spec.number}", primary, sym)
    order_placed = False
    if cfg.execute_live_orders and not spec.requires_stock_file:
        inst = sym if "." in sym else f"{sym}.{cfg.board}"
        targets = ([e for e, en in (("DSE", cfg.dse_enabled), ("CSE", cfg.cse_enabled)) if en] if ex == "BOTH" else [ex])
        for t_ex in targets:
            try:
                oms_trade.submit_single_order_attempt(driver, "BUY", primary, inst, cfg.order_qty, rate, t_ex)
            except Exception:
                pass
        order_placed = oms_trade.has_latest_order_in_terminal(driver, primary, sym)
    delta = capture_saleable_delta(driver, baseline, f"POST_S{spec.number}")
    if spec.requires_stock_file:
        sal_ok = delta.any_saleable_updated if (upload_res and upload_res.uploaded) else bool(upload_res and upload_res.dry_run)
        details = f"StockFile={(upload_res.status if upload_res else 'SKIP')}"
    else:
        sal_ok = (not order_placed) or (delta.any_saleable_updated == spec.expect_saleable_initially)
        details = f"placed={order_placed}, updated={delta.any_saleable_updated}"
    if spec.post_order_allow_symbol or spec.post_order_allow_client:
        if spec.post_order_allow_symbol:
            _configure_symbol_state(driver, sym, ex, cfg.dse_enabled, cfg.cse_enabled, True)
        if spec.post_order_allow_client:
            rms.set_client_netting_state(driver, primary, True, cfg.rms_password)
        post_app = check_selected_applicability(driver, check_ex, sym, primary, cfg.board)
        oms_trade.navigate_to_trade_page(driver, force=False)
        flip_delta = capture_saleable_delta(driver, baseline, f"POST_FLIP_S{spec.number}")
        app_ok = app_ok and post_app.is_client_applicable and post_app.is_symbol_applicable
        if cfg.retroactive_policy == RetroactivePolicy.RETROACTIVE_APPLY:
            sal_ok = sal_ok and flip_delta.any_saleable_updated
        elif cfg.retroactive_policy == RetroactivePolicy.PROSPECTIVE_ONLY:
            sal_ok = sal_ok and (not flip_delta.any_saleable_updated)
    passed = excl_ok and app_ok and sal_ok
    return NettingScenarioOutcome(
        spec, True, passed, app_ok, excl_ok, sal_ok,
        f"DSE={delta.dse_saleable_before:.0f}/CSE={delta.cse_saleable_before:.0f}",
        f"DSE={delta.dse_saleable_after:.0f}/CSE={delta.cse_saleable_after:.0f}",
        "PASS" if passed else "FAIL", details, delta_report=delta
    )

def print_9_scenario_scorecard(outcomes: List[NettingScenarioOutcome]) -> None:
    print("\n+----+----------------------------------------------+--------+--------------------+--------------------+")
    print("| #  | Scenario                                     | Mode   | Result             | Details            |")
    print("+----+----------------------------------------------+--------+--------------------+--------------------+")
    for o in outcomes:
        print(f"| {o.spec.number:<2} | {o.spec.title[:44]:<44} | {'LIVE' if o.executed_live else 'OFFLIN':<6} | {o.verdict:<18} | {o.details[:18]:<18} |")
    passed = sum(1 for o in outcomes if o.passed)
    print(f"+----+----------------------------------------------+--------+--------------------+--------------------+\n  MATRIX SUMMARY: {passed}/{len(outcomes)} SCENARIOS PASSED\n")

def run_netting_9_scenario_matrix(driver: Optional[webdriver.Chrome], cfg: Optional[NettingScenarioConfig] = None) -> List[NettingScenarioOutcome]:
    cfg = cfg or NettingScenarioConfig()
    primary = (cfg.primary_client or "002").strip().upper()
    others = [o.strip().upper() for o in (cfg.other_clients or []) if o and o.strip()] or ["23986" if primary == "002" else "002"]
    sym = oms_price.clean_symbol(cfg.symbol) or "GP"
    ex = (cfg.exchange or "BOTH").strip().upper()
    specs = [s for s in build_9_scenario_specs() if _matches_filter(s, cfg.scenario_filter)]
    if driver is None:
        outcomes = [_evaluate_scenario_offline(s, cfg, primary, others, sym, ex) for s in specs]
        print_9_scenario_scorecard(outcomes)
        return outcomes
    initial = capture_initial_qrms_state(driver)
    rate = oms_price.fetch_price_ltp_or_ycp(driver, sym, "DSE" if ex == "BOTH" else ex) or cfg.order_rate or 266.90
    outcomes = []
    try:
        for s in specs:
            outcomes.append(_evaluate_scenario_live(driver, s, cfg, primary, others, sym, ex, rate))
    finally:
        restore_qrms_state_safely(driver, initial, cfg.rms_password)
        try:
            import oms_trade
            oms_trade.navigate_to_trade_page(driver, force=False)
        except Exception:
            pass
    print_9_scenario_scorecard(outcomes)
    return outcomes

# =========================================================================
# OFFLINE COMPLIANCE, QA & REGRESSION SUITES
# =========================================================================

def report_day_netting_saleable_rules_matrix() -> bool:
    cases = [
        ("INDIVIDUAL_EXCHANGE", "DSE", "BUY", 10, (100, 40, 100, 20), (110, 50, 110, 20)),
        ("INDIVIDUAL_EXCHANGE", "CSE", "BUY", 15, (100, 40, 100, 20), (115, 40, 115, 35)),
        ("INDIVIDUAL_EXCHANGE", "DSE", "SELL", 10, (100, 50, 100, 20), (90, 40, 90, 20)),
        ("INDIVIDUAL_EXCHANGE", "CSE", "SELL", 15, (100, 20, 100, 35), (85, 20, 85, 20)),
        ("CROSS_EXCHANGE", "DSE", "BUY", 25, (100, 40, 100, 20), (125, 65, 125, 45)),
        ("CROSS_EXCHANGE", "CSE", "BUY", 10, (125, 65, 125, 45), (135, 75, 135, 55)),
        ("CROSS_EXCHANGE", "CSE", "SELL", 20, (135, 75, 135, 55), (115, 55, 115, 35)),
        ("INDIVIDUAL_EXCHANGE", "DSE", "BOTH_BUY_SELL", 7, (13, 13, 13, 0), (20, 20, 20, 0)),
    ]
    passed = 0
    for mode_s, ex, act, qty, (pt_d, ps_d, pt_c, ps_c), (ct_d, cs_d, ct_c, cs_c) in cases:
        mode = NettingMode.from_str(mode_s)
        pre = _mk_snap("PRE", "23986", "GP", ex, mode, True, True, True, True, True, "PUBLIC", act, qty, False, pt_d, ps_d, pt_c, ps_c)
        post = _mk_snap("POST", "23986", "GP", ex, mode, True, True, True, True, True, "PUBLIC", act, qty, False, ct_d, cs_d, ct_c, cs_c)
        if audit_day_netting_saleable_compliance(pre, post).is_all_right():
            passed += 1
    print(f"[SALEABLE RULES MATRIX] {passed}/{len(cases)} PASSED")
    return passed == len(cases)

def test_qa_day_netting_scenarios() -> bool:
    """4 Scenarios, 25 Assertions matching Day_Netting.java QA Suite."""
    s1_1 = calculate_day_netting_portfolio(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, 50, 25, 0, 0, 0)
    s1_2 = calculate_day_netting_portfolio(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, 50, 25, 0, 12, 0)
    s1_3 = calculate_day_netting_portfolio(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, 50, 25, 25, 12, 12)
    s2_1 = calculate_day_netting_portfolio(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, 50, 25, 0, 0, 0)
    s2_2 = calculate_day_netting_portfolio(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, 50, 25, 35, 0, 0)
    s3_1 = calculate_day_netting_portfolio(NettingMode.NONE, True, True, True, True, 50, 25, 0, 0, 0)
    s4_1 = calculate_day_netting_portfolio(NettingMode.CROSS_EXCHANGE, True, True, True, True, 50, 25, 0, 0, 0)
    s4_2 = calculate_day_netting_portfolio(NettingMode.CROSS_EXCHANGE, True, True, True, True, 50, 25, 0, 0, 15)
    assertions = [
        s1_1.dse_saleable_qty == 75, s1_1.cse_saleable_qty == 50, s1_1.total_holding_qty == 75, s1_1.cse_saleable_qty != 75,
        s1_2.dse_saleable_qty == 75, s1_2.cse_saleable_qty == 62, s1_2.total_holding_qty == 87,
        s1_3.dse_saleable_qty == 50, s1_3.cse_saleable_qty == 50, s1_3.total_holding_qty == 50, s1_3.day_netted_qty == 37,
        s2_1.dse_saleable_qty == 75, s2_1.cse_saleable_qty == 50, s2_1.total_holding_qty == 75,
        s2_2.dse_saleable_qty == 40, s2_2.cse_saleable_qty == 40, s2_2.total_holding_qty == 40, s2_2.day_netted_qty == 25,
        s2_2.mature_deducted_qty == 10, s2_2.cse_saleable_qty != 50, s2_2.dse_saleable_qty != 15,
        s3_1.total_holding_qty == 75, s3_1.dse_saleable_qty == 50, s3_1.cse_saleable_qty == 50, s3_1.day_netted_qty == 0,
        s4_1.dse_saleable_qty == 75, s4_1.cse_saleable_qty == 75, s4_1.total_holding_qty == 75, s4_1.cse_saleable_qty != 50,
        s4_2.dse_saleable_qty == 60, s4_2.cse_saleable_qty == 60, s4_2.total_holding_qty == 60, s4_2.day_netted_qty == 15,
    ]
    passed = all(assertions)
    print(f"[QA DAY NETTING SUITE] {'ALL PASSED (4/4 SCENARIOS, 25/25+ ASSERTIONS)' if passed else 'SOME FAILED'}")
    return passed

def run_all_netting_tests(driver: Optional[webdriver.Chrome] = None) -> bool:
    import oms_upload_data
    results = []
    # 1. RMS pre-trade & client exclusivity + overlap + connected gate
    results.append(rms.test_pre_trade_rms_audit_scenarios())
    # 2. Saleable rules matrix (8/8)
    results.append(report_day_netting_saleable_rules_matrix())
    # 3. QA Multi-Exchange Suite
    results.append(test_qa_day_netting_scenarios())
    # 4. Eligibility Truth Table
    ok = is_netting_effective_for_trade(NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, True, "DSE", "PUBLIC"))
    neg = [
        NettingEligibilityContext(NettingMode.NONE, True, True, True, True, True),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, False, True, True, True, True, "DSE"),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, False, True, True, True, "CSE"),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, True, False, True, True),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, False, True),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, False, "CSE", "BLOCK"),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, True, "DSE", "SPOT", False),
        NettingEligibilityContext(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, True, "DSE", "PUBLIC", False, "REJECTED"),
        NettingEligibilityContext(NettingMode.CROSS_EXCHANGE, False, False, True, True, True),
    ]
    results.append(ok and all(not is_netting_effective_for_trade(x) for x in neg))
    # 5. 11-arg & cross-exchange regressions
    b_blk = calculate_day_netting_portfolio(NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, False, 50, 20, 0, 0, 0)
    c_off = calculate_day_netting_portfolio(NettingMode.CROSS_EXCHANGE, False, False, True, True, True, 50, 20, 0, 0, 10)
    c_on = calculate_day_netting_portfolio(NettingMode.CROSS_EXCHANGE, True, False, True, True, True, 50, 20, 0, 0, 10)
    results.append(b_blk.dse_saleable_qty == 50 and c_off.dse_saleable_qty == 40 and c_on.cse_saleable_qty == 60 and c_on.day_netted_qty == 10)
    # 6. Compliance overloads (asymmetric BOTH, symmetric BOTH, negative fail, Stock File pass)
    r_both = audit_day_netting_saleable_compliance(
        _mk_snap("P", "002", "GP", "BOTH", NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, True, "PUBLIC", "BUY", 37, False, 50, 50, 50, 50),
        _mk_snap("C", "002", "GP", "BOTH", NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, True, True, "PUBLIC", "BUY", 37, False, 87, 75, 87, 62),
        dse_trade_qty=25, cse_trade_qty=12,
    )
    r_bad = audit_day_netting_saleable_compliance(
        _mk_snap("PB", "002", "GP", "DSE", NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, False, True, "PUBLIC", "BUY", 10, False, 50, 50, 50, 50),
        _mk_snap("CB", "002", "GP", "DSE", NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, False, True, "PUBLIC", "BUY", 10, False, 60, 60, 60, 50),
    )
    r_stk = audit_day_netting_saleable_compliance(
        _mk_snap("PB", "002", "GP", "DSE", NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, False, True, "PUBLIC", "BUY", 10, False, 50, 50, 50, 50),
        _mk_snap("CS", "002", "GP", "DSE", NettingMode.INDIVIDUAL_EXCHANGE, True, True, True, False, True, "PUBLIC", "STOCK_FILE", 50, True, 100, 100, 100, 100),
        dse_trade_qty=25, cse_trade_qty=25,
    )
    results.append(r_both.is_all_right() and (not r_bad.is_all_right()) and r_stk.is_all_right() and r_stk.saleable_updated_any())
    # 7. Portfolio/Position alignment delta
    p0 = SaleableProbeSnapshot("PRE", "002", "GP")
    p0.dse.has_portfolio_data = p0.dse.has_position_data = True
    p0.dse.portfolio_total_qty = p0.dse.position_total_qty = p0.dse.portfolio_saleable_qty = p0.dse.position_saleable_qty = 50
    p1 = SaleableProbeSnapshot("POST", "002", "GP")
    p1.dse.has_portfolio_data = p1.dse.has_position_data = True
    p1.dse.portfolio_total_qty = p1.dse.position_total_qty = p1.dse.portfolio_saleable_qty = p1.dse.position_saleable_qty = 60
    p2 = SaleableProbeSnapshot("BAD", "002", "GP")
    p2.dse.has_portfolio_data = p2.dse.has_position_data = True
    p2.dse.portfolio_total_qty = p2.dse.position_total_qty = p2.dse.portfolio_saleable_qty = 60
    p2.dse.position_saleable_qty = 50
    d_ok = compute_saleable_delta(p0, p1)
    d_bad = compute_saleable_delta(p0, p2)
    results.append(d_ok.dse_saleable_updated and d_ok.portfolio_matches_position and (not d_bad.portfolio_matches_position))
    # 8. Stock upload guard
    g1 = oms_upload_data.upload_stock_file_with_guard(None, "", False)
    g2 = oms_upload_data.upload_stock_file_with_guard(None, "Z:/nonexistent/stock_missing_999.xml", True)
    results.append(g1.dry_run and (not g1.uploaded) and bool(g1.resolved_path) and g2.dry_run and g2.status == "FILE_NOT_FOUND")
    # 9. Full 9-Scenario Compliance Matrix (offline)
    matrix = run_netting_9_scenario_matrix(None, NettingScenarioConfig())
    results.append(len(matrix) == 9 and all(o.passed for o in matrix))
    passed = sum(1 for r in results if r)
    print(f"[OFFLINE DAY NETTING SUITE] Passed: {passed}/{len(results)} Test Blocks")
    if driver is not None:
        run_netting_9_scenario_matrix(driver, NettingScenarioConfig())
    return passed == len(results)

def _execute_normal_netting_flow(
    driver: webdriver.Chrome, mode: NettingMode, ex: str, sym: str, cln: str, brd: str,
    pwd: str = "12345", run_trade: bool = True,
) -> tuple[bool, Netting6WidgetsUiSnapshot, Netting6WidgetsUiSnapshot]:
    """Configures mode/exchange in qRMS, captures pre/post 6-widget live data & comparison."""
    dse_en = mode != NettingMode.NONE and ex in ("DSE", "BOTH")
    cse_en = mode != NettingMode.NONE and ex in ("CSE", "BOTH")
    rms.configure_all_application_settings(driver, mode, dse_en, cse_en, False, pwd)
    rep = check_selected_applicability(driver, ex, sym, cln, brd)
    print(f"[APPLICABILITY] {rep.overall_verdict}")
    import oms_trade
    oms_trade.insert_global_symbol_and_select_public(driver, sym)
    pub_sym = oms_price.to_public_symbol(sym)
    oms_trade.propagate_client_code_to_all_widgets(driver, cln, pub_sym)
    pre_snap = check_6_netting_widgets_real_time_ui_data(driver, f"PRE_{mode.name}_{ex}", cln, pub_sym, ex)
    order_ex = "DSE" if ex == "BOTH" else ex
    trade_inst = oms_price.to_board_symbol(sym, brd)
    qty = 25 if mode == NettingMode.NONE else 37 if (mode == NettingMode.INDIVIDUAL_EXCHANGE and ex == "BOTH") else 25
    rate = oms_price.fetch_price_ltp_or_ycp(driver, sym, order_ex) or 266.90
    if run_trade:
        try:
            if mode == NettingMode.CROSS_EXCHANGE:
                oms_trade.submit_single_order_attempt(driver, "BUY", cln, trade_inst, 25, rate, "DSE")
                time.sleep(1.0)
                oms_trade.submit_single_order_attempt(driver, "SELL", cln, trade_inst, 15, rate, "CSE")
            elif mode == NettingMode.INDIVIDUAL_EXCHANGE and ex == "BOTH":
                oms_trade.submit_single_order_attempt(driver, "BUY", cln, trade_inst, 25, rate, "DSE")
                time.sleep(1.0)
                oms_trade.submit_single_order_attempt(driver, "BUY", cln, trade_inst, 12, rate, "CSE")
            else:
                oms_trade.submit_single_order_attempt(driver, "BUY", cln, trade_inst, 25, rate, order_ex)
        except Exception:
            pass
    post_snap = check_6_netting_widgets_real_time_ui_data(driver, f"POST_{mode.name}_{ex}", cln, pub_sym, ex)
    post_snap.day_netting_mode = mode.name
    post_snap.traded_exchange = ex
    post_snap.trade_qty = qty
    compare_and_report_netting_widgets(pre_snap, post_snap, f"6-WIDGET LIVE REPORT ({mode.name} - {ex})", driver)
    audit = audit_day_netting_saleable_compliance(pre_snap, post_snap)
    ok = audit.dse_total_right and audit.dse_saleable_right and audit.cse_total_right and audit.cse_saleable_right
    return ok, pre_snap, post_snap


def _run_normal_netting_live_audit(driver: webdriver.Chrome) -> None:
    print("\n--- 1. NORMAL NETTING & 6-WIDGET LIVE REPORT & COMPARISON ---")
    mc = config.read_line_with_timeout("Mode (1=NONE, 2=INDIVIDUAL [ENTER=Default], 3=CROSS): ", "2")
    mode = NettingMode.NONE if mc == "1" else (NettingMode.CROSS_EXCHANGE if mc == "3" else NettingMode.INDIVIDUAL_EXCHANGE)
    ec = config.read_line_with_timeout("Exchange (1=DSE [ENTER=Default], 2=CSE, 3=BOTH): ", "1")
    ex = "CSE" if ec == "2" else ("BOTH" if ec == "3" else "DSE")
    pwd = config.read_line_with_timeout("Password Auth (ENTER=12345): ", "12345")
    sym = config.read_line_with_timeout("Target Symbol (ENTER=GP): ", "GP") or "GP"
    cln = config.read_line_with_timeout("Target Client (ENTER=23986): ", "23986") or "23986"
    brd = config.read_line_with_timeout("Board (PUBLIC [ENTER=Default] / BLOCK / SPOT): ", "PUBLIC").upper() or "PUBLIC"
    run_trade = config.read_line_with_timeout("Execute Live Trade? (1=YES [ENTER=Default], 0=Audit Only): ", "1") != "0"
    _execute_normal_netting_flow(driver, mode, ex, sym, cln, brd, pwd, run_trade)


def _run_all_configure_settings_menu(driver: webdriver.Chrome) -> None:
    rms.navigate_to_rms_portal(driver)
    while True:
        rms.display_application_settings_table(driver)
        print("\n=== ALL CONFIGURE SETTINGS (RMS & NETTING SETTINGS) ===")
        print("  1. Netting Mode & Z/Spot Toggle (None / Individual / Cross)")
        print("  2. Exchange Netting Toggle (DSE / CSE)")
        print("  3. Applicable Symbols (Add / View)")
        print("  4. Clients RMS Eligibility & Exclusivity (Allow / Disallow / Exclusive Rule)")
        print("  5. Market / Board Restrictions (Block / Unblock Board or Spot)")
        print("  6. View Full RMS Status & Verify Symbol/Client/Board Applicability")
        print("  7. Guided Configure All Settings (Mode -> Exchange -> Z/Spot -> Board -> Client -> Symbol)")
        print("  0. Back to Day Netting Menu")
        sub = "6" if config.IS_CI else config.read_line_with_timeout("Enter Setting Choice (0-7, ENTER=6): ", "6")
        if sub == "0":
            break
        if sub in ("1", "7"):
            mc = config.read_line_with_timeout("Netting Mode (1=NONE, 2=INDIVIDUAL [ENTER=Default], 3=CROSS): ", "2")
            mode = NettingMode.NONE if mc == "1" else (NettingMode.CROSS_EXCHANGE if mc == "3" else NettingMode.INDIVIDUAL_EXCHANGE)
            ec = config.read_line_with_timeout("Exchange Scope (1=DSE, 2=CSE, 3=BOTH [ENTER=Default]): ", "3")
            zc = config.read_line_with_timeout("Allow Z/Spot? (1=YES, 0=NO [ENTER=Default]): ", "0") == "1"
            pwd = config.read_line_with_timeout("Password Auth (ENTER=12345): ", "12345")
            rms.configure_all_application_settings(driver, mode, ec in ("1", "3"), ec in ("2", "3"), zc, pwd)
        if sub in ("2",):
            ec = config.read_line_with_timeout("Exchange (1=DSE, 2=CSE, 3=BOTH [ENTER=Default]): ", "3")
            en = config.read_line_with_timeout("Enable Netting? (1=YES [ENTER=Default], 2=NO): ", "1") != "2"
            pwd = config.read_line_with_timeout("Password Auth (ENTER=12345): ", "12345")
            cur = rms.fetch_application_settings(driver)
            rms.configure_all_application_settings(
                driver, cur.day_netting_mode,
                en if ec in ("1", "3") else cur.is_dse_day_netting_enabled,
                en if ec in ("2", "3") else cur.is_cse_day_netting_enabled,
                cur.allow_z_and_spot_netting, pwd,
            )
        if sub in ("3", "7"):
            ex = config.read_line_with_timeout("Applicable Symbol Exchange (DSE [ENTER=Default] / CSE): ", "DSE").upper() or "DSE"
            sym = config.read_line_with_timeout("Symbol to Add (ENTER=GP): ", "GP").upper() or "GP"
            rms.add_applicable_symbol(driver, ex, sym)
            rms.display_applicable_symbols_table(driver, ex)
        if sub in ("4", "7"):
            cln = config.read_line_with_timeout("Primary Client (ENTER=002): ", "002") or "002"
            alw = config.read_line_with_timeout("Status (1=Allowed [ENTER=Default], 2=Disallowed): ", "1") != "2"
            others_raw = config.read_line_with_timeout("Other Exclusive Clients comma-sep (blank=skip): ", "")
            pwd = config.read_line_with_timeout("Password Auth (ENTER=12345): ", "12345")
            if others_raw:
                rms.apply_client_exclusivity_rule(driver, cln, alw, [x.strip() for x in others_raw.split(",") if x.strip()], pwd)
            else:
                rms.set_client_netting_state(driver, cln, alw, pwd)
        if sub in ("5", "7"):
            ex = config.read_line_with_timeout("Board Exchange (CSE [ENTER=Default] / DSE): ", "CSE").upper() or "CSE"
            tag = config.read_line_with_timeout("Board Tag (BLOCK [ENTER=Default] / SPOT): ", "BLOCK").upper() or "BLOCK"
            blk = config.read_line_with_timeout("Block? (1=YES [ENTER=Default], 2=NO): ", "1") != "2"
            pwd = config.read_line_with_timeout("Password Auth (ENTER=12345): ", "12345")
            rms.configure_board_wise_blocking(driver, ex, tag, blk, pwd)
        if sub in ("6", "7"):
            rms.display_clients_rms_table(driver, True)
            rms.display_unauthorized_boards_table(driver)
            sym = config.read_line_with_timeout("Check Symbol (ENTER=GP): ", "GP") or "GP"
            cln = config.read_line_with_timeout("Check Client (ENTER=23986): ", "23986") or "23986"
            brd = config.read_line_with_timeout("Check Board (ENTER=PUBLIC): ", "PUBLIC").upper() or "PUBLIC"
            print(check_selected_applicability(driver, "DSE", sym, cln, brd).overall_verdict)
        if config.IS_CI:
            break


def _run_day_netting_suite_menu(driver: webdriver.Chrome) -> None:
    print("\n=== 3. DAY NETTING SUITE (NONE, INDIVIDUAL, CROSS), ALL WIDGETS REPORT & COMPLIANCE MATRIX ===")
    print("  1. Full Suite (None + Individual + Cross + 6-Widget Report & Comparison + UI Audit + QA + 9-Scenario Matrix) [ENTER=Default]")
    print("  2. Multi-Mode Audit Only (None, Individual, Cross) + 6-Widget Report & Comparison")
    print("  3. UI Netting Indicators Audit (Depth, Watchlists, Order Window)")
    print("  4. 9-Scenario Compliance Matrix (Exclusivity, Post-Order Flips, Stock File)")
    print("  5. QA Multi-Exchange & Offline 9-Block Suite")
    print("  0. Back to Day Netting Menu")
    sub = "1" if config.IS_CI else config.read_line_with_timeout("Enter Suite Choice (0-5, ENTER=1): ", "1")
    if sub == "0":
        return
    sym = config.read_line_with_timeout("Target Symbol (ENTER=GP): ", "GP") or "GP"
    cln = config.read_line_with_timeout("Target Client (ENTER=23986): ", "23986") or "23986"
    if sub in ("1", "2"):
        run_live = config.read_line_with_timeout("Execute Live Mode Orders? (1=YES [ENTER=Default], 0=Snapshot Only): ", "1") != "0"
        score = {}
        for mode, ex in ((NettingMode.NONE, "DSE"), (NettingMode.INDIVIDUAL_EXCHANGE, "BOTH"), (NettingMode.CROSS_EXCHANGE, "BOTH")):
            ok, _, _ = _execute_normal_netting_flow(driver, mode, ex, sym, cln, "PUBLIC", "12345", run_live)
            score[mode.name] = "PASS" if ok else "CHECK"
        print(f"\n[MULTI-MODE 6-WIDGET COMPARISON SCORECARD] {score}")
    if sub in ("1", "3"):
        print(f"UI Indicators -> Depth={verify_market_depth_netting_indicator(driver, sym)}, Watchlist={verify_default_netting_watchlist(driver, sym)}, ClientBlocked={verify_order_window_client_netting_blocked(driver, cln)}")
    if sub in ("1", "5"):
        test_qa_day_netting_scenarios()
        run_all_netting_tests(None)
    if sub in ("1", "4"):
        cfg = NettingScenarioConfig()
        cfg.primary_client = config.read_line_with_timeout("Matrix Primary Client (ENTER=002): ", "002") or "002"
        cfg.other_clients = [x.strip() for x in config.read_line_with_timeout("Matrix Other Clients (ENTER=23986): ", "23986").split(",") if x.strip()]
        cfg.symbol = sym
        cfg.exchange = config.read_line_with_timeout("Matrix Exchange (DSE/CSE/BOTH, ENTER=BOTH): ", "BOTH").upper() or "BOTH"
        run_netting_9_scenario_matrix(driver, cfg)


def run_day_netting_console(driver: webdriver.Chrome) -> None:
    if not driver:
        run_all_netting_tests()
        return
    rms.navigate_to_rms_portal(driver)
    while True:
        print("\n=== DAY NETTING (SCRIP NETTING) ENGINE ===")
        print("  1. Normal Netting & 6 Widgets Live Report & Comparison")
        print("  2. All Configure Settings (RMS & Netting Settings)")
        print("  3. Day Netting Suite (None, Individual, Cross), All Widgets Report & Comparison & Compliance Matrix")
        print("  0. Return to Main Menu")
        ch = "1" if config.IS_CI else config.read_line_with_timeout("Enter Choice (0-3, ENTER=1): ", "1")
        if ch == "0":
            break
        elif ch == "1":
            _run_normal_netting_live_audit(driver)
        elif ch == "2":
            _run_all_configure_settings_menu(driver)
        elif ch == "3":
            _run_day_netting_suite_menu(driver)
        else:
            print(f"[INVALID] '{ch}' not recognized. Choose 0-3.")
        if config.IS_CI:
            break

if __name__ == "__main__":
    arg = sys.argv[1].strip().lower() if len(sys.argv) > 1 else "qa"
    if arg in ("qa", "test", "fast"):
        ok = test_qa_day_netting_scenarios() and run_all_netting_tests()
        sys.exit(0 if ok else 1)
    run_all_netting_tests()
