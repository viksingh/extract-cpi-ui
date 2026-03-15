package com.sakiv.cpi.extractor.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Manages locale/language preferences and provides access to the ResourceBundle
 * for internationalized strings.
 */
public class LocaleManager {

    private static final String PREFS_FILE = "cpi-extractor-lang.properties";
    private static final String BUNDLE_BASE = "messages";

    private static Locale currentLocale;
    private static ResourceBundle bundle;

    static {
        currentLocale = loadSavedLocale();
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
    }

    public static ResourceBundle getBundle() {
        return bundle;
    }

    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    public static String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);
        saveLocale(locale);
    }

    private static Locale loadSavedLocale() {
        Path prefsPath = getPrefsPath();
        if (Files.exists(prefsPath)) {
            try (InputStream is = new FileInputStream(prefsPath.toFile())) {
                Properties props = new Properties();
                props.load(is);
                String lang = props.getProperty("language", "en");
                return Locale.forLanguageTag(lang);
            } catch (IOException ignored) {
            }
        }
        return Locale.ENGLISH;
    }

    private static void saveLocale(Locale locale) {
        try {
            Path prefsPath = getPrefsPath();
            Properties props = new Properties();
            props.setProperty("language", locale.getLanguage());
            try (OutputStream os = new FileOutputStream(prefsPath.toFile())) {
                props.store(os, "CPI Extractor Language Preferences");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path getPrefsPath() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".cpi-extractor", PREFS_FILE);
    }
}
