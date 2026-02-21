"""
Automation script: launch the CPI Extractor UI JAR, interact with it,
and take screenshots for the blog post.
"""

import subprocess
import time
import os
import sys

import pyautogui
import pygetwindow as gw
from PIL import ImageGrab

pyautogui.PAUSE = 0.4
pyautogui.FAILSAFE = False

# ── paths ──────────────────────────────────────────────────────────────────────
JAR = r"C:\Users\vikas\OneDrive\Documents\proj\BTPISExtract\extract-cpi-ui\target\cpi-artifact-extractor-ui-1.0.0.jar"
CONFIG = r"C:\Users\vikas\OneDrive\Documents\SAP CPI Config\trial_account.properties"
IMAGES = r"C:\Users\vikas\OneDrive\Documents\proj\BTPISExtract\extract-cpi-ui\blog\images"

os.makedirs(IMAGES, exist_ok=True)

# ── helpers ────────────────────────────────────────────────────────────────────
def ss(name):
    path = os.path.join(IMAGES, name)
    ImageGrab.grab().save(path)
    print(f"[screenshot] {path}")
    return path

def get_win():
    wins = gw.getWindowsWithTitle('SAP CPI Artifact Extractor')
    return wins[0] if wins else None

def wait_win(timeout=50):
    t = time.time()
    while time.time() - t < timeout:
        w = get_win()
        if w and w.width > 100:
            return w
        time.sleep(0.5)
    return None

def activate():
    w = get_win()
    if w:
        try:
            w.activate()
        except Exception:
            pass
        time.sleep(0.5)
    return w

# ── launch ────────────────────────────────────────────────────────────────────
print("Launching JAR …")
proc = subprocess.Popen(["java", "-jar", JAR])

print("Waiting for JavaFX window …")
win = wait_win(50)
if not win:
    proc.terminate()
    sys.exit("ERROR: window never appeared")

print(f"Window found: '{win.title}'  pos=({win.left},{win.top})  size=({win.width}x{win.height})")

# Maximise so screenshots are consistent
win.maximize()
time.sleep(3)
win = activate()
time.sleep(1)

ss("01_initial_launch.png")
print("Screenshot 1 – initial launch")

# ── window geometry helpers ───────────────────────────────────────────────────
def dims():
    w = get_win()
    return w.left, w.top, w.width, w.height   # x0, y0, W, H

x0, y0, W, H = dims()
print(f"Maximised window: ({x0},{y0}) {W}x{H}")

# Estimated action-button row (centre Y from window top):
#   title bar ≈30  padding-top=15  app-title≈22  spacing=10
#   ConnectionPane ≈179  spacing=10  OptionsHBox ≈227  spacing=10  btn-centre≈17
ACTION_ROW_Y = y0 + 30 + 15 + 22 + 10 + 179 + 10 + 227 + 10 + 17   # ≈520

# Tab-header row (just below action buttons + spacing)
TAB_ROW_Y = ACTION_ROW_Y + 17 + 10 + 15   # btn-half + spacing + tab-half ≈43

# ── click "Load Config File" ──────────────────────────────────────────────────
LOAD_CFG_X = x0 + 90       # leftmost button, ≈90px from left edge
print(f"Clicking 'Load Config File' at ({LOAD_CFG_X}, {ACTION_ROW_Y})")
pyautogui.click(LOAD_CFG_X, ACTION_ROW_Y)
time.sleep(3)

# ── handle the Windows file-chooser dialog ────────────────────────────────────
# The JavaFX FileChooser opens the native Windows dialog.
# Strategy: send the full path to the "File name" field at the bottom.

print("Trying to navigate file dialog …")
# Alt+D focuses the address bar in Windows dialogs
pyautogui.hotkey("alt", "d")
time.sleep(0.5)
pyautogui.hotkey("ctrl", "a")
dir_path = os.path.dirname(CONFIG).replace("/", "\\")
pyautogui.typewrite(dir_path, interval=0.03)
pyautogui.press("enter")
time.sleep(1.5)

# Now type the filename in the filename field
# (Alt+N should move focus to the filename field in Windows file dialogs;
#  if not, just typing usually lands there after navigating the folder)
pyautogui.hotkey("alt", "n")
time.sleep(0.3)
fname = os.path.basename(CONFIG)
pyautogui.typewrite(fname, interval=0.03)
pyautogui.press("enter")
time.sleep(2)

# Bring main window back to front
activate()
time.sleep(1)

ss("02_config_loaded.png")
print("Screenshot 2 – config loaded")

# ── click "Extract & Export" ──────────────────────────────────────────────────
EXTRACT_X = x0 + W - 80    # rightmost button ≈80px from right edge
print(f"Clicking 'Extract & Export' at ({EXTRACT_X}, {ACTION_ROW_Y})")
pyautogui.click(EXTRACT_X, ACTION_ROW_Y)
time.sleep(4)

ss("03_extracting.png")
print("Screenshot 3 – extraction in progress")

# ── wait for extraction ───────────────────────────────────────────────────────
print("Waiting up to 60 s for extraction to finish …")
time.sleep(60)

activate()
time.sleep(1)
ss("04_extraction_complete.png")
print("Screenshot 4 – extraction complete (Summary tab)")

# ── click result tabs ─────────────────────────────────────────────────────────
# 6 tabs: Summary(0) Packages(1) Flows(2) Value Maps(3) Configs(4) Runtime(5)
TAB_W = W // 6

def click_tab(idx, name, shot):
    tx = x0 + int(TAB_W * (idx + 0.5))
    pyautogui.click(tx, TAB_ROW_Y)
    time.sleep(1.5)
    ss(shot)
    print(f"Screenshot – {name}")

click_tab(1, "Packages", "05_packages_tab.png")
click_tab(2, "Flows",    "06_flows_tab.png")
click_tab(3, "Value Maps","07_valuemaps_tab.png")
click_tab(5, "Runtime",  "08_runtime_tab.png")

# ── date-filter demo ──────────────────────────────────────────────────────────
# Click back to Summary, then enable the date filter checkbox
click_tab(0, "Summary (date filter off)", "09_summary_no_filter.png")

# The "Enable Date Filter" checkbox is inside the Extraction Options pane
# Approximately: y = y0 + 30 + 15 + 22 + 10 + 179 + 10 + 27 + 8 + 25*5 + 10 + 12  ≈ y0+438
# x ≈ half of left pane: x0 + 30
DATE_CB_X = x0 + 40
DATE_CB_Y = y0 + 30 + 15 + 22 + 10 + 179 + 10 + 27 + 8 + 125 + 5 + 10 + 12   # ≈ y0+453

print(f"Clicking date-filter checkbox at ({DATE_CB_X}, {DATE_CB_Y})")
pyautogui.click(DATE_CB_X, DATE_CB_Y)
time.sleep(0.8)

ss("10_date_filter_enabled.png")
print("Screenshot – date filter enabled")

print("\nAll screenshots captured successfully.")
