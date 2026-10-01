package com.example.eassistent;

import com.example.eassistent.model.ClassItem;
import com.example.eassistent.model.DaySchedule;
import com.example.eassistent.model.PeriodSchedule;
import com.example.eassistent.model.ScheduleData;
import com.example.eassistent.parser.UrnikParser;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class UrnikParserTest {

    @Test
    public void testUrnikParserDirect() throws Exception {
        File file = new File("src/urnik.txt");
        assertTrue("urnik.txt exists", file.exists());
        String html = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        ScheduleData data = UrnikParser.parse(html, "https://urniki.easistent.com/test");
        assertNotNull(data);
        assertEquals("Šolski center Nova Gorica - Elektrotehniška in računalniška šola", data.getSchoolTitle());
        assertEquals("4. RB (10383463)", data.getClassName());
        assertEquals("Teden 5: 28. 9. - 4. 10.", data.getWeekText());
        assertEquals(5, data.getDays().size());

        // Check Monday
        DaySchedule mon = data.getDays().get(0);
        assertEquals("Ponedeljek", mon.getDayName());
        assertEquals("28. 9.", mon.getDateText());

        // Check hour 1 on Monday (MAT, M. Petrovič, E34)
        PeriodSchedule p1 = mon.getPeriods().get(1);
        assertEquals("1. ura", p1.getHourName());
        assertEquals(1, p1.getItems().size());
        ClassItem mat = p1.getItems().get(0);
        assertEquals("MAT", mat.getSubject());
        assertEquals("M. Petrovič", mat.getProfessor());
        assertEquals("E34", mat.getClassroom());

        // Check JSON serialization and deserialization
        String json = data.toJsonString();
        assertNotNull(json);
        assertTrue(json.length() > 500);

        ScheduleData restored = ScheduleData.fromJsonString(json);
        assertNotNull(restored);
        assertEquals(data.getSchoolTitle(), restored.getSchoolTitle());
        assertEquals(data.getClassName(), restored.getClassName());
        assertEquals(data.getDays().size(), restored.getDays().size());
        assertEquals(data.getDays().get(0).getPeriods().get(1).getItems().get(0).getProfessor(),
                restored.getDays().get(0).getPeriods().get(1).getItems().get(0).getProfessor());

        System.out.println("JSON size: " + json.length() + " bytes");
    }
}
