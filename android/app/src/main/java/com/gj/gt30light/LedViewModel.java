package com.gj.gt30light;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class LedViewModel extends AndroidViewModel {

    private final ShizukuBridge bridge;

    private final MutableLiveData<ShizukuBridge.State> bridgeState = new MutableLiveData<>();
    private final MutableLiveData<String> bridgeDetail = new MutableLiveData<>();
    private final MutableLiveData<String> toast = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ledOn = new MutableLiveData<>(false);
    private final MutableLiveData<String> currentEffect = new MutableLiveData<>("off");
    private final MutableLiveData<Integer> colorR = new MutableLiveData<>(255);
    private final MutableLiveData<Integer> colorG = new MutableLiveData<>(255);
    private final MutableLiveData<Integer> colorB = new MutableLiveData<>(255);

    public LedViewModel(@NonNull Application application) {
        super(application);
        bridge = new ShizukuBridge(application);
        bridge.attach((state, detail) -> {
            bridgeState.postValue(state);
            bridgeDetail.postValue(detail);
            if (state == ShizukuBridge.State.READY) {
                refreshFromHardware();
            }
        });
    }

    @Override
    protected void onCleared() {
        bridge.detach();
        super.onCleared();
    }

    public LiveData<ShizukuBridge.State> getBridgeState() {
        return bridgeState;
    }

    public LiveData<String> getBridgeDetail() {
        return bridgeDetail;
    }

    public LiveData<String> getToast() {
        return toast;
    }

    public LiveData<Boolean> getLedOn() {
        return ledOn;
    }

    public LiveData<String> getCurrentEffect() {
        return currentEffect;
    }

    public LiveData<Integer> getColorR() {
        return colorR;
    }

    public LiveData<Integer> getColorG() {
        return colorG;
    }

    public LiveData<Integer> getColorB() {
        return colorB;
    }

    public void connect() {
        bridge.connect();
    }

    public void requestPermission() {
        bridge.requestPermission();
    }

    public void updateColor(int r, int g, int b) {
        colorR.postValue(clampByte(r));
        colorG.postValue(clampByte(g));
        colorB.postValue(clampByte(b));
    }

    public void setOn() {
        send(LedProtocol.effectFrame("on"), true, "on");
    }

    public void setOff() {
        send(LedProtocol.frame(LedProtocol.SCENE_OFF, 0, 0, 0), false, "off");
    }

    public void applyEffect(String name) {
        if (!LedProtocol.isEffect(name)) {
            toast.postValue("Unknown effect: " + name);
            return;
        }
        boolean on = !"off".equals(name);
        send(LedProtocol.effectFrame(name), on, name);
    }

    public void applyColor(int r, int g, int b) {
        updateColor(r, g, b);
        send(LedProtocol.frame(LedProtocol.SCENE_CUSTOM_GLOW,
                clampByte(r), clampByte(g), clampByte(b)), true, "custom");
    }

    public void sendRawScene(int scene) {
        if (scene < 0 || scene > 255) {
            toast.postValue("Scene must be 0-255");
            return;
        }
        send(LedProtocol.frame(scene, 0, 0, 0), scene != LedProtocol.SCENE_OFF,
                LedProtocol.sceneName(scene));
    }

    public void refreshFromHardware() {
        bridge.getEffect(new ShizukuBridge.Callback() {
            @Override
            public void onSuccess(String message) {
                String[] parts = message.trim().split("\\s+");
                if (parts.length != LedProtocol.FRAME_LEN) {
                    return;
                }
                try {
                    int scene = Integer.parseInt(parts[3], 16);
                    ledOn.postValue(scene != LedProtocol.SCENE_OFF);
                    currentEffect.postValue(LedProtocol.sceneName(scene));
                    colorR.postValue(Integer.parseInt(parts[5], 16));
                    colorG.postValue(Integer.parseInt(parts[6], 16));
                    colorB.postValue(Integer.parseInt(parts[7], 16));
                } catch (NumberFormatException ignored) {
                    // keep last known state
                }
            }

            @Override
            public void onError(String error) {
                // Silent: state UI already shows bridge status.
            }
        });
    }

    private int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void send(int[] frame, boolean on, String effect) {
        bridge.setEffect(frame, new ShizukuBridge.Callback() {
            @Override
            public void onSuccess(String message) {
                ledOn.postValue(on);
                currentEffect.postValue(effect);
                toast.postValue(message);
            }

            @Override
            public void onError(String error) {
                toast.postValue("Error: " + error);
            }
        });
    }
}
