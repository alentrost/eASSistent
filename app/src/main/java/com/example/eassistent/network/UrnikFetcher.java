package com.example.eassistent.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UrnikFetcher {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UG1A.240905.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.0.0 Mobile Safari/537.36";

    public interface FetchCallback {
        void onSuccess(String html);
        void onError(Exception error);
    }

    /**
     * Synchronously fetches the HTML content of the timetable mimicking a real Android browser profile.
     */
    public static String fetchHtmlSync(String urlString) throws IOException {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("URL urnika ni naveden.");
        }

        URL url = new URL(urlString.trim());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);

        // Faking browser / mobile profile headers ("curling as a profile")
        conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "sl-SI,sl;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"");
        conn.setRequestProperty("Sec-Ch-Ua-Mobile", "?1");
        conn.setRequestProperty("Sec-Ch-Ua-Platform", "\"Android\"");
        conn.setRequestProperty("Sec-Fetch-Dest", "document");
        conn.setRequestProperty("Sec-Fetch-Mode", "navigate");
        conn.setRequestProperty("Sec-Fetch-Site", "none");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Pragma", "no-cache");

        int responseCode = conn.getResponseCode();
        if (responseCode >= 300 && responseCode < 400) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                return fetchHtmlSync(redirectUrl);
            }
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Napaka pri povezavi s strežnikom. Koda: " + responseCode);
        }

        try (InputStream in = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Extracts URL parts from the base eAsistent URL and builds the AJAX URL for a specific week.
     * Base URL format: https://urniki.easistent.com/urniki/{hash}/razredi/{id_razred}/dijak/{id_dijak}
     * Or:              https://urniki.easistent.com/urniki/{hash}/oddelki/{id_razred}/dijak/{id_dijak}
     * AJAX URL format:  https://urniki.easistent.com/urniki/ajax_urnik/{id_sola}/{id_razred}/0/{id_dijak}/0/{week}/0/1
     *
     * id_sola is extracted from the HTML page (hidden in JavaScript).
     */
    public static String buildAjaxWeekUrl(String baseUrl, int idSola, int week) {
        if (baseUrl == null) return null;
        try {
            // Extract id_razred and id_dijak from URL path
            // Pattern: .../razredi/{id_razred}/dijak/{id_dijak} or .../oddelki/{id_razred}/dijak/{id_dijak}
            String path = new URL(baseUrl.trim()).getPath();
            String[] segments = path.split("/");
            
            String idRazred = "0";
            String idDijak = "0";
            
            for (int i = 0; i < segments.length; i++) {
                if ((segments[i].equals("razredi") || segments[i].equals("oddelki")) && i + 1 < segments.length) {
                    idRazred = segments[i + 1];
                }
                if (segments[i].equals("dijak") && i + 1 < segments.length) {
                    idDijak = segments[i + 1];
                }
            }
            
            return "https://urniki.easistent.com/urniki/ajax_urnik/" 
                    + idSola + "/" + idRazred + "/0/" + idDijak + "/0/" + week + "/0/1";
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Fetches a specific week's schedule via the AJAX endpoint.
     * Returns the raw response which contains fields separated by the Unit Separator character (\u001f):
     * [0]=weekNumber, [1]=startDate, [2]=endDate, [3]=scheduleHTML, [4]=qversion
     */
    public static String fetchWeekAjaxSync(String ajaxUrl) throws IOException {
        if (ajaxUrl == null || ajaxUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("AJAX URL ni naveden.");
        }
        
        URL url = new URL(ajaxUrl.trim());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        
        conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Language", "sl-SI,sl;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Pragma", "no-cache");
        
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Napaka pri povezavi s strežnikom. Koda: " + responseCode);
        }
        
        try (InputStream in = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
