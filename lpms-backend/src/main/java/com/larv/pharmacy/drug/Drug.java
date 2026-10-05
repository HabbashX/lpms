package com.larv.pharmacy.drug;

import com.larv.pharmacy.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Medicine catalog entry. Deliberately carries <b>no purchase price</b>:
 * prices live on inventory batches so every historical purchase is preserved.
 * {@code currentQuantity} is a cached mirror of the sum of batch quantities,
 * maintained transactionally by the inventory service.
 */
@Entity
@Table(name = "drugs", uniqueConstraints = @UniqueConstraint(name = "uk_drugs_barcode", columnNames = "barcode"))
public class Drug extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "generic_name", length = 150)
    private String genericName;

    @Column(length = 50)
    private String barcode;

    @Column(length = 150)
    private String manufacturer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "dosage_form", length = 30)
    private DosageForm dosageForm;

    @Column(length = 50)
    private String strength;

    /** Dispensing unit, e.g. BOX, BOTTLE, STRIP. */
    @Column(length = 30)
    private String unit;

    @Column(length = 500)
    private String description;

    /** Default selling price used by the POS when a sale line carries no explicit price. */
    @Column(name = "selling_price", precision = 19, scale = 4)
    private java.math.BigDecimal sellingPrice;

    /** Threshold below/at which the drug is reported as low stock. */
    @Column(name = "minimum_stock_level", nullable = false)
    private int minimumStockLevel;

    /** Cached sum of {@code stock_batches.remaining_quantity}. */
    @Column(name = "current_quantity", nullable = false)
    private int currentQuantity;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGenericName() {
        return genericName;
    }

    public void setGenericName(String genericName) {
        this.genericName = genericName;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public DosageForm getDosageForm() {
        return dosageForm;
    }

    public void setDosageForm(DosageForm dosageForm) {
        this.dosageForm = dosageForm;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public java.math.BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(java.math.BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMinimumStockLevel() {
        return minimumStockLevel;
    }

    public void setMinimumStockLevel(int minimumStockLevel) {
        this.minimumStockLevel = minimumStockLevel;
    }

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(int currentQuantity) {
        this.currentQuantity = currentQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
