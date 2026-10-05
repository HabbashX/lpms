package com.lpms.core.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.R;
import com.lpms.core.error.ApiError;
import com.lpms.databinding.ViewStateBinding;

/**
 * Drives the shared {@code view_state.xml} block from a {@link UiState} and shows/hides
 * the screen's own content view.
 *
 * <p>Typical use in a Fragment:</p>
 * <pre>{@code
 * UiState<SaleResponse> state = viewModel.state.getValue();
 * stateRenderer.render(state, binding.content, binding.state);
 * binding.state.stateRetry.setOnClickListener(v -> viewModel.retry());
 * }</pre>
 */
public final class StateRenderer {

    private final ViewStateBinding stateView;

    public StateRenderer(@NonNull ViewStateBinding stateView) {
        this.stateView = stateView;
        stateView.stateRetry.setOnClickListener(v -> {
            if (retryListener != null) {
                retryListener.onRetry();
            }
        });
    }

    private RetryListener retryListener;

    /** Invoked when the user taps the retry button inside the state view. */
    public interface RetryListener {
        void onRetry();
    }

    public void setRetryListener(@Nullable RetryListener listener) {
        this.retryListener = listener;
    }

    /**
     * @param state    current state; null is treated as loading
     * @param content  the screen's real content view, hidden while loading/empty/error
     */
    public <T> void render(@Nullable UiState<T> state, @NonNull View content) {
        render(state, content, null);
    }

    /**
     * @param emptyMessage message shown for the EMPTY state; falls back to a generic string
     */
    public <T> void render(@Nullable UiState<T> state,
                           @NonNull View content,
                           @Nullable CharSequence emptyMessage) {
        boolean showContent = state != null && state.isContent();
        content.setVisibility(showContent ? View.VISIBLE : View.GONE);

        if (state == null || state.isLoading()) {
            showProgress();
            return;
        }
        if (state.isContent()) {
            stateView.getRoot().setVisibility(View.GONE);
            return;
        }
        if (state.isEmpty()) {
            stateView.getRoot().setVisibility(View.VISIBLE);
            stateView.stateProgress.setVisibility(View.GONE);
            stateView.stateMessage.setVisibility(View.VISIBLE);
            stateView.stateMessage.setText(emptyMessage == null
                    ? stateView.getRoot().getContext().getString(R.string.empty_generic)
                    : emptyMessage);
            stateView.stateRetry.setVisibility(View.GONE);
            return;
        }
        showError(java.util.Objects.requireNonNull(state.errorOrNull()));
    }

    /** Explicit loading (pull-to-refresh keeps content visible, so it is not used there). */
    public void showProgress() {
        stateView.getRoot().setVisibility(View.VISIBLE);
        stateView.stateProgress.setVisibility(View.VISIBLE);
        stateView.stateMessage.setVisibility(View.GONE);
        stateView.stateRetry.setVisibility(View.GONE);
    }

    public void showError(@NonNull ApiError error) {
        android.content.Context context = stateView.getRoot().getContext();
        ErrorPresenter.Action action = ErrorPresenter.actionFor(error);
        stateView.getRoot().setVisibility(View.VISIBLE);
        stateView.stateProgress.setVisibility(View.GONE);
        stateView.stateMessage.setVisibility(View.VISIBLE);
        stateView.stateMessage.setText(ErrorPresenter.message(context, error));
        // A forbidden/session error is not retryable in place: the session layer takes
        // over navigation, so the button would be misleading.
        stateView.stateRetry.setVisibility(
                action == ErrorPresenter.Action.RETRY || action == ErrorPresenter.Action.SNACKBAR
                        ? View.VISIBLE : View.GONE);
    }

    public void hide() {
        stateView.getRoot().setVisibility(View.GONE);
    }
}