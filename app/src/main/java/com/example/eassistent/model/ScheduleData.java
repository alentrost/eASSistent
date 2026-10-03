package com.example.eassistent.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ScheduleData implements Serializable {
    private String schoolTitle;
    private String className;
    private String weekText;
    private long lastUpdatedMillis;
    private String sourceUrl;
    private List<DaySchedule> days;
    private List<String> hourNames;
    private List<String> hourTimes;
    private int currentWeekNumber;
    private int totalWeeks;
    private int idSola;

    public ScheduleData() {
        this.schoolTitle = "";
        this.className = "";
        this.weekText = "";
        this.sourceUrl = "";
        this.lastUpdatedMillis = 0;
        this.days = new ArrayList<>();
        this.hourNames = new ArrayList<>();
        this.hourTimes = new ArrayList<>();
    }

    public String getSchoolTitle() {
        return schoolTitle;
    }

    public void setSchoolTitle(String schoolTitle) {
        this.schoolTitle = schoolTitle;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getWeekText() {
        return weekText;
    }

    public void setWeekText(String weekText) {
        this.weekText = weekText;
    }

    public long getLastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    public void setLastUpdatedMillis(long lastUpdatedMillis) {
        this.lastUpdatedMillis = lastUpdatedMillis;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public List<DaySchedule> getDays() {
        return days;
    }

    public void addDay(DaySchedule day) {
        if (day != null) {
            this.days.add(day);
        }
    }

    public List<String> getHourNames() {
        return hourNames;
    }

    public void setHourNames(List<String> hourNames) {
        this.hourNames = hourNames;
    }

    public List<String> getHourTimes() {
        return hourTimes;
    }

    public void setHourTimes(List<String> hourTimes) {
        this.hourTimes = hourTimes;
    }

    public int getCurrentWeekNumber() { return currentWeekNumber; }
    public void setCurrentWeekNumber(int currentWeekNumber) { this.currentWeekNumber = currentWeekNumber; }
    public int getTotalWeeks() { return totalWeeks; }
    public void setTotalWeeks(int totalWeeks) { this.totalWeeks = totalWeeks; }
    public int getIdSola() { return idSola; }
    public void setIdSola(int idSola) { this.idSola = idSola; }

    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schoolTitle", schoolTitle);
        json.put("className", className);
        json.put("weekText", weekText);
        json.put("lastUpdatedMillis", lastUpdatedMillis);
        json.put("sourceUrl", sourceUrl);
        json.put("currentWeekNumber", currentWeekNumber);
        json.put("totalWeeks", totalWeeks);
        json.put("idSola", idSola);

        JSONArray daysArr = new JSONArray();
        for (DaySchedule d : days) {
            daysArr.put(d.toJson());
        }
        json.put("days", daysArr);

        JSONArray hnArr = new JSONArray();
        for (String h : hourNames) {
            hnArr.put(h);
        }
        json.put("hourNames", hnArr);

        JSONArray htArr = new JSONArray();
        for (String t : hourTimes) {
            htArr.put(t);
        }
        json.put("hourTimes", htArr);

        return json.toString();
    }

    public static ScheduleData fromJsonString(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(jsonStr);
            ScheduleData data = new ScheduleData();
            data.setSchoolTitle(json.optString("schoolTitle", ""));
            data.setClassName(json.optString("className", ""));
            data.setWeekText(json.optString("weekText", ""));
            data.setLastUpdatedMillis(json.optLong("lastUpdatedMillis", 0));
            data.setSourceUrl(json.optString("sourceUrl", ""));
            data.setCurrentWeekNumber(json.optInt("currentWeekNumber", 0));
            data.setTotalWeeks(json.optInt("totalWeeks", 0));
            data.setIdSola(json.optInt("idSola", 0));

            JSONArray daysArr = json.optJSONArray("days");
            if (daysArr != null) {
                for (int i = 0; i < daysArr.length(); i++) {
                    data.addDay(DaySchedule.fromJson(daysArr.optJSONObject(i)));
                }
            }

            JSONArray hnArr = json.optJSONArray("hourNames");
            if (hnArr != null) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < hnArr.length(); i++) {
                    names.add(hnArr.optString(i));
                }
                data.setHourNames(names);
            }

            JSONArray htArr = json.optJSONArray("hourTimes");
            if (htArr != null) {
                List<String> times = new ArrayList<>();
                for (int i = 0; i < htArr.length(); i++) {
                    times.add(htArr.optString(i));
                }
                data.setHourTimes(times);
            }

            return data;
        } catch (JSONException e) {
            return null;
        }
    }
}
