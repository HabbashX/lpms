package com.lpms.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.network.NetworkCall;
import com.lpms.data.repo.AuthRepository;
import com.lpms.domain.PasswordPolicy;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Forced change-password logic.
 *
 * <p>The screen is non-dismissable whenever {@code mustChangePassword} is true (an
 * admin reset, or the first admin bootstrap) and while the backend answers other
 * endpoints with 403 {@code PASSWORD_CHANGE_REQUIRED}.</p>
 *
 * <p>On success the server invalidates every token, so local storage is discarded and
 * the user must sign in again — the ViewModel never tries to continue the old session.</p>
 */
@HiltViewModel
public final class ChangePasswordViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> success = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> currentPasswordError = new MutableLiveData<>();
    private final MutableLiveData<String> newPasswordError = new MutableLiveData<>();
    private final MutableLiveData<String> confirmPasswordError = new MutableLiveData<>();
    private final MutableLiveData<PasswordPolicy.Result> policy = new MutableLiveData<>();

    @Inject
    public ChangePasswordViewModel(@NonNull AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @NonNull
    public LiveData<Boolean> submitting() {
        return submitting;
    }

    /** True once the change succeeded; the Fragment then routes to Login. */
    @NonNull
    public LiveData<Boolean> success() {
        return success;
    }

    /** Generic server-side failure for the form (e.g. INVALID_CURRENT_PASSWORD). */
    @NonNull
    public LiveData<String> errorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<String> currentPasswordError() {
        return currentPasswordError;
    }

    @NonNull
    public LiveData<String> newPasswordError() {
        return newPasswordError;
    }

    @NonNull
    public LiveData<String> confirmPasswordError() {
        return confirmPasswordError;
    }

    /** Live policy evaluation that drives the hint list under the new-password field. */
    @NonNull
    public LiveData<PasswordPolicy.Result> policy() {
        return policy;
    }

    /** Username of the signed-in user, for the "must not contain username" rule. */
    @NonNull
    public String currentUsername() {
        com.lpms.core.auth.Session session = authRepository.sessions().current();
        return session == null ? "" : session.getUsername();
    }

    /**
     * @param username the signed-in user, used by the "must not contain username" rule
     */
    public void submit(@NonNull String username,
                       @NonNull String currentPassword,
                       @NonNull String newPassword,
                       @NonNull String confirmPassword) {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        clearErrors();

        PasswordPolicy.Result result =
                PasswordPolicy.evaluate(newPassword, username, currentPassword);
        policy.setValue(result);

        boolean valid = true;
        if (currentPassword.trim().isEmpty()) {
            currentPasswordError.setValue(com.lpms.R.string.error_validation);
            valid = false;
        }
        if (!result.isValid()) {
            valid = false;
        }
        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordError.setValue(com.lpms.R.string.change_password_mismatch);
            valid = false;
        }
        if (!valid) {
            return;
        }

        submitting.setValue(true);
        disposables.add(authRepository.changePassword(currentPassword, newPassword)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            submitting.setValue(false);
                            // SessionManager already wiped storage; go back to Login.
                            success.setValue(true);
                        },
                        throwable -> {
                            submitting.setValue(false);
                            applyServerError(NetworkCall.asApiError(throwable));
                        }));
    }

    /** Re-evaluates the policy as the user types, without submitting anything. */
    public void onNewPasswordChanged(@NonNull String username,
                                     @NonNull String newPassword,
                                     @Nullable String currentPassword) {
        policy.setValue(PasswordPolicy.evaluate(newPassword, username, currentPassword));
    }

    public void onCurrentPasswordChanged() {
        currentPasswordError.setValue(null);
        errorMessage.setValue(null);
    }

    public void clearErrors() {
        currentPasswordError.setValue(null);
        newPasswordError.setValue(null);
        confirmPasswordError.setValue(null);
        errorMessage.setValue(null);
    }

    private void applyServerError(com.lpms.core.error.ApiError error) {
        String currentMessage = error.messageFor("currentPassword");
        if (currentMessage != null) {
            currentPasswordError.setValue(currentMessage);
            return;
        }
        String newMessage = error.messageFor("newPassword");
        if (newMessage != null) {
            newPasswordError.setValue(newMessage);
            return;
        }
        errorMessage.setValue(error.getMessage());
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}