package com.steamdeck.launcher.core;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts the session's host processes - proot and the audio daemon - and reports the exit status.
 * The pid is read out of the hidden field on {@code java.lang.Process} because stopping a session
 * means killing that exact process: proot runs with {@code --kill-on-exit}, so the whole guest
 * tree goes with it.
 */
public final class ProcessHelper {
    private static final String TAG = "ProcessHelper";

    private ProcessHelper() {}

    public static int exec(String command, String[] envp, File workingDir) {
        return exec(command, envp, workingDir, null, null);
    }

    /**
     * @param lineCallback receives stdout and stderr line by line, or null to discard both. The
     *                     Linux session sends everything to its own log file, so nothing is passed
     *                     here for it; the audio daemon is the one that benefits.
     */
    public static int exec(String command, String[] envp, File workingDir,
                           Callback<Integer> terminationCallback, Callback<String> lineCallback) {
        int pid = -1;
        try {
            ProcessBuilder builder = new ProcessBuilder(splitCommand(command));
            if (workingDir != null) builder.directory(workingDir);
            if (envp != null) {
                // Added to the app's own environment rather than replacing it, which is what
                // Bannerlator does on the path this was taken from. proot needs nothing from the
                // inherited set, but the guest command starts with `env -i` anyway, so clearing
                // here buys nothing and only differs from the configuration proven on a device.
                for (String entry : envp) {
                    int eq = entry.indexOf('=');
                    if (eq > 0) builder.environment().put(entry.substring(0, eq), entry.substring(eq + 1));
                }
            }
            builder.redirectErrorStream(true);
            if (lineCallback == null) builder.redirectOutput(new File("/dev/null"));
            Process process = builder.start();

            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
            Log.i(TAG, "started pid " + pid + ": " + command);

            if (lineCallback != null) drain(process.getInputStream(), lineCallback);
            if (terminationCallback != null) {
                Thread waiter = new Thread(() -> {
                    int status = -1;
                    try {
                        status = process.waitFor();
                    } catch (InterruptedException ignored) {
                    } finally {
                        terminationCallback.call(status);
                    }
                }, "proc-wait-" + pid);
                waiter.setPriority(Thread.NORM_PRIORITY - 1);
                waiter.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "exec " + command, e);
        }
        return pid;
    }

    private static void drain(InputStream stream, Callback<String> lineCallback) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    lineCallback.call(line);
                }
            } catch (Exception ignored) {
            }
        }, "proc-out");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    /** Splits on spaces, honouring backslash-escaped spaces - paths under /data are full of them. */
    public static String[] splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ' ') {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts.toArray(new String[0]);
    }
}
