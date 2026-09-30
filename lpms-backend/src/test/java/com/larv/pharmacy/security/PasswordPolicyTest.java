package com.larv.pharmacy.security;

import com.larv.pharmacy.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> PasswordPolicy.validate("Lpms#Change2026", "admin"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1", "user"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("8 and 72");
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> PasswordPolicy.validate("admin1234", "admin"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("username");
        assertThatThrownBy(() -> PasswordPolicy.validate("xxADMINxx99", "admin"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsPasswordWithoutDigitOrLetter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("onlylettershere", "user"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("123456789012", "user"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsNullPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null, "user"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void skipsUsernameCheckWhenUsernameMissing() {
        assertThatCode(() -> PasswordPolicy.validate("genericPass9", null))
                .doesNotThrowAnyException();
    }
}
