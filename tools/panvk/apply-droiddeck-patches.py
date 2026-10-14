#!/usr/bin/env python3
"""Apply DroidDeck's PanVK patches to a Mesa tree (after the kbase series).
Robust against include-order drift: edits by pattern, not by line number.
Usage: apply-droiddeck-patches.py <mesa-dir>
"""
import re
import sys
from pathlib import Path

def patch_rdev(mesa: Path):
    """Report the kbase node as the DRM render node on glibc builds."""
    p = mesa / "src/panfrost/vulkan/panvk_physical_device.c"
    s = p.read_text()
    # Already applied if our kbase-specific block is present.
    if "the Linux runtime presents the kbase" in s:
        print("rdev: already applied")
        return
    # 1. Add the detect_os.h include after the include block.
    if '"util/detect_os.h"' not in s:
        # Insert after the last #include line in the header block.
        m = list(re.finditer(r'^#include .+$', s, re.M))
        if not m:
            raise SystemExit("rdev: no #include lines found")
        last = m[-1]
        s = s[:last.end()] + '\n#include "util/detect_os.h"' + s[last.end():]
    # 2. Insert the rdev reporting in the kbase init path.
    # The anchor comes from the kbase patch series; if it's absent (e.g. the
    # series changed), skip gracefully — this is an optimization for gamescope,
    # not required for the driver to work.
    anchor = "zeroed; WSI paths that compare rdev values will simply not match."
    if anchor not in s:
        print("rdev: kbase anchor not found, skipping (non-fatal)")
        return
    idx = s.index(anchor) + len(anchor)
    # Find the end of the comment block (*/).
    end_comment = s.index("*/", idx) + 2
    code = """
#if !DETECT_OS_ANDROID
   {
      struct stat st;
      if (stat(path, &st) == 0 && S_ISCHR(st.st_mode)) {
         device->drm.render_rdev = st.st_rdev;
         device->drm.primary_rdev = st.st_rdev;
      }
   }
#endif"""
    s = s[:end_comment] + code + s[end_comment:]
    # Ensure <sys/stat.h> is included for struct stat.
    if "#include <sys/stat.h>" not in s:
        m = re.search(r'^#include <sys/ioctl.h>$', s, re.M)
        if m:
            s = s[:m.end()] + '\n#include <sys/stat.h>' + s[m.end():]
    p.write_text(s)
    print("rdev: applied")

def patch_fn_addr(mesa: Path):
    """Fix provoking-vertex function address truncation (u32 -> u64)."""
    p = mesa / "src/panfrost/vulkan/csf/panvk_vX_cmd_draw.c"
    s = p.read_text()
    # Match the declaration flexibly (whitespace may vary).
    m = re.search(r'^([ \t]*)uint32_t (fn_addr =)$', s, re.M)
    if not m:
        # Already fixed or pattern gone.
        if re.search(r'^[ \t]*uint64_t fn_addr =$', s, re.M):
            print("fn_addr: already applied")
            return
        raise SystemExit("fn_addr: pattern not found")
    indent = m.group(1)
    s = s[:m.start()] + f"{indent}uint64_t fn_addr =" + s[m.end():]
    p.write_text(s)
    print("fn_addr: applied")

if __name__ == "__main__":
    mesa = Path(sys.argv[1])
    patch_rdev(mesa)
    patch_fn_addr(mesa)
    print("OK")
