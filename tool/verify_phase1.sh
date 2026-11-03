#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

node --check preview/app.js
python3 - <<'PY'
import json
for path in ('lib/l10n/app_en.arb', 'assets/data/planner_rules.json', 'assets/data/starter_manifest.json'):
    with open(path, encoding='utf-8') as source:
        json.load(source)
print('Localization and data files are valid JSON.')
PY

if ! command -v flutter >/dev/null 2>&1; then
  echo 'Flutter is not installed. Run the remaining checks on an Android/Windows Flutter machine.' >&2
  exit 2
fi
flutter pub get
flutter gen-l10n
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test

if [[ "${RUN_ANDROID_BUILD:-0}" == "1" ]]; then
  flutter build apk --debug
fi
if [[ "${RUN_WINDOWS_BUILD:-0}" == "1" ]]; then
  flutter build windows --debug
fi
