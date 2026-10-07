package com.lpms.ui.customers;

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

import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.databinding.FragmentCustomerFormBinding;
import com.lpms.ui.common.FormError;

import dagger.hilt.android.AndroidEntryPoint;

/** Create / edit a customer. */
@AndroidEntryPoint
public final class CustomerFormFragment extends Fragment {
    private FragmentCustomerFormBinding binding;
    private CustomerFormViewModel viewModel;
    private StateRenderer stateRenderer;

    /** Guards against the loaded customer overwriting what the user has typed. */
    private boolean populating;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CustomerFormViewModel.class);

        binding.toolbar.setTitle(viewModel.isEditing()
                ? R.string.customer_edit_title : R.string.customer_add_title);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        binding.activeSwitch.setVisibility(viewModel.isEditing() ? View.VISIBLE : View.GONE);
        binding.saveButton.setOnClickListener(v -> save());
        binding.name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (binding.nameLayout.getError() != null) {
                    binding.nameLayout.setError(null);
                    binding.nameLayout.setErrorEnabled(false);
                }
            }
        });

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.submitting().observe(getViewLifecycleOwner(), busy ->
                binding.saveButton.setEnabled(!Boolean.TRUE.equals(busy)));
        viewModel.nameError().observe(getViewLifecycleOwner(), issue ->
                applyError(issue));
        viewModel.message().observe(getViewLifecycleOwner(), issue -> {
            if (issue == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), issue.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });
        viewModel.saved().observe(getViewLifecycleOwner(), id -> {
            if (id != null) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<CustomerResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent() || populating) {
            return;
        }
        CustomerResponse customer = state.valueOrNull();
        if (customer == null) {
            return;
        }
        populating = true;
        try {
            binding.name.setText(customer.getName() == null ? "" : customer.getName());
            binding.phone.setText(customer.getPhone() == null ? "" : customer.getPhone());
            binding.address.setText(customer.getAddress() == null ? "" : customer.getAddress());
            binding.notes.setText(customer.getNotes() == null ? "" : customer.getNotes());
            binding.activeSwitch.setChecked(!customer.isActive());
        } finally {
            populating = false;
        }
    }

    private void applyError(@Nullable FormError issue) {
        if (issue == null) {
            binding.nameLayout.setError(null);
            binding.nameLayout.setErrorEnabled(false);
            return;
        }
        binding.nameLayout.setError(issue.resolve(requireContext()));
        binding.nameLayout.setErrorEnabled(true);
    }

    private void save() {
        viewModel.save(text(binding.name), text(binding.phone), text(binding.address),
                text(binding.notes),
                viewModel.isEditing() && binding.activeSwitch.isChecked());
    }

    @NonNull
    private String text(@NonNull com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}