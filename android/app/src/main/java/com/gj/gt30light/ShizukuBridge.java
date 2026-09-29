package com.gj.gt30light;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Single owner of all Shizuku state.
 *
 * <p>Control path: {@code /system/bin/service call} executed in a Shizuku
 * remote process (shell uid) — the same identity our on-device probes used.
 * Direct binder lookup from the app process returns null for this vendor HAL,
 * and Shizuku user services do not start on this MediaTek device, so the
 * shell path is the working one. Sysfs writes and in-app Settings writes are
 * intentionally not here: both are denied for apps on this device.
 */
public final class ShizukuBridge {

    public enum State {
        UNAVAILABLE,
        WAITING_FOR_SHIZUKU,
        NEEDS_PERMISSION,
        READY,
        ERROR
    }

    public interface Callback {
        void onSuccess(String message);

        void onError(String error);
    }

    public interface StateListener {
        void onStateChanged(State state, String detail);
    }

    private static final String TAG = "GT30Light";
    private static final int REQUEST_CODE = 4001;
    private static final String[] SHIZUKU_PACKAGES = {
            "moe.shizuku.privileged.api",
            "moe.shizuku.manager"
    };
    private static final int[] EMPTY_FRAME = new int[LedProtocol.FRAME_LEN];

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gt30-bridge");
        t.setDaemon(true);
        return t;
    });

    private volatile State state = State.WAITING_FOR_SHIZUKU;
    private volatile String detail = "starting";
    private StateListener listener;

    private final Shizuku.OnBinderReceivedListener onBinderReceived =
            () -> evaluate("binder received");
    private final Shizuku.OnBinderDeadListener onBinderDead =
            () -> evaluate("binder died");
    private final Shizuku.OnRequestPermissionResultListener onPermissionResult =
            (requestCode, grantResult) -> evaluate("permission result");

    public ShizukuBridge(Context context) {
        this.app = context.getApplicationContext();
    }

    public void attach(StateListener l) {
        listener = l;
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived);
        Shizuku.addBinderDeadListener(onBinderDead);
        Shizuku.addRequestPermissionResultListener(onPermissionResult);
        evaluate("attach");
    }

    public void detach() {
        Shizuku.removeBinderReceivedListener(onBinderReceived);
        Shizuku.removeBinderDeadListener(onBinderDead);
        Shizuku.removeRequestPermissionResultListener(onPermissionResult);
        listener = null;
    }

    public State getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public void connect() {
        evaluate("connect requested");
    }

    public void requestPermission() {
        if (!Shizuku.pingBinder()) {
            evaluate("permission requested without binder");
            return;
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable t) {
            Log.e(TAG, "requestPermission failed", t);
            setState(State.ERROR, "requestPermission failed: " + t.getMessage());
        }
    }

    public void setEffect(int[] frame, Callback cb) {
        final int[] copy = frame == null ? null : frame.clone();
        io.execute(() -> {
            try {
                if (copy == null || copy.length != LedProtocol.FRAME_LEN) {
                    fail(cb, "bad frame");
                    return;
                }
                ensureReady();
                ShizukuShell.Result r = ShizukuShell.exec(
                        ShizukuShell.serviceCall(LedProtocol.TX_SET_EFFECT, copy));
                List<Integer> w = ShizukuShell.parcelWords(r.stdout);
                if (w != null && w.size() >= 2 && w.get(0) == 0 && w.get(1) == 1) {
                    ok(cb, "sent " + LedProtocol.encode(copy));
                } else {
                    fail(cb, "HAL set failed: " + snippet(r));
                }
            } catch (Throwable t) {
                Log.e(TAG, "setEffect failed", t);
                fail(cb, describeFailure(t));
            }
        });
    }

    public void getEffect(Callback cb) {
        io.execute(() -> {
            try {
                ensureReady();
                ShizukuShell.Result r = ShizukuShell.exec(
                        ShizukuShell.serviceCall(LedProtocol.TX_GET_EFFECT, EMPTY_FRAME));
                List<Integer> w = ShizukuShell.parcelWords(r.stdout);
                if (w != null && w.size() == 3 + LedProtocol.FRAME_LEN
                        && w.get(0) == 0 && w.get(1) == 1
                        && w.get(2) == LedProtocol.FRAME_LEN) {
                    int[] got = new int[LedProtocol.FRAME_LEN];
                    for (int i = 0; i < got.length; i++) {
                        got[i] = w.get(3 + i);
                    }
                    ok(cb, LedProtocol.encode(got));
                } else {
                    fail(cb, "HAL get failed: " + snippet(r));
                }
            } catch (Throwable t) {
                Log.e(TAG, "getEffect failed", t);
                fail(cb, describeFailure(t));
            }
        });
    }

    private void ensureReady() {
        if (!isReady()) {
            evaluate("call while not ready");
            throw new IllegalStateException("not ready: " + detail);
        }
    }

    private void evaluate(String why) {
        State next;
        String msg;
        try {
            if (!isShizukuInstalled()) {
                next = State.UNAVAILABLE;
                msg = "Shizuku app not installed";
            } else if (!Shizuku.pingBinder()) {
                next = State.WAITING_FOR_SHIZUKU;
                msg = "Shizuku server not running";
            } else if (Shizuku.checkSelfPermission() != 0) {
                next = State.NEEDS_PERMISSION;
                msg = "Shizuku permission not granted";
            } else {
                next = State.READY;
                msg = "Connected";
            }
        } catch (Throwable t) {
            Log.e(TAG, "evaluate failed (" + why + ")", t);
            next = State.ERROR;
            msg = String.valueOf(t.getMessage());
        }
        setState(next, msg);
    }

    private void setState(State next, String msg) {
        state = next;
        detail = msg == null ? "" : msg;
        final StateListener l = listener;
        if (l != null) {
            main.post(() -> l.onStateChanged(next, detail));
        }
    }

    private boolean isShizukuInstalled() {
        PackageManager pm = app.getPackageManager();
        for (String pkg : SHIZUKU_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
                // try next id
            }
        }
        return false;
    }

    private String snippet(ShizukuShell.Result r) {
        String out = r.stdout.trim();
        String err = r.stderr.trim();
        String s = !out.isEmpty() ? out : err;
        if (s.isEmpty()) {
            s = "exit=" + r.exitCode;
        }
        if (s.length() > 160) {
            s = s.substring(0, 160);
        }
        return s.replace('\n', ' ');
    }

    private String describeFailure(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isEmpty()) {
            m = t.getClass().getSimpleName();
        }
        return m;
    }

    private void ok(Callback cb, String message) {
        if (cb == null) {
            return;
        }
        main.post(() -> cb.onSuccess(message));
    }

    private void fail(Callback cb, String error) {
        if (cb == null) {
            return;
        }
        main.post(() -> cb.onError(error));
    }
}
