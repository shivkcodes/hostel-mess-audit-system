package edu.hostel.mess.web.profile;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/** Server-side profile validation that mirrors the migration's size and numeric constraints. */
public final class UserProfileValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("[0-9+(). xX-]{4,30}");

    private UserProfileValidator() { }

    public static UserProfile validate(FormData form) throws ValidationException {
        String fullName = required(form.fullName(), 120, "Enter your full name.");
        String email = required(form.emailAddress(), 254, "Enter your email address.");
        if (!EMAIL.matcher(email).matches()) throw new ValidationException("Enter a valid email address.");
        String phone = optional(form.phoneNumber(), 30, "Phone number");
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new ValidationException("Enter a valid phone number or leave it blank.");
        }
        String institution = required(form.institutionName(), 160, "Enter your college or institution.");
        String course = required(form.courseName(), 100, "Enter your course or program.");
        Integer semester = integer(form.semesterNumber(), true, 1, 12, "Semester");
        Integer studyYear = integer(form.studyYear(), false, 1, 10, "Year of study");
        Integer graduation = integer(form.graduationYear(), false, 1950, 2200, "Graduation year");

        return new UserProfile(fullName, email, phone,
                optional(form.addressLine(), 240, "Address"),
                optional(form.city(), 100, "City"),
                optional(form.stateName(), 100, "State"),
                optional(form.postalCode(), 20, "Postal code"),
                institution, course,
                optional(form.departmentName(), 100, "Department"), semester, studyYear,
                optional(form.enrollmentId(), 80, "Student ID"), graduation);
    }

    public static List<String> missingRequiredFields(FormData form) {
        List<String> missing = new ArrayList<>();
        if (form.fullName() == null || form.fullName().isBlank()) missing.add("Full name");
        if (form.emailAddress() == null || form.emailAddress().isBlank()) missing.add("Email address");
        if (form.institutionName() == null || form.institutionName().isBlank()) missing.add("College or institution");
        if (form.courseName() == null || form.courseName().isBlank()) missing.add("Course or program");
        if (form.semesterNumber() == null || form.semesterNumber().isBlank()) missing.add("Current semester");
        return List.copyOf(missing);
    }

    private static String required(String raw, int max, String message) throws ValidationException {
        String value = clean(raw, max, message);
        if (value == null) throw new ValidationException(message);
        return value;
    }

    private static String optional(String raw, int max, String label) throws ValidationException {
        String value = clean(raw, max, label);
        if (value != null && value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ValidationException(label + " contains unsupported characters.");
        }
        return value;
    }

    private static String clean(String raw, int max, String label) throws ValidationException {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.codePointCount(0, value.length()) > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ValidationException(label + " is too long or contains unsupported characters.");
        }
        return value;
    }

    private static Integer integer(String raw, boolean required, int min, int max, String label)
            throws ValidationException {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            if (required) throw new ValidationException("Enter your " + label.toLowerCase() + ".");
            return null;
        }
        if (value.length() > 4 || !value.chars().allMatch(Character::isDigit)) {
            throw new ValidationException("Enter a valid " + label.toLowerCase() + ".");
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new ValidationException("Enter a valid " + label.toLowerCase() + ".");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ValidationException("Enter a valid " + label.toLowerCase() + ".");
        }
    }

    public record FormData(String fullName, String emailAddress, String phoneNumber, String addressLine,
            String city, String stateName, String postalCode, String institutionName, String courseName,
            String departmentName, String semesterNumber, String studyYear, String enrollmentId,
            String graduationYear) {
        public String getFullName() { return fullName; }
        public String getEmailAddress() { return emailAddress; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getAddressLine() { return addressLine; }
        public String getCity() { return city; }
        public String getStateName() { return stateName; }
        public String getPostalCode() { return postalCode; }
        public String getInstitutionName() { return institutionName; }
        public String getCourseName() { return courseName; }
        public String getDepartmentName() { return departmentName; }
        public String getSemesterNumber() { return semesterNumber; }
        public String getStudyYear() { return studyYear; }
        public String getEnrollmentId() { return enrollmentId; }
        public String getGraduationYear() { return graduationYear; }

        public static FormData from(UserProfile profile) {
            return new FormData(profile.fullName(), profile.emailAddress(), profile.phoneNumber(),
                    profile.addressLine(), profile.city(), profile.stateName(), profile.postalCode(),
                    profile.institutionName(), profile.courseName(), profile.departmentName(),
                    string(profile.semesterNumber()), string(profile.studyYear()), profile.enrollmentId(),
                    string(profile.graduationYear()));
        }

        private static String string(Integer value) { return value == null ? "" : value.toString(); }
    }

    public static final class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;
        public ValidationException(String message) { super(message); }
    }
}
