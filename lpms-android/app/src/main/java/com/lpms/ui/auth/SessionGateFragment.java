package com.lpms.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.lpms.R;
import com.lpms.databinding.FragmentSessionGateBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Invisible routing screen shown while the stored session is validated against
 * {@code /auth/me}. It never renders content beyond a centred progress indicator.
 */
@AndroidEntryPoint
public final class SessionGateFragment extends Fragment {

    private FragmentSessionGateBinding binding;
    private SessionGateViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSessionGateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SessionGateViewModel.class);

        binding.gateMessage.setText(R.string.loading);
        binding.retry.setOnClickListener(v -> {
            binding.gateMessage.setVisibility(View.GONE);
            binding.retry.setVisibility(View.GONE);
            viewModel.resolve();
        });

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.resolve();
    }

    private void render(com.lpms.core.ui.UiState<SessionGateViewModel.Destination> state) {
        if (state.isLoading()) {
            binding.gateMessage.setVisibility(View.GONE);
            binding.retry.setVisibility(View.GONE);
            return;
        }
        if (state.isError()) {
            // A network blip must not strand the user: offer retry instead of Login.
            binding.gateMessage.setVisibility(View.VISIBLE);
            binding.gateMessage.setText(com.lpms.core.ui.ErrorPresenter.message(
                    requireContext(), state.errorOrNull()));
            binding.retry.setVisibility(View.VISIBLE);
            return;
        }
        SessionGateViewModel.Destination destination = state.valueOrNull();
        if (destination == null) {
            return;
        }
        int action;
        switch (destination) {
            case CHANGE_PASSWORD:
                action = R.id.action_global_changePassword;
                break;
            case HOME:
                action = R.id.action_global_home;
                break;
            case LOGIN:
            default:
                action = R.id.action_global_login;
                break;
        }
        // popUpTo(start) so Back from the first real screen returns to the launcher
        // instead of re-running the gate.
        NavHostFragment.findNavController(this).navigate(action);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
