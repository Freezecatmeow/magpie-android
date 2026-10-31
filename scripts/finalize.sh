#!/usr/bin/env bash
set -eo pipefail
cd "E:/deepseek work"
echo "waiting for valid tarball..."
for i in $(seq 1 720); do
  sz=$(stat -c%s magpie-src.tar.gz 2>/dev/null || echo 0)
  if [ "$sz" -gt 70000000 ] && gzip -t magpie-src.tar.gz 2>/dev/null; then echo "tarball valid size=$sz"; break; fi
  sleep 10
done
sz=$(stat -c%s magpie-src.tar.gz 2>/dev/null || echo 0)
if ! gzip -t magpie-src.tar.gz 2>/dev/null; then echo "TARBALL_INVALID_ABORT size=$sz"; exit 1; fi
rm -rf magpie-src && mkdir -p magpie-src
tar -xzf magpie-src.tar.gz -C magpie-src --strip-components=1
echo "EXTRACTED gofiles=$(find magpie-src -name '*.go' | wc -l)"
ls magpie-src | head
cd magpie-src
echo "=== go build (real main) ==="
if GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -tags nogui -trimpath -buildvcs=false -ldflags "-s -w -X main.version=v0.1.2431-android" -o "E:/deepseek work/magpie-android/out/libmagpie.so" . > /tmp/gobuild.log 2>&1; then
  echo "BUILD_OK"
else
  echo "BUILD_FAIL"; tail -40 /tmp/gobuild.log; exit 3
fi
file "E:/deepseek work/magpie-android/out/libmagpie.so"
go version -m "E:/deepseek work/magpie-android/out/libmagpie.so" | head -3
ls -la "E:/deepseek work/magpie-android/out/libmagpie.so"
echo "=== assemble apk ==="
bash "E:/deepseek work/magpie-android/scripts/build-apk2.sh" "E:/deepseek work/magpie-android/out/libmagpie.so" 2>&1 | tail -30
echo "FINALIZE_DONE"
