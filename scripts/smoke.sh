#!/usr/bin/env bash
set -eo pipefail
SRC="E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431"
OUT="E:/deepseek work/magpie-android/out"
cd "$SRC"
echo "build host exe..."
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -tags nogui -o "$OUT/magpie-host.exe" . > /tmp/hostbuild.log 2>&1 || { echo HOSTBUILD_FAIL; tail -30 /tmp/hostbuild.log; exit 1; }
echo HOSTBUILD_OK
HOMEDIR="E:/deepseek work/magpie-android/smoke/home"; mkdir -p "$HOMEDIR"
cd "$OUT"
USERPROFILE="$HOMEDIR" HOME="$HOMEDIR" MAGPIE_WEB_KEY=testkey ./magpie-host.exe web --addr 127.0.0.1:3499 --no-open > /tmp/magpieserve.log 2>&1 &
PID=$!
echo "pid=$PID; waiting..."
ok=0
for i in $(seq 1 40); do
  code=$(curl -s -o /tmp/out.html -w "%{http_code}" "http://127.0.0.1:3499/?k=testkey" 2>/dev/null || echo 000)
  if [ "$code" = "200" ]; then ok=1; echo "HTTP $code"; break; fi
  sleep 0.5
done
echo "--- serve log tail ---"; tail -8 /tmp/magpieserve.log
echo "--- html head ---"; head -c 400 /tmp/out.html 2>/dev/null; echo
kill $PID 2>/dev/null || true
echo "SMOKE_OK=$ok"
