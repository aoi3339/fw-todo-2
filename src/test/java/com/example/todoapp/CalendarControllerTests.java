package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.*;

class CalendarControllerTests {

    private final CalendarController controller = new CalendarController();

    @Test
    void weekStartsOnSundayAndContainsExactlySevenDays() {
        var model = new ExtendedModelMap();
        assertEquals("calendar", controller.calendar(null, null, "week", LocalDate.of(2026, 4, 6), model));
        assertEquals(LocalDate.of(2026, 4, 5), model.get("dataFrom"));
        assertEquals(LocalDate.of(2026, 4, 11), model.get("dataTo"));
        List<?> weeks = (List<?>) model.get("weeks");
        assertEquals(1, weeks.size());
        List<?> days = (List<?>) weeks.get(0);
        assertEquals(7, days.size());
        for (int day = 0; day < 7; day++) {
            assertEquals(LocalDate.of(2026, 4, 5).plusDays(day), days.get(day));
        }
    }

    @Test
    void sundayDoesNotMoveToPreviousWeek() {
        var model = new ExtendedModelMap();
        controller.calendar(null, null, "week", LocalDate.of(2026, 4, 5), model);
        assertEquals(LocalDate.of(2026, 4, 5), model.get("dataFrom"));
        assertEquals(LocalDate.of(2026, 3, 29), model.get("previousWeek"));
        assertEquals(LocalDate.of(2026, 4, 12), model.get("nextWeek"));
    }

    @Test
    void weekCanCrossYearBoundary() {
        var model = new ExtendedModelMap();
        controller.calendar(null, null, "week", LocalDate.of(2026, 1, 1), model);
        assertEquals(LocalDate.of(2025, 12, 28), model.get("dataFrom"));
        assertEquals(LocalDate.of(2026, 1, 3), model.get("dataTo"));
        assertEquals(2026, model.get("year"));
        assertEquals(1, model.get("month"));
    }

    @Test
    void monthKeepsFullMonthRangeAndEmptyCellPositions() {
        var model = new ExtendedModelMap();
        controller.calendar(2026, 4, "month", null, model);
        assertEquals(false, model.get("weekView"));
        assertEquals(LocalDate.of(2026, 4, 1), model.get("dataFrom"));
        assertEquals(LocalDate.of(2026, 4, 30), model.get("dataTo"));
        List<?> weeks = (List<?>) model.get("weeks");
        assertEquals(5, weeks.size());
        List<?> firstWeek = (List<?>) weeks.get(0);
        assertNull(firstWeek.get(0));
        assertNull(firstWeek.get(1));
        assertNull(firstWeek.get(2));
        assertEquals(LocalDate.of(2026, 4, 1), firstWeek.get(3));
        List<?> lastWeek = (List<?>) weeks.get(4);
        assertEquals(LocalDate.of(2026, 4, 30), lastWeek.get(4));
        assertNull(lastWeek.get(5));
        assertNull(lastWeek.get(6));
    }

    @Test
    void switchingBackToMonthUsesTheSelectedDate() {
        var model = new ExtendedModelMap();
        LocalDate date = LocalDate.of(2026, 12, 31);
        controller.calendar(null, null, "month", date, model);
        assertEquals(2026, model.get("year"));
        assertEquals(12, model.get("month"));
        assertEquals(date, model.get("selectedDate"));
        assertEquals(LocalDate.of(2026, 12, 1), model.get("dataFrom"));
        assertEquals(LocalDate.of(2026, 12, 31), model.get("dataTo"));
    }
}
