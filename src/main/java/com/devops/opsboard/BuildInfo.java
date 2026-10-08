package com.devops.opsboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build metadata written by Maven resource filtering (build.properties). */
public final class BuildInfo {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/build.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException ignored) {
            // fall back to defaults
        }
    }

    private BuildInfo() {}

    public static String get(String key, String def) {
        return PROPS.getProperty(key, def);
    }
}
