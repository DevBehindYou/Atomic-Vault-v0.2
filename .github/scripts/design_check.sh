#!/usr/bin/env bash
# Atomic design system guard (plan step 7.9): fails when screen code goes
# around the tokens or brings back the pre-0.4.0 design layer.
set -uo pipefail
SRC=app/src/main/java
status=0

check() {  # check <description> <grep -E pattern> [path-exclude-regex]
  local hits
  hits=$(grep -rnE --include=*.kt "$2" "$SRC" | grep -vE "${3:-^$}" || true)
  if [ -n "$hits" ]; then
    echo "::error::$1"
    echo "$hits" | head -20
    status=1
  fi
}

check "Hard-coded colour outside ui/theme (use AtomicTheme.colors)" 'Color\(0x' '/ui/theme/'
check "Pre-0.4.0 design layer referenced" '\b(LiquidGlassSurface|GlassVariant|AmbientVaultBackground|AtomicFontSize|AtomicFontWeight|GlassSpring|GlassEasing)\b'
check "Filled icons (the design system uses Outlined only)" 'Icons\.(Default|Filled)\.|icons\.filled\.'
check "Spring or bounce animation (the design system uses ease or linear only)" '\bspring\(|DampingRatio'
check "Downloadable fonts would contact Google; bundle fonts in res/font" 'GoogleFont|googlefonts'

if [ "$status" = 0 ]; then echo "Design check passed"; fi
exit $status
