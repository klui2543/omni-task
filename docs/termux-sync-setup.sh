#!/data/data/com.termux/files/usr/bin/bash
# Sets up Obsidian's headless sync (obsidian-headless, the `ob` command) in Termux, so Omni can sync the vault in the
# background instead of opening the Obsidian app over itself (see app/.../data/TermuxSync.kt).
#
# Paste into Termux:
#   cat > ~/omni-setup.sh <<'EOF'
#   ...this whole file...
#   EOF
#   bash ~/omni-setup.sh
#
# It asks for the Obsidian account sign-in (and the vault's end-to-end password when the vault has one) on the
# terminal itself; nothing typed there is printed or saved by this script. Safe to run again from the top: every step
# skips what is already done.

VAULT=/storage/emulated/0/OmniVault
TERMUX_HOME=/data/data/com.termux/files/home

# Prompts read from the terminal even though the script itself came from a file.
TTY=/dev/tty

step() { echo; echo "==> $1"; }
fail() { echo; echo "หยุด: $1"; exit 1; }

set -e

step "อัปเดตแพ็กเกจของ Termux (ใช้เวลาสักครู่)"
# Keeps the installed config files when a package asks whether to replace them, so no question stops the script.
DPKG_OPTS=(-o Dpkg::Options::=--force-confold -o Dpkg::Options::=--force-confdef)
yes | pkg update -y
yes | pkg upgrade -y "${DPKG_OPTS[@]}"

step "ติดตั้ง Node.js และเครื่องมือสร้างโปรแกรม"
yes | pkg install -y "${DPKG_OPTS[@]}" nodejs-lts python make clang binutils

# obsidian-headless needs Node.js 22 or newer; Termux's LTS package may be older, then the current one is used.
node_major() { node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/'; }
major=$(node_major)
if [ -z "$major" ] || [ "$major" -lt 22 ]; then
  echo "Node.js รุ่น ${major:-ไม่พบ} เก่าเกินไป ลงรุ่นใหม่แทน"
  yes | pkg install -y "${DPKG_OPTS[@]}" nodejs
  major=$(node_major)
fi
if [ -z "$major" ] || [ "$major" -lt 22 ]; then
  fail "ต้องใช้ Node.js รุ่น 22 ขึ้นไป แต่ในเครื่องเป็นรุ่น ${major:-ไม่พบ} ลอง pkg install nodejs แล้วรันสคริปต์นี้ใหม่"
fi

# node-gyp on Termux looks for an Android NDK it does not need; an empty path stops it from failing.
mkdir -p ~/.gyp
echo "{'variables':{'android_ndk_path':''}}" > ~/.gyp/include.gypi

step "ติดตั้งตัวซิงก์ Obsidian แบบไม่มีหน้าจอ (obsidian-headless)"
npm install -g obsidian-headless
command -v ob >/dev/null || fail "ติดตั้ง ob ไม่สำเร็จ ลองรันสคริปต์นี้ใหม่อีกครั้ง"

step "ขอสิทธิ์เข้าถึงที่เก็บไฟล์ของเครื่อง กดอนุญาตในกล่องที่ขึ้นมา"
if [ ! -d ~/storage/shared ]; then
  termux-setup-storage || true
  # The grant arrives a moment after the dialog closes: wait for it, up to 60 seconds.
  waited=0
  while [ ! -d ~/storage/shared ] && [ "$waited" -lt 60 ]; do
    sleep 2
    waited=$((waited + 2))
  done
fi
[ -d ~/storage/shared ] || fail "ยังไม่ได้สิทธิ์ที่เก็บไฟล์ รัน termux-setup-storage แล้วกดอนุญาต จากนั้นรันสคริปต์นี้ใหม่"

step "อนุญาตให้ Omni สั่งงาน Termux (allow-external-apps)"
mkdir -p ~/.termux
touch ~/.termux/termux.properties
if grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true' ~/.termux/termux.properties; then
  echo "ตั้งไว้แล้ว"
else
  # Drop any other value of the key first, so the file holds one clear line.
  sed -i '/^[[:space:]]*allow-external-apps[[:space:]]*=/d' ~/.termux/termux.properties
  echo "allow-external-apps = true" >> ~/.termux/termux.properties
fi
termux-reload-settings || echo "โหลดค่าตั้ง Termux ใหม่ไม่ได้ ปิด Termux แล้วเปิดใหม่หลังสคริปต์จบ"

mkdir -p "$VAULT"

# The sign-in and the vault setup ask questions: they run without set -e so a wrong answer can be tried again.
set +e

step "เข้าสู่ระบบบัญชี Obsidian (พิมพ์อีเมลและรหัสผ่านตามที่ถาม)"
until ob login < "$TTY"; do
  echo "เข้าสู่ระบบไม่สำเร็จ กด Enter เพื่อลองใหม่ หรือกด Ctrl+C เพื่อเลิก"
  read -r _ < "$TTY"
done

# Exit 0 only when the folder already has a sync setup with its keys (3: none, 2: keys missing).
if ob sync-status --path "$VAULT" >/dev/null 2>&1; then
  echo "โฟลเดอร์ OmniVault ผูกไว้แล้ว ข้ามขั้นนี้"
else
  step "vault ในบัญชี Obsidian Sync"
  remote=$(ob sync-list-remote 2>&1)
  echo "$remote"
  # Each vault is listed as:   <id>  "<name>"  (<region>)
  if printf '%s\n' "$remote" | grep -Fq '  "Klui"  '; then
    REMOTE_VAULT="Klui"
    echo "ใช้ vault ชื่อ Klui"
  else
    REMOTE_VAULT=""
    echo "ไม่พบ vault ชื่อ Klui ในรายการด้านบน"
  fi
  while :; do
    if [ -z "$REMOTE_VAULT" ]; then
      echo "พิมพ์ชื่อ vault ที่จะใช้ ตามรายการด้านบน (ในเครื่องหมายคำพูด) แล้วกด Enter (กด Ctrl+C เพื่อเลิก)"
      read -r REMOTE_VAULT < "$TTY" || fail "อ่านคำตอบจากหน้าจอไม่ได้ เปิด Termux แล้วรันสคริปต์นี้ใหม่"
      [ -n "$REMOTE_VAULT" ] || continue
    fi
    step "ผูกโฟลเดอร์ OmniVault กับ vault ชื่อ $REMOTE_VAULT (ถ้า vault มีรหัสผ่านเข้ารหัส จะถามตรงนี้)"
    if ob sync-setup --vault "$REMOTE_VAULT" --path "$VAULT" --device-name "omni-phone" < "$TTY"; then
      break
    fi
    echo "ผูก vault ไม่สำเร็จ ลองพิมพ์ชื่อ vault ใหม่ (กด Ctrl+C เพื่อเลิก)"
    # The next round asks for the name again, so a wrong name is not tried over and over.
    REMOTE_VAULT=""
  done
fi

set -e

step "ไม่ซิงก์ค่าตั้งของแอป Obsidian (ซิงก์เฉพาะโน้ตและไฟล์)"
ob sync-config --path "$VAULT" --configs ""

step "เขียนสคริปต์ที่ Omni เรียก (~/omni-sync.sh)"
cat > "$TERMUX_HOME/omni-sync.sh" <<'SYNC'
#!/data/data/com.termux/files/usr/bin/bash
# Run by Omni through Termux's RUN_COMMAND: one sync of the vault, then exit with ob's exit code.
# Omni passes "leave" when it leaves the screen: that run waits for a sync that holds the lock (what Omni wrote must
# go up); without it (Omni came back to the screen) a held lock ends the run at once. Exit 75 means busy.
VAULT=/storage/emulated/0/OmniVault
TMP="${TMPDIR:-/data/data/com.termux/files/usr/tmp}"
LOCK="$TMP/omni-sync.lock"
WAIT="$TMP/omni-sync.wait"

# Takes the lock folder $1. One left by a run that was killed is taken over after 10 minutes.
take() {
  mkdir "$1" 2>/dev/null && return 0
  if [ -n "$(find "$1" -maxdepth 0 -mmin +10 2>/dev/null)" ]; then
    rm -rf "$1"
    mkdir "$1" 2>/dev/null && return 0
  fi
  return 1
}

# One sync at a time.
if ! take "$LOCK"; then
  if [ "$1" != "leave" ]; then
    echo "omni-sync: another sync is running" >&2
    exit 75
  fi
  # One leave run waits at a time; it syncs whatever the others wrote too.
  if ! take "$WAIT"; then
    echo "omni-sync: another leave sync is already waiting" >&2
    exit 75
  fi
  trap 'rm -rf "$WAIT"' EXIT
  waited=0
  until take "$LOCK"; do
    if [ "$waited" -ge 150 ]; then
      echo "omni-sync: waited 150 seconds and another sync is still running" >&2
      exit 75
    fi
    sleep 2
    waited=$((waited + 2))
  done
  trap 'rm -rf "$LOCK" "$WAIT"' EXIT
  rm -rf "$WAIT"
fi
trap 'rm -rf "$LOCK"' EXIT

timeout 170 ob sync --path "$VAULT"
exit $?
SYNC
chmod 700 "$TERMUX_HOME/omni-sync.sh"

step "ซิงก์ครั้งแรก ดาวน์โหลดทั้ง vault (อาจใช้เวลานาน)"
set +e
bash "$TERMUX_HOME/omni-sync.sh"
first=$?
set -e
if [ "$first" -ne 0 ]; then
  echo
  echo "ยังไม่เสร็จ: ซิงก์ครั้งแรกยังไม่จบ (รหัส $first)"
  echo "รันคำสั่ง bash ~/omni-sync.sh อีกครั้ง และรันซ้ำจนจบโดยไม่มีข้อความผิดพลาด"
  echo "ถ้า vault ใหญ่มาก รอบละ 170 วินาทีอาจไม่พอ รันต่อได้ มันจะดาวน์โหลดต่อจากเดิม"
  echo "จบแล้วจึงเลือกโฟลเดอร์ OmniVault ใน Omni"
  exit 1
fi

echo
echo "เสร็จแล้ว เปิด Omni แล้วเลือกโฟลเดอร์ OmniVault เป็น vault และกดอนุญาตเมื่อ Omni ขอสิทธิ์สั่งงาน Termux"
