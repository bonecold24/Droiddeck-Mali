#!/usr/bin/env bash
# Builds a glibc (Linux runtime) PanVK for Mali GPUs on Android's kbase kernel driver, packed as a
# zip the app imports under GPU drivers > Linux runtime driver (LinuxVulkanDriverManager).
#
# Source is OUR driver fork (bonecold24/panvk-kbase-g720, forked from
# zenithblue-oss/panvk-kbase-android): PanVK-on-kbase validated on the exact target
# (Dimensity 9300+ / Immortalis-G720 MC12). Override SRC_REPO/SRC_BRANCH/SRC_COMMIT to build
# another tree. The DroidDeck patches in tools/panvk/patches/ apply on top; they were written
# against upstream Mesa PanVK and must be re-verified whenever the source pin moves (Phase 4).
#
# Cross-compiles for aarch64 glibc on an x86_64 Linux host without root and without touching
# the host's packages:
#   - host LLVM 22 / clang / libclc / SPIRV-LLVM-Translator from the Arch archive, unpacked into a
#     private prefix, to build Mesa's host tools (mesa_clc, vtn_bindgen2, panfrost_compile);
#   - an Arch Linux ARM sysroot (the runtime's distribution) for the target;
#   - clang --target=aarch64-unknown-linux-gnu with the NDK's ld.lld.
# Needs: git, curl, python3 (venv), pkg-config, bison, flex, wayland-scanner, glslangValidator,
# spirv-tools (headers + .pc), and the Android NDK (for ld.lld/llvm-ar).
#
# Usage: tools/panvk/build-panvk-kbase.sh [workdir]   -> <workdir>/PanVK-kbase-glibc-DroidDeck.zip
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
W=$(realpath -m "${1:-$HOME/panvk-work}")
NDK=${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/27.3.13750724}
NDKBIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
SRC_REPO=${PANVK_SRC_REPO:-https://github.com/bonecold24/panvk-kbase-g720.git}
SRC_BRANCH=${PANVK_SRC_BRANCH:-main}
# Pinned driver-fork commit; see sources.lock / UPSTREAM_PIN. Empty = branch head (not reproducible).
SRC_COMMIT=${PANVK_SRC_COMMIT:-}
ARCH_ARCHIVE=https://archive.archlinux.org/packages
ALARM=http://de3.mirror.archlinuxarm.org/aarch64
HOST_PKGS=(l/llvm/llvm-22.1.8-2-x86_64 l/llvm-libs/llvm-libs-22.1.8-2-x86_64 c/clang/clang-22.1.8-1-x86_64
           l/libclc/libclc-22.1.8-2-any s/spirv-llvm-translator/spirv-llvm-translator-22.1.6-1-x86_64
           s/spirv-headers/spirv-headers-1%3A1.4.357.0-1-any)
# libstdc++ is left out on purpose: with only gcc's libstdc++.a in the sysroot, Mesa's few C++
# objects link it statically, so the driver asks nothing of the runtime's libstdc++ version.
TARGET_PKGS=(glibc linux-api-headers gcc libgcc zlib expat libdrm wayland wayland-protocols
             libx11 libxcb xorgproto libxau libxdmcp libxext libxrandr libxrender libxshmfence libxfixes libffi)

mkdir -p "$W"/{pkgs,hostroot,armpkgs,sysroot,host-bin}
cd "$W"

# Python build tools.
[ -x venv/bin/meson ] || { python3 -m venv venv && venv/bin/pip -q install meson ninja mako pyyaml packaging pycparser; }
export PATH="$W/venv/bin:$W/host-bin:$PATH"

# Host LLVM, private prefix; its .pc files are pointed at the prefix.
for u in "${HOST_PKGS[@]}"; do
  f=pkgs/$(basename "$u").pkg.tar.zst
  [ -s "$f" ] || curl -sSfLo "$f" "$ARCH_ARCHIVE/$u.pkg.tar.zst"
  tar --zstd -xf "$f" -C hostroot --exclude=.PKGINFO --exclude=.BUILDINFO --exclude=.MTREE --exclude=.INSTALL
done
grep -rl '^prefix=/usr$' hostroot/usr/lib/pkgconfig hostroot/usr/share/pkgconfig | xargs -r sed -i "s#^prefix=/usr\$#prefix=$W/hostroot/usr#"
sed -i "s#^libexecdir=/usr/share/clc#libexecdir=$W/hostroot/usr/share/clc#" hostroot/usr/share/pkgconfig/libclc.pc
export LD_LIBRARY_PATH="$W/hostroot/usr/lib"

# Arch Linux ARM sysroot.
for repo in core extra; do [ -s armpkgs/idx-$repo.html ] || curl -sSfLo armpkgs/idx-$repo.html "$ALARM/$repo/"; done
for p in "${TARGET_PKGS[@]}"; do
  re=${p//+/\\+}
  f=$(grep -hoE "href=\"$re-[0-9][^\"]*(aarch64|any)\.pkg\.tar\.[a-z]+\"" armpkgs/idx-*.html | sed 's/href="//;s/"$//' | sort -V | tail -1)
  repo=$(grep -lF "$f" armpkgs/idx-*.html | head -1 | sed 's#.*idx-##;s#\.html##')
  [ -s "armpkgs/$f" ] || curl -sSfLo "armpkgs/$f" "$ALARM/$repo/$(printf '%s' "$f" | sed 's/+/%2B/g')"
  tar -xf "armpkgs/$f" -C sysroot --exclude=.PKGINFO --exclude=.BUILDINFO --exclude=.MTREE --exclude=.INSTALL
done

# Source: upstream driver repo (scripts + patch series) + pinned Mesa.
# The driver repo is a packaging repo, not a Mesa tree: fetch Mesa at the
# pinned commit, then apply the kbase patch series for the g720 profile.
DRIVER_REPO=${PANVK_SRC_REPO:-https://github.com/zenithblue-oss/panvk-kbase-android.git}
DRIVER_COMMIT=${PANVK_SRC_COMMIT:-0183fc489f17156a6b266360b0c296cfd9109da1}
if [ "$(git -C driver-repo remote get-url origin 2>/dev/null)" != "$DRIVER_REPO" ]; then
  rm -rf driver-repo
  git clone -q "$DRIVER_REPO" driver-repo
fi
git -C driver-repo fetch -q origin "$DRIVER_COMMIT" --depth 1 2>/dev/null || true
git -C driver-repo checkout -q -f "$DRIVER_COMMIT"
# Assemble Mesa + kbase patches into src/mesa.
mkdir -p src
driver-repo/scripts/fetch-mesa.sh src
driver-repo/scripts/apply-patches.sh --profile g720-v12-csf --mesa "$W/src/mesa"
MESASRC="$W/src/mesa"
# DroidDeck's own patches on top (pattern-based, robust to include drift).
python3 "$HERE/apply-droiddeck-patches.py" "$MESASRC"

# Host tools, against the private LLVM.
PKG_CONFIG_PATH="$W/hostroot/usr/lib/pkgconfig:$W/hostroot/usr/share/pkgconfig" PATH="$W/hostroot/usr/bin:$PATH" \
  meson setup --wipe "$MESASRC/build-host" "$MESASRC" -Dplatforms=[] -Dgallium-drivers=[] -Dvulkan-drivers=[] -Dtools=panfrost \
  -Dprecomp-compiler=enabled -Dinstall-precomp-compiler=true -Dllvm=enabled -Dmesa-clc=enabled -Dinstall-mesa-clc=true
ninja -C "$MESASRC/build-host" src/compiler/clc/mesa_clc src/compiler/spirv/vtn_bindgen2 src/panfrost/clc/panfrost_compile
ln -sf "$MESASRC/build-host/src/compiler/clc/mesa_clc" host-bin/mesa_clc
ln -sf "$MESASRC/build-host/src/compiler/spirv/vtn_bindgen2" host-bin/vtn_bindgen2
ln -sf "$MESASRC/build-host/src/panfrost/clc/panfrost_compile" host-bin/panfrost_compile

# Cross file: the linker only on link lines, or meson's -Werror checks fail on an unused argument.
cat > cross-aarch64-glibc.ini <<EOF
[binaries]
c = ['$W/hostroot/usr/bin/clang', '--target=aarch64-unknown-linux-gnu', '--sysroot=$W/sysroot']
cpp = ['$W/hostroot/usr/bin/clang++', '--target=aarch64-unknown-linux-gnu', '--sysroot=$W/sysroot']
ar = '$NDKBIN/llvm-ar'
strip = '$NDKBIN/llvm-strip'
pkg-config = 'pkg-config'

[properties]
sys_root = '$W/sysroot'
pkg_config_libdir = ['$W/sysroot/usr/lib/pkgconfig', '$W/sysroot/usr/share/pkgconfig']

[built-in options]
c_link_args = ['--ld-path=$NDKBIN/ld.lld']
cpp_link_args = ['--ld-path=$NDKBIN/ld.lld']

[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'
EOF

meson setup --wipe "$MESASRC/build-glibc" "$MESASRC" --cross-file cross-aarch64-glibc.ini -Dbuildtype=release \
  -Dplatforms=x11,wayland -Dglx=disabled -Dgbm=disabled -Degl=disabled -Dopengl=false -Dgles1=disabled \
  -Dgles2=disabled -Dglvnd=disabled -Dvalgrind=disabled -Dgallium-drivers= -Dzstd=disabled \
  -Dmesa-clc=system -Dprecomp-compiler=system -Dvulkan-drivers=panfrost -Dllvm=disabled \
  -Dpanfrost-kmds=kbase,panthor,panfrost -Dlibunwind=disabled -Dexpat=enabled
ninja -C "$MESASRC/build-glibc" src/panfrost/vulkan/libvulkan_panfrost.so

# Package: the library and a meta.json in LinuxVulkanDriverManager's schema.
rm -rf out && mkdir out
"$NDKBIN/llvm-strip" --strip-debug -o out/libvulkan_panfrost.so "$MESASRC/build-glibc/src/panfrost/vulkan/libvulkan_panfrost.so"
glibc=$(readelf -V out/libvulkan_panfrost.so | grep -o 'GLIBC_[0-9.]*' | sort -uV | tail -1 | sed 's/GLIBC_//')
srcpin=$(git -C "$MESASRC" rev-parse --short HEAD)
cat > out/meta.json <<EOF
{
  "schemaVersion": 1,
  "kind": "linux-vulkan-icd",
  "name": "PanVK kbase (glibc) for DroidDeck-Mali",
  "driverVersion": "PanVK kbase-CSF (${srcpin} + DroidDeck-Mali patches)",
  "minGlibc": "$glibc",
  "libc": "glibc"
}
EOF
python3 - "$W" <<'PY'
import sys, zipfile
w = sys.argv[1]
with zipfile.ZipFile(f"{w}/PanVK-kbase-glibc-DroidDeck.zip", "w", zipfile.ZIP_DEFLATED) as z:
    z.write(f"{w}/out/libvulkan_panfrost.so", "libvulkan_panfrost.so")
    z.write(f"{w}/out/meta.json", "meta.json")
PY
echo "built $W/PanVK-kbase-glibc-DroidDeck.zip (needs glibc $glibc)"
