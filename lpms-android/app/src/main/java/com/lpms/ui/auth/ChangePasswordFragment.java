package com.lpms.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.lpms.R;
import com.lpms.databinding.FragmentChangePasswordBinding;
import com.lpms.domain.PasswordPolicy;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Forced, non-dismissable change-password screen.
 *
 * <p>Reached either from the session gate when {@code mustChangePassword} is true, or
 * from any screen that receives 403 {@code PASSWORD_CHANGE_REQUIRED}. Back navigation is
 * disabled so the user cannot escape into the rest of the app.</p>
 */
@AndroidEntryPoint
public final class ChangePasswordFragment extends Fragment {

    private FragmentChangePasswordBinding binding;
    private ChangePasswordViewModel viewModel;
    private String username = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChangePasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ChangePasswordViewModel.class);

        // Non-dismissable: no toolbar, no back gesture.
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().getOnBackPressedDispatcher()
                .onBackPressed());

        username = viewModel.currentUsername();

        binding.currentPassword.addTextChangedListener(simpleWatcher(
                text -> viewModel.onCurrentPasswordChanged()));
        binding.newPassword.addTextChangedListener(simpleWatcher(text -> {
            binding.newPasswordLayout.setError(null);
            viewModel.onNewPasswordChanged(username, text, text(binding.currentPassword));
        }));
        binding.confirmPassword.addTextChangedListener(simpleWatcher(text ->
                binding.confirmPasswordLayout.setError(null)));

        binding.submit.setOnClickListener(v -> viewModel.submit(
                username,
                text(binding.currentPassword),
                text(binding.newPassword),
                text(binding.confirmPassword)));

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.submitting().observe(getViewLifecycleOwner(), submitting -> {
            boolean busy = Boolean.TRUE.equals(submitting);
            binding.submit.setEnabled(!busy);
            binding.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        });

        viewModel.currentPasswordError().observe(getViewLifecycleOwner(), message ->
                applyError(binding.currentPasswordLayout, message));
        viewModel.newPasswordError().observe(getViewLifecycleOwner(), message ->
                applyError(binding.newPasswordLayout, message));
        viewModel.confirmPasswordError().observe(getViewLifecycleOwner(), message ->
                applyError(binding.confirmPasswordLayout, message));

        viewModel.errorMessage().observe(getViewLifecycleOwner(), message -> {
            binding.errorBanner.setVisibility(message == null ? View.GONE : View.VISIBLE);
            binding.errorBanner.setText(message == null ? "" : message);
        });

        viewModel.policy().observe(getViewLifecycleOwner(), this::renderPolicy);

        viewModel.success().observe(getViewLifecycleOwner(), success -> {
            if (!Boolean.TRUE.equals(success)) {
                return;
            }
            // All tokens are invalid now; the only correct destination is Login.
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_global_login);
        });
    }

    private void renderPolicy(@Nullable PasswordPolicy.Result result) {
        if (binding == null || result == null) {
            return;
        }
        binding.policyContainer.removeAllViews();
        List<PasswordPolicy.Rule> rules = result.rules();
        for (int i = 0; i < rules.size(); i++) {
            PasswordPolicy.Rule rule = rules.get(i);
            TextView row = (TextView) getLayoutInflater()
                    .inflate(R.layout.item_policy_hint, binding.policyContainer, false);
            row.setText(hintText(rule));
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    rule.isSatisfied() ? R.drawable.ic_check : R.drawable.ic_error_outline, 0, 0, 0);
            row.setAlpha(rule.isSatisfied() ? 0.6f : 1f);
            binding.policyContainer.addView(row);
        }
    }

    @NonNull
    private String hintText(@NonNull PasswordPolicy.Rule rule) {
        switch (rule.getStringResourceName()) {
            case "error_password_length":
                return getString(R.string.error_password_length, rule.getMin(), rule.getMax());
            case "error_password_letter":
                return getString(R.string.error_password_letter);
            case "error_password_digit":
                return getString(R.string.error_password_digit);
            case "error_password_contains_username":
                return getString(R.string.error_password_contains_username);
            case "error_password_same":
            default:
                return getString(R.string.error_password_same);
        }
    }

    private void applyError(@NonNull com.google.android.material.textfield.TextInputLayout layout,
                            @Nullable String message) {
        if (message == null) {
            layout.setError(null);
            layout.setErrorEnabled(false);
            return;
        }
        layout.setError(message);
        layout.setErrorEnabled(true);
    }

    @NonNull
    private String text(@NonNull TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @NonNull
    private static TextWatcher simpleWatcher(@NonNull java.util.function.Consumer<String> action) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                action.accept(s == null ? "" : s.toString());
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}