package org.sightlesscoders.chess.online;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JsonUtil {
    public static JSONObject obj() {
        return new JSONObject();
    }

    public static JSONObject obj(String key, Object value) {
        JSONObject o = new JSONObject();
        put(o, key, value);
        return o;
    }

    public static void put(JSONObject o, String key, Object value) {
        try {
            if (value == null) {
                o.put(key, JSONObject.NULL);
            } else if (value instanceof String) {
                o.put(key, value);
            } else if (value instanceof Number) {
                o.put(key, value);
            } else if (value instanceof Boolean) {
                o.put(key, value);
            } else if (value instanceof JSONObject) {
                o.put(key, value);
            } else if (value instanceof JSONArray) {
                o.put(key, value);
            } else {
                o.put(key, value.toString());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static JSONArray arr() {
        return new JSONArray();
    }

    public static JSONArray arr(Object... values) {
        JSONArray a = new JSONArray();
        for (Object v : values) {
            put(a, v);
        }
        return a;
    }

    public static void put(JSONArray a, Object value) {
        if (value == null) {
            a.put(JSONObject.NULL);
        } else if (value instanceof String) {
            a.put(value);
        } else if (value instanceof Number) {
            a.put((Number) value);
        } else if (value instanceof Boolean) {
            a.put((Boolean) value);
        } else if (value instanceof JSONObject) {
            a.put(value);
        } else if (value instanceof JSONArray) {
            a.put(value);
        } else {
            a.put(value.toString());
        }
    }

    public static String getString(JSONObject o, String key, String def) {
        try {
            return o.has(key) && !o.isNull(key) ? o.getString(key) : def;
        } catch (JSONException e) {
            return def;
        }
    }

    public static int getInt(JSONObject o, String key, int def) {
        try {
            return o.has(key) && !o.isNull(key) ? o.getInt(key) : def;
        } catch (JSONException e) {
            return def;
        }
    }

    public static long getLong(JSONObject o, String key, long def) {
        try {
            return o.has(key) && !o.isNull(key) ? o.getLong(key) : def;
        } catch (JSONException e) {
            return def;
        }
    }

    public static boolean getBoolean(JSONObject o, String key, boolean def) {
        try {
            return o.has(key) && !o.isNull(key) && o.getBoolean(key);
        } catch (JSONException e) {
            return def;
        }
    }

    public static JSONObject getObject(JSONObject o, String key) {
        try {
            return o.has(key) && !o.isNull(key) ? o.getJSONObject(key) : null;
        } catch (JSONException e) {
            return null;
        }
    }

    public static JSONArray getArray(JSONObject o, String key) {
        try {
            return o.has(key) && !o.isNull(key) ? o.getJSONArray(key) : null;
        } catch (JSONException e) {
            return null;
        }
    }

    public static List<String> toStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                list.add(arr.getString(i));
            } catch (JSONException ignored) {}
        }
        return list;
    }
}