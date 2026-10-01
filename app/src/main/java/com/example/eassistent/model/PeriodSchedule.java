package com.example.eassistent.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PeriodSchedule implements Serializable {
    private int hourNumber;
    private String hourName;
    private String hourTime;
    private List<ClassItem> items;

    public PeriodSchedule() {
        this.items = new ArrayList<>();
        this.hourName = "";
        this.hourTime = "";
    }

    public PeriodSchedule(int hourNumber, String hourName, String hourTime) {
        this.hourNumber = hourNumber;
        this.hourName = hourName != null ? hourName : "";
        this.hourTime = hourTime != null ? hourTime : "";
        this.items = new ArrayList<>();
    }

    public int getHourNumber() {
        return hourNumber;
    }

    public void setHourNumber(int hourNumber) {
        this.hourNumber = hourNumber;
    }

    public String getHourName() {
        return hourName;
    }

    public void setHourName(String hourName) {
        this.hourName = hourName;
    }

    public String getHourTime() {
        return hourTime;
    }

    public void setHourTime(String hourTime) {
        this.hourTime = hourTime;
    }

    public List<ClassItem> getItems() {
        return items;
    }

    public void addItem(ClassItem item) {
        if (item != null) {
            this.items.add(item);
        }
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("hourNumber", hourNumber);
        json.put("hourName", hourName);
        json.put("hourTime", hourTime);
        JSONArray arr = new JSONArray();
        for (ClassItem item : items) {
            arr.put(item.toJson());
        }
        json.put("items", arr);
        return json;
    }

    public static PeriodSchedule fromJson(JSONObject json) {
        if (json == null) return new PeriodSchedule();
        PeriodSchedule p = new PeriodSchedule(
                json.optInt("hourNumber", 0),
                json.optString("hourName", ""),
                json.optString("hourTime", "")
        );
        JSONArray arr = json.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                p.addItem(ClassItem.fromJson(arr.optJSONObject(i)));
            }
        }
        return p;
    }
}
