package com.devops.opsboard;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

final class Http {
    private Http() {}

    static void json(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(Json.toJson(body));
    }
}
