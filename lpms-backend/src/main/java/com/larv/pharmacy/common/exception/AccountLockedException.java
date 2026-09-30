package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends PharmacyException {

    public AccountLockedException(long retrySeconds) {
        super("ACCOUNT_LOCKED", HttpStatus.LOCKED,
                "Account temporarily locked due to repeated failed logins. Try again in " + retrySeconds + " seconds");
    }
}
