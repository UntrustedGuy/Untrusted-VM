package org.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSONObject {
    private final Map<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(String json) {
        if (json == null || json.trim().isEmpty()) return;
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|true|false|null|[0-9.]+)");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String rawVal = m.group(2).trim();
            if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                map.put(key, rawVal.substring(1, rawVal.length() - 1));
            } else if ("true".equalsIgnoreCase(rawVal)) {
                map.put(key, Boolean.TRUE);
            } else if ("false".equalsIgnoreCase(rawVal)) {
                map.put(key, Boolean.FALSE);
            } else if ("null".equalsIgnoreCase(rawVal)) {
                map.put(key, null);
            } else if (rawVal.contains(".")) {
                try { map.put(key, Double.parseDouble(rawVal)); } catch (Exception e) {}
            } else {
                try { map.put(key, Long.parseLong(rawVal)); } catch (Exception e) {}
            }
        }
    }

    public JSONObject put(String key, Object value) {
        map.put(key, value);
        return this;
    }

    public int optInt(String key, int fallback) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return fallback;
    }

    public long optLong(String key, long fallback) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong((String) v); } catch (Exception ignored) {}
        }
        return fallback;
    }

    public boolean optBoolean(String key, boolean fallback) {
        Object v = map.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            if ("true".equalsIgnoreCase((String) v)) return true;
            if ("false".equalsIgnoreCase((String) v)) return false;
        }
        return fallback;
    }

    public String optString(String key, String fallback) {
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }

    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (count++ > 0) sb.append(",\n");
            sb.append("  \"").append(entry.getKey()).append("\": ");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(0);
    }
}
