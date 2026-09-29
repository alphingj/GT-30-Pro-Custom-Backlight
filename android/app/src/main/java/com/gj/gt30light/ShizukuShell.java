package com.gj.gt30light;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Runs {@code /system/bin/service call ...} in a Shizuku remote process
 * (shell uid) and parses the {@code Result: Parcel(...)} output.
 *
 * <p>Why a shell command instead of a direct binder transact: the app process
 * cannot see the vendor tranled HAL through its own ServiceManager
 * (lookup returns null), and Shizuku user services do not start on this
 * MediaTek device (Shizuku issue #1198, server 13.6.0). The remote process
 * runs as shell — the exact identity our on-device probes used — so the
 * command lines here mirror the verified probe commands byte for byte,
 * including the int[] length prefix the HAL stub reads via createIntArray.
 */
final class ShizukuShell {
    private static final String TAG = "GT30Light";
    private static final String SERVICE_BIN = "/system/bin/service";
    private static final long TIMEOUT_MS = 20000;

    static final class Result {
        final int exitCode;
        final String stdout;
        final String stderr;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private ShizukuShell() {
    }

    static String[] serviceCall(int tx, int[] frame) {
        List<String> cmd = new ArrayList<>();
        cmd.add(SERVICE_BIN);
        cmd.add("call");
        cmd.add(LedProtocol.HAL_SERVICE);
        cmd.add(String.valueOf(tx));
        cmd.add("i32");
        cmd.add(String.valueOf(frame.length));
        for (int v : frame) {
            cmd.add("i32");
            cmd.add(String.valueOf(v));
        }
        return cmd.toArray(new String[0]);
    }

    static Result exec(String... cmd) throws Exception {
        if (!Shizuku.pingBinder()) {
            throw new IllegalStateException("Shizuku binder not received");
        }
        IShizukuService server =
                IShizukuService.Stub.asInterface(Shizuku.getBinder());
        IRemoteProcess proc = server.newProcess(cmd, null, null);
        if (proc == null) {
            throw new IllegalStateException("newProcess returned null");
        }
        try {
            boolean done;
            try {
                done = proc.waitForTimeout(TIMEOUT_MS, "MILLISECONDS");
            } catch (Exception e) {
                throw new IllegalStateException("wait failed: " + e.getMessage(), e);
            }
            if (!done) {
                throw new IllegalStateException("command timed out");
            }
            int exit = proc.exitValue();
            String stdout = readAll(proc.getInputStream());
            String stderr = readAll(proc.getErrorStream());
            return new Result(exit, stdout, stderr);
        } finally {
            try {
                proc.destroy();
            } catch (Exception e) {
                Log.w(TAG, "destroy failed: " + e);
            }
        }
    }

    /**
     * Extracts the raw int words from {@code Result: Parcel(...)} output.
     * Handles both multi-line hexdump and single-line forms. Returns null
     * when the output contains no result parcel.
     */
    static List<Integer> parcelWords(String stdout) {
        int i = stdout.indexOf("Result: Parcel(");
        if (i < 0) {
            return null;
        }
        String body = stdout.substring(i + "Result: Parcel(".length());
        List<Integer> words = new ArrayList<>();
        Pattern hex = Pattern.compile("[0-9a-fA-F]{8}");
        for (String line : body.split("\n")) {
            line = line.replaceFirst("^\\s*0x[0-9a-fA-F]+:\\s*", "")
                    .replaceFirst("'.*$", "");
            Matcher m = hex.matcher(line);
            while (m.find()) {
                words.add((int) Long.parseLong(m.group(), 16));
            }
        }
        return words;
    }

    private static String readAll(ParcelFileDescriptor fd) throws Exception {
        if (fd == null) {
            return "";
        }
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
