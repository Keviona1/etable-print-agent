package com.etable.printagent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = Config.class.getResourceAsStream("/agent.properties")) {

            if (input == null) {
                System.err.println("FATAL: Unable to find agent.properties in src/main/resources.");
            } else {

                props.load(input);
            }

        } catch (IOException ex) {
            System.err.println("Error loading agent.properties.");
            ex.printStackTrace();
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}