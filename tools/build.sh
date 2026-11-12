#!/usr/bin/env bash
# Build AudioFool for Android the way a release needs it, and print a few lines.
#
#   tools/build.sh               all tests, the screenshot tests, the signed release build,
#                                the signer check and the no-Last.fm-key check, then copy to dist/
#   tools/build.sh --no-copy     the same, but leave dist/ alone
#   tools/build.sh --skip-tests  the release build and its checks only
#
# It never uploads anything. Publishing is the /release skill, after the user says so.
# Bump versionName and versionCode in app/build.gradle.kts before a build that will go out:
# this script refuses to overwrite a different APK already in dist/ under the same version.
#
# Run it with the sandbox off: Gradle's daemon needs its sockets.
# Exit codes: 0 done, 1 a step or check failed, 3 dist/ already holds a different APK.
# The full Gradle output is in build/ship.log.

set -u
cd "$(dirname "$0")/.." || exit 1

NO_COPY=0
SKIP_TESTS=0
for arg in "$@"; do
  case "$arg" in
    --no-copy) NO_COPY=1 ;;
    --skip-tests) SKIP_TESTS=1 ;;
    *) echo "unknown option: $arg"; exit 64 ;;
  esac
done

# This PC's quirks (docs: "This PC's quirks"): Android Studio's JDK, the Zscaler trust
# store, and a short folder for the loopback socket.
HOME_WIN=$(cygpath -m "$USERPROFILE")
HOME_UNIX=$(cygpath -u "$USERPROFILE")
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=$HOME_WIN/.audiofool-tmp -Djavax.net.ssl.trustStore=$HOME_WIN/.gradle/truststore-with-zscaler.jks -Djavax.net.ssl.trustStorePassword=changeit"

LOG=build/ship.log
mkdir -p build
APK=app/build/outputs/apk/release/app-release.apk
GRADLE_PROPS="${GRADLE_USER_HOME:-$HOME_UNIX/.gradle}/gradle.properties"
RELEASE_KEY=1fdefe0a0ec5f4b6636139b35bf39db89eea2c689a3aa17abc12d8cec5be2c21

fail() {
  echo "$1 FAILED"
  grep -E "FAILED|What went wrong|^e: |error:|Exception|differs" "$LOG" 2>/dev/null | sort -u | head -20 | sed 's/^/  /'
  echo "full log: $LOG"
  exit 1
}

# "N tests, F failures" from a folder of JUnit XML results.
count_tests() {
  cat "$1"/*.xml 2>/dev/null | grep -oE '<testsuite [^>]*' |
    sed -E 's/.* tests="([0-9]+)".* failures="([0-9]+)".*/\1 \2/' |
    awk '{ t += $1; f += $2 } END { printf "%d tests, %d failures", t, f }'
}

VERSION=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
CODE=$(grep -E '^\s*versionCode\s*=' app/build.gradle.kts | head -1 | tr -cd '0-9')
echo "version  $VERSION (versionCode $CODE)"

TASKS=":app:assembleRelease"
if [ "$SKIP_TESTS" -eq 0 ]; then
  TASKS=":core:test :app:testDebugUnitTest :app:verifyRoborazziDebug $TASKS"
fi
# shellcheck disable=SC2086
./gradlew $TASKS --console=plain -q > "$LOG" 2>&1 || fail "gradle"

if [ "$SKIP_TESTS" -eq 0 ]; then
  PICTURES=$(ls app/screenshots/*.png 2>/dev/null | wc -l | tr -d ' ')
  echo "tests    core: $(count_tests core/build/test-results/test); app: $(count_tests app/build/test-results/testDebugUnitTest); $PICTURES screenshots match"
else
  echo "tests    skipped (--skip-tests)"
fi

[ -f "$APK" ] || fail "release build (no APK at $APK)"
SIZE=$(du -h "$APK" | cut -f1)
echo "build    app-release.apk, $SIZE"

# The signer: a build signed with any other key will not install as an update.
SDK="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk"
APKSIGNER=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner.bat
SIGNER=$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | grep -i 'SHA-256 digest' | head -1 | sed -E 's/.*: *//' | tr -d ' \r')
if [ "$SIGNER" != "$RELEASE_KEY" ]; then
  echo "signer   FAILED: the APK is not signed with the release key (got '${SIGNER:-nothing}')"
  exit 1
fi
echo "signer   ok, the release key"

# The Last.fm key and secret must NOT be in the APK (2026-11-12, spec 7): the user types
# them on each phone, so the repo and its releases can be public. gradle.properties keeps
# them only so this check knows what to look for, in every file inside the APK. The values
# stay in shell variables and are never printed.
prop_value() { grep -E "^$1=" "$GRADLE_PROPS" 2>/dev/null | head -1 | cut -d= -f2- | tr -d ' \r'; }
LASTFM_KEY=$(prop_value 'audiofool\.lastfm\.apiKey')
LASTFM_SECRET=$(prop_value 'audiofool\.lastfm\.secret')
if [ -z "$LASTFM_KEY" ] || [ -z "$LASTFM_SECRET" ]; then
  echo "last.fm  FAILED: keep audiofool.lastfm.apiKey and .secret in gradle.properties, so the APK can be checked for them"
  exit 1
fi
if unzip -p "$APK" 2>/dev/null | grep -a -q -i -F -e "$LASTFM_KEY" -e "$LASTFM_SECRET"; then
  echo "last.fm  FAILED: the APK carries the Last.fm key or secret"
  exit 1
fi
echo "last.fm  ok, no Last.fm key or secret in the APK"

DEST="dist/AudioFool-$VERSION.apk"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
if [ "$NO_COPY" -eq 1 ]; then
  echo "dist     not copied (--no-copy)"
elif [ -e "$DEST" ] && cmp -s "$APK" "$DEST"; then
  echo "dist     $DEST is already this build, sha256 $SHA"
elif [ -e "$DEST" ]; then
  echo "dist     NOT COPIED: $DEST exists and is a different build. Bump versionName and versionCode first."
  exit 3
else
  mkdir -p dist
  cp "$APK" "$DEST"
  echo "dist     $DEST, sha256 $SHA"
fi
exit 0
