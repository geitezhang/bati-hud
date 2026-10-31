#!/bin/bash
# 巴蒂监控条 —— 一键构建（aapt2 → javac → d8 → zipalign → apksigner）
# 依赖：JDK 17、Android SDK（build-tools + 任一 platform）
set -euo pipefail
cd "$(dirname "$0")"

# --- SDK 位置：优先环境变量，其次常见安装路径 ---
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for c in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "$HOME/Android/sdk"; do
    [ -d "$c" ] && SDK="$c" && break
  done
fi
[ -n "$SDK" ] && [ -d "$SDK" ] || { echo "找不到 Android SDK，请设置 ANDROID_HOME"; exit 1; }

BT=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
BT="${BT%/}"
[ -n "$BT" ] && [ -d "$BT" ] || { echo "找不到 build-tools，请用 sdkmanager 安装"; exit 1; }

PLATFORM=$(ls -d "$SDK"/platforms/android-*/ 2>/dev/null | sort -V | tail -1)
PLATFORM="${PLATFORM%/}"
[ -n "$PLATFORM" ] && [ -d "$PLATFORM" ] || { echo "找不到 platform，请用 sdkmanager 安装"; exit 1; }

# --- JDK 17 ---
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  fi
fi
[ -n "${JAVA_HOME:-}" ] || { echo "需要 JDK 17，请设置 JAVA_HOME"; exit 1; }
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"

AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"

echo "SDK:      $SDK"
echo "build-tools: $(basename "$BT")"
echo "platform: $(basename "$PLATFORM")"
echo "JAVA_HOME: $JAVA_HOME"

rm -rf build
mkdir -p build/gen build/classes build/dex

echo "[1/6] 编译资源"
"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/base.apk -I "$PLATFORM/android.jar" \
  --manifest AndroidManifest.xml \
  --java build/gen \
  --min-sdk-version 21 --target-sdk-version 30 \
  build/res.zip

echo "[2/6] javac"
find src build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$PLATFORM/android.jar" \
  -d build/classes @build/sources.txt 2>build/javac.log || {
    echo "javac 失败："; grep -E "error:" build/javac.log | head -20; exit 1; }
[ -s build/javac.log ] && grep -E "error:" build/javac.log && exit 1 || true

echo "[3/6] d8"
find build/classes -name '*.class' > build/classes.txt
"$D8" --min-api 21 --lib "$PLATFORM/android.jar" --output build/dex @build/classes.txt >/dev/null

echo "[4/6] 打包"
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)

echo "[5/6] 对齐"
"$ZIPALIGN" -f -p 4 build/unsigned.apk build/aligned.apk

echo "[6/6] 签名"
# 本地自签名用的密钥，第一次跑会生成；不要提交进版本库
[ -f hud.jks ] || keytool -genkeypair -keystore hud.jks -alias hud \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass hudpass -keypass hudpass -dname "CN=Bati, OU=TV, O=Bati" >/dev/null 2>&1
"$APKSIGNER" sign --ks hud.jks --ks-pass pass:hudpass --key-pass pass:hudpass \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out BatiHud.apk build/aligned.apk

"$APKSIGNER" verify --print-certs BatiHud.apk | head -3
ls -lh BatiHud.apk
