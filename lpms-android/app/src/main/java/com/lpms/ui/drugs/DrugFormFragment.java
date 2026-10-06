package com.lpms.ui.drugs;

import android.os.Bundle;
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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.lpms.R;
import com.lpms.core.ui.StateRenderer;
import com.lpms.core.ui.UiState;
import com.lpms.core.util.Money;
import com.lpms.data.dto.CategoryResponse;
import com.lpms.data.dto.DosageForm;
import com.lpms.databinding.FragmentDrugFormBinding;
import com.lpms.ui.common.FormError;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Create / edit a drug.
 *
 * <p>Category and dosage form are dropdowns; the category list comes from the cached
 * {@code GET /categories} and only the {@code categoryId} is ever sent. Selling price is a
 * {@code BigDecimal} typed with a locale-tolerant decimal separator.</p>
 */
@AndroidEntryPoint
public final class DrugFormFragment extends Fragment {

    private FragmentDrugFormBinding binding;
    private DrugFormViewModel viewModel;
    private StateRenderer stateRenderer;

    private final List<CategoryResponse> categoryOptions = new ArrayList<>();
    private DosageForm[] dosageForms = DosageForm.values();

    /** Guards the dropdown text watcher while the loaded drug is written into the fields. */
    private boolean populating;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDrugFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DrugFormViewModel.class);

        binding.toolbar.setTitle(viewModel.isEditing()
                ? R.string.drug_edit_title : R.string.drug_add_title);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        stateRenderer = new StateRenderer(binding.state);
        stateRenderer.setRetryListener(viewModel::load);

        setUpDosageFormDropdown();
        setUpValidationWatchers();

        binding.saveButton.setOnClickListener(v -> save());
        binding.sellingPriceLayout.setHelperText(getString(R.string.drug_selling_price_hint));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.categories().observe(getViewLifecycleOwner(), this::bindCategoryOptions);
        viewModel.submitting().observe(getViewLifecycleOwner(), submitting ->
                binding.saveButton.setEnabled(!Boolean.TRUE.equals(submitting)));
        viewModel.nameError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.nameLayout, issue));
        viewModel.barcodeError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.barcodeLayout, issue));
        viewModel.priceError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.sellingPriceLayout, issue));
        viewModel.minimumStockError().observe(getViewLifecycleOwner(), issue ->
                applyError(binding.minimumStockLayout, issue));
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

    private void setUpDosageFormDropdown() {
        String[] labels = new String[dosageForms.length];
        for (int i = 0; i < dosageForms.length; i++) {
            labels[i] = dosageForms[i].name();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels);
        binding.dosageFormInput.setAdapter(adapter);
    }

    private void setUpValidationWatchers() {
        clearErrorOnType(binding.name, binding.nameLayout);
        clearErrorOnType(binding.barcode, binding.barcodeLayout);
        clearErrorOnType(binding.sellingPrice, binding.sellingPriceLayout);
        clearErrorOnType(binding.minimumStockLevel, binding.minimumStockLayout);
        binding.name.setOnFocusChangeListener((v, focused) -> {
            if (!focused) {
                viewModel.clearErrors();
            }
        });
    }

    private void clearErrorOnType(@NonNull TextInputEditText field,
                                  @NonNull com.google.android.material.textfield.TextInputLayout layout) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (layout.getError() != null) {
                    layout.setError(null);
                    layout.setErrorEnabled(false);
                }
            }
        });
    }

    private void render(@Nullable UiState<DrugFormViewModel.FormState> state) {
        stateRenderer.render(state, binding.content);
        if (state == null || !state.isContent()) {
            return;
        }
        DrugFormViewModel.FormState form = state.valueOrNull();
        if (form == null || populating) {
            return;
        }
        populating = true;
        try {
            binding.name.setText(nullToEmpty(form.name));
            binding.genericName.setText(nullToEmpty(form.genericName));
            binding.barcode.setText(nullToEmpty(form.barcode));
            binding.manufacturer.setText(nullToEmpty(form.manufacturer));
            binding.strength.setText(nullToEmpty(form.strength));
            binding.unit.setText(nullToEmpty(form.unit));
            binding.description.setText(nullToEmpty(form.description));
            binding.sellingPrice.setText(form.sellingPrice == null
                    ? "" : Money.toPlainString(form.sellingPrice));
            binding.minimumStockLevel.setText(form.minimumStockLevel == null
                    ? "" : String.valueOf(form.minimumStockLevel));
            binding.dosageFormInput.setText(form.dosageForm == null
                    ? "" : form.dosageForm.name(), false);
            binding.activeSwitch.setChecked(form.active);

            if (form.currentQuantity > 0) {
                // Stock only moves through purchases, never by editing the drug.
                binding.stockHint.setText(getString(R.string.drug_stock_readonly,
                        form.currentQuantity));
                binding.stockHint.setVisibility(View.VISIBLE);
            } else {
                binding.stockHint.setVisibility(View.GONE);
            }
            selectCategory(form.categoryId);
        } finally {
            populating = false;
        }
    }

    private void bindCategoryOptions(@Nullable List<CategoryResponse> categories) {
        categoryOptions.clear();
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.drug_no_category));
        if (categories != null) {
            for (CategoryResponse category : categories) {
                categoryOptions.add(category);
                labels.add(category.getName() == null ? "" : category.getName());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels);
        binding.categoryInput.setAdapter(adapter);
        binding.categoryInput.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                return;
            }
            selectCategory(categoryOptions.get(position - 1).getId());
        });
    }

    /** Keeps the dropdown label in sync with the stored id. */
    private void selectCategory(@Nullable Long categoryId) {
        if (categoryId == null) {
            binding.categoryInput.setText(getString(R.string.drug_no_category), false);
            return;
        }
        for (int i = 0; i < categoryOptions.size(); i++) {
            if (categoryId.equals(categoryOptions.get(i).getId())) {
                String name = categoryOptions.get(i).getName();
                binding.categoryInput.setText(name == null ? "" : name, false);
                return;
            }
        }
    }

    @Nullable
    private Long selectedCategoryId() {
        String typed = text(binding.categoryInput);
        for (int i = 0; i < categoryOptions.size(); i++) {
            CategoryResponse category = categoryOptions.get(i);
            if (category.getName() != null && category.getName().equals(typed.trim())) {
                return category.getId();
            }
        }
        return null;
    }

    @Nullable
    private DosageForm selectedDosageForm() {
        String typed = text(binding.dosageFormInput).trim();
        if (typed.isEmpty()) {
            return null;
        }
        return DosageForm.fromNullable(typed);
    }

    private void save() {
        viewModel.save(
                text(binding.name),
                text(binding.genericName),
                text(binding.barcode),
                text(binding.manufacturer),
                selectedCategoryId(),
                selectedDosageForm(),
                text(binding.strength),
                text(binding.unit),
                text(binding.sellingPrice),
                text(binding.description),
                text(binding.minimumStockLevel),
                binding.activeSwitch.isChecked());
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

    @NonNull
    private String text(@NonNull MaterialAutoCompleteTextView field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @NonNull
    private String text(@NonNull TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    @NonNull
    private String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}