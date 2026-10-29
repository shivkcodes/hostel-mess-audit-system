package edu.hostel.mess.web.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AdminDataRepositoryTimestampTest {
    @Test
    void mapsTimestampAndOracleDateColumnsToJdbcTimestampIncludingNulls() throws SQLException {
        Timestamp arrived = Timestamp.valueOf("2026-10-28 14:16:00.0");
        Timestamp ordered = Timestamp.valueOf("2026-10-28 09:30:00.0");
        ResultSet resultSet = resultSet(
                Map.of("arrived_at", Types.TIMESTAMP, "verified_at", Types.TIMESTAMP, "ordered_at", Types.DATE),
                Map.of("arrived_at", arrived, "ordered_at", ordered));

        Map<String, Object> mapped = new AdminDataRepository().row(
                resultSet, "arrived_at", "verified_at", "ordered_at");

        assertSame(arrived, mapped.get("arrived_at"));
        assertInstanceOf(Timestamp.class, mapped.get("arrived_at"));
        assertNull(mapped.get("verified_at"), "Nullable timestamps must remain null.");
        assertEquals(ordered, mapped.get("ordered_at"), "Oracle DATE time components must be preserved.");
        assertInstanceOf(Timestamp.class, mapped.get("ordered_at"));
    }

    @Test
    void recognizesOracleTimestampTypeNamesWhenTheDriverUsesAVendorTypeCode() throws SQLException {
        Timestamp reviewed = Timestamp.valueOf("2026-10-28 15:45:12.0");
        ResultSet resultSet = resultSet(Map.of("reviewed_at", -102), Map.of("reviewed_at", reviewed),
                Map.of("reviewed_at", "TIMESTAMPLTZ"));

        Map<String, Object> mapped = new AdminDataRepository().row(resultSet, "reviewed_at");

        assertSame(reviewed, mapped.get("reviewed_at"));
        assertInstanceOf(Timestamp.class, mapped.get("reviewed_at"));
    }

    private ResultSet resultSet(Map<String, Integer> types, Map<String, Object> values) {
        return resultSet(types, values, Map.of());
    }

    private ResultSet resultSet(Map<String, Integer> types, Map<String, Object> values,
            Map<String, String> typeNames) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        int next = 1;
        for (String column : types.keySet()) indexes.put(column, next++);

        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (proxy, method, args) -> {
            int index = (Integer) args[0];
            String column = indexes.entrySet().stream().filter(entry -> entry.getValue() == index)
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            return switch (method.getName()) {
                case "getColumnType" -> types.get(column);
                case "getColumnTypeName" -> typeNames.getOrDefault(column, defaultTypeName(types.get(column)));
                default -> defaultValue(method.getReturnType());
            };
        });

        return proxy(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "getMetaData" -> metadata;
            case "findColumn" -> indexes.get(args[0]);
            case "getTimestamp" -> values.get(args[0]);
            case "getObject" -> throw new AssertionError("Date/time columns must use a typed JDBC getter.");
            default -> defaultValue(method.getReturnType());
        });
    }

    private String defaultTypeName(int sqlType) {
        if (sqlType == Types.DATE) return "DATE";
        if (sqlType == Types.TIMESTAMP) return "TIMESTAMP";
        return "OTHER";
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
