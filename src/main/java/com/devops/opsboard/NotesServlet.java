package com.devops.opsboard;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Deploy journal API (persists to the mapped volume):
 *   GET    /api/notes            list entries, newest first
 *   POST   /api/notes  text=...  add entry
 *   DELETE /api/notes            clear all
 */
@WebServlet("/api/notes")
public class NotesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Http.json(resp, 200, snapshot());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        String text = req.getParameter("text");
        if (text == null || text.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "text is required");
            Http.json(resp, 400, err);
            return;
        }
        DataStore.add(text);
        Http.json(resp, 201, snapshot());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        DataStore.clear();
        Http.json(resp, 200, snapshot());
    }

    private Map<String, Object> snapshot() throws IOException {
        List<Map<String, Object>> items = new ArrayList<>();
        for (DataStore.Entry e : DataStore.list()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", Instant.ofEpochMilli(e.time()).toString());
            m.put("text", e.text());
            items.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dataDir", DataStore.dir().toString());
        body.put("count", items.size());
        body.put("entries", items);
        return body;
    }
}
