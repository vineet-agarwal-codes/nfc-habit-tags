#!/usr/bin/env bash
# Downloads the unsigned release bundle from a `release` workflow run and signs it with the Play upload key
# kept on this VM (~/.config/habit-tags/release.env + mise-upload.jks). The key is never uploaded anywhere.
# Usage: sign-release.sh <run-id>   → ~/mise-release/mise-<versionCode>.aab
set -euo pipefail
run=$1
set -a; . "$HOME/.config/habit-tags/release.env"; set +a
out="$HOME/mise-release"; mkdir -p "$out"
tmp=$(mktemp -d)
gh run download "$run" -n mise-release-aab-unsigned -D "$tmp"
signed="$out/mise-run$run.aab"
cp "$tmp/app-release.aab" "$signed"
jarsigner -keystore "$MISE_UPLOAD_STORE_FILE" -storepass:env MISE_UPLOAD_STORE_PASSWORD -keypass:env MISE_UPLOAD_KEY_PASSWORD \
  -sigalg SHA256withRSA -digestalg SHA-256 "$signed" "$MISE_UPLOAD_KEY_ALIAS" >/dev/null
jarsigner -verify "$signed" | grep -q "jar verified" && echo "signed: $signed"
rm -rf "$tmp"
