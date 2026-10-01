package com.example.eassistent.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DaySchedule implements Serializable {
    private int dayIndex;
    private String dayName;
    private String dateText;
    private boolean isToday;
    private List<PeriodSchedule> periods;

    public DaySchedule() {
        this.periods = new ArrayList<>();
        this.dayName = "";
        this.dateText = "";
    }

    public DaySchedule(int dayIndex, String dayName, String dateText, boolean isToday) {
        this.dayIndex = dayIndex;
        this.dayName = dayName != null ? dayName : "";
        this.dateText = dateText != null ? dateText : "";
        this.isToday = isToday;
        this.periods = new ArrayList<>();
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public void setDayIndex(int dayIndex) {
        this.dayIndex = dayIndex;
    }

    public String getDayName() {
        return dayName;
    }

    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    public String getDateText() {
        return dateText;
    }

    public void setDateText(String dateText) {
        this.dateText = dateText;
    }

    public boolean isToday() {
        return isToday;
    }

    public void setToday(boolean today) {
        isToday = today;
    }

    public List<PeriodSchedule> getPeriods() {
        return periods;
    }

    public void addPeriod(PeriodSchedule period) {
        if (period != null) {
            this.periods.add(period);
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("dayIndex", dayIndex);
        json.put("dayName", dayName);
        json.put("dateText", dateText);
        json.put("isToday", isToday);
        JSONArray arr = new JSONArray();
        for (PeriodSchedule p : periods) {
            arr.put(p.toJson());
        }
        json.put("periods", arr);
        return json;
    }

    public static DaySchedule fromJson(JSONObject json) {
        if (json == null) return new DaySchedule();
        DaySchedule d = new DaySchedule(
                json.optInt("dayIndex", 0),
                json.optString("dayName", ""),
                json.optString("dateText", ""),
                json.optBoolean("isToday", false)
        );
        JSONArray arr = json.optJSONArray("periods");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                d.addPeriod(PeriodSchedule.fromJson(arr.optJSONObject(i)));
            }
        }
        return d;
    }
}
