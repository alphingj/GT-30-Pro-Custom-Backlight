package com.gj.gt30light;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GT30 Pro rear-LED vendor HAL protocol.
 *
 * <p>Service: {@code vendor.hardware.tranled.ITranLed/default} (AIDL v1).
 * Transaction 1 = setLightingEffect(int[8]) -> boolean.
 * Transaction 2 = getLightingEffect(int[8]) -> (boolean, int[8]).
 * No interface token is required by this HAL.
 *
 * <p>Frame layout (8 ints): [0]=cmd(0), [1]=subcmd(1), [2]=index(0),
 * [3]=scene, [4]=variant(0-3, always 0 for single-variant scenes),
 * [5..7]=color bytes (rendered by scene 0x01; zero in presets).
 *
 * <p>Scene bytes below are firmware scenes (decimal), verified two ways:
 * the setFlash packed-switch/input map in TranLedGt30Pro AND on-device
 * visual testing (22 = breathing green, 64 = bright white blink, 20 =
 * charging, 51-53 = camera shutter timers, 91/92/93 = party effects, ...).
 * The system remaps its controller-level IDs before sending (15 -&gt; 4,
 * 26 -&gt; 21, 27 -&gt; 22); sending controller IDs raw hits unknown
 * firmware scenes, which render as a dim fallback.
 */
public final class LedProtocol {
    public static final String HAL_SERVICE = "vendor.hardware.tranled.ITranLed/default";
    public static final String SETTING_KEY = "tran_led_lighting_setting";

    public static final int TX_SET_EFFECT = 1;
    public static final int TX_GET_EFFECT = 2;

    public static final int FRAME_LEN = 8;

    public static final int SCENE_OFF = 0;
    public static final int SCENE_ON = 64;
    public static final int SCENE_NOTIFICATION = 4;
    public static final int SCENE_CHARGING = 20;
    public static final int SCENE_CAMERA_RECORD = 51;
    public static final int SCENE_GAME_LAUNCH = 22;
    public static final int SCENE_MUSIC_PREVIEW = 10;
    public static final int SCENE_FLIP_TO_FLASH = 3;
    public static final int SCENE_RED_TRAIL = 5;
    public static final int SCENE_DIM_WHITE = 9;
    public static final int SCENE_MUSIC_ONCE = 11;
    public static final int SCENE_CAMERA_5S = 52;
    public static final int SCENE_CAMERA_10S = 53;
    public static final int SCENE_RED_TRAILER = 54;
    public static final int SCENE_RED_SWEEP = 62;
    public static final int SCENE_RED_SWEEP_ALT = 63;
    public static final int SCENE_WHITE_ALT = 65;
    public static final int SCENE_BLUE_FLOW = 81;
    public static final int SCENE_BLUE_FLOW_ALT = 82;
    public static final int SCENE_BLUE_BLINK_LOOP = 83;
    public static final int SCENE_XARENA_FLASH = 61;
    public static final int SCENE_PARTY_BREATHE = 91;
    public static final int SCENE_PARTY_METEOR = 92;
    public static final int SCENE_PARTY_RHYTHM = 93;
    public static final int SCENE_BLUE_DRIP = 80;
    // Scene 1 renders bytes [5..7] as a dim custom glow (subtle by design);
    // it backs the Custom Color button, not ON.
    public static final int SCENE_CUSTOM_GLOW = 1;

    private static final Map<String, Integer> SCENES;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("off", SCENE_OFF);
        m.put("on", SCENE_ON);
        m.put("game-launch", SCENE_GAME_LAUNCH);
        m.put("notification", SCENE_NOTIFICATION);
        m.put("music-preview", SCENE_MUSIC_PREVIEW);
        m.put("charging", SCENE_CHARGING);
        m.put("camera-record", SCENE_CAMERA_RECORD);
        m.put("flip-to-flash", SCENE_FLIP_TO_FLASH);
        m.put("red-trail", SCENE_RED_TRAIL);
        m.put("dim-white", SCENE_DIM_WHITE);
        m.put("music-once", SCENE_MUSIC_ONCE);
        m.put("camera-5s", SCENE_CAMERA_5S);
        m.put("camera-10s", SCENE_CAMERA_10S);
        m.put("red-trailer", SCENE_RED_TRAILER);
        m.put("red-sweep", SCENE_RED_SWEEP);
        m.put("red-sweep-alt", SCENE_RED_SWEEP_ALT);
        m.put("white-alt", SCENE_WHITE_ALT);
        m.put("blue-flow", SCENE_BLUE_FLOW);
        m.put("blue-flow-alt", SCENE_BLUE_FLOW_ALT);
        m.put("blue-blink-loop", SCENE_BLUE_BLINK_LOOP);
        m.put("xarena-flash", SCENE_XARENA_FLASH);
        m.put("party-breathe", SCENE_PARTY_BREATHE);
        m.put("party-meteor", SCENE_PARTY_METEOR);
        m.put("party-rhythm", SCENE_PARTY_RHYTHM);
        m.put("blue-drip", SCENE_BLUE_DRIP);
        SCENES = Collections.unmodifiableMap(m);
    }

    private LedProtocol() {
    }

    public static Map<String, Integer> scenes() {
        return SCENES;
    }

    public static String[] effectNames() {
        return SCENES.keySet().toArray(new String[0]);
    }

    public static boolean isEffect(String name) {
        return name != null && SCENES.containsKey(name);
    }

    public static int sceneFor(String name) {
        Integer v = SCENES.get(name);
        return v == null ? SCENE_OFF : v;
    }

    public static String sceneName(int scene) {
        for (Map.Entry<String, Integer> e : SCENES.entrySet()) {
            if (e.getValue() == scene) {
                return e.getKey();
            }
        }
        return "unknown(0x" + Integer.toHexString(scene) + ")";
    }

    public static int[] frame(int scene, int r, int g, int b) {
        return new int[]{
                0, 1, 0,
                scene & 0xff,
                0,
                r & 0xff, g & 0xff, b & 0xff
        };
    }

    public static int[] effectFrame(String name) {
        return frame(sceneFor(name), 0, 0, 0);
    }

    public static String encode(int[] frame) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frame.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.US, "%02x", frame[i] & 0xff));
        }
        return sb.toString();
    }
}
