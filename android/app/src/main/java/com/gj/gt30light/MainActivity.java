package com.gj.gt30light;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.gj.gt30light.databinding.ActivityMainBinding;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final Map<String, String> EFFECT_LABELS = new LinkedHashMap<>();
    static {
        EFFECT_LABELS.put("off", "STANDBY");
        EFFECT_LABELS.put("on", "WHITE SURGE");
        EFFECT_LABELS.put("game-launch", "GAME LAUNCH");
        EFFECT_LABELS.put("notification", "NOTIFY PULSE");
        EFFECT_LABELS.put("music-preview", "MUSIC MODE");
        EFFECT_LABELS.put("charging", "CHARGE CYCLE");
        EFFECT_LABELS.put("camera-record", "SHUTTER");
        EFFECT_LABELS.put("flip-to-flash", "FLIP FLASH");
        EFFECT_LABELS.put("red-trail", "RED TRAIL");
        EFFECT_LABELS.put("dim-white", "DIM WHITE");
        EFFECT_LABELS.put("music-once", "MUSIC ONCE");
        EFFECT_LABELS.put("camera-5s", "SHUTTER 5S");
        EFFECT_LABELS.put("camera-10s", "SHUTTER 10S");
        EFFECT_LABELS.put("red-trailer", "RED TRAILER");
        EFFECT_LABELS.put("red-sweep", "RED SWEEP");
        EFFECT_LABELS.put("red-sweep-alt", "RED SWEEP ALT");
        EFFECT_LABELS.put("white-alt", "WHITE SURGE ALT");
        EFFECT_LABELS.put("xarena-flash", "XARENA");
        EFFECT_LABELS.put("party-breathe", "PARTY BREATHE");
        EFFECT_LABELS.put("party-meteor", "PARTY METEOR");
        EFFECT_LABELS.put("party-rhythm", "PARTY RHYTHM");
        EFFECT_LABELS.put("blue-drip", "BLUE DRIP");
        EFFECT_LABELS.put("blue-flow", "BLUE FLOW");
        EFFECT_LABELS.put("blue-flow-alt", "BLUE FLOW ALT");
        EFFECT_LABELS.put("blue-blink-loop", "BLUE BLINK LOOP");
    }

    private ActivityMainBinding binding;
    private LedViewModel viewModel;
    private final List<MaterialButton> effectButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LedViewModel.class);

        setupUI();
        observeViewModel();
        viewModel.connect();
    }

    private void setupUI() {
        binding.btnOn.setOnClickListener(v -> viewModel.setOn());
        binding.btnOff.setOnClickListener(v -> viewModel.setOff());

        buildEffectGrid();

        binding.btnApplyColor.setOnClickListener(v -> {
            int r = (int) binding.colorRed.getValue();
            int g = (int) binding.colorGreen.getValue();
            int b = (int) binding.colorBlue.getValue();
            viewModel.applyColor(r, g, b);
        });

        com.google.android.material.slider.Slider.OnChangeListener colorChanged =
                (slider, value, fromUser) -> {
                    if (fromUser) {
                        viewModel.updateColor(
                                (int) binding.colorRed.getValue(),
                                (int) binding.colorGreen.getValue(),
                                (int) binding.colorBlue.getValue());
                    }
                };
        binding.colorRed.addOnChangeListener(colorChanged);
        binding.colorGreen.addOnChangeListener(colorChanged);
        binding.colorBlue.addOnChangeListener(colorChanged);

        binding.btnShizuku.setOnClickListener(v -> onShizukuButton());
        binding.btnCoffee.setOnClickListener(v -> openSupportLink());
        binding.btnShizukuGuide.setOnClickListener(v -> openShizukuSite());
        binding.btnDevSettings.setOnClickListener(v -> openDevSettings());

        binding.btnSendScene.setOnClickListener(v -> {
            String text = binding.sceneIdInput.getText() == null
                    ? "" : binding.sceneIdInput.getText().toString().trim();
            try {
                viewModel.sendRawScene(Integer.parseInt(text));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Enter a scene number", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildEffectGrid() {
        GridLayout grid = binding.effectGrid;
        grid.removeAllViews();
        effectButtons.clear();
        int cols = 2;
        int i = 0;
        for (Map.Entry<String, String> e : EFFECT_LABELS.entrySet()) {
            if (!LedProtocol.isEffect(e.getKey())) {
                continue;
            }
            final String key = e.getKey();
            MaterialButton b = new MaterialButton(
                    this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setCheckable(true);
            b.setChecked(false);
            b.setText(e.getValue());
            b.setTextSize(12);
            b.setTypeface(getResources().getFont(R.font.mecha));
            b.setCornerRadius(getResources().getDimensionPixelSize(R.dimen.effect_corner));
            b.setBackgroundTintList(getColorStateList(R.color.effect_toggle_tint));
            b.setTextColor(getColorStateList(R.color.effect_toggle_text));
            b.setStrokeColor(getColorStateList(R.color.effect_toggle_stroke));
            b.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.effect_stroke));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / cols, 1f),
                    GridLayout.spec(i % cols, 1f));
            lp.width = 0;
            int m = getResources().getDimensionPixelSize(R.dimen.effect_margin);
            lp.setMargins(m, m, m, m);
            b.setOnClickListener(v -> {
                for (MaterialButton other : effectButtons) {
                    other.setChecked(other == b);
                }
                viewModel.applyEffect(key);
            });
            grid.addView(b, lp);
            effectButtons.add(b);
            i++;
        }
    }

    private void onShizukuButton() {
        ShizukuBridge.State state = viewModel.getBridgeState().getValue();
        if (state == null) {
            viewModel.connect();
            return;
        }
        switch (state) {
            case NEEDS_PERMISSION:
                viewModel.requestPermission();
                break;
            case WAITING_FOR_SHIZUKU:
            case ERROR:
                viewModel.connect();
                Toast.makeText(this,
                        getString(R.string.shizuku_start_hint), Toast.LENGTH_LONG).show();
                break;
            case UNAVAILABLE:
                Toast.makeText(this,
                        getString(R.string.shizuku_not_available), Toast.LENGTH_LONG).show();
                break;
            case READY:
                viewModel.refreshFromHardware();
                break;
            default:
                viewModel.connect();
                break;
        }
    }

    private void openSupportLink() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.support_link))));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.support_link), Toast.LENGTH_LONG).show();
        }
    }

    private void openShizukuSite() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.shizuku_url))));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.shizuku_url), Toast.LENGTH_LONG).show();
        }
    }

    private void openDevSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Open Settings → Developer options", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void observeViewModel() {
        viewModel.getLedOn().observe(this, on -> {
            binding.statusTitle.setText(on
                    ? getString(R.string.led_status_on)
                    : getString(R.string.led_status_off));
            binding.ledCore.setBackgroundTintList(ColorStateList.valueOf(
                    getColor(on ? R.color.neon_green : R.color.neon_off)));
        });

        viewModel.getCurrentEffect().observe(this, effect -> {
            if (effect == null) {
                return;
            }
            String label = EFFECT_LABELS.get(effect);
            if (label != null && Boolean.TRUE.equals(viewModel.getLedOn().getValue())) {
                binding.statusTitle.setText(getString(R.string.led_status_on) + " · " + label);
            }
            int idx = 0;
            for (String key : EFFECT_LABELS.keySet()) {
                if (idx < effectButtons.size() && LedProtocol.isEffect(key)) {
                    effectButtons.get(idx).setChecked(key.equals(effect));
                    idx++;
                }
            }
        });

        viewModel.getToast().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getBridgeState().observe(this, state -> updateForState(state));
        viewModel.getBridgeDetail().observe(this, detail -> {
            binding.statusSubtitle.setText(detail == null ? "" : detail);
        });

        viewModel.getColorR().observe(this, v -> syncSlider(binding.colorRed, v));
        viewModel.getColorG().observe(this, v -> syncSlider(binding.colorGreen, v));
        viewModel.getColorB().observe(this, v -> syncSlider(binding.colorBlue, v));
    }

    private void syncSlider(com.google.android.material.slider.Slider slider, Integer v) {
        if (v != null && Math.abs(slider.getValue() - v) > 0.5f) {
            slider.setValue(v);
        }
    }

    private void updateForState(ShizukuBridge.State state) {
        if (state == null) {
            state = ShizukuBridge.State.WAITING_FOR_SHIZUKU;
        }
        boolean ready = state == ShizukuBridge.State.READY;

        binding.btnOn.setEnabled(ready);
        binding.btnOff.setEnabled(ready);
        binding.btnApplyColor.setEnabled(ready);
        binding.sceneTestRow.setVisibility(ready ? View.VISIBLE : View.GONE);
        binding.colorPickerCard.setVisibility(ready ? View.VISIBLE : View.GONE);
        for (MaterialButton b : effectButtons) {
            b.setEnabled(ready);
        }

        int dot;
        int linkColor;
        String link;
        switch (state) {
            case READY:
                dot = R.color.neon_cyan;
                linkColor = R.color.neon_cyan;
                link = "LINK // ONLINE";
                binding.btnShizuku.setText(getString(R.string.disconnect_shizuku));
                break;
            case NEEDS_PERMISSION:
                dot = R.color.neon_orange;
                linkColor = R.color.neon_orange;
                link = "LINK // GRANT ACCESS";
                binding.btnShizuku.setText(getString(R.string.grant_shizuku));
                break;
            case UNAVAILABLE:
                dot = R.color.neon_red;
                linkColor = R.color.neon_red;
                link = "LINK // NO UPLINK";
                binding.btnShizuku.setText(getString(R.string.install_shizuku));
                break;
            case WAITING_FOR_SHIZUKU:
            case ERROR:
            default:
                dot = R.color.neon_off;
                linkColor = R.color.neon_orange;
                link = state == ShizukuBridge.State.ERROR ? "LINK // FAULT" : "LINK // STANDBY";
                binding.btnShizuku.setText(getString(R.string.connect_shizuku));
                break;
        }
        binding.statusDot.setImageTintList(ColorStateList.valueOf(getColor(dot)));
        binding.linkState.setText(link);
        binding.linkState.setTextColor(getColor(linkColor));
    }
}
