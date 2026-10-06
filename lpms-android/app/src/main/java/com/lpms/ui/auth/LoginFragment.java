package com.lpms.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.core.error.ApiErrorCodes;
import com.lpms.core.ui.ErrorPresenter;
import com.lpms.databinding.FragmentLoginBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Sign-in screen.
 *
 * <p>Handles the four documented auth failures, keeps the submit button disabled while
 * the request is in flight, and shows a 429 countdown. On the dev flavor it also exposes
 * the server URL override used by {@link com.lpms.core.network.BaseUrlInterceptor}.</p>
 */
@AndroidEntryPoint
public final class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private LoginViewModel viewModel;



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setUpInputs();
        observeViewModel();
    }


    private void setUpInputs() {
        binding.username.addTextChangedListener(afterTextChanged(
                text -> {
                    if (binding.usernameLayout.getError() != null) {
                        binding.usernameLayout.setError(null);
                    }
                    binding.usernameLayout.setErrorEnabled(false);
                    clearError();
                }));

        binding.password.addTextChangedListener(afterTextChanged(
                text -> {
                    if (binding.passwordLayout.getError() != null) {
                        binding.passwordLayout.setError(null);
                    }
                    binding.passwordLayout.setErrorEnabled(false);
                    clearError();
                }));

        binding.password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        binding.loginButton.setOnClickListener(v -> submit());
    }


    private void submit() {
        clearError();
        viewModel.login(text(binding.username), text(binding.password));
    }

    private void observeViewModel() {
        viewModel.loading().observe(getViewLifecycleOwner(), loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            binding.loginButton.setEnabled(!busy && isNotRateLimited());
            binding.loginProgress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
            binding.usernameLayout.setEnabled(!busy);
            binding.passwordLayout.setEnabled(!busy);
        });

        viewModel.next().observe(getViewLifecycleOwner(), next -> {
            if (next == null) {
                return;
            }
            int action = next == LoginViewModel.Next.CHANGE_PASSWORD
                    ? R.id.changePasswordFragment
                    : R.id.dashboardFragment;
            NavHostFragment.findNavController(this).navigate(action);
        });

        viewModel.error().observe(getViewLifecycleOwner(), this::renderError);

        // Shown only when a login attempt fails with a transport error: tells the user
        // whether the deployed server is asleep or the device is offline.
        viewModel.connectivityHintRes().observe(getViewLifecycleOwner(), res ->
                showBanner(res == null ? "" : getString(res)));


        viewModel.rateLimitCountdown().observe(getViewLifecycleOwner(), seconds -> {
            long value = seconds == null ? 0L : seconds;
            binding.loginButton.setEnabled(value == 0L
                    && !Boolean.TRUE.equals(viewModel.loading().getValue()));
            if (value > 0L) {
                long minutes = (value + 59L) / 60L;
                binding.loginButton.setText(getString(R.string.login_wait_minutes, minutes));
            } else {
                binding.loginButton.setText(R.string.login);
            }
        });
    }

    private void renderError(@Nullable LoginViewModel.LoginError loginError) {
        if (loginError == null) {
            clearError();
            return;
        }
        ApiError error = loginError.getError();
        String message = ErrorPresenter.message(requireContext(), error);

        // Validation-style failures belong on the field; everything else is a banner.
        if (ApiErrorCodes.VALIDATION_FAILED.equals(error.getCode())) {
            applyFieldError(binding.usernameLayout, "username", error);
            applyFieldError(binding.passwordLayout, "password", error);
            if (binding.usernameLayout.getError() == null
                    && binding.passwordLayout.getError() == null) {
                showBanner(message);
            }
            return;
        }

        switch (error.getCode()) {
            case ApiErrorCodes.INVALID_CREDENTIALS:
                applyFieldError(binding.passwordLayout, "password",
                        error.isValidationFailure() ? error : null, message);
                return;
            case ApiErrorCodes.ACCOUNT_LOCKED:
                showBanner(getString(R.string.error_account_locked_hint, message));
                return;
            case ApiErrorCodes.RATE_LIMITED:
                showBanner(getString(R.string.error_rate_limited));
                return;
            case ApiErrorCodes.ACCOUNT_DISABLED:
                showBanner(getString(R.string.error_account_disabled_hint, message));
                return;
            default:
                showBanner(message);
        }
    }

    private void applyFieldError(@NonNull TextInputLayout layout,
                                 @NonNull String field,
                                 @NonNull ApiError error) {
        String specific = error.messageFor(field);
        if (specific != null) {
            layout.setError(specific);
            layout.setErrorEnabled(true);
        }
    }

    private void applyFieldError(@NonNull TextInputLayout layout,
                                 @NonNull String field,
                                 @Nullable ApiError error,
                                 @NonNull String fallback) {
        String specific = error == null ? null : error.messageFor(field);
        layout.setError(specific == null ? fallback : specific);
        layout.setErrorEnabled(true);
    }

    /** Inline banner: inline is better than a snackbar for errors tied to this form. */
    private void showBanner(@NonNull String message) {
        binding.errorBanner.setText(message);
        binding.errorBanner.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        if (binding == null) {
            return;
        }
        binding.errorBanner.setVisibility(View.GONE);
    }

    private boolean isNotRateLimited() {
        Long seconds = viewModel.rateLimitCountdown().getValue();
        return seconds == null || seconds == 0L;
    }

    @NonNull
    private String text(@NonNull TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @NonNull
    private static TextWatcher afterTextChanged(@NonNull java.util.function.Consumer<String> action) {
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
