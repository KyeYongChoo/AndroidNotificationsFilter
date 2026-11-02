#!/bin/sh
# Runs inside the container. Expects the repo mounted read-only at /src (with a signed release
# APK already built on the host) and a writable folder at /out.
set -eu

# The mounted repo belongs to a different user, which git refuses to read without this.
git config --global --add safe.directory '*'

# Build from a fresh clone of the committed source, like F-Droid does. Uncommitted changes,
# local.properties and keystore.properties are left behind, so this build comes out unsigned.
rm -rf /build
git clone -q /src /build
cd /build
echo "Building commit $(git rev-parse --short HEAD) with $(java -version 2>&1 | head -n 1)"
bash gradlew --no-daemon --console=plain assembleRelease

signed=/src/app/build/outputs/apk/release/app-release.apk
unsigned=app/build/outputs/apk/release/app-release-unsigned.apk
cp "$unsigned" /out/linux-unsigned.apk

# The same check F-Droid runs: copy the signature from the host's APK onto this build and
# see whether it still verifies. It only does if every byte matches.
if apksigcopier compare "$signed" --unsigned "$unsigned"; then
  echo "REPRODUCIBLE: the Linux build matches your signed Windows APK."
else
  echo "NOT REPRODUCIBLE, or the check itself failed (see any error above)."
  echo "The Linux APK is in repro/out/ for comparing."
  exit 1
fi
