#!/bin/bash
# The app's own start: finds a Java new enough for the launcher window and runs it.
# A Mac that hasn't run Minecraft through Prism yet may not have one, and then this
# does the same job itself, with a plain dialog instead of the window.
HERE="$(cd "$(dirname "$0")/../Resources" && pwd)"
PRISM_DATA="$HOME/Library/Application Support/PrismLauncher"
INSTANCE="$PRISM_DATA/instances/Kokuhaku"
NAME="$(printf '\xe2\xb8\xb8\xea\x99\xae\xe1\x9b\x89\xe2\x9b\xa7\xe1\x9a\xa6\xe2\xb8\xb8')"

new_enough() {
  local v
  v="$("$1" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [ -n "$v" ] && [ "$v" -ge 17 ] 2>/dev/null
}

find_java() {
  local j
  while IFS= read -r j; do
    if new_enough "$j"; then echo "$j"; return; fi
  done < <(find "$PRISM_DATA/java" -path '*/bin/java' -type f 2>/dev/null)
  if /usr/libexec/java_home -v 17+ >/dev/null 2>&1; then
    j="$(/usr/libexec/java_home -v 17+)/bin/java"
    if new_enough "$j"; then echo "$j"; return; fi
  fi
}

JAVA="$(find_java)"
if [ -n "$JAVA" ]; then
  exec "$JAVA" -Dapple.awt.application.name="$NAME" -jar "$HERE/launcher.jar" "$HERE/game.zip"
fi

# ---- No Java yet: the same steps, without the window.
APP=""
for a in "/Applications/Prism Launcher.app" "$HOME/Applications/Prism Launcher.app" "/Applications/PrismLauncher.app" "$HOME/Downloads/Prism Launcher.app"; do
  [ -d "$a" ] && { APP="$a"; break; }
done
[ -n "$APP" ] || APP="$(mdfind "kMDItemCFBundleIdentifier == 'org.prismlauncher.PrismLauncher'" 2>/dev/null | grep -v '/.Trash/' | head -n 1)"
[ -n "$APP" ] || APP="$(mdfind "kMDItemContentType == 'com.apple.application-bundle' && kMDItemFSName == '*prism*'cd" 2>/dev/null | grep -v '/.Trash/' | head -n 1)"
if [ -z "$APP" ] || [ ! -d "$APP" ]; then
  osascript -e "display dialog \"This needs Prism Launcher.\n\nInstall it from prismlauncher.org, then open this again.\" with title \"$NAME\" buttons {\"OK\"} default button 1" >/dev/null
  exit 1
fi
osascript -e "display dialog \"Someone left a note in your locker.\" with title \"$NAME\" buttons {\"Quit\", \"Launch\"} default button 2 cancel button 1" >/dev/null || exit 0

VERSION="$(unzip -p "$HERE/game.zip" version.txt 2>/dev/null)"
if [ "$(cat "$INSTANCE/.kokuhaku-version" 2>/dev/null)" != "$VERSION" ]; then
  KEEP="$(unzip -p "$HERE/game.zip" instances/Kokuhaku/downloads.txt 2>/dev/null | cut -d' ' -f1)"
  if [ -d "$INSTANCE/.minecraft/mods" ]; then
    for jar in "$INSTANCE/.minecraft/mods"/*.jar; do
      [ -e "$jar" ] || continue
      echo "$KEEP" | grep -qxF "$(basename "$jar")" || rm -f "$jar"
    done
  fi
  mkdir -p "$PRISM_DATA"
  unzip -oq "$HERE/game.zip" -x version.txt -d "$PRISM_DATA" || exit 1
  printf '%s' "$VERSION" > "$INSTANCE/.kokuhaku-version"
fi

fetch() { # url, file, sha1
  [ -f "$2" ] && [ "$(shasum -a 1 "$2" | cut -d' ' -f1)" = "$3" ] && return 0
  curl -fsSL "$1" -o "$2.part" || return 1
  [ "$(shasum -a 1 "$2.part" | cut -d' ' -f1)" = "$3" ] || { rm -f "$2.part"; return 1; }
  mv -f "$2.part" "$2"
}

mkdir -p "$INSTANCE/.minecraft/mods"
if [ -f "$INSTANCE/downloads.txt" ]; then
  while read -r file url sha; do
    [ -n "$sha" ] || continue
    fetch "$url" "$INSTANCE/.minecraft/mods/$file" "$sha" || {
      osascript -e "display dialog \"It couldn't fetch $file.\n\nCheck you're online and try again.\" with title \"$NAME\" buttons {\"OK\"} default button 1" >/dev/null
      exit 1
    }
  done < "$INSTANCE/downloads.txt"
fi
if [ -f "$INSTANCE/map.txt" ]; then
  read -r url sha < "$INSTANCE/map.txt"
  MAP="$INSTANCE/.minecraft/kokuhaku-map"
  if [ -n "$sha" ] && [ "$(cat "$MAP/.version" 2>/dev/null)" != "$sha" ]; then
    if fetch "$url" "$INSTANCE/.minecraft/kokuhaku-map.zip" "$sha"; then
      rm -rf "$MAP" && mkdir -p "$MAP" && unzip -oq "$INSTANCE/.minecraft/kokuhaku-map.zip" -d "$MAP" && printf '%s' "$sha" > "$MAP/.version"
      rm -f "$INSTANCE/.minecraft/kokuhaku-map.zip"
    fi
  fi
fi

osascript -e 'quit app "Prism Launcher"' >/dev/null 2>&1
sleep 2
open -j -g -a "$APP" --args --launch Kokuhaku
