#!/usr/bin/env bash
# 本地构建辅助：固定 JDK 17 / Android SDK
set -e
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug --console=plain "$@"
