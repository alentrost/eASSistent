package com.example.eassistent.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class ClassItem implements Serializable {
    private String subject;
    private String fullSubject;
    private String professor;
    private String classroom;
    private String group;
    private String badge;
    private boolean isEvent;

    public ClassItem() {
        this.subject = "";
        this.fullSubject = "";
        this.professor = "";
        this.classroom = "";
        this.group = "";
        this.badge = "";
        this.isEvent = false;
    }

    public ClassItem(String subject, String fullSubject, String professor, String classroom, String group, String badge, boolean isEvent) {
        this.subject = subject != null ? subject : "";
        this.fullSubject = fullSubject != null ? fullSubject : "";
        this.professor = professor != null ? professor : "";
        this.classroom = classroom != null ? classroom : "";
        this.group = group != null ? group : "";
        this.badge = badge != null ? badge : "";
        this.isEvent = isEvent;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFullSubject() {
        return fullSubject;
    }

    public void setFullSubject(String fullSubject) {
        this.fullSubject = fullSubject;
    }

    public String getProfessor() {
        return professor;
    }

    public void setProfessor(String professor) {
        this.professor = professor;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public boolean isEvent() {
        return isEvent;
    }

    public void setEvent(boolean event) {
        isEvent = event;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("subject", subject);
        json.put("fullSubject", fullSubject);
        json.put("professor", professor);
        json.put("classroom", classroom);
        json.put("group", group);
        json.put("badge", badge);
        json.put("isEvent", isEvent);
        return json;
    }

    public static ClassItem fromJson(JSONObject json) {
        if (json == null) return new ClassItem();
        return new ClassItem(
                json.optString("subject", ""),
                json.optString("fullSubject", ""),
                json.optString("professor", ""),
                json.optString("classroom", ""),
                json.optString("group", ""),
                json.optString("badge", ""),
                json.optBoolean("isEvent", false)
        );
    }
}
