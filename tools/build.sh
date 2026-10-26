#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
python3 tools/create-signing-key.py
if [[ -f ./gradlew ]]; then
    bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain
else
    gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain
fi
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/QingYing-0.5.0.apk
sha256sum dist/QingYing-0.5.0.apk > dist/SHA256SUMS
