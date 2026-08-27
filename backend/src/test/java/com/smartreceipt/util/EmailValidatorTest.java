package com.smartreceipt.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailValidatorTest {

    @Test
    @DisplayName("Should accept valid email formats")
    void testValidEmails() {
        assertTrue(EmailValidator.isValid("example@gmail.com"));
        assertTrue(EmailValidator.isValid("user.name@domain.com"));
        assertTrue(EmailValidator.isValid("user+tag@sub.domain.org"));
        assertTrue(EmailValidator.isValid("contact@company.co.in"));
    }

    @Test
    @DisplayName("Should reject invalid email formats requested by specification")
    void testInvalidEmailFormats() {
        assertFalse(EmailValidator.isValid("abc"));
        assertFalse(EmailValidator.isValid("abc@"));
        assertFalse(EmailValidator.isValid("@gmail.com"));
        assertFalse(EmailValidator.isValid("abc@gmail"));
        assertFalse(EmailValidator.isValid("abc@gmail."));
        assertFalse(EmailValidator.isValid("abc@gmail.c"));
        assertFalse(EmailValidator.isValid("abc@gmail..com"));
        assertFalse(EmailValidator.isValid("abc..def@gmail.com"));
        assertFalse(EmailValidator.isValid(".abc@gmail.com"));
        assertFalse(EmailValidator.isValid("abc.@gmail.com"));
        assertFalse(EmailValidator.isValid(null));
        assertFalse(EmailValidator.isValid(""));
    }
}
