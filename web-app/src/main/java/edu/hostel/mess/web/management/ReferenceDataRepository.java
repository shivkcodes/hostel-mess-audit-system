package edu.hostel.mess.web.management;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reference-data reads and transaction-safe administrative mutations. */
public final class ReferenceDataRepository {
    public PageData loadPage(Connection connection, Directory directory, String search, Long selectedId)
            throws SQLException {
        List<DirectoryRecord> records = loadRecords(connection, directory, search);
        DirectoryRecord selected = selectedId == null ? null : loadRecord(connection, directory, selectedId);
        List<Dependency> dependencies = selected == null ? List.of()
                : loadDependencies(connection, directory, selectedId);
        return new PageData(records, selected, dependencies);
    }

    public void create(Connection connection, Directory directory, FormInput input, String actor)
            throws SQLException, ManagementException {
        inTransaction(connection, () -> {
            if (codeExists(connection, directory, input.code(), null)) {
                throw new ManagementException("That code is already in use. Choose a different code.");
            }
            try (PreparedStatement statement = connection.prepareStatement(directory.insertSql)) {
                bindMutableFields(statement, directory, input);
                statement.executeUpdate();
            }
            long id = findIdByCode(connection, directory, input.code());
            audit(connection, directory, Long.toString(id), "I", actor, null,
                    "code=" + input.code() + "; name=" + input.name() + unitAudit(directory, input.baseUnit()));
            return null;
        });
    }

    public void update(Connection connection, Directory directory, long id, FormInput input, String actor)
            throws SQLException, ManagementException {
        inTransaction(connection, () -> {
            Map<String, String> current = lockRecord(connection, directory, id);
            if (current == null) {
                throw new ManagementException("That record no longer exists. Refresh the list and try again.", 404);
            }
            if (codeExists(connection, directory, input.code(), id)) {
                throw new ManagementException("That code is already in use. Choose a different code.");
            }
            if (directory == Directory.INGREDIENTS
                    && !input.baseUnit().equals(current.get("base_unit"))
                    && hasDependencies(loadDependencies(connection, directory, id))) {
                throw new ManagementException(
                        "The base unit cannot be changed because this ingredient is referenced by order or inventory history.");
            }
            try (PreparedStatement statement = connection.prepareStatement(directory.updateSql)) {
                bindMutableFields(statement, directory, input);
                statement.setLong(directory.updateParameterCount, id);
                if (statement.executeUpdate() != 1) {
                    throw new ManagementException("That record no longer exists. Refresh the list and try again.", 404);
                }
            }
            audit(connection, directory, Long.toString(id), "U", actor,
                    "code=" + current.get("entity_code") + "; name=" + current.get("entity_name")
                            + unitAudit(directory, current.get("base_unit")),
                    "code=" + input.code() + "; name=" + input.name() + unitAudit(directory, input.baseUnit()));
            return null;
        });
    }

    /** Returns false when the record already had the requested active state. */
    public boolean setActive(Connection connection, Directory directory, long id, boolean active, String actor)
            throws SQLException, ManagementException {
        return inTransaction(connection, () -> {
            Map<String, String> current = lockRecord(connection, directory, id);
            if (current == null) {
                throw new ManagementException("That record no longer exists. Refresh the list and try again.");
            }
            String desired = active ? "Y" : "N";
            if (desired.equals(current.get("active_flag"))) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(directory.activeSql)) {
                statement.setString(1, desired);
                statement.setLong(2, id);
                if (statement.executeUpdate() != 1) {
                    throw new ManagementException("That record could not be updated. Refresh the list and try again.");
                }
            }
            audit(connection, directory, Long.toString(id), "U", actor,
                    "active_flag=" + current.get("active_flag"), "active_flag=" + desired);
            return true;
        });
    }

    public void delete(Connection connection, Directory directory, long id)
            throws SQLException, ManagementException {
        delete(connection, directory, id, "administrator");
    }

    public void delete(Connection connection, Directory directory, long id, String actor)
            throws SQLException, ManagementException {
        try {
            inTransaction(connection, () -> {
                Map<String, String> current = lockRecord(connection, directory, id);
                if (current == null) {
                    throw new ManagementException("That record no longer exists. Refresh the list and try again.", 404);
                }
                List<Dependency> dependencies = loadDependencies(connection, directory, id);
                if (hasDependencies(dependencies)) {
                    throw new ManagementException(directory.blockedDeleteMessage);
                }
                try (PreparedStatement statement = connection.prepareStatement(directory.deleteSql)) {
                    statement.setLong(1, id);
                    if (statement.executeUpdate() != 1) {
                        throw new ManagementException("That record no longer exists. Refresh the list and try again.", 404);
                    }
                }
                audit(connection, directory, Long.toString(id), "D", actor,
                        "code=" + current.get("entity_code") + "; name=" + current.get("entity_name"),
                        "Deleted after the dependency check confirmed no references.");
                return null;
            });
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 2292) {
                throw new ManagementException(directory.blockedDeleteMessage);
            }
            throw exception;
        }
    }

    private long findIdByCode(Connection connection, Directory directory, String code) throws SQLException {
        String sql = "SELECT " + directory.idColumn + " FROM " + directory.tableName + " WHERE " + directory.codeColumn + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Created reference record could not be identified.");
    }

    private String unitAudit(Directory directory, String unit) {
        return directory == Directory.INGREDIENTS ? "; base_unit=" + unit : "";
    }

    private void audit(Connection connection, Directory directory, String recordKey, String operation,
            String actor, String oldValues, String newValues) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_log (table_name, record_key, operation_type, changed_by, old_values, new_values)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, directory.tableName.toUpperCase(java.util.Locale.ROOT));
            statement.setString(2, recordKey);
            statement.setString(3, operation);
            statement.setString(4, actor);
            if (oldValues == null) statement.setNull(5, java.sql.Types.CLOB); else statement.setString(5, oldValues);
            if (newValues == null) statement.setNull(6, java.sql.Types.CLOB); else statement.setString(6, newValues);
            statement.executeUpdate();
        }
    }

    private <T> T inTransaction(Connection connection, SqlWork<T> work)
            throws SQLException, ManagementException {
        connection.setAutoCommit(false);
        try {
            T result = work.run();
            connection.commit();
            return result;
        } catch (SQLException | ManagementException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    private Map<String, String> lockRecord(Connection connection, Directory directory, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(directory.lockSql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Map<String, String> values = new LinkedHashMap<>();
                values.put("entity_code", resultSet.getString("entity_code"));
                values.put("entity_name", resultSet.getString("entity_name"));
                values.put("active_flag", resultSet.getString("active_flag"));
                if (directory == Directory.INGREDIENTS) {
                    values.put("base_unit", resultSet.getString("base_unit"));
                }
                return values;
            }
        }
    }

    private boolean codeExists(Connection connection, Directory directory, String code, Long excludedId)
            throws SQLException {
        String sql = excludedId == null ? directory.codeExistsSql : directory.codeExistsExceptSql;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            if (excludedId != null) {
                statement.setLong(2, excludedId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void bindMutableFields(PreparedStatement statement, Directory directory, FormInput input)
            throws SQLException {
        statement.setString(1, input.code());
        statement.setString(2, input.name());
        if (directory == Directory.VENDORS) {
            setNullable(statement, 3, input.contactName());
            setNullable(statement, 4, input.phone());
            setNullable(statement, 5, input.email());
        } else if (directory == Directory.INGREDIENTS) {
            statement.setString(3, input.baseUnit());
        }
    }

    private void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private boolean hasDependencies(List<Dependency> dependencies) {
        return dependencies.stream().anyMatch(dependency -> dependency.count() > 0);
    }

    private List<DirectoryRecord> loadRecords(Connection connection, Directory directory, String search)
            throws SQLException {
        boolean filtered = search != null && !search.isBlank();
        String sql = directory.listSql(filtered);
        List<DirectoryRecord> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (filtered) {
                statement.setString(1, search);
                statement.setString(2, search);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(readRecord(resultSet, directory));
                }
            }
        }
        return List.copyOf(rows);
    }

    private DirectoryRecord loadRecord(Connection connection, Directory directory, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(directory.detailSql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readRecord(resultSet, directory) : null;
            }
        }
    }

    private List<Dependency> loadDependencies(Connection connection, Directory directory, long id)
            throws SQLException {
        List<Dependency> dependencies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(directory.dependencySql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    for (int index = 0; index < directory.dependencyLabels.length; index++) {
                        dependencies.add(new Dependency(directory.dependencyLabels[index],
                                resultSet.getLong(index + 1)));
                    }
                }
            }
        }
        return List.copyOf(dependencies);
    }

    private DirectoryRecord readRecord(ResultSet resultSet, Directory directory) throws SQLException {
        List<Attribute> attributes = new ArrayList<>();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", resultSet.getString("entity_code"));
        fields.put("name", resultSet.getString("entity_name"));
        for (String[] attribute : directory.attributeColumns) {
            String value = resultSet.getString(attribute[1]);
            fields.put(attribute[1], value == null ? "" : value);
            if (value != null && !value.isBlank()) {
                attributes.add(new Attribute(attribute[0], value));
            }
        }
        return new DirectoryRecord(resultSet.getLong("entity_id"), resultSet.getString("entity_code"),
                resultSet.getString("entity_name"), resultSet.getString("active_flag"),
                resultSet.getTimestamp("created_at"), List.copyOf(attributes), Map.copyOf(fields));
    }

    private interface SqlWork<T> {
        T run() throws SQLException, ManagementException;
    }

    public enum Directory {
        VENDORS("Vendors", "/vendors", "vendor", "vendor_id", "vendor_code", "vendor_name",
                "vendors",
                new String[][] { { "Contact name", "contact_name" }, { "Phone", "phone" }, { "Email", "email" } },
                """
                SELECT vendor_id AS entity_id, vendor_code AS entity_code, vendor_name AS entity_name,
                       contact_name, phone, email, active_flag, created_at
                FROM vendors
                ORDER BY vendor_name, vendor_id
                """,
                """
                SELECT vendor_id AS entity_id, vendor_code AS entity_code, vendor_name AS entity_name,
                       contact_name, phone, email, active_flag, created_at
                FROM vendors WHERE vendor_id = ?
                """,
                """
                SELECT vendor_id AS entity_id, vendor_code AS entity_code, vendor_name AS entity_name,
                       contact_name, phone, email, active_flag, created_at
                FROM vendors WHERE vendor_id = ? FOR UPDATE
                """,
                "INSERT INTO vendors (vendor_code, vendor_name, contact_name, phone, email) VALUES (?, ?, ?, ?, ?)",
                "UPDATE vendors SET vendor_code = ?, vendor_name = ?, contact_name = ?, phone = ?, email = ? WHERE vendor_id = ?",
                "UPDATE vendors SET active_flag = ? WHERE vendor_id = ?",
                "DELETE FROM vendors WHERE vendor_id = ?", 6,
                "SELECT 1 FROM vendors WHERE UPPER(vendor_code) = UPPER(?)",
                "SELECT 1 FROM vendors WHERE UPPER(vendor_code) = UPPER(?) AND vendor_id <> ?",
                """
                SELECT (SELECT COUNT(*) FROM purchase_orders po WHERE po.vendor_id = v.vendor_id),
                       (SELECT COUNT(*) FROM vendor_contract_rules r WHERE r.vendor_id = v.vendor_id),
                       (SELECT COUNT(*) FROM delivery_receipts dr JOIN purchase_orders po ON po.po_id = dr.po_id
                        WHERE po.vendor_id = v.vendor_id),
                       (SELECT COUNT(*) FROM vendor_penalty_reviews pr JOIN purchase_orders po ON po.po_id = pr.po_id
                        WHERE po.vendor_id = v.vendor_id)
                FROM vendors v WHERE v.vendor_id = ?
                """,
                new String[] { "Purchase orders", "Vendor contract rules", "Delivery receipts through purchase orders",
                        "Penalty reviews through purchase orders" },
                "This vendor is referenced by procurement or contract history, so it cannot be deleted. Deactivate it instead."),
        INGREDIENTS("Ingredients", "/ingredients", "ingredient", "ingredient_id", "ingredient_code", "ingredient_name",
                "ingredients",
                new String[][] { { "Base unit", "base_unit" } },
                """
                SELECT ingredient_id AS entity_id, ingredient_code AS entity_code,
                       ingredient_name AS entity_name, base_unit, active_flag, created_at
                FROM ingredients
                ORDER BY ingredient_name, ingredient_id
                """,
                """
                SELECT ingredient_id AS entity_id, ingredient_code AS entity_code,
                       ingredient_name AS entity_name, base_unit, active_flag, created_at
                FROM ingredients WHERE ingredient_id = ?
                """,
                """
                SELECT ingredient_id AS entity_id, ingredient_code AS entity_code, ingredient_name AS entity_name,
                       base_unit, active_flag FROM ingredients WHERE ingredient_id = ? FOR UPDATE
                """,
                "INSERT INTO ingredients (ingredient_code, ingredient_name, base_unit) VALUES (?, ?, ?)",
                "UPDATE ingredients SET ingredient_code = ?, ingredient_name = ?, base_unit = ? WHERE ingredient_id = ?",
                "UPDATE ingredients SET active_flag = ? WHERE ingredient_id = ?",
                "DELETE FROM ingredients WHERE ingredient_id = ?", 4,
                "SELECT 1 FROM ingredients WHERE UPPER(ingredient_code) = UPPER(?)",
                "SELECT 1 FROM ingredients WHERE UPPER(ingredient_code) = UPPER(?) AND ingredient_id <> ?",
                """
                SELECT (SELECT COUNT(*) FROM purchase_order_items poi WHERE poi.ingredient_id = i.ingredient_id),
                       (SELECT COUNT(*) FROM delivery_receipt_items dri JOIN purchase_order_items poi
                        ON poi.po_item_id = dri.po_item_id WHERE poi.ingredient_id = i.ingredient_id),
                       (SELECT COUNT(*) FROM inventory_balances ib WHERE ib.ingredient_id = i.ingredient_id),
                       (SELECT COUNT(*) FROM inventory_transactions it WHERE it.ingredient_id = i.ingredient_id)
                FROM ingredients i WHERE i.ingredient_id = ?
                """,
                new String[] { "Purchase-order items", "Receipt items through purchase-order items",
                        "Inventory balance rows", "Inventory transactions" },
                "This ingredient is referenced by order, receipt, or inventory history, so it cannot be deleted. Deactivate it instead."),
        MESS_BLOCKS("Mess blocks", "/mess-blocks", "mess block", "block_id", "block_code", "block_name",
                "mess_blocks",
                new String[0][0],
                """
                SELECT block_id AS entity_id, block_code AS entity_code,
                       block_name AS entity_name, active_flag, created_at
                FROM mess_blocks
                ORDER BY block_name, block_id
                """,
                """
                SELECT block_id AS entity_id, block_code AS entity_code,
                       block_name AS entity_name, active_flag, created_at
                FROM mess_blocks WHERE block_id = ?
                """,
                "SELECT block_id AS entity_id, block_code AS entity_code, block_name AS entity_name, active_flag FROM mess_blocks WHERE block_id = ? FOR UPDATE",
                "INSERT INTO mess_blocks (block_code, block_name) VALUES (?, ?)",
                "UPDATE mess_blocks SET block_code = ?, block_name = ? WHERE block_id = ?",
                "UPDATE mess_blocks SET active_flag = ? WHERE block_id = ?",
                "DELETE FROM mess_blocks WHERE block_id = ?", 3,
                "SELECT 1 FROM mess_blocks WHERE UPPER(block_code) = UPPER(?)",
                "SELECT 1 FROM mess_blocks WHERE UPPER(block_code) = UPPER(?) AND block_id <> ?",
                """
                SELECT (SELECT COUNT(*) FROM purchase_orders po WHERE po.block_id = b.block_id),
                       (SELECT COUNT(*) FROM delivery_receipts dr JOIN purchase_orders po ON po.po_id = dr.po_id
                        WHERE po.block_id = b.block_id),
                       (SELECT COUNT(*) FROM inventory_balances ib WHERE ib.block_id = b.block_id),
                       (SELECT COUNT(*) FROM inventory_transactions it WHERE it.block_id = b.block_id),
                       (SELECT COUNT(*) FROM daily_mess_records mr WHERE mr.block_id = b.block_id)
                FROM mess_blocks b WHERE b.block_id = ?
                """,
                new String[] { "Purchase orders", "Delivery receipts through purchase orders",
                        "Inventory balance rows", "Inventory transactions", "Daily mess records" },
                "This mess block is referenced by procurement, inventory, or daily mess history, so it cannot be deleted. Deactivate it instead.");

        private final String label;
        private final String path;
        private final String singular;
        private final String idColumn;
        private final String codeColumn;
        private final String nameColumn;
        private final String tableName;
        private final String[][] attributeColumns;
        private final String listSql;
        private final String detailSql;
        private final String lockSql;
        private final String insertSql;
        private final String updateSql;
        private final String activeSql;
        private final String deleteSql;
        private final int updateParameterCount;
        private final String codeExistsSql;
        private final String codeExistsExceptSql;
        private final String dependencySql;
        private final String[] dependencyLabels;
        private final String blockedDeleteMessage;

        Directory(String label, String path, String singular, String idColumn, String codeColumn, String nameColumn,
                String tableName,
                String[][] attributeColumns, String listSql, String detailSql, String lockSql,
                String insertSql, String updateSql, String activeSql, String deleteSql, int updateParameterCount,
                String codeExistsSql, String codeExistsExceptSql, String dependencySql,
                String[] dependencyLabels, String blockedDeleteMessage) {
            this.label = label;
            this.path = path;
            this.singular = singular;
            this.idColumn = idColumn;
            this.codeColumn = codeColumn;
            this.nameColumn = nameColumn;
            this.tableName = tableName;
            this.attributeColumns = attributeColumns;
            this.listSql = listSql;
            this.detailSql = detailSql;
            this.lockSql = lockSql;
            this.insertSql = insertSql;
            this.updateSql = updateSql;
            this.activeSql = activeSql;
            this.deleteSql = deleteSql;
            this.updateParameterCount = updateParameterCount;
            this.codeExistsSql = codeExistsSql;
            this.codeExistsExceptSql = codeExistsExceptSql;
            this.dependencySql = dependencySql;
            this.dependencyLabels = dependencyLabels;
            this.blockedDeleteMessage = blockedDeleteMessage;
        }

        String listSql(boolean filtered) {
            if (!filtered) {
                return listSql;
            }
            int orderBy = listSql.lastIndexOf("ORDER BY");
            String selectAndFrom = listSql.substring(0, orderBy);
            String order = listSql.substring(orderBy);
            return selectAndFrom + "WHERE (INSTR(LOWER(" + codeColumn + "), LOWER(?)) > 0 "
                    + "OR INSTR(LOWER(" + nameColumn + "), LOWER(?)) > 0)\n" + order;
        }

        public String label() { return label; }
        public String path() { return path; }
        public String singular() { return singular; }
        public boolean hasVendorFields() { return this == VENDORS; }
        public boolean hasUnitField() { return this == INGREDIENTS; }
        public String blockedDeleteMessage() { return blockedDeleteMessage; }

        public static Directory fromPath(String path) {
            for (Directory directory : values()) {
                if (directory.path.equals(path)) {
                    return directory;
                }
            }
            return null;
        }
    }

    public record FormInput(String code, String name, String contactName, String phone, String email, String baseUnit) { }

    public static final class ManagementException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        public ManagementException(String message) {
            this(message, 409);
        }

        public ManagementException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() { return statusCode; }
    }

    public record Attribute(String label, String value) {
        public String getLabel() { return label; }
        public String getValue() { return value; }
    }

    public record DirectoryRecord(long id, String code, String name, String activeFlag,
            Timestamp createdAt, List<Attribute> attributes, Map<String, String> fields) {
        public long getId() { return id; }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getActiveFlag() { return activeFlag; }
        public Timestamp getCreatedAt() { return createdAt; }
        public List<Attribute> getAttributes() { return attributes; }
        public Map<String, String> getFields() { return fields; }
    }

    public record Dependency(String label, long count) {
        public String getLabel() { return label; }
        public long getCount() { return count; }
    }

    public record PageData(List<DirectoryRecord> records, DirectoryRecord selectedRecord,
            List<Dependency> dependencies) {
        public List<DirectoryRecord> getRecords() { return records; }
        public DirectoryRecord getSelectedRecord() { return selectedRecord; }
        public List<Dependency> getDependencies() { return dependencies; }
    }
}
