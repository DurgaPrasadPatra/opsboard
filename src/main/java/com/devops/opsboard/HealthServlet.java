package com.devops.opsboard;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Liveness/readiness endpoint: GET /health */
@WebServlet("/health")
public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean storage = DataStore.writable();
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("dataDirWritable", storage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", storage ? "UP" : "DEGRADED");
        body.put("checks", checks);
        Http.json(resp, storage ? 200 : 503, body);
    }
}
