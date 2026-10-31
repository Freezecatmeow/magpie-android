#!/usr/bin/env bash
set -e
BIN="$1"
if [ -z "$BIN" ] || [ ! -f "$BIN" ]; then echo "usage: build-apk.sh <libmagpie.so>"; exit 2; fi
SDK="E:/deepseek work/magpie-android/sdk"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
APP="E:/deepseek work/magpie-android/app"
OUT="E:/deepseek work/magpie-android/build"
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile =="
"$BT/aapt2.exe" compile --dir "$APP/res" -o "$OUT/res.zip"
echo "== aapt2 link =="
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$AJ" --manifest "$APP/AndroidManifest.xml" -R "$OUT/res.zip" --java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 34 --auto-add-overlay
echo "== javac =="
javac --release 11 -cp "$AJ" -d "$OUT/classes" "$OUT/gen/ai/usemagpie/android/R.java" "$APP/src/ai/usemagpie/android/MainActivity.java"
echo "== jar classes =="
python -c "import os,zipfile;root=os.environ["CLS"];out=os.environ["JAR"];z=zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED);[z.write(os.path.join(dp,f),os.path.relpath(os.path.join(dp,f),root).replace(os.sep,"/")) for dp,dn,fn in os.walk(root) for f in fn];z.close()"
echo "== d8 =="
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 24 --lib "$AJ" --output "$OUT/dex" "$OUT/classes.jar"
echo "== package =="
python - "$OUT/base.apk" "$OUT/dex/classes.dex" "$BIN" "$OUT/unsigned.apk" <<'PY'
import sys, zipfile, shutil, os
base, dex, so, out = sys.argv[1:5]
shutil.copyfile(base, out)
z = zipfile.ZipFile(out, 'a', zipfile.ZIP_DEFLATED)
z.write(dex, 'classes.dex')
z.write(so, 'lib/arm64-v8a/libmagpie.so')
z.close()
print('packaged', out, os.path.getsize(out))
PY
echo "== zipalign =="
"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
echo "== keystore =="
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$OUT/debug.keystore" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
echo "== apksigner =="
java -jar "$BT/lib/apksigner.jar" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$OUT/magpie.apk" "$OUT/aligned.apk"
echo "== verify =="
java -jar "$BT/lib/apksigner.jar" verify --verbose --print-certs "$OUT/magpie.apk" | head -20
echo "== badging =="
"$BT/aapt2.exe" dump badging "$OUT/magpie.apk" | head -20
ls -la "$OUT/magpie.apk"
