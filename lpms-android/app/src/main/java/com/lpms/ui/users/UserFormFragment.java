package com.lpms.ui.users;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.UserResponse;
import com.lpms.databinding.FragmentUserFormBinding;
import com.lpms.ui.common.FormError;

import dagger.hilt.android.AndroidEntryPoint;

/** Create / edit a staff account. */
@AndroidEntryPoint
public final class UserFormFragment extends Fragment {
    private FragmentUserFormBinding binding;
    private UserFormViewModel viewModel;
    private StateRenderer stateRenderer;

    /** Guards the dropdown watcher while the loaded account is written in. */
    private boolean populating;

    private Role[] roles = Role.values();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(UserFormViewModel.class);

        binding.toolbar.setTitle(viewModel.isEditing()
                ? R.string.user_edit_title : R.string.user_add_title);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        // A password is only ever set at creation; editing sends username and role only.
        boolean creating = !viewModel.isEditing();
        binding.passwordLayout.setVisibility(creating ? View.VISIBLE : View.GONE);
        binding.passwordHelp.setVisibility(creating ? View.VISIBLE : View.GONE);

        setUpRoleDropdown();
        setUpValidationWatchers();

        binding.saveButton.setOnClickListener(v -> save());

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.submitting().observe(getViewLifecycleOwner(), busy ->
                binding.saveButton.setEnabled(!Boolean.TRUE.equals(busy)));
        viewModel.usernameError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.usernameLayout, issue));
        viewModel.passwordError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.passwordLayout, issue));
        viewModel.roleError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.roleLayout, issue));
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

    private void setUpRoleDropdown() {
        String[] labels = new String[roles.length];
        for (int i = 0; i < roles.length; i++) {
            labels[i] = roles[i].name();
        }
        binding.roleInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
    }

    private void setUpValidationWatchers() {
        clearErrorOnType(binding.username, binding.usernameLayout);
        clearErrorOnType(binding.password, binding.passwordLayout);
    }

    private void clearErrorOnType(@NonNull com.google.android.material.textfield.TextInputEditText field,
                                  @NonNull com.google.android.material.textfield
                                          .TextInputLayout layout) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (layout.getError() != null) {
                    layout.setError(null);
                    layout.setErrorEnabled(false);
                }
            }
        });
    }

    private void render(@Nullable UiState<UserResponse> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent() || populating) {
            return;
        }
        UserResponse user = state.valueOrNull();
        if (user == null) {
            return;
        }
        populating = true;
        try {
            binding.username.setText(user.getUsername() == null ? "" : user.getUsername());
            String role = user.getRole();
            binding.roleInput.setText(role == null ? "" : role, false);
        } finally {
            populating = false;
        }
    }

    private void applyError(@NonNull com.google.android.material.textfield.TextInputLayout layout,
                            @Nullable FormError issue) {
        if (issue == null) {
            layout.setError(null);
            layout.setErrorEnabled(false);
            return;
        }
        layout.setError(issue.resolve(requireContext()));
        layout.setErrorEnabled(true);
    }

    private void save() {
        String typedRole = binding.roleInput.getText() == null
                ? "" : binding.roleInput.getText().toString();
        viewModel.save(text(binding.username), text(binding.password), typedRole.trim());
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