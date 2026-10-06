#!/usr/bin/env bash
# Helpers shared by the emulator scripts. Expects $OUT (artifact folder).

# uiautomator returns "null root node" while a window is still coming up or
# animating, so retry instead of letting `set -e` end the script on the first miss.
dump() {
  local i
  for i in 1 2 3 4 5; do
    rm -f "$OUT/ui.xml"
    if adb shell uiautomator dump /sdcard/ui.xml 2>&1 | grep -qi "dumped to"        && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# find_center <attr> <value> [exact]  -> "x y" of the first matching node, or nothing (ignores case)
find_center() {
  python3 - "$OUT/ui.xml" "$1" "$2" "${3:-contains}" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
attr, val, mode = sys.argv[2], sys.argv[3], sys.argv[4]
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    a = re.search(r'\b' + attr + r'="([^"]*)"', node)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not (a and b):
        continue
    # Case-insensitive: the design system renders labels in uppercase.
    have, want = a.group(1).lower(), val.lower()
    hit = (have == want) if mode == "exact" else (want in have)
    if hit:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}

# nth_edit_center <n> -> center of the nth (1-based) EditText
nth_edit_center() {
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
n = int(sys.argv[2])
nodes = [m.group(0) for m in re.finditer(r'<node [^>]*class="android\.widget\.EditText"[^>]*>', xml)]
if len(nodes) >= n:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodes[n - 1])
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}

# nth_edit_bottom <n> -> "x bottom" of the nth (1-based) EditText
nth_edit_bottom() {
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
n = int(sys.argv[2])
nodes = [m.group(0) for m in re.finditer(r'<node [^>]*class="android\.widget\.EditText"[^>]*>', xml)]
if len(nodes) >= n:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodes[n - 1])
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, y2)
PY
}

fail() {
  echo "::error::$1"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  cp "$OUT/ui.xml" "$OUT/failure_ui.xml" 2>/dev/null || true
  # What was on screen, in the job log (artifacts are not always reachable).
  if [ -f "$OUT/ui.xml" ]; then
    echo "--- on screen (text, content-desc, resource-id) ---"
    grep -oE '(text|content-desc|resource-id)="[^"]+"' "$OUT/ui.xml" | sort -u | head -60 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  # Errors and exceptions, also in the job log.
  echo "--- logcat errors (last 60) ---"
  grep -E ' E |FATAL|Exception|Caused by|^\s+at ' "$OUT/logcat.txt" | grep -vE 'chatty|GnssHAL|ConnectivityService' | tail -60 || true
  exit 1
}

# tap_node <attr> <value> [exact] [seconds-to-wait-after]
tap_node() {
  dump || fail "UI dump failed before tapping $1=\"$2\""
  local c
  c=$(find_center "$1" "$2" "${3:-contains}")
  [ -n "$c" ] || fail "Could not find $1=\"$2\" on screen"
  echo "Tap $1=\"$2\" at $c"
  adb shell input tap $c
  sleep "${4:-2}"
}

# wait_for_text <substring> [timeout-seconds]
wait_for_text() {
  local deadline=$((SECONDS + ${2:-20}))
  while [ $SECONDS -lt $deadline ]; do
    if dump && grep -qi "$1" "$OUT/ui.xml"; then return 0; fi
    sleep 2
  done
  fail "Timed out waiting for \"$1\""
}
