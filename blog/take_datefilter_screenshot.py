"""
Focused screenshot: app with date filter enabled, mode selected, date = 15/02/2026.
"""

import subprocess, time, os, sys
import pyautogui
import pygetwindow as gw
from PIL import ImageGrab

pyautogui.PAUSE = 0.4
pyautogui.FAILSAFE = False

JAR    = r"C:\Users\vikas\OneDrive\Documents\proj\BTPISExtract\extract-cpi-ui\target\cpi-artifact-extractor-ui-1.0.0.jar"
CONFIG = r"C:\Users\vikas\OneDrive\Documents\SAP CPI Config\trial_account.properties"
IMAGES = r"C:\Users\vikas\OneDrive\Documents\proj\BTPISExtract\extract-cpi-ui\blog\images"

def ss(name):
    path = os.path.join(IMAGES, name)
    ImageGrab.grab().save(path)
    print(f"[ss] {path}")
    return path

def get_win():
    wins = gw.getWindowsWithTitle('SAP CPI Artifact Extractor')
    return wins[0] if wins else None

def wait_win(t=50):
    end = time.time() + t
    while time.time() < end:
        w = get_win()
        if w and w.width > 100:
            return w
        time.sleep(0.5)
    return None

def activate():
    w = get_win()
    if w:
        try: w.activate()
        except: pass
        time.sleep(0.5)
    return w

# ── 1. Launch ─────────────────────────────────────────────────────────────────
print("Launching JAR …")
proc = subprocess.Popen(["java", "-jar", JAR])
win = wait_win(50)
if not win:
    proc.terminate()
    sys.exit("Window not found")

win.maximize()
time.sleep(3)
activate()
time.sleep(1)

w  = get_win()
x0, y0, W, H = w.left, w.top, w.width, w.height
print(f"Window: ({x0},{y0}) {W}x{H}")

# ── 2. Load config ────────────────────────────────────────────────────────────
# Action-button row Y (same formula as main script, worked in previous run → y≈512)
ACTION_ROW_Y = y0 + 30 + 15 + 22 + 10 + 179 + 10 + 227 + 10 + 17

LOAD_CFG_X = x0 + 90
print(f"Load Config File at ({LOAD_CFG_X}, {ACTION_ROW_Y})")
pyautogui.click(LOAD_CFG_X, ACTION_ROW_Y)
time.sleep(3)

# File dialog: navigate folder → type filename → open
pyautogui.hotkey("alt", "d")
time.sleep(0.5)
pyautogui.hotkey("ctrl", "a")
pyautogui.typewrite(os.path.dirname(CONFIG).replace("/", "\\"), interval=0.03)
pyautogui.press("enter")
time.sleep(1.5)
pyautogui.hotkey("alt", "n")
time.sleep(0.3)
pyautogui.typewrite(os.path.basename(CONFIG), interval=0.03)
pyautogui.press("enter")
time.sleep(2)

activate()
time.sleep(1)
print("Config loaded.")

# ── 3. Enable date filter checkbox ────────────────────────────────────────────
# Position confirmed from previous run: (x0+40, y0+453)
DATE_CB_X = x0 + 40
DATE_CB_Y = y0 + 453
print(f"Enabling date filter checkbox at ({DATE_CB_X}, {DATE_CB_Y})")
pyautogui.click(DATE_CB_X, DATE_CB_Y)
time.sleep(1)

# Capture state so we can verify the controls appeared
ss("debug_after_checkbox.png")

# ── 4. Select mode from combo ─────────────────────────────────────────────────
# Controls HBox sits one row below the checkbox (spacing=6, each item ~27px tall)
CONTROLS_Y = DATE_CB_Y + 13 + 6 + 13    # half-checkbox + spacing + half-control ≈ +32

# The controls HBox has padding-left=20; it sits inside the left pane whose
# content starts at x0+25 (window 15px padding + pane 10px left padding).
# Mode combo prefWidth=200, centre at x0+25+20+100 = x0+145
MODE_X  = x0 + 145
# Date picker prefWidth=150, spacing=8 after 200px combo → centre at x0+25+20+200+8+75=x0+328
DATEPK_X = x0 + 328

print(f"Controls row Y ~= {CONTROLS_Y}")
print(f"Mode combo at ({MODE_X}, {CONTROLS_Y})")
print(f"Date picker at ({DATEPK_X}, {CONTROLS_Y})")

# Click the mode combo to open the drop-down list
pyautogui.click(MODE_X, CONTROLS_Y)
time.sleep(0.8)

# The first drop-down item appears directly below the combo (~27px lower).
# Items: 0=Modified since  1=Created since  2=Created or modified since
# Press Home to ensure we land on the first item, then Enter to confirm.
pyautogui.press("home")
time.sleep(0.2)
pyautogui.press("enter")
time.sleep(0.5)

ss("debug_after_mode_select.png")
print("Mode selected.")

# ── 5. Set date in the DatePicker ─────────────────────────────────────────────
# Click the text-field area of the DatePicker (NOT the calendar icon on the right)
pyautogui.click(DATEPK_X, CONTROLS_Y)
time.sleep(0.5)

# Select all existing text and replace with the date.
# JavaFX DatePicker date format is locale-dependent.
# Try DD/MM/YYYY first (en_AU / Australian locale).
pyautogui.hotkey("ctrl", "a")
time.sleep(0.2)
pyautogui.typewrite("15/02/2026", interval=0.05)
time.sleep(0.3)
# Commit by pressing Enter
pyautogui.press("enter")
time.sleep(0.8)

ss("debug_after_date_enter.png")

# Click somewhere neutral (Tenant URL field) to deselect / confirm the date
pyautogui.click(x0 + W // 2, y0 + 90)
time.sleep(0.5)

# ── 6. Final screenshot ───────────────────────────────────────────────────────
activate()
time.sleep(0.5)
ss("11_date_filter_with_date.png")
print("Done – screenshot saved as 11_date_filter_with_date.png")
