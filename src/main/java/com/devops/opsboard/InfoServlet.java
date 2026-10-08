package com.devops.opsboard;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Build, runtime and storage details: GET /api/info */
@WebServlet("/api/info")
public class InfoServlet extends HttpServlet {
    private static final Instant STARTED = Instant.now();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", "OpsBoard");
        app.put("version", BuildInfo.get("app.version", "dev"));
        app.put("gitCommit", BuildInfo.get("git.commit", "unknown"));
        app.put("buildNumber", BuildInfo.get("build.number", "0"));
        app.put("buildTime", BuildInfo.get("build.time", "unknown"));

        Runtime rt = Runtime.getRuntime();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("hostname", hostname());
        runtime.put("insideDocker", new File("/.dockerenv").exists());
        runtime.put("server", getServletContext().getServerInfo());
        runtime.put("java", System.getProperty("java.version"));
        runtime.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        runtime.put("processors", rt.availableProcessors());
        runtime.put("startedAt", STARTED.toString());
        runtime.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        runtime.put("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1048576);
        runtime.put("memoryMaxMb", rt.maxMemory() / 1048576);

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("dataDir", DataStore.dir().toString());
        storage.put("writable", DataStore.writable());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", app);
        body.put("runtime", runtime);
        body.put("storage", storage);
        body.put("contextPath", req.getContextPath());
        // flat copy so shell health checks can grep it easily
        body.put("gitCommit", app.get("gitCommit"));
        Http.json(resp, 200, body);
    }

    private static String hostname() {
        String env = System.getenv("HOSTNAME"); // container ID inside Docker
        if (env != null && !env.isBlank()) return env;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
