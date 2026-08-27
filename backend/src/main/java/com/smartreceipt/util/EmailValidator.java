package com.smartreceipt.util;

import java.util.regex.Pattern;

public class EmailValidator {

    // General strict email regex pattern
    private static final Pattern STRICT_EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,64}@)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$"
    );

    public static boolean isValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String normalized = email.trim();

        // 1. Basic structural pattern check
        if (!STRICT_EMAIL_PATTERN.matcher(normalized).matches()) {
            return false;
        }

        String[] parts = normalized.split("@");
        if (parts.length != 2) {
            return false;
        }

        String localPart = parts[0];
        String domainPart = parts[1].toLowerCase();

        // 2. Local part rules: no leading/trailing dot, no consecutive dots
        if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            return false;
        }

        // 3. Domain part rules: no leading/trailing dot or hyphen
        if (domainPart.startsWith(".") || domainPart.endsWith(".") || domainPart.startsWith("-") || domainPart.endsWith("-")) {
            return false;
        }

        // 4. TLD rule: must have a dot and valid alphabetic TLD of length >= 2
        int lastDotIndex = domainPart.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == domainPart.length() - 1) {
            return false;
        }
        String tld = domainPart.substring(lastDotIndex + 1);
        if (!tld.matches("^[a-zA-Z]{2,63}$")) {
            return false;
        }

        // 5. Gmail-specific rule: If domain begins with 'gmail', it MUST strictly be 'gmail.com'
        if (domainPart.startsWith("gmail") && !domainPart.equals("gmail.com")) {
            return false;
        }

        return true;
    }
}
