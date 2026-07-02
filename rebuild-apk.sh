#!/bin/bash
# 一键重打包 APK — EnglishTranslationTool-mobile
# 用途：代码/功能改动后，运行此脚本生成新 APK（带时间戳版本号）

export JAVA_HOME="D:/java/jdk-17"
export ANDROID_HOME="D:/android-sdk"
export GRADLE_USER_HOME="D:/gradle-cache"

set -e

TIMESTAMP=$(date +%Y%m%d-%H%M)
PROJECT_DIR="D:/photovoltaic/EnglishTranslationTool-mobile"

echo "=== 1/5 npm build ==="
npm run build

echo "=== 2/5 update version ==="
cd "$PROJECT_DIR"
node -e "
const p=require('./package.json');
const ts='${TIMESTAMP}';
p.version='1.0.'+ts;
require('fs').writeFileSync('./package.json',JSON.stringify(p,null,2)+'\n');
console.log('version ->', p.version);
"

echo "=== 3/5 cap sync ==="
npx cap sync android

echo "=== 4/5 gradle build ==="
cd android
./gradlew assembleDebug

echo "=== 5/5 rename APK ==="
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
APK_DST="app/build/outputs/apk/debug/ett-v${TIMESTAMP}.apk"
cp "$APK_SRC" "$APK_DST"
ls -lh "$APK_DST"
echo "APK 就绪: ett-v${TIMESTAMP}.apk"
