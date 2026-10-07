package com.lpms.ui.users;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.CreateUserRequest;
import com.lpms.data.dto.UpdateUserRequest;
import com.lpms.data.dto.UserResponse;
import com.lpms.data.repo.UserRepository;
import com.lpms.ui.common.FormError;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Create / edit a staff account. ADMIN only.
 *
 * <p>Password is required when creating and never editable afterwards: changing it goes
 * through the reset action, which forces a change at next sign-in. The server also forces
 * a change for a newly created account, so the password here is only ever a starting one.</p>
 */
@HiltViewModel
public final class UserFormViewModel extends ViewModel {

    /** Mirrors the server's own minimum, so the round trip is not wasted. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final long userId;

    private final MutableLiveData<UiState<UserResponse>> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<FormError> usernameError = new MutableLiveData<>();
    private final MutableLiveData<FormError> passwordError = new MutableLiveData<>();
    private final MutableLiveData<FormError> roleError = new MutableLiveData<>();
    private final MutableLiveData<FormError> message = new MutableLiveData<>();
    private final MutableLiveData<Long> saved = new MutableLiveData<>();

    @Inject
    public UserFormViewModel(@NonNull UserRepository userRepository,
                             @NonNull SavedStateHandle handle) {
        this.userRepository = userRepository;
        Long id = handle.get(UserListFragment.ARG_USER_ID);
        this.userId = id == null ? 0L : id;
    }

    @NonNull
    public LiveData<UiState<UserResponse>> state() {
        return state;
    }

    @NonNull
    public LiveData<Boolean> submitting() {
        return submitting;
    }

    @NonNull
    public LiveData<FormError> usernameError() {
        return usernameError;
    }

    @NonNull
    public LiveData<FormError> passwordError() {
        return passwordError;
    }

    @NonNull
    public LiveData<FormError> roleError() {
        return roleError;
    }

    @NonNull
    public LiveData<FormError> message() {
        return message;
    }

    @NonNull
    public LiveData<Long> saved() {
        return saved;
    }

    public boolean isEditing() {
        return userId > 0L;
    }

    public void load() {
        if (!isEditing()) {
            // A ready-to-fill form, with no spinner flash on a create screen.
            state.setValue(UiState.content(new UserResponse(
                    null, null, null, true, false, null, null, null)));
            return;
        }
        state.setValue(UiState.loading());
        disposables.add(userRepository.get(userId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> state.setValue(UiState.content(user)),
                        throwable -> state.setValue(
                                UiState.error(NetworkCall.asApiError(throwable)))));
    }

    public void save(@Nullable String username, @Nullable String password,
                     @Nullable String roleWireValue) {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        clearErrors();

        boolean valid = true;

        if (username == null || username.trim().isEmpty()) {
            usernameError.setValue(FormError.of(R.string.user_error_username_required));
            valid = false;
        }

        if (!isEditing()) {
            String typed = password == null ? "" : password;
            if (typed.length() < MIN_PASSWORD_LENGTH) {
                passwordError.setValue(FormError.of(R.string.user_error_password_short));
                valid = false;
            }
        }

        com.lpms.core.auth.Role role =
                com.lpms.core.auth.Role.fromNullable(roleWireValue);
        if (role == null) {
            roleError.setValue(FormError.of(R.string.user_error_role_required));
            valid = false;
        }

        if (!valid) {
            return;
        }

        submitting.setValue(true);
        Single<UserResponse> call = isEditing()
                ? userRepository.update(userId,
                new UpdateUserRequest(username.trim(), role.name()))
                : userRepository.create(new CreateUserRequest(username.trim(), password, role.name()));

        disposables.add(call
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> {
                            submitting.setValue(false);
                            saved.setValue(user.getId() == null ? userId : user.getId());
                        },
                        throwable -> {
                            submitting.setValue(false);
                            onSaveFailed(throwable);
                        }));
    }

    private void onSaveFailed(@NonNull Throwable throwable) {
        com.lpms.core.error.ApiError error = NetworkCall.asApiError(throwable);
        // 409 USERNAME_TAKEN and 400 VALIDATION_FAILED both name the field they rejected.
        String usernameMessage = error.messageFor("username");
        if (usernameMessage != null) {
            usernameError.setValue(FormError.of(usernameMessage));
            return;
        }
        String passwordMessage = error.messageFor("password");
        if (passwordMessage != null) {
            passwordError.setValue(FormError.of(passwordMessage));
            return;
        }
        String roleMessage = error.messageFor("role");
        if (roleMessage != null) {
            roleError.setValue(FormError.of(roleMessage));
            return;
        }
        message.setValue(FormError.of(error.getMessage()));
    }

    public void clearErrors() {
        usernameError.setValue(null);
        passwordError.setValue(null);
        roleError.setValue(null);
        message.setValue(null);
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}