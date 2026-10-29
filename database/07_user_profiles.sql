-- MessAudit user profile extension. Run once after 06_authentication_schema_proposal.sql.
-- No authentication columns or existing user roles are modified.
-- Profile completion is derived by the application from the required profile fields.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

CREATE TABLE user_profiles (
    user_id           NUMBER NOT NULL,
    full_name         VARCHAR2(120 CHAR),
    email_address     VARCHAR2(254 CHAR),
    phone_number      VARCHAR2(30 CHAR),
    address_line      VARCHAR2(240 CHAR),
    city              VARCHAR2(100 CHAR),
    state_name        VARCHAR2(100 CHAR),
    postal_code       VARCHAR2(20 CHAR),
    institution_name  VARCHAR2(160 CHAR),
    course_name       VARCHAR2(100 CHAR),
    department_name   VARCHAR2(100 CHAR),
    semester_number   NUMBER(2),
    study_year        NUMBER(2),
    enrollment_id     VARCHAR2(80 CHAR),
    graduation_year   NUMBER(4),
    created_at        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id)
        REFERENCES app_users (user_id),
    CONSTRAINT ck_user_profiles_semester CHECK (semester_number IS NULL OR semester_number BETWEEN 1 AND 12),
    CONSTRAINT ck_user_profiles_study_year CHECK (study_year IS NULL OR study_year BETWEEN 1 AND 10),
    CONSTRAINT ck_user_profiles_graduation CHECK (graduation_year IS NULL OR graduation_year BETWEEN 1950 AND 2200)
);

