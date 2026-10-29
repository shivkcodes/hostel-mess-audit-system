package edu.hostel.mess.web.management;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AdminDataValidationTest {
    @Test
    void missingMeasurementsStayNullAndZeroMealsAreAllowed() {
        AdminDataRepository.DailyDraft draft = new AdminDataRepository.DailyDraft(
                1, LocalDate.of(2026, 10, 28), 0, null, null, null, null);
        assertDoesNotThrow(() -> AdminDataRepository.validateDailyDraft(draft));
    }

    @Test
    void measuredLeftoversRequireMethodAndCannotExceedPreparation() {
        AdminDataRepository.DailyDraft missingMethod = new AdminDataRepository.DailyDraft(
                1, LocalDate.of(2026, 10, 28), 15, new BigDecimal("4.500"), BigDecimal.ZERO, null, null);
        assertThrows(AdminDataRepository.AdminException.class,
                () -> AdminDataRepository.validateDailyDraft(missingMethod));

        AdminDataRepository.DailyDraft overPrepared = new AdminDataRepository.DailyDraft(
                1, LocalDate.of(2026, 10, 28), 15, new BigDecimal("4.500"), new BigDecimal("4.501"), "Measured", null);
        assertThrows(AdminDataRepository.AdminException.class,
                () -> AdminDataRepository.validateDailyDraft(overPrepared));
    }

    @Test
    void decimalQuantitiesRespectOraclePrecision() {
        AdminDataRepository.DailyDraft tooPrecise = new AdminDataRepository.DailyDraft(
                1, LocalDate.of(2026, 10, 28), 15, new BigDecimal("1000000000"), null, null, null);
        assertThrows(AdminDataRepository.AdminException.class,
                () -> AdminDataRepository.validateDailyDraft(tooPrecise));
    }
}
