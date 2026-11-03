#!/bin/bash
# Build, test and sign a release APK, then print how to get it onto the TV.
#   tools/release.sh            -> release/tvchannel-<versionName>.apk (git-ignored)
# Needs local.properties with release.keystore / release.alias / release.password (see docs/sources.md, "Rilis").
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64} ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
grep -q '^release.keystore=' local.properties || { echo "local.properties has no release.keystore: the APK would be unsigned" >&2; exit 1; }
./gradlew -q testDebugUnitTest assembleRelease
version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
mkdir -p release
out=release/tvchannel-$version.apk
cp app/build/outputs/apk/release/app-release.apk "$out"
"$(ls "$ANDROID_HOME"/build-tools/*/apksigner | tail -1)" verify "$out"
echo "$out  $(du -h "$out" | cut -f1)  sha256 $(sha256sum "$out" | cut -d' ' -f1)"
echo "adb:        adb -s 100.108.220.15:5555 install -r $out"
echo "Downloader: python3 -m http.server 8000 --bind 100.82.45.9 --directory release   (then open http://100.82.45.9:8000/$(basename "$out") in Downloader on the TV)"
