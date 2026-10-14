package com.droiddeck.launcher.gpu;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.droiddeck.launcher.R;
import com.droiddeck.launcher.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imported LINUX Vulkan drivers: glibc Turnip ICDs ({@code Turnip-<tag>[-variant]-Linux.zip} from
 * Banners-Turnip), or on a Mali (MediaTek) GPU a glibc Mesa build for it - PanVK on the kbase
 * kernel interface, {@code libvulkan_panfrost.so} - for the runtime: the gamescope session that
 * runs Valve's native ARM64 Steam client, and the labwc desktop beside it. This is the driver that DRAWS there: the client's own UI
 * (OpenGL through the runtime's Zink), every game the client launches (D3D through Proton's
 * DXVK/VKD3D) and everything on the desktop. Putting the frame on the screen stays the Android
 * driver's job, in the app's compositor ({@link TurnipDriver}).
 *
 * <p>Not interchangeable with that one: the client and its games are glibc processes and cannot
 * load a bionic object at all, and this one cannot be loaded by the app. The check that separates
 * them is the libc the driver links against - {@code libc.so.6} here, {@code libc.so} there.
 *
 * <p>Layout (under the app's files dir, which the session sees by its full host path):
 * <pre>
 *   files/linux_vulkan_drivers/&lt;id&gt;/
 *       libvulkan_freedreno.so   the driver (libvulkan_panfrost.so and the like for a non-Adreno
 *                                one: meta.json's libraryName says which; absent means this name)
 *       icd.json                 generated Vulkan ICD manifest; library_path = the .so's ABSOLUTE path
 *       meta.json                name / driverVersion / minGlibc, our own schema
 * </pre>
 * The session gets {@code icd.json}'s path in {@code BL_VK_DRIVER} and exports it as
 * {@code VK_DRIVER_FILES}, so nothing inside the runtime is modified and the choice is reversible.
 *
 * <p>Versioned driver packages (the mission's driver-package layout: libvulkan_panfrost.so +
 * panvk_kbase_icd.aarch64.json + meta.json + MANIFEST.json + SHA256SUMS.txt) import through
 * {@link #importDriverPackage(Uri, String)}: every file's SHA-256 is verified against
 * SHA256SUMS.txt and the .so against MANIFEST.json's binarySha256, and each version lands in
 * its own directory (name-packageVersion-binarySha256[:8]) so versions coexist and the
 * previous one stays available for rollback. Both ABIs are accepted; only glibc drivers
 * are usable as Linux runtime drivers (see {@link #isLinuxRuntimeUsable(String)}).
 *
 * <p>Ported from Bannerlator's {@code LinuxVulkanDriverManager} (GPL-3.0), unchanged in behaviour.
 */
public class LinuxVulkanDriverManager {
    private static final String TAG = "LinuxVulkanDriver";
    public static final String DIR_NAME = "linux_vulkan_drivers";
    public static final String LIB_NAME = "libvulkan_freedreno.so";
    public static final String ICD_NAME = "icd.json";
    public static final String META_NAME = "meta.json";

    private final Context context;
    private final File rootDir;

    public LinuxVulkanDriverManager(Context context) {
        this.context = context;
        this.rootDir = new File(context.getFilesDir(), DIR_NAME);
        if (!rootDir.exists()) rootDir.mkdirs();
    }

    public File getDriverDir(String id) {
        return new File(rootDir, id);
    }

    public boolean isInstalled(String id) {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) return false;
        File dir = getDriverDir(id);
        return new File(dir, getLibraryName(id)).isFile() && new File(dir, ICD_NAME).isFile();
    }

    /** The driver's file name in its folder: meta.json's libraryName, or Turnip's for older imports. */
    public String getLibraryName(String id) {
        JSONObject m = readMeta(id);
        String name = m != null ? m.optString("libraryName", "") : "";
        return isDriverLibraryName(name) ? name : LIB_NAME;
    }

    /**
     * A Vulkan ICD's file name as Mesa builds them: libvulkan_freedreno.so (Turnip),
     * libvulkan_panfrost.so (PanVK, Mali), libvulkan_lvp.so ... - never a path, never the loader
     * itself (libvulkan.so).
     */
    static boolean isDriverLibraryName(String name) {
        return name != null && name.matches("libvulkan_[A-Za-z0-9_]+\\.so");
    }

    public String getIcdPath(String id) {
        return isInstalled(id) ? new File(getDriverDir(id), ICD_NAME).getAbsolutePath() : null;
    }

    public List<String> enumerateInstalledDrivers() {
        ArrayList<String> ids = new ArrayList<>();
        File[] dirs = rootDir.listFiles();
        if (dirs == null) return ids;
        for (File d : dirs) if (d.isDirectory() && isInstalled(d.getName())) ids.add(d.getName());
        Collections.sort(ids);
        return ids;
    }

    private JSONObject readMeta(String id) {
        try {
            File meta = new File(getDriverDir(id), META_NAME);
            if (!meta.isFile()) return null;
            return new JSONObject(FileUtils.readString(meta));
        } catch (Exception e) {
            return null;
        }
    }

    public String getDriverName(String id) {
        JSONObject m = readMeta(id);
        String name = m != null ? m.optString("name", "") : "";
        return name.isEmpty() ? id : name;
    }

    public String getDriverVersion(String id) {
        JSONObject m = readMeta(id);
        return m != null ? m.optString("driverVersion", "") : "";
    }

    public String getMinGlibc(String id) {
        JSONObject m = readMeta(id);
        return m != null ? m.optString("minGlibc", "") : "";
    }

    public void removeDriver(String id) {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) return;
        Log.d(TAG, "removing imported Linux Vulkan driver " + id);
        FileUtils.delete(getDriverDir(id));
    }

    public String installDriver(Uri zipUri, String displayName) throws IOException {
        if (looksLikeDriverPackage(zipUri)) {
            Log.i(TAG, "zip holds a versioned driver package; importing with SHA-256 verification");
            return importDriverPackage(zipUri, displayName);
        }
        File tmpDir = newStagingDir();
        boolean keep = false;
        try {
            String soName = null;
            JSONObject zipMeta = null;
            try (InputStream is = context.getContentResolver().openInputStream(zipUri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                if (is == null) throw new IOException("cannot open " + zipUri);
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String base = new File(entry.getName()).getName();
                    if (base.isEmpty()) continue;
                    if (base.startsWith("libvulkan_") && base.endsWith(".so")) {
                        if (soName != null) Log.w(TAG, "zip has several libvulkan_*.so; using the first (" + soName + ")");
                        else {
                            Files.copy(zis, new File(tmpDir, LIB_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING);
                            soName = base;
                        }
                    } else if (base.equals(META_NAME)) {
                        try {
                            zipMeta = new JSONObject(new String(readAll(zis), StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            Log.w(TAG, "meta.json unreadable, ignoring: " + e.getMessage());
                        }
                    }
                }
            }
            String id = adopt(tmpDir, soName, zipMeta, displayName);
            keep = true;
            return id;
        } finally {
            if (!keep) FileUtils.delete(tmpDir);
        }
    }

    /**
     * Import a versioned driver package zip: the mission's driver-package layout
     * (libvulkan_panfrost.so + panvk_kbase_icd.aarch64.json + meta.json + MANIFEST.json +
     * SHA256SUMS.txt). Every file's SHA-256 is verified against SHA256SUMS.txt and the
     * .so against MANIFEST.json's binarySha256; a tampered, incomplete or malformed
     * package is rejected with the reason and nothing is registered.
     *
     * <p>Both ABIs are accepted. A glibc package registers as a Linux runtime driver
     * (offered for VK_DRIVER_FILES); a bionic (Android) package is verified and kept as
     * a versioned entry but never offered as a Linux runtime driver - Linux processes
     * cannot load it. See {@link #getAbi(String)} and {@link #isLinuxRuntimeUsable(String)}.
     *
     * <p>Each import lands in its own versioned directory
     * (name-packageVersion-binarySha256[:8]); older versions are kept, so rolling back
     * is selecting {@link #previousVersionId(String)}.
     */
    public String importDriverPackage(Uri zipUri, String displayName) throws IOException {
        File tmpDir = newStagingDir();
        try {
            try (InputStream is = context.getContentResolver().openInputStream(zipUri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                if (is == null) throw new IOException("cannot open " + zipUri);
                extractPackageEntries(zis, tmpDir);
            }
            return importDriverPackageDir(tmpDir);
        } finally {
            FileUtils.delete(tmpDir);
        }
    }

    /**
     * Import an already-extracted package directory (the bundled asset copy, or a
     * downloaded-and-unzipped package). Verifies before registering.
     */
    public String importDriverPackageDir(File packageDir) throws IOException {
        try {
            InstalledDriver installed = DriverPackageInstaller.install(packageDir, rootDir);
            for (String w : installed.getWarnings()) Log.w(TAG, "driver package " + installed.getId() + ": " + w);
            Log.i(TAG, "imported driver package " + installed.getId() + " (" + installed.getPackageVersion()
                + ", abi=" + installed.getAbi() + ") -> " + installed.getDir());
            return installed.getId();
        } catch (DriverPackageException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** True when the zip holds a versioned driver package (a MANIFEST.json entry). */
    private boolean looksLikeDriverPackage(Uri zipUri) {
        try (InputStream is = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(is)) {
            if (is == null) return false;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && new File(entry.getName()).getName().equals("MANIFEST.json")) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "cannot peek into " + zipUri + ": " + e.getMessage());
        }
        return false;
    }

    /** Extract every file entry by basename (zip-slip safe: directory parts are dropped). */
    private static void extractPackageEntries(ZipInputStream zis, File tmpDir) throws IOException {
        ZipEntry entry;
        byte[] buf = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            String base = new File(entry.getName()).getName();
            if (base.isEmpty() || base.equals(".") || base.equals("..")) continue;
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(tmpDir, base))) {
                int r;
                while ((r = zis.read(buf)) > 0) fos.write(buf, 0, r);
            }
        }
    }

    /**
     * The driver's ABI ("linux-aarch64-glibc", "android-aarch64-bionic").
     * Legacy Turnip imports predate the field and report "glibc" (all were glibc-verified).
     */
    public String getAbi(String id) {
        JSONObject m = readMeta(id);
        if (m == null) return "";
        String abi = m.optString("abi", "");
        if (!abi.isEmpty()) return abi;
        return m.optString("libc", "glibc");
    }

    /** Whether the driver can back the Linux runtime: only glibc drivers load in Linux processes. */
    public boolean isLinuxRuntimeUsable(String id) {
        return getAbi(id).toLowerCase(java.util.Locale.US).contains("glibc");
    }

    public String getBackend(String id) {
        JSONObject m = readMeta(id);
        return m != null ? m.optString("backend", "") : "";
    }

    public String getProfile(String id) {
        JSONObject m = readMeta(id);
        return m != null ? m.optString("profile", "") : "";
    }

    public String getBinarySha256(String id) {
        JSONObject m = readMeta(id);
        return m != null ? m.optString("binarySha256", "") : "";
    }

    public String getPackageVersion(String id) {
        JSONObject m = readMeta(id);
        if (m == null) return "";
        String v = m.optString("packageVersion", "");
        return v.isEmpty() ? m.optString("driverVersion", "") : v;
    }

    /** Installed version ids of the driver named {@code driverName}, newest first. */
    public List<String> listVersions(String driverName) {
        List<String> ids = new ArrayList<>();
        for (InstalledVersion v : DriverPackageInstaller.listVersions(rootDir, driverName)) ids.add(v.getId());
        return ids;
    }

    /** The installed version just older than {@code currentId} - the rollback target - or null. */
    public String previousVersionId(String currentId) {
        InstalledVersion v = DriverPackageInstaller.previousVersion(rootDir, currentId);
        return v != null ? v.getId() : null;
    }

    File newStagingDir() throws IOException {
        File tmpDir = new File(rootDir, ".tmp-" + System.nanoTime());
        FileUtils.delete(tmpDir);
        if (!tmpDir.mkdirs()) throw new IOException("cannot create " + tmpDir);
        return tmpDir;
    }

    String adopt(File tmpDir, String soName, JSONObject zipMeta, String displayName) throws IOException {
        try {
            if (soName == null) {
                throw new IllegalArgumentException(context.getString(R.string.lvd_no_library));
            }
            File so = new File(tmpDir, LIB_NAME);
            if (!isAarch64Elf(so)) {
                throw new IllegalArgumentException(context.getString(R.string.lvd_not_aarch64, soName));
            }
            if (!containsAscii(so, "libc.so.6")) {
                throw new IllegalArgumentException(context.getString(R.string.lvd_not_glibc, soName));
            }
            // The staged copy is always LIB_NAME; a driver that is not Turnip keeps its own name, so
            // the folder (and a session log naming the library) says what it is.
            String libraryName = isDriverLibraryName(soName) ? soName : LIB_NAME;
            if (!libraryName.equals(LIB_NAME)) {
                File named = new File(tmpDir, libraryName);
                if (!so.renameTo(named)) throw new IOException("cannot rename " + so + " to " + libraryName);
                so = named;
            }
            String kind = zipMeta != null ? zipMeta.optString("kind", "") : "";
            if (!kind.isEmpty() && !"linux-vulkan-icd".equals(kind)) {
                Log.w(TAG, "meta.json says kind=" + kind + ", importing anyway (the binary is glibc)");
            }

            String name = zipMeta != null ? zipMeta.optString("name", "") : "";
            String version = zipMeta != null ? zipMeta.optString("driverVersion", "") : "";
            String minGlibc = zipMeta != null ? zipMeta.optString("minGlibc", "") : "";
            if (name.isEmpty()) {
                name = displayName != null ? displayName : "linux-driver";
                if (name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
            }
            String id = uniqueId(sanitizeId(name));
            File dir = getDriverDir(id);

            JSONObject icd = new JSONObject();
            icd.put("file_format_version", "1.0.0");
            JSONObject icdBody = new JSONObject();
            icdBody.put("library_path", new File(dir, libraryName).getAbsolutePath());
            icdBody.put("api_version", "1.1.274");
            icd.put("ICD", icdBody);
            if (!FileUtils.writeString(new File(tmpDir, ICD_NAME), icd.toString(2))) throw new IOException("cannot write icd.json");

            JSONObject meta = new JSONObject();
            meta.put("schemaVersion", 1);
            meta.put("kind", "linux-vulkan-icd");
            meta.put("name", name);
            meta.put("driverVersion", version);
            meta.put("libc", "glibc");
            meta.put("minGlibc", minGlibc);
            meta.put("sourceLibraryName", soName);
            meta.put("libraryName", libraryName);
            meta.put("importedAt", System.currentTimeMillis());
            if (!FileUtils.writeString(new File(tmpDir, META_NAME), meta.toString(2))) throw new IOException("cannot write meta.json");

            if (!tmpDir.renameTo(dir)) throw new IOException("cannot move into " + dir);
            Log.i(TAG, "imported Linux Vulkan driver " + id + " (" + soName + ", minGlibc=" + minGlibc + ") -> " + dir);
            return id;
        } catch (org.json.JSONException e) {
            throw new IOException("manifest write failed: " + e.getMessage());
        }
    }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (getDriverDir(id).exists()) id = base + "-" + (n++);
        return id;
    }

    static String sanitizeId(String name) {
        String s = name.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+", "");
        if (s.length() > 64) s = s.substring(0, 64);
        return s.isEmpty() ? "linux-driver" : s;
    }

    static boolean isAarch64Elf(File f) {
        return ElfInspect.isAarch64Elf(f);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    static boolean containsAscii(File f, String needle) {
        return ElfInspect.containsAscii(f, needle);
    }
}
