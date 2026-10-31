#!/usr/bin/env bash
set -eo pipefail
cd "E:/deepseek work"
rm -f mg.zip
echo "downloading module zip from goproxy.cn ..."
curl -sL --fail --retry 2 -o mg.zip "https://goproxy.cn/github.com/yetone/magpie/@v/v0.1.2431.zip"
ls -la mg.zip
rm -rf mzsrc && mkdir mzsrc
python - mg.zip mzsrc <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1]); z.extractall(sys.argv[2])
names = z.namelist()
print("entries", len(names))
print("has main.go:", any(n.endswith("/main.go") and n.count("/")==3 for n in names))
PY
SRC="E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431"
echo "SRC top:"; ls "$SRC" | head -30
echo "gofiles: $(find "$SRC" -name '*.go' | wc -l)"
echo "main.go present: $(ls "$SRC/main.go" 2>&1)"
echo "plugins dir: $(ls -d "$SRC/plugins" 2>&1)"
cd "$SRC"
echo "=== go build real main ==="
if GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -tags nogui -trimpath -buildvcs=false -ldflags "-s -w -X main.version=v0.1.2431-android" -o "E:/deepseek work/magpie-android/out/libmagpie.so" . > /tmp/gobuild2.log 2>&1; then
  echo "BUILD_OK"
else
  echo "BUILD_FAIL"; tail -40 /tmp/gobuild2.log; exit 3
fi
file "E:/deepseek work/magpie-android/out/libmagpie.so"
ls -la "E:/deepseek work/magpie-android/out/libmagpie.so"
echo "=== assemble apk ==="
bash "E:/deepseek work/magpie-android/scripts/build-apk2.sh" "E:/deepseek work/magpie-android/out/libmagpie.so" 2>&1 | tail -30
echo "FINALIZE2_DONE"
