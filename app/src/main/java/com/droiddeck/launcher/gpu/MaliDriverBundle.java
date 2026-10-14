package com.droiddeck.launcher.gpu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.droiddeck.launcher.core.DeviceSupport;
import com.droiddeck.launcher.core.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * The driver package bundled with the APK: the mission's PanVK-kbase build for the
 * Mali-G720 (v12 CSF), shipped under {@code assets/driver-bundles/} so first run on a
 * detected Mali GPU auto-imports it - no download needed.
 *
 * <p>The bundled .so is the Android (bionic) ABI build: it is verified and registered
 * as a versioned driver entry like any imported package, but it is not a Linux
 * runtime driver (see {@link LinuxVulkanDriverManager#isLinuxRuntimeUsable(String)}).
 * The glibc ICD variant for the Linux userland is built on-device (NEEDS-REAL-MALI);
 * when it is imported, the auto-import below stands aside - it only fills the gap
 * when no panvk-kbase driver is registered at all.
 */
public final class MaliDriverBundle {
    private static final String TAG = "MaliDriverBundle";

    /** Asset directory holding the bundled driver package. */
    public static final String ASSET_DIR = "driver-bundles/panvk-kbase-g720-v0.1.0-rc3-universal+local1";

    /** The backend value the bundled package registers under. */
    public static final String BACKEND = "panvk-kbase";

    private static final String PREFS = "mali_driver_bundle";
    private static final String KEY_IMPORTED = "imported";

    private MaliDriverBundle() {}

    /**
     * First-run import of the bundled driver package: on a Mali GPU with no
     * panvk-kbase driver registered yet, copies the bundled package out of assets
     * and imports it (SHA-256 verified). Runs off the calling thread; never throws.
     */
    public static void ensureImportedAsync(Context context) {
        if (!DeviceSupport.mali()) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                String id = ensureImported(app);
                if (id != null) Log.i(TAG, "bundled Mali driver imported as " + id);
            } catch (Exception e) {
                Log.w(TAG, "bundled Mali driver import failed: " + e.getMessage());
            }
        }, "mali-driver-bundle").start();
    }

    /**
     * Synchronous core, exposed for tests. Copies the bundled package out of assets,
     * verifies and registers it; returns the installed id, or null when there was
     * nothing to do. Sets the imported flag even when a driver is already present,
     * so a retry does not re-copy 24 MB.
     */
    static String ensureImported(Context context) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        if (prefs.getBoolean(KEY_IMPORTED, false)) return null;
        LinuxVulkanDriverManager manager = new LinuxVulkanDriverManager(context);
        for (String id : manager.enumerateInstalledDrivers()) {
            if (BACKEND.equals(manager.getBackend(id))) {
                prefs.edit().putBoolean(KEY_IMPORTED, true).apply();
                return id;
            }
        }
        File staging = new File(context.getCacheDir(), "mali-driver-bundle");
        FileUtils.delete(staging);
        if (!staging.mkdirs()) throw new IOException("cannot create " + staging);
        String[] names;
        try {
            names = context.getAssets().list(ASSET_DIR);
        } catch (IOException e) {
            throw new IOException("bundled driver package missing from assets: " + ASSET_DIR);
        }
        if (names == null || names.length == 0) throw new IOException("bundled driver package empty: " + ASSET_DIR);
        for (String name : names) {
            if (!FileUtils.copyAsset(context, ASSET_DIR + "/" + name, new File(staging, name))) {
                throw new IOException("cannot copy bundled driver file " + name);
            }
        }
        try {
            String id = manager.importDriverPackageDir(staging);
            prefs.edit().putBoolean(KEY_IMPORTED, true).apply();
            return id;
        } finally {
            FileUtils.delete(staging);
        }
    }
}
