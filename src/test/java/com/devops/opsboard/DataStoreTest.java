package com.devops.opsboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class DataStoreTest {
    @Test
    void addListClearRoundTrip() throws IOException {
        DataStore.clear();
        DataStore.add("first\tentry\nwith junk");
        DataStore.add("second");
        var list = DataStore.list();
        assertEquals(2, list.size());
        assertEquals("second", list.get(0).text());          // newest first
        assertEquals("first entry with junk", list.get(1).text());
        DataStore.clear();
        assertTrue(DataStore.list().isEmpty());
    }
}
