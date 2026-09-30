package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends PharmacyException {

    public AccountDisabledException() {
        super("ACCOUNT_DISABLED", HttpStatus.UNAUTHORIZED, "Account is disabled");
    }
}
