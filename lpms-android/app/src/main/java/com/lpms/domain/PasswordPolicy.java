package com.lpms.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-side mirror of the backend password policy so the user gets instant feedback
 * instead of a round trip.
 *
 * <p>Policy: 8–72 characters, at least one letter and one digit, must not contain the
 * username (case-insensitive), and must differ from the current password.</p>
 *
 * <p>The server remains authoritative — this only decides which hints to highlight.</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    /** One rule's verdict, used to drive the live hint list on the change-password form. */
    public static final class Result {

        private final List<Rule> rules = new ArrayList<>();

        private Result(@NonNull String password, @Nullable String username, @Nullable String current) {
            String lower = password.toLowerCase(Locale.ROOT);

            rules.add(new Rule(MIN_LENGTH <= password.length() && password.length() <= MAX_LENGTH,
                    "error_password_length", MIN_LENGTH, MAX_LENGTH));
            rules.add(new Rule(hasLetter(password),
                    "error_password_letter", 0, 0));
            rules.add(new Rule(hasDigit(password),
                    "error_password_digit", 0, 0));
            rules.add(new Rule(username == null || username.trim().isEmpty()
                            || !lower.contains(username.trim().toLowerCase(Locale.ROOT)),
                    "error_password_contains_username", 0, 0));
            rules.add(new Rule(current == null || current.isEmpty() || !password.equals(current),
                    "error_password_same", 0, 0));
        }

        public boolean isValid() {
            for (Rule rule : rules) {
                if (!rule.satisfied) {
                    return false;
                }
            }
            return true;
        }

        @NonNull
        public List<Rule> rules() {
            return rules;
        }

        /** True when this specific hint should render as an error rather than a hint. */
        public boolean isSatisfied(int index) {
            return index >= 0 && index < rules.size() && rules.get(index).satisfied;
        }
    }

    /** A single policy check plus the string resource describing it. */
    public static final class Rule {

        private final boolean satisfied;
        private final String stringResourceName;
        private final int min;
        private final int max;

        Rule(boolean satisfied, @NonNull String stringResourceName, int min, int max) {
            this.satisfied = satisfied;
            this.stringResourceName = stringResourceName;
            this.min = min;
            this.max = max;
        }

        public boolean isSatisfied() {
            return satisfied;
        }

        @NonNull
        public String getStringResourceName() {
            return stringResourceName;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }
    }

    @NonNull
    public static Result evaluate(@NonNull String password,
                                  @Nullable String username,
                                  @Nullable String currentPassword) {
        return new Result(password, username, currentPassword);
    }

    public static boolean isAcceptable(@NonNull String password,
                                       @Nullable String username,
                                       @Nullable String currentPassword) {
        return evaluate(password, username, currentPassword).isValid();
    }

    private static boolean hasLetter(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}