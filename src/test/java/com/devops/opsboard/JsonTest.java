package com.devops.opsboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void quoteEscapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\"", Json.quote("a\"b\\c\n"));
    }

    @Test
    void mapAndListSerialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("n", 3);
        m.put("tags", List.of("x", "y"));
        assertEquals("{\"ok\":true,\"n\":3,\"tags\":[\"x\",\"y\"]}", Json.toJson(m));
    }

    @Test
    void nullIsJsonNull() {
        assertEquals("null", Json.toJson(null));
    }
}
