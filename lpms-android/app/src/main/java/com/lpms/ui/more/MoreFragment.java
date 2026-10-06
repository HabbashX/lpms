package com.lpms.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lpms.MainActivity;
import com.lpms.R;
import com.lpms.core.auth.Role;
import com.lpms.databinding.FragmentMoreBinding;
import com.lpms.databinding.ItemMoreRowBinding;
import com.lpms.ui.shell.ShellViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Hub for everything that does not deserve a bottom-navigation slot.
 *
 * <p>Entries are derived from the <b>server-provided</b> role in {@link ShellViewModel},
 * which re-reads {@code /auth/me} on resume, so a demotion or a disablement removes
 * entries without the user signing out. A row is only listed once its destination exists
 * in the nav graph — no dead ends.</p>
 */
@AndroidEntryPoint
public final class MoreFragment extends Fragment {

    private FragmentMoreBinding binding;
    private ShellViewModel shell;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        shell = ((MainActivity) requireActivity()).shellViewModel();
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            // The role may have changed server-side while this screen was in the back stack.
            render();
        }
    }

    private void render() {
        // Clear first: render() runs on view creation AND on every resume (the role can
        // change server-side), so appending without clearing duplicated every section.
        binding.rows.removeAllViews();

        Role role = shell.currentRole();
        binding.role.setText(getString(R.string.more_role_format, role.name()));
        binding.username.setText(shell.session().getValue() == null
                ? "" : shell.session().getValue().getUsername());

        addRow(R.drawable.ic_nav_pos, R.string.nav_pos,
                () -> NavHostFragment.findNavController(this)
                        .navigate(R.id.posFragment));

        addRow(R.drawable.ic_drugs, R.string.nav_drugs,
                () -> NavHostFragment.findNavController(this)
                        .navigate(R.id.drugListFragment));

        if (role.canManageCatalog()) {
            addRow(R.drawable.ic_audit, R.string.nav_categories,
                    () -> NavHostFragment.findNavController(this)
                            .navigate(R.id.categoriesFragment));
        }

        addRow(R.drawable.ic_nav_profile, R.string.more_change_password,
                () -> NavHostFragment.findNavController(this)
                        .navigate(R.id.changePasswordFragment));

        // Reports / Users / Audit / Settings rows are added here as their destinations
        // land in the nav graph, each behind its role gate:
        //   role.canViewReports()  → Reports
        //   role.canAdminister()   → Users, Audit log, Settings
        // Listing an entry before its destination exists would dead-end the user.

        binding.signOut.setOnClickListener(v -> confirmSignOut());
    }

    private void addRow(int iconRes, int titleRes, @NonNull Runnable action) {
        ItemMoreRowBinding row =
                ItemMoreRowBinding.inflate(LayoutInflater.from(requireContext()),
                        binding.rows, false);
        row.icon.setImageResource(iconRes);
        row.label.setText(titleRes);
        row.getRoot().setOnClickListener(v -> action.run());
        binding.rows.addView(row.getRoot());
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.logout_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> shell.signOut())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}