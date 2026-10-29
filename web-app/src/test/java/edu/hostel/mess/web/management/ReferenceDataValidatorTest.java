package edu.hostel.mess.web.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.hostel.mess.web.management.ReferenceDataRepository.Directory;
import edu.hostel.mess.web.management.ReferenceDataRepository.FormInput;

class ReferenceDataValidatorTest {
    @Test
    void trimsAndNormalizesCodeAndUnit() throws Exception {
        FormInput vendor = ReferenceDataValidator.validate(Directory.VENDORS,
                "  vendor-a ", "  Campus Foods  ", "  Sam  ", " +1 (555) 123-1234 ",
                "sam@example.invalid", null);
        assertEquals("VENDOR-A", vendor.code());
        assertEquals("Campus Foods", vendor.name());
        assertEquals("Sam", vendor.contactName());

        FormInput ingredient = ReferenceDataValidator.validate(Directory.INGREDIENTS,
                "rice", "Rice", null, null, null, "kg");
        assertEquals("RICE", ingredient.code());
        assertEquals("KG", ingredient.baseUnit());
        assertNull(ingredient.email());
    }

    @Test
    void rejectsMissingAndOverlongRequiredFields() {
        assertThrows(ReferenceDataValidator.ValidationException.class,
                () -> ReferenceDataValidator.validate(Directory.MESS_BLOCKS,
                        " ", "North", null, null, null, null));
        assertThrows(ReferenceDataValidator.ValidationException.class,
                () -> ReferenceDataValidator.validate(Directory.MESS_BLOCKS,
                        "B".repeat(21), "North", null, null, null, null));
    }

    @Test
    void rejectsUnsupportedIngredientUnitAndInvalidVendorContactFields() {
        assertThrows(ReferenceDataValidator.ValidationException.class,
                () -> ReferenceDataValidator.validate(Directory.INGREDIENTS,
                        "RICE", "Rice", null, null, null, "KG/L"));
        assertThrows(ReferenceDataValidator.ValidationException.class,
                () -> ReferenceDataValidator.validate(Directory.VENDORS,
                        "V-1", "Vendor", null, "1".repeat(31), null, null));
        assertThrows(ReferenceDataValidator.ValidationException.class,
                () -> ReferenceDataValidator.validate(Directory.VENDORS,
                        "V-1", "Vendor", null, null, "not-an-email", null));
    }
}
