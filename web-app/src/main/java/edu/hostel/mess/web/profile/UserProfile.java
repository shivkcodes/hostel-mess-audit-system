package edu.hostel.mess.web.profile;

/** Private, self-service profile fields. Authentication secrets are intentionally excluded. */
public record UserProfile(
        String fullName,
        String emailAddress,
        String phoneNumber,
        String addressLine,
        String city,
        String stateName,
        String postalCode,
        String institutionName,
        String courseName,
        String departmentName,
        Integer semesterNumber,
        Integer studyYear,
        String enrollmentId,
        Integer graduationYear) {

    public boolean isComplete() {
        return present(fullName) && present(emailAddress) && present(institutionName)
                && present(courseName) && semesterNumber != null;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

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
    public Integer getSemesterNumber() { return semesterNumber; }
    public Integer getStudyYear() { return studyYear; }
    public String getEnrollmentId() { return enrollmentId; }
    public Integer getGraduationYear() { return graduationYear; }
}
