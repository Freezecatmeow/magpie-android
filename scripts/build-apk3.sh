#!/usr/bin/env bash
set -eo pipefail
BIN="$1"
BUNSO="${2:-E:/deepseek work/magpie-android/out/libbun.so}"
if [ -z "$BIN" ] || [ ! -f "$BIN" ]; then echo "usage: build-apk3.sh <libmagpie.so> [libbun.so]"; exit 2; fi
SDK="E:/deepseek work/magpie-android/sdk"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
APP="E:/deepseek work/magpie-android/app"
OUT="E:/deepseek work/magpie-android/build"
JC="C:/Program Files/Java/jdk-11.0.14/bin/javac.exe"
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile =="
"$BT/aapt2.exe" compile --dir "$APP/res" -o "$OUT/res.zip"
echo "== aapt2 link (with assets) =="
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$AJ" --manifest "$APP/AndroidManifest.xml" -R "$OUT/res.zip" -A "$APP/assets" --java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 34 --auto-add-overlay
echo "== javac =="
"$JC" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d "$OUT/classes" "$OUT/gen/ai/usemagpie/android/R.java" "$APP/src/ai/usemagpie/android/MainActivity.java"
echo "== jar classes =="
CLS="$OUT/classes" JAR="$OUT/classes.jar" python -c "import os,zipfile;root=os.environ['CLS'];out=os.environ['JAR'];z=zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED);[z.write(os.path.join(dp,f),os.path.relpath(os.path.join(dp,f),root).replace(os.sep,'/')) for dp,dn,fn in os.walk(root) for f in fn];z.close()"
echo "== d8 =="
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 24 --lib "$AJ" --output "$OUT/dex" "$OUT/classes.jar"
echo "== package (dex + libmagpie + libbun) =="
python - "$OUT/base.apk" "$OUT/dex/classes.dex" "$BIN" "$BUNSO" "$OUT/unsigned.apk" <<'PY'
import sys, zipfile, shutil, os
base, dex, magpie, bun, out = sys.argv[1:6]
shutil.copyfile(base, out)
z = zipfile.ZipFile(out, 'a', zipfile.ZIP_DEFLATED)
z.write(dex, 'classes.dex')
z.write(magpie, 'lib/arm64-v8a/libmagpie.so')
if bun and os.path.exists(bun):
    z.write(bun, 'lib/arm64-v8a/libbun.so')
z.close()
print('packaged', out, os.path.getsize(out))
PY
echo "== zipalign =="
"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
echo "== keystore =="
if [ ! -f "$OUT/debug.keystore" ]; then
  "C:/Program Files/Java/jdk-11.0.14/bin/keytool.exe" -genkeypair -keystore "$OUT/debug.keystore" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
echo "== apksigner =="
java -jar "$BT/lib/apksigner.jar" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$OUT/magpie.apk" "$OUT/aligned.apk"
echo "== verify =="
java -jar "$BT/lib/apksigner.jar" verify "$OUT/magpie.apk" 2>&1 | grep -E "Verifies" | head -3
echo "== badging =="
"$BT/aapt2.exe" dump badging "$OUT/magpie.apk" | grep -E "package:|native-code|targetSdk"
echo "== zip listing =="
python -c "import zipfile;[print(i.filename,i.file_size) for i in zipfile.ZipFile('$OUT/magpie.apk').infolist() if i.filename.startswith('lib/') or i.filename=='assets/seed.zip']"
ls -la "$OUT/magpie.apk"
