package com.gj.gt30light;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.gj.gt30light.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private LedViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        viewModel = new ViewModelProvider(this).get(LedViewModel.class);

        setupUI();
        observeViewModel();
        viewModel.connect();
    }

    private void setupUI() {
        binding.btnOn.setOnClickListener(v -> viewModel.setOn());
        binding.btnOff.setOnClickListener(v -> viewModel.setOff());

        String[] effects = LedProtocol.effectNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, effects);
        binding.effectSelector.setAdapter(adapter);
        binding.effectSelector.setOnItemClickListener((parent, view, position, id) -> {
            String effect = (String) parent.getItemAtPosition(position);
            viewModel.applyEffect(effect);
        });

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

        binding.fabTest.setOnClickListener(v -> viewModel.applyEffect("game-launch"));

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

    private void observeViewModel() {
        viewModel.getLedOn().observe(this, on -> {
            binding.statusTitle.setText(on
                    ? getString(R.string.led_status_on)
                    : getString(R.string.led_status_off));
            binding.ledIndicator.setBackgroundColor(
                    getColor(on ? R.color.led_indicator_on : R.color.led_indicator_off));
        });

        viewModel.getCurrentEffect().observe(this, effect -> {
            if (effect != null && viewModel.getLedOn().getValue() != null) {
                if (Boolean.TRUE.equals(viewModel.getLedOn().getValue())) {
                    binding.statusTitle.setText(getString(R.string.led_status_on) + " · " + effect);
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

    private void updateForState(ShizukuBridge.State state) {        if (state == null) {
            state = ShizukuBridge.State.WAITING_FOR_SHIZUKU;
        }
        boolean ready = state == ShizukuBridge.State.READY;

        binding.btnOn.setEnabled(ready);
        binding.btnOff.setEnabled(ready);
        binding.effectSelector.setEnabled(ready);
        binding.btnApplyColor.setEnabled(ready);
        binding.sceneTestRow.setVisibility(ready ? View.VISIBLE : View.GONE);
        binding.fabTest.setVisibility(ready ? View.VISIBLE : View.GONE);
        binding.colorPickerCard.setVisibility(ready ? View.VISIBLE : View.GONE);

        switch (state) {
            case READY:
                binding.shizukuStatusIcon.setImageResource(R.drawable.ic_shizuku_connected);
                binding.btnShizuku.setText(getString(R.string.disconnect_shizuku));
                break;
            case NEEDS_PERMISSION:
                binding.shizukuStatusIcon.setImageResource(R.drawable.ic_shizuku_disconnected);
                binding.btnShizuku.setText(getString(R.string.grant_shizuku));
                break;
            case UNAVAILABLE:
                binding.shizukuStatusIcon.setImageResource(R.drawable.ic_shizuku_disconnected);
                binding.btnShizuku.setText(getString(R.string.install_shizuku));
                break;
            case WAITING_FOR_SHIZUKU:
            case ERROR:
            default:
                binding.shizukuStatusIcon.setImageResource(R.drawable.ic_shizuku_disconnected);
                binding.btnShizuku.setText(getString(R.string.connect_shizuku));
                break;
        }
    }
}
