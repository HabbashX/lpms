package com.lpms.ui.categories;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.lpms.R;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.databinding.FragmentCategoriesBinding;
import com.lpms.databinding.ItemCategoryBinding;
import com.lpms.ui.drugs.DrugListFragment;

import java.util.Collections;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Category list with add / rename / delete.
 *
 * <p>{@code CATEGORY_ALREADY_EXISTS} and {@code CATEGORY_IN_USE} are surfaced as the
 * server's own message, so the user learns exactly what to change.</p>
 */
@AndroidEntryPoint
public final class CategoriesFragment extends Fragment {

    private FragmentCategoriesBinding binding;
    private CategoriesViewModel viewModel;
    private CategoryAdapter adapter;
    private StateRenderer stateRenderer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CategoriesViewModel.class);

        binding.toolbar.setTitle(R.string.nav_categories);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        adapter = new CategoryAdapter(this::showRowMenu);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);

        binding.addButton.setOnClickListener(v -> showNameDialog(null));
        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.busy().observe(getViewLifecycleOwner(), busy ->
                binding.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.GONE));
        viewModel.message().observe(getViewLifecycleOwner(), message -> {
            if (message == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), message.resolve(requireContext()),
                    Snackbar.LENGTH_LONG).show();
        });
        viewModel.blockedBy().observe(getViewLifecycleOwner(), category -> {
            if (category != null) {
                showBlockedBy(category);
            }
        });

        viewModel.load();
    }

    private void render(@Nullable UiState<List<CategoryResponse>> state) {
        boolean empty = state != null && state.isEmpty();
        stateRenderer.render(state, binding.list,
                getString(R.string.categories_empty));
        adapter.submitList(empty || state == null || !state.isContent()
                ? Collections.emptyList()
                : Collections.unmodifiableList(state.valueOrNull()));
    }

    private void showRowMenu(@NonNull CategoryResponse category, @NonNull View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 1, R.string.action_edit);
        menu.getMenu().add(0, 2, 2, R.string.action_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showNameDialog(category);
            } else {
                confirmDelete(category);
            }
            return true;
        });
        menu.show();
    }

    /** One dialog serves both create and rename. */
    private void showNameDialog(@Nullable CategoryResponse existing) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_category_name, null, false);
        TextInputEditText input = content.findViewById(R.id.name);
        if (existing != null && existing.getName() != null) {
            input.setText(existing.getName());
            input.setSelection(input.getText() == null ? 0 : input.getText().length());
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                ((com.google.android.material.textfield.TextInputLayout) content)
                        .setError(null);
            }
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? R.string.category_add : R.string.category_rename)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(v -> dialog.getButton(
                        androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value = input.getText() == null ? "" : input.getText().toString();
                    if (value.trim().isEmpty()) {
                        ((com.google.android.material.textfield.TextInputLayout) content)
                                .setError(getString(R.string.category_error_name_required));
                        return;
                    }
                    if (existing == null) {
                        viewModel.create(value);
                    } else {
                        viewModel.rename(existing, value);
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmDelete(@NonNull CategoryResponse category) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category_delete)
                .setMessage(getString(R.string.category_delete_confirm, category.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.delete(category))
                .show();
    }

    /**
     * The backend refuses to delete a category that drugs still reference, which is right -
     * it would orphan those drugs. The refusal used to be a dead end, so this offers the
     * only useful next step: see the drugs in that category and move them.
     */
    private void showBlockedBy(@NonNull CategoryResponse category) {
        if (category.getId() == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category_in_use_title)
                .setMessage(getString(R.string.category_in_use_body, category.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.category_view_drugs, (dialog, which) -> {
                    Bundle args = new Bundle();
                    args.putLong(DrugListFragment.ARG_CATEGORY_ID, category.getId());
                    args.putString(DrugListFragment.ARG_CATEGORY_NAME,
                            category.getName() == null ? "" : category.getName());
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.drugListFragment, args);
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Plain list adapter; the category list is small and fully cached. */
    private static final class CategoryAdapter
            extends ListAdapter<CategoryResponse, CategoryAdapter.ViewHolder> {

        interface Listener {
            void onRowMenu(@NonNull CategoryResponse category, @NonNull View anchor);
        }

        private final Listener listener;

        CategoryAdapter(@NonNull Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemCategoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {

            private final ItemCategoryBinding binding;

            ViewHolder(@NonNull ItemCategoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(@NonNull CategoryResponse category, @NonNull Listener listener) {
                binding.name.setText(category.getName() == null ? "" : category.getName());
                binding.menu.setOnClickListener(v -> listener.onRowMenu(category, v));
            }
        }

        private static final DiffUtil.ItemCallback<CategoryResponse> DIFF =
                new DiffUtil.ItemCallback<>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull CategoryResponse oldItem,
                                                   @NonNull CategoryResponse newItem) {
                        return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull CategoryResponse oldItem,
                                                      @NonNull CategoryResponse newItem) {
                        return oldItem.getName() == null
                                ? newItem.getName() == null
                                : oldItem.getName().equals(newItem.getName());
                    }
                };
    }
}