#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
sdk_dir=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"${HOME}/Library/Android/sdk"}}
java_dir=${JAVA_HOME:-"/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"}
image_name=${STEAMDECK_BUILD_IMAGE:-steamdeck-local-cross:24.04-v2}

if [[ ! -x "${sdk_dir}/platform-tools/adb" ]]; then
    echo "Android SDK not found at ${sdk_dir}; set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi
if [[ ! -x "${java_dir}/bin/java" ]]; then
    echo "Java 17 not found at ${java_dir}; set JAVA_HOME." >&2
    exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to cross-compile the glibc ARM64 preload libraries." >&2
    exit 1
fi

if ! docker image inspect "${image_name}" >/dev/null 2>&1; then
    docker build --platform linux/amd64 -t "${image_name}" \
        -f "${repo_root}/tools/local-cross.Dockerfile" "${repo_root}"
fi

docker run --rm --platform linux/amd64 \
    --user "$(id -u):$(id -g)" \
    -v "${repo_root}:/src" -w /src "${image_name}" bash -lc '
        set -euo pipefail
        d=app/src/main/assets/linuxfs
        mkdir -p "$d"
        aarch64-linux-gnu-g++ -shared -fPIC -O2 -Wall -Wno-attributes -Wno-nonnull-compare \
            -pthread -std=c++17 -static-libstdc++ -static-libgcc \
            -o "$d/libfakeinput.so" app/src/main/cpp/fakeinput_steam.cpp -ldl
        aarch64-linux-gnu-strip --strip-unneeded "$d/libfakeinput.so"
        aarch64-linux-gnu-gcc -shared -fPIC -O2 -Wall -pthread \
            -o "$d/libblsession.so" tools/linuxfs/preload/*.c -ldl
        aarch64-linux-gnu-strip --strip-unneeded "$d/libblsession.so"
        for script in tools/linuxfs/overlay/usr/local/bin/bannerlator-*; do
            install -Dm644 "$script" "$d/usr/local/bin/$(basename "$script")"
        done
        install -Dm644 tools/linuxfs/desktop/steamdeck-desktop "$d/usr/local/bin/steamdeck-desktop"
        install -Dm644 tools/linuxfs/desktop/autostart "$d/etc/xdg/labwc/autostart"
        install -Dm644 tools/linuxfs/desktop/rc.xml "$d/etc/xdg/labwc/rc.xml"
        install -Dm644 tools/linuxfs/desktop/panel.conf "$d/etc/xdg/lxqt/panel.conf"
        install -Dm644 tools/linuxfs/desktop/firefox-steamdeck.js \
            "$d/usr/lib/firefox/defaults/pref/steamdeck.js"

        need=$(aarch64-linux-gnu-readelf -d "$d/libfakeinput.so" | sed -n "s/.*NEEDED.*\\[\\(.*\\)\\]/\\1/p")
        for bad in libstdc++.so.6 libgcc_s.so.1; do
            if printf "%s\\n" "$need" | grep -qx "$bad"; then
                echo "libfakeinput.so links $bad; the C++ runtime must stay static" >&2
                exit 1
            fi
        done
        syms() { aarch64-linux-gnu-readelf -Ws "$1" | awk '\''$4 == "FUNC" && $5 == "GLOBAL" {sub(/@.*/, "", $8); print $8}'\''; }
        fake=$(syms "$d/libfakeinput.so")
        for sym in open openat ioctl read close poll ppoll select stat fstat access scandir; do
            printf "%s\\n" "$fake" | grep -qx "$sym" || {
                echo "libfakeinput.so does not export $sym" >&2
                exit 1
            }
        done
        session=$(syms "$d/libblsession.so")
        for sym in socket bind getsockname setsockopt statfs statvfs; do
            printf "%s\\n" "$session" | grep -qx "$sym" || {
                echo "libblsession.so does not export $sym" >&2
                exit 1
            }
        done
        test -f "$d/usr/local/bin/bannerlator-session"
        test -f "$d/usr/local/bin/bannerlator-proton-extra"
    '

export ANDROID_HOME="${sdk_dir}"
export ANDROID_SDK_ROOT="${sdk_dir}"
export JAVA_HOME="${java_dir}"
ndk_version=${STEAMDECK_NDK_VERSION:-}
if [[ -z "${ndk_version}" ]]; then
    ndk_path=$(find "${sdk_dir}/ndk" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -1)
    ndk_version=${ndk_path##*/}
fi
if [[ -z "${ndk_version}" || ! -d "${sdk_dir}/ndk/${ndk_version}" ]]; then
    echo "No Android NDK found under ${sdk_dir}/ndk; set STEAMDECK_NDK_VERSION." >&2
    exit 1
fi

cd "${repo_root}"
./gradlew assembleRelease -PndkVersion="${ndk_version}"

apk="${repo_root}/app/build/outputs/apk/release/app-release.apk"
build_tools=$(find "${sdk_dir}/build-tools" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -1)
if [[ ! -x "${build_tools}/zipalign" || ! -x "${build_tools}/apksigner" ]]; then
    echo "Android build-tools with zipalign/apksigner are required under ${sdk_dir}/build-tools." >&2
    exit 1
fi

staging_dir=$(mktemp -d "${TMPDIR:-/tmp}/steamdeck-apk.XXXXXX")
trap 'rm -rf -- "${staging_dir}"' EXIT
"${build_tools}/zipalign" -p -f 4 "${apk}" "${staging_dir}/app-release.aligned.apk"
"${build_tools}/apksigner" sign \
    --ks keystore/testkey.p12 --ks-type PKCS12 --ks-pass pass:android \
    --ks-key-alias testkey --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "${apk}" "${staging_dir}/app-release.aligned.apk"

signature_output=$("${build_tools}/apksigner" verify --min-sdk-version 21 --verbose --print-certs "${apk}")
printf '%s\n' "${signature_output}"
for scheme in \
    'Verified using v1 scheme (JAR signing): true' \
    'Verified using v2 scheme (APK Signature Scheme v2): true' \
    'Verified using v3 scheme (APK Signature Scheme v3): true'; do
    grep -qF "${scheme}" <<<"${signature_output}" || {
        echo "APK signature check failed: ${scheme}" >&2
        exit 1
    }
done
grep -qF 'Signer #1 certificate DN: EMAILADDRESS=android@android.com, CN=Android, OU=Android, O=Android' \
    <<<"${signature_output}"

docker run --rm --platform linux/amd64 -v "${repo_root}:/src:ro" -w /src "${image_name}" \
    bash -lc '
        set -euo pipefail
        apk=app/build/outputs/apk/release/app-release.apk
        work=$(mktemp -d)
        unzip -q "$apk" "lib/arm64-v8a/*" -d "$work"
        cd "$work/lib/arm64-v8a"
        system="libc.so libm.so libdl.so liblog.so libandroid.so libz.so libvulkan.so
            libGLESv2.so libEGL.so libnativewindow.so libjnigraphics.so libaaudio.so
            libOpenSLES.so libmediandk.so libcamera2ndk.so libsync.so libneuralnetworks.so"
        fail=0
        for so in *.so; do
            for need in $(readelf -d "$so" | sed -n "s/.*NEEDED.*\\[\\(.*\\)\\]/\\1/p"); do
                [ -f "$need" ] && continue
                case " $(echo $system) " in *" $need "*) continue ;; esac
                echo "missing: $so -> $need"
                fail=1
            done
        done
        [ "$fail" -eq 0 ]
        echo "every NEEDED resolves"
    '

printf 'APK: %s\n' "${apk}"
printf 'SHA-256: '
shasum -a 256 "${apk}" | awk '{print $1}'
