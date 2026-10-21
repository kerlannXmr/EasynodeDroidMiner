#!/usr/bin/env bash
# Télécharge monerod pour Android ARM64, le vérifie (SHA256 + signature GPG de
# hashes.txt, même logique que verify_monero_gpg() d'EasyMonerod) puis le place
# dans l'APK sous app/src/main/jniLibs/arm64-v8a/libmonerod.so.
#
# Usage : MONERO_VERSION=0.18.5.4 bash scripts/fetch_monerod.sh
set -euo pipefail

VERSION="${MONERO_VERSION:?MONERO_VERSION manquant (ex: MONERO_VERSION=0.18.5.4)}"
FILE="monero-android-armv8-v${VERSION}.tar.bz2"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
# Empreintes des clés autorisées à signer hashes.txt (binaryfate, luigi1111)
TRUSTED_FPRS="${MONERO_TRUSTED_FPRS:-81AC591FE9C4B65C5806AFC3F0AF4D462A0BDF92 8777AB8F778EE89487A2F8E7F4ACA0183641E010}"

die() { echo "✖ $*" >&2; exit 1; }
ok()  { echo "✓ $*"; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cd "$WORK"

echo "ℹ Téléchargement de $FILE"
curl -fsSL --retry 3 -A "$UA" "https://downloads.getmonero.org/cli/$FILE" -o "$FILE" \
  || die "Téléchargement impossible (version $VERSION inexistante pour Android ?)"
SIZE=$(stat -c%s "$FILE")
[ "$SIZE" -gt 20000000 ] || die "Fichier trop petit ($SIZE octets)"
ok "Archive téléchargée ($((SIZE / 1024 / 1024)) Mo)"

curl -fsSL --retry 3 -A "$UA" "https://www.getmonero.org/downloads/hashes.txt" -o hashes.txt \
  || die "Téléchargement de hashes.txt impossible"
grep -q -- "-----BEGIN PGP SIGNED MESSAGE-----" hashes.txt || die "hashes.txt n'est pas signé"

export GNUPGHOME="$WORK/gnupg"
mkdir -m 700 "$GNUPGHOME"
for key in binaryfate.asc luigi1111.asc; do
  if curl -fsSL "https://raw.githubusercontent.com/monero-project/monero/master/utils/gpg_keys/$key" -o "$key"; then
    gpg --batch --quiet --import "$key" 2>/dev/null || true
  fi
done

STATUS=$(LANG=C gpg --batch --status-fd 1 --output hashes_signed.txt --decrypt hashes.txt 2>/dev/null || true)
PRIMARY_FPR=$(echo "$STATUS" | awk '$2=="VALIDSIG"{print $NF; exit}')
SIG_FPR=$(echo "$STATUS" | awk '$2=="VALIDSIG"{print $3; exit}')
SIGNER=$(echo "$STATUS" | awk '$2=="GOODSIG"{$1=$2=$3=""; sub(/^ +/,""); print; exit}')
[ -n "$PRIMARY_FPR" ] || die "Signature GPG invalide ou non vérifiable"

TRUSTED=0
for f in $TRUSTED_FPRS; do
  if [ "$PRIMARY_FPR" = "$f" ] || [ "$SIG_FPR" = "$f" ]; then TRUSTED=1; fi
done
[ "$TRUSTED" -eq 1 ] || die "hashes.txt signé par une clé NON reconnue : $PRIMARY_FPR ($SIGNER)"
ok "Signature GPG valide : $SIGNER ($PRIMARY_FPR)"

# Le hash doit figurer dans la partie SIGNÉE
EXPECTED=$(tr -d '\r' < hashes_signed.txt | grep -F "$FILE" | awk '{print $1}' | head -n1)
[ -n "$EXPECTED" ] || die "Aucun hash signé pour $FILE"
ACTUAL=$(sha256sum "$FILE" | awk '{print $1}')
[ "$EXPECTED" = "$ACTUAL" ] || die "HASH SHA256 INVALIDE - attendu $EXPECTED, obtenu $ACTUAL"
ok "SHA256 vérifié : $ACTUAL"

mkdir extract
tar -xjf "$FILE" -C extract
BIN=$(find extract -type f -name monerod | head -n1)
[ -n "$BIN" ] || die "monerod introuvable dans l'archive"
file "$BIN" | grep -q "aarch64" || die "monerod n'est pas un binaire ARM64 : $(file "$BIN")"

mkdir -p "$OUT_DIR"
install -m 755 "$BIN" "$OUT_DIR/libmonerod.so"
ok "monerod v$VERSION installé dans app/src/main/jniLibs/arm64-v8a/libmonerod.so"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### monerod v$VERSION vérifié"
    echo "- Archive : \`$FILE\`"
    echo "- SHA256 : \`$ACTUAL\`"
    echo "- Signé par : $SIGNER (\`$PRIMARY_FPR\`)"
  } >> "$GITHUB_STEP_SUMMARY"
fi
