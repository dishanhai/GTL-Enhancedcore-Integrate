package com.gtl.enhancedcore.common.config;

import java.io.IOException;
import java.util.Properties;

/** Pure-JDK parser for the client-only preview settings file. */
public final class PreviewLayerSettings {
    private PreviewLayerSettings() {}

    public static boolean parse(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new java.io.StringReader(text));
        } catch (IOException impossible) {
            throw new IllegalArgumentException(impossible);
        }
        String value = properties.getProperty("previewLayers", "true").trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("previewLayers: expected true / false");
    }
}
