#!/usr/bin/env bash
set -eo pipefail
BASE="E:/deepseek work/magpie-android"
DL="$BASE/dl2"; mkdir -p "$DL"
pyext(){ python -c "import zipfile,sys;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$2"; }

echo "=== android bun zip ==="
[ -f "$DL/bun-android.zip" ] || curl -sL -o "$DL/bun-android.zip" "https://registry.npmmirror.com/-/binary/bun/bun-v1.4.2/bun-linux-aarch64-android.zip"
echo "size=$(stat -c%s "$DL/bun-android.zip")"; sha256sum "$DL/bun-android.zip"
rm -rf "$BASE/bun-android"; mkdir -p "$BASE/bun-android"; pyext "$DL/bun-android.zip" "$BASE/bun-android"
echo "--- android bun files ---"; find "$BASE/bun-android" -maxdepth 2 -type f -exec ls -la {} \; | head
file "$BASE/bun-android/bun-linux-aarch64-android/bun" 2>&1 | head -1

echo "=== windows bun (for seeding on builder) ==="
[ -f "$DL/bun-win.zip" ] || curl -sL -o "$DL/bun-win.zip" "https://registry.npmmirror.com/-/binary/bun/bun-v1.4.2/bun-windows-x64-baseline.zip"
echo "win size=$(stat -c%s "$DL/bun-win.zip")"
rm -rf "$BASE/bun-win"; mkdir -p "$BASE/bun-win"; pyext "$DL/bun-win.zip" "$BASE/bun-win"
find "$BASE/bun-win" -name 'bun.exe' | head

echo "=== seed magpie config with plugins via real CLI ==="
SEED="$BASE/seeded"; rm -rf "$SEED"; mkdir -p "$SEED/.config/magpie" "$SEED/.cache/magpie/bun/1.4.2"
cp "$BASE/bun-win/bun-windows-x64-baseline/bun.exe" "$SEED/.cache/magpie/bun/1.4.2/magpie-bun.exe"
printf '{"npmRegistry":"https://registry.npmmirror.com","chinaMirror":true}' > "$SEED/.config/magpie/settings.json"
cd "$BASE/out"
USERPROFILE="$SEED" HOME="$SEED" ./magpie-host.exe plugin add "@magpie-community/opencode-workbuddy-auth" > "$BASE/seed-add-wb.log" 2>&1; echo "WB_EXIT=$?"
USERPROFILE="$SEED" HOME="$SEED" ./magpie-host.exe plugin add "@magpie-community/opencode-qoder-auth" > "$BASE/seed-add-qd.log" 2>&1; echo "QD_EXIT=$?"
echo "=== wb log tail ==="; tail -5 "$BASE/seed-add-wb.log"
echo "=== qd log tail ==="; tail -5 "$BASE/seed-add-qd.log"
echo "=== plugins.json ==="; cat "$SEED/.config/magpie/plugins.json" 2>&1
echo "=== plugins dir ==="; ls -la "$SEED/.config/magpie/plugins" 2>&1 | head
echo "=== node_modules magpie-community ==="; ls "$SEED/.config/magpie/plugins/node_modules/@magpie-community" 2>&1 | head
echo "SEED_DONE"
