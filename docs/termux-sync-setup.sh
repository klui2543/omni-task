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
# terminal itself; nothing typed there is printed or saved by this script.

VAULT=/storage/emulated/0/OmniVault
TERMUX_HOME=/data/data/com.termux/files/home

# Prompts read from the terminal even though the script itself came from a file.
TTY=/dev/tty

step() { echo; echo "==> $1"; }
fail() { echo; echo "หยุด: $1"; exit 1; }

set -e

step "อัปเดตแพ็กเกจของ Termux (ใช้เวลาสักครู่)"
pkg update -y && pkg upgrade -y

step "ติดตั้ง Node.js และเครื่องมือสร้างโปรแกรม"
pkg install -y nodejs-lts python make clang binutils

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

if [ ! -d "$VAULT/.obsidian" ]; then
  step "ผูกโฟลเดอร์ OmniVault กับ vault ชื่อ Klui ใน Obsidian Sync (ถ้า vault มีรหัสผ่านเข้ารหัส จะถามตรงนี้)"
  until ob sync-setup --vault "Klui" --path "$VAULT" --device-name "omni-phone" < "$TTY"; do
    echo "ผูก vault ไม่สำเร็จ กด Enter เพื่อลองใหม่ หรือกด Ctrl+C เพื่อเลิก"
    read -r _ < "$TTY"
  done
else
  echo "โฟลเดอร์ OmniVault ผูกไว้แล้ว ข้ามขั้นนี้"
fi

set -e

step "ไม่ซิงก์ค่าตั้งของแอป Obsidian (ซิงก์เฉพาะโน้ตและไฟล์)"
ob sync-config --path "$VAULT" --configs ""

step "เขียนสคริปต์ที่ Omni เรียก (~/omni-sync.sh)"
cat > "$TERMUX_HOME/omni-sync.sh" <<'SYNC'
#!/data/data/com.termux/files/usr/bin/bash
# Run by Omni through Termux's RUN_COMMAND: one sync of the vault, then exit with ob's exit code.
VAULT=/storage/emulated/0/OmniVault
LOCK="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/omni-sync.lock"

# One run at a time. A lock left by a run that was killed is taken over after 10 minutes.
if ! mkdir "$LOCK" 2>/dev/null; then
  if [ -n "$(find "$LOCK" -maxdepth 0 -mmin +10 2>/dev/null)" ]; then
    rm -rf "$LOCK"
    mkdir "$LOCK" || { echo "omni-sync: another sync holds the lock" >&2; exit 75; }
  else
    echo "omni-sync: another sync is running" >&2
    exit 75
  fi
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
  echo "ซิงก์ครั้งแรกยังไม่จบ (รหัส $first) รัน bash ~/omni-sync.sh อีกครั้งจนจบ"
  echo "ถ้า vault ใหญ่มาก รอบละ 170 วินาทีอาจไม่พอ รันต่อได้ มันจะดาวน์โหลดต่อจากเดิม"
fi

echo
echo "เสร็จแล้ว เปิด Omni แล้วเลือกโฟลเดอร์ OmniVault เป็น vault และกดอนุญาตเมื่อ Omni ขอสิทธิ์สั่งงาน Termux"
