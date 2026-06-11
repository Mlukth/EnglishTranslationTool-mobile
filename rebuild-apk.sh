#!/bin/bash
# 一键重打包 APK — EnglishTranslationTool-mobile
# 用途：代码/功能改动后，运行此脚本生成新 APK

export JAVA_HOME="D:/java/jdk-17"
export ANDROID_HOME="D:/android-sdk"
export GRADLE_USER_HOME="D:/gradle-cache"

set -e

echo "=== 1/3 cap sync ==="
cd "D:/photovoltaic/EnglishTranslationTool-mobile"
npx cap sync android

echo "=== 2/3 gradle build ==="
cd android
./gradlew assembleDebug

echo "=== 3/3 done ==="
ls -lh "app/build/outputs/apk/debug/app-debug.apk"
echo "APK 就绪"
