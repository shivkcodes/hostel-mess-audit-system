package edu.hostel.mess.web.management;

import java.util.Locale;
import java.util.Set;

import edu.hostel.mess.web.management.ReferenceDataRepository.Directory;
import edu.hostel.mess.web.management.ReferenceDataRepository.FormInput;

/** Server-side validation for the reference-data forms. */
public final class ReferenceDataValidator {
    private static final Set<String> ALLOWED_UNITS = Set.of("KG", "L", "EACH");
    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private ReferenceDataValidator() { }

    public static FormInput validate(Directory directory, String codeValue, String nameValue,
            String contactValue, String phoneValue, String emailValue, String unitValue)
            throws ValidationException {
        if (directory == null) {
            throw new ValidationException("Choose a supported reference-data type.");
        }
        String code = normalized(codeValue, "Code", true).toUpperCase(Locale.ROOT);
        String name = normalized(nameValue, "Name", true);
        requireLength(code, "Code", 20);
        requireLength(name, "Name", directory == Directory.VENDORS ? 120 : 100);

        String contact = null;
        String phone = null;
        String email = null;
        String unit = null;
        if (directory == Directory.VENDORS) {
            contact = normalized(contactValue, "Contact name", false);
            phone = normalized(phoneValue, "Phone", false);
            email = normalized(emailValue, "Email", false);
            requireLength(contact, "Contact name", 100);
            requireLength(phone, "Phone", 30);
            requireLength(email, "Email", 254);
            if (email != null && !email.matches(EMAIL_PATTERN)) {
                throw new ValidationException("Enter a valid email address or leave the email field blank.");
            }
        } else if (directory == Directory.INGREDIENTS) {
            unit = normalized(unitValue, "Base unit", true);
            unit = unit.toUpperCase(Locale.ROOT);
            requireLength(unit, "Base unit", 10);
            if (!ALLOWED_UNITS.contains(unit)) {
                throw new ValidationException("Choose one of the supported base units: KG, L, or EACH.");
            }
        }
        return new FormInput(code, name, contact, phone, email, unit);
    }

    private static String normalized(String value, String label, boolean required) throws ValidationException {
        if (value == null) {
            if (required) {
                throw new ValidationException(label + " is required.");
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            if (required) {
                throw new ValidationException(label + " is required.");
            }
            return null;
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new ValidationException(label + " contains unsupported control characters.");
        }
        return normalized;
    }

    private static void requireLength(String value, String label, int maximum) throws ValidationException {
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            throw new ValidationException(label + " must be " + maximum + " characters or fewer.");
        }
    }

    public static final class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;

        public ValidationException(String message) {
            super(message);
        }
    }
}
