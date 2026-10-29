package edu.hostel.mess.web.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Minimal JSON encoder for the typed, read-only analytics payloads sent to JSP data attributes. */
public final class AnalyticsJson {
    private AnalyticsJson() {
    }

    public static String serialize(List<? extends Map<String, ?>> rows) {
        StringBuilder json = new StringBuilder("[");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (rowIndex > 0) {
                json.append(',');
            }
            json.append('{');
            int fieldIndex = 0;
            for (Map.Entry<String, ?> field : rows.get(rowIndex).entrySet()) {
                if (fieldIndex++ > 0) {
                    json.append(',');
                }
                appendString(json, field.getKey());
                json.append(':');
                appendValue(json, field.getValue());
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number number) {
            if (number instanceof BigDecimal decimal) {
                json.append(decimal.toPlainString());
            } else {
                json.append(number);
            }
        } else if (value instanceof Boolean bool) {
            json.append(bool);
        } else {
            appendString(json, value.toString());
        }
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '<' -> json.append("\\u003c");
                case '>' -> json.append("\\u003e");
                case '&' -> json.append("\\u0026");
                case '\u2028' -> json.append("\\u2028");
                case '\u2029' -> json.append("\\u2029");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
