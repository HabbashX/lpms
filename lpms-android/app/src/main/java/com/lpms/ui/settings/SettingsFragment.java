package com.lpms.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lpms.R;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.SettingResponse;
import com.lpms.data.dto.SettingType;
import com.lpms.databinding.FragmentSettingsBinding;
import com.lpms.ui.common.FormError;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Pharmacy settings.
 *
 * <p>Fields are generated from the server's response because it decides which settings
 * exist and what type each one is. A boolean gets a switch, an integer a number field, and
 * an unrecognised type is shown read-only - sending it back untouched would be a guess.</p>
 */
@AndroidEntryPoint
public final class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private StateRenderer stateRenderer;

    /** Guards the generated field watchers while the server values are written in. */
    private boolean populating;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.saveButton.setOnClickListener(v -> viewModel.save());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.saving().observe(getViewLifecycleOwner(), saving -> {
            boolean working = Boolean.TRUE.equals(saving);
            binding.busy.setVisibility(working ? View.VISIBLE : View.GONE);
            // A second tap would send the same partial update twice.
            binding.saveButton.setEnabled(!working);
        });
        viewModel.message().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<List<SettingResponse>> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        buildFields(state.valueOrNull());
    }

    private void buildFields(@Nullable List<SettingResponse> settings) {
        binding.fields.removeAllViews();
        if (settings == null || settings.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        populating = true;
        try {
            for (SettingResponse setting : settings) {
                String key = setting.getKey();
                if (key == null) {
                    continue;
                }
                SettingType type = setting.settingType();
                if (type == SettingType.BOOLEAN) {
                    binding.fields.addView(buildSwitch(inflater, setting, key));
                } else if (type == SettingType.INTEGER) {
                    binding.fields.addView(buildNumberField(inflater, setting, key));
                } else {
                    // UNKNOWN: shown, never sent back.
                    binding.fields.addView(buildReadOnly(inflater, setting));
                }
            }
        } finally {
            populating = false;
        }
    }

    @NonNull
    private MaterialSwitch buildSwitch(@NonNull LayoutInflater inflater,
                                       @NonNull SettingResponse setting,
                                       @NonNull String key) {
        MaterialSwitch toggle = (MaterialSwitch) inflater.inflate(R.layout.item_setting_switch,
                binding.fields, false);
        toggle.setText(labelOf(setting));
        toggle.setChecked("true".equalsIgnoreCase(setting.getValue()));
        toggle.setOnCheckedChangeListener((view, checked) -> {
            if (!populating) {
                viewModel.onValueChanged(key, checked ? "true" : "false");
            }
        });
        return toggle;
    }

    @NonNull
    private TextInputLayout buildNumberField(@NonNull LayoutInflater inflater,
                                             @NonNull SettingResponse setting,
                                             @NonNull String key) {
        TextInputLayout layout = (TextInputLayout) inflater.inflate(
                R.layout.item_setting_number, binding.fields, false);
        layout.setHint(labelOf(setting));
        TextInputEditText field = layout.findViewById(R.id.value);
        field.setText(viewModel.valueFor(setting));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!populating) {
                    viewModel.onValueChanged(key, s == null ? "" : s.toString());
                }
            }
        });
        return layout;
    }

    /** A setting this build cannot edit is still worth seeing, but must not be sent back. */
    @NonNull
    private View buildReadOnly(@NonNull LayoutInflater inflater,
                               @NonNull SettingResponse setting) {
        View row = inflater.inflate(R.layout.item_setting_readonly, binding.fields, false);
        ((android.widget.TextView) row.findViewById(R.id.label)).setText(labelOf(setting));
        ((android.widget.TextView) row.findViewById(R.id.value)).setText(
                setting.getValue() == null ? "" : setting.getValue());
        return row;
    }

    /** The server's own description wins; the key is the fallback for a blank one. */
    @NonNull
    private String labelOf(@NonNull SettingResponse setting) {
        String description = setting.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            return description.trim();
        }
        return setting.getKey() == null ? "" : setting.getKey();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}