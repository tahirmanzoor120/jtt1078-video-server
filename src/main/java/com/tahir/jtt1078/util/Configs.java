package com.tahir.jtt1078.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public final class Configs
{
    static Properties properties = new Properties();
    static Logger LOGGER = LoggerFactory.getLogger(Configs.class);

    public static void init(String configFilePath)
    {
        try {
            File file = new File((configFilePath.startsWith("/") ? "." : "") + configFilePath);
            if (file.exists()) {
                LOGGER.info("Config file loaded: {}", file.getName());
                properties.load(new FileInputStream(file));
            } else {
                LOGGER.info("Config file not found. Using predefined configurations.");
                properties.load(Configs.class.getResourceAsStream(configFilePath));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String get(String key)
    {
        Object val = properties.get(key);
        if (val != null) {
            return String.valueOf(val).trim();
        }
        return null;
    }

    public static int getInt(String key, int defaultValue)
    {
        String val = get(key);
        if (val != null) {
            return Integer.parseInt(val);
        }
        return defaultValue;
    }

    public static boolean getBoolean(String key)
    {
        String val = get(key);
        if (val != null) {
            return "on".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val);
        }
        return false;
    }
}
