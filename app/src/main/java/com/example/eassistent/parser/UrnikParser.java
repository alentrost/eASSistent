package com.example.eassistent.parser;

import com.example.eassistent.model.ClassItem;
import com.example.eassistent.model.DaySchedule;
import com.example.eassistent.model.PeriodSchedule;
import com.example.eassistent.model.ScheduleData;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class UrnikParser {

    public static ScheduleData parse(String html, String sourceUrl) {
        if (html == null || html.trim().isEmpty()) {
            return null;
        }

        Document doc = Jsoup.parse(html);
        ScheduleData schedule = new ScheduleData();
        schedule.setSourceUrl(sourceUrl != null ? sourceUrl : "");
        schedule.setLastUpdatedMillis(System.currentTimeMillis());

        // 1. School Title
        String rawTitle = doc.title();
        String schoolTitle = "";
        if (rawTitle != null && rawTitle.contains("::")) {
            String[] parts = rawTitle.split("::");
            schoolTitle = parts[parts.length - 1].trim();
        } else if (rawTitle != null) {
            schoolTitle = rawTitle.trim();
        }
        schedule.setSchoolTitle(schoolTitle);

        // 2. Class Name
        Element selectedClass = doc.selectFirst("#id_parameter option[selected]");
        String className = selectedClass != null ? selectedClass.text().trim() : "";
        Element selectedStudent = doc.selectFirst("#id_dijak option[selected]");
        if (selectedStudent != null && !selectedStudent.val().equals("0")) {
            String dijakText = selectedStudent.text().trim();
            if (!className.isEmpty()) {
                className += " (" + dijakText + ")";
            } else {
                className = dijakText;
            }
        }
        schedule.setClassName(className);

        // 3. Week Name / Range
        Element weekToggle = doc.selectFirst("#tedni-toggle");
        String weekText = "";
        if (weekToggle != null) {
            weekText = weekToggle.text().replaceAll("\\s+", " ").trim();
        }
        schedule.setWeekText(weekText);

        // 4. Day Headers
        Elements dayHeaders = doc.select("table.ednevnik-seznam_ur_teden thead tr th");
        List<DaySchedule> days = new ArrayList<>();
        // Index 0 is the hour label th, indices 1..5 are days
        for (int i = 1; i < dayHeaders.size() && i <= 5; i++) {
            Element th = dayHeaders.get(i);
            String dayName = th.select(".days").text().trim();
            String dateText = th.select(".date").text().trim();
            boolean isToday = th.hasClass("ednevnik-seznam_ur_teden-td-danes");

            DaySchedule day = new DaySchedule(i - 1, dayName, dateText, isToday);
            days.add(day);
        }

        // Fallback if no days found in thead
        if (days.isEmpty()) {
            String[] defaultDays = {"Ponedeljek", "Torek", "Sreda", "Četrtek", "Petek"};
            for (int i = 0; i < defaultDays.length; i++) {
                days.add(new DaySchedule(i, defaultDays[i], "", false));
            }
        }

        // 5. Hour Rows & Cells
        Elements rows = doc.select("table.ednevnik-seznam_ur_teden tbody tr");
        List<String> hourNames = new ArrayList<>();
        List<String> hourTimes = new ArrayList<>();

        int hourIdx = 0;
        for (Element row : rows) {
            Element th = row.selectFirst("th");
            if (th == null) continue;

            String hourName = th.select(".naziv-ure").text().trim();
            String hourTime = th.select(".potek-ure").text().trim();
            if (hourName.isEmpty()) continue;

            hourNames.add(hourName);
            hourTimes.add(hourTime);

            Elements cells = row.select("td.ednevnik-seznam_ur_teden-td");
            for (int d = 0; d < days.size() && d < cells.size(); d++) {
                Element cell = cells.get(d);
                DaySchedule day = days.get(d);

                if (cell.hasClass("ednevnik-seznam_ur_teden-td-danes")) {
                    day.setToday(true);
                }

                PeriodSchedule period = new PeriodSchedule(hourIdx, hourName, hourTime);

                // Screen-reader text for accessibility and full details
                Element srSpan = cell.selectFirst(".public-urnik-sr-only");
                String srText = srSpan != null ? srSpan.text().trim() : "";

                Elements blocks = cell.select(".ednevnik-seznam_ur_teden-blok-wrap");
                for (Element block : blocks) {
                    ClassItem item = parseBlock(block, srText);
                    period.addItem(item);
                }

                day.addPeriod(period);
            }
            hourIdx++;
        }

        for (DaySchedule day : days) {
            schedule.addDay(day);
        }
        schedule.setHourNames(hourNames);
        schedule.setHourTimes(hourTimes);

        return schedule;
    }

    private static ClassItem parseBlock(Element block, String srText) {
        ClassItem item = new ClassItem();

        // 1. Badge / Tag
        Element badgeEl = block.selectFirst(".wl-tag-xs");
        String badge = badgeEl != null ? badgeEl.text().trim() : "";
        item.setBadge(badge);

        boolean isEvent = "DOG".equalsIgnoreCase(badge) ||
                block.hasClass("ednevnik-seznam_ur_teden-td-dogodek") ||
                (srText != null && srText.toLowerCase().contains("dogodek"));
        item.setEvent(isEvent);

        // 2. Subject Short Name
        String subject = "";
        Element subjSpan = block.selectFirst(".ednevnik-title span");
        if (subjSpan != null) {
            subject = subjSpan.text().trim();
        } else {
            Element titleEl = block.selectFirst(".ednevnik-title");
            if (titleEl != null) {
                subject = titleEl.text().trim();
            }
        }
        item.setSubject(subject);

        // 3. Subtitles (Professor, Classroom, Group)
        Elements subtitles = block.select(".ednevnik-subtitle");
        for (int i = 0; i < subtitles.size(); i++) {
            String text = subtitles.get(i).text().trim();
            if (text.isEmpty()) continue;

            if (text.toLowerCase().contains("skupina")) {
                item.setGroup(text);
            } else if (text.contains(",")) {
                // Typical: "M. Petrovič, E34"
                String[] parts = text.split(",", 2);
                item.setProfessor(parts[0].trim());
                item.setClassroom(parts[1].trim());
            } else if (isEvent && item.getClassroom().isEmpty()) {
                // In events: subtitle often has classroom like "NP-05"
                item.setClassroom(text);
            } else if (item.getProfessor().isEmpty()) {
                item.setProfessor(text);
            } else if (item.getClassroom().isEmpty()) {
                item.setClassroom(text);
            }
        }

        // 4. Parse full subject and full professor from srText if available
        if (srText != null && !srText.isEmpty()) {
            parseSrDetails(srText, item);
        }

        return item;
    }

    private static void parseSrDetails(String srText, ClassItem item) {
        try {
            // Example:
            // "Četrtek prvi deseti. osma ura od 13 ur 30 do 14 ur 15. Računalniški produkti in storitve, praksa. profesor Marko Marčetić. učilnica E19"
            // Or: "Torek devetindvajseti deveti. sesta ura od 11 ur 50 do 12 ur 35. dogodek. Predstavitev maturantskega plesa. ucilnice NP-05"

            if (item.getFullSubject().isEmpty()) {
                int odDoIdx = srText.indexOf(" do ");
                if (odDoIdx != -1) {
                    int endDotIdx = srText.indexOf(".", odDoIdx);
                    if (endDotIdx != -1 && endDotIdx + 1 < srText.length()) {
                        String afterTime = srText.substring(endDotIdx + 1).trim();
                        // Cut at "profesor" or "učilnic" or "dogodek"
                        String fullSubj = afterTime;
                        int profIdx = fullSubj.toLowerCase().indexOf("profesor");
                        int ucilIdx = fullSubj.toLowerCase().indexOf("učilnic");
                        if (ucilIdx == -1) ucilIdx = fullSubj.toLowerCase().indexOf("ucilnic");

                        int cutIdx = -1;
                        if (profIdx != -1 && ucilIdx != -1) {
                            cutIdx = Math.min(profIdx, ucilIdx);
                        } else if (profIdx != -1) {
                            cutIdx = profIdx;
                        } else if (ucilIdx != -1) {
                            cutIdx = ucilIdx;
                        }

                        if (cutIdx != -1) {
                            fullSubj = fullSubj.substring(0, cutIdx).trim();
                        }
                        if (fullSubj.endsWith(".")) {
                            fullSubj = fullSubj.substring(0, fullSubj.length() - 1).trim();
                        }
                        if (fullSubj.toLowerCase().startsWith("dogodek.")) {
                            fullSubj = fullSubj.substring(8).trim();
                        }
                        if (!fullSubj.isEmpty()) {
                            item.setFullSubject(fullSubj);
                        }
                    }
                }
            }

            // Extract full professor name if item has none or only abbreviated
            int profKeyword = srText.toLowerCase().indexOf("profesor ");
            if (profKeyword != -1) {
                String afterProf = srText.substring(profKeyword + 9).trim();
                int dotIdx = afterProf.indexOf(".");
                String fullProf = dotIdx != -1 ? afterProf.substring(0, dotIdx).trim() : afterProf.trim();
                if (!fullProf.isEmpty()) {
                    // If we have "M. Petrovič" and full is "Marko Marčetić", full name is very nice to display or keep!
                    if (item.getProfessor().isEmpty()) {
                        item.setProfessor(fullProf);
                    }
                }
            }

            // Extract classroom if missing
            if (item.getClassroom().isEmpty()) {
                int roomKeyword = srText.toLowerCase().indexOf("učilnica ");
                if (roomKeyword == -1) roomKeyword = srText.toLowerCase().indexOf("ucilnica ");
                if (roomKeyword == -1) roomKeyword = srText.toLowerCase().indexOf("učilnice ");
                if (roomKeyword == -1) roomKeyword = srText.toLowerCase().indexOf("ucilnice ");

                if (roomKeyword != -1) {
                    int start = srText.indexOf(" ", roomKeyword) + 1;
                    String afterRoom = srText.substring(start).trim();
                    int end = afterRoom.indexOf(".");
                    String room = end != -1 ? afterRoom.substring(0, end).trim() : afterRoom.trim();
                    if (!room.isEmpty()) {
                        item.setClassroom(room);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
