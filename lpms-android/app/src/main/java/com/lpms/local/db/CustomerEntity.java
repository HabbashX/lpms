package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/** A customer as stored for offline mode; mirrors {@link com.lpms.data.dto.CustomerResponse}. */
@Entity(tableName = "customers")
public final class CustomerEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    private final long id;

    @NonNull
    @ColumnInfo(name = "name")
    private final String name;

    @Nullable
    @ColumnInfo(name = "phone")
    private final String phone;

    @Nullable
    @ColumnInfo(name = "email")
    private final String email;

    @Nullable
    @ColumnInfo(name = "address")
    private final String address;

    /** Positive means the customer owes the pharmacy. */
    @NonNull
    @ColumnInfo(name = "balance")
    private final BigDecimal balance;

    @ColumnInfo(name = "active")
    private final boolean active;

    public CustomerEntity(long id,
                          @NonNull String name,
                          @Nullable String phone,
                          @Nullable String email,
                          @Nullable String address,
                          @NonNull BigDecimal balance,
                          boolean active) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.balance = balance;
        this.active = active;
    }

    public long getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getPhone() {
        return phone;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @NonNull
    public BigDecimal getBalance() {
        return balance;
    }

    public boolean isActive() {
        return active;
    }
}
