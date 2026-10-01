package com.example.eassistent.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.eassistent.model.ScheduleData;
import com.example.eassistent.parser.UrnikParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class UrnikStorage {

    private static final String PREFS_NAME = "eassistent_prefs";
    private static final String KEY_URNIK_URL = "key_urnik_url";
    private static final String CACHE_FILE_NAME = "urnik_cached.json";

    private static final String KEY_DARK_MODE = "key_dark_mode";

    public static final String DEFAULT_URL =
            "https://urniki.easistent.com/urniki/e29aeb36cd1efde89c2b2c28e33209813ec32756/oddelki/854650/dijak/10383463";

    public static String getSavedUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_URNIK_URL, DEFAULT_URL);
    }

    public static void saveUrl(Context context, String url) {
        if (url != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_URNIK_URL, url.trim()).apply();
        }
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkMode(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    private static final String KEY_REFRESH_LEFT = "key_refresh_left";

    public static boolean isRefreshButtonLeft(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_REFRESH_LEFT, false);
    }

    public static void setRefreshButtonLeft(Context context, boolean left) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_REFRESH_LEFT, left).apply();
    }

    public static synchronized void saveSchedule(Context context, ScheduleData schedule) {
        if (schedule == null) return;
        try {
            String json = schedule.toJsonString();
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized ScheduleData loadSchedule(Context context) {
        File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
        if (cacheFile.exists()) {
            try (FileInputStream fis = new FileInputStream(cacheFile);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                ScheduleData data = ScheduleData.fromJsonString(sb.toString());
                if (data != null && !data.getDays().isEmpty()) {
                    return data;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // If no cache exists, load bundled offline seed asset for instant 0ms first launch!
        try (InputStream is = context.getAssets().open("urnik_seed.html");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            ScheduleData seedData = UrnikParser.parse(sb.toString(), DEFAULT_URL);
            if (seedData != null) {
                saveSchedule(context, seedData);
                return seedData;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
