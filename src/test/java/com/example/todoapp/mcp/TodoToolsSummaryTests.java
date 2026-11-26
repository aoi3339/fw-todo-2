package com.example.todoapp.mcp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.example.todoapp.Todo;
import com.example.todoapp.TodoService;
import com.example.todoapp.api.HolidayClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TodoToolsSummaryTests {

    private final TodoService service = mock(TodoService.class);
    private final TodoTools tools = new TodoTools(service, mock(HolidayClient.class));

    @Test
    void countsCategoriesAndOnlyOverdueIncompleteItems() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        LocalDate from = today.minusDays(7);
        LocalDate to = today.plusDays(7);
        when(service.search(null, null, "asc", from, to)).thenReturn(List.of(
                todo("デザイン", today.minusDays(1), false),
                todo("デザイン", today.minusDays(1), true),
                todo("資格", today, false),
                todo("資格", today.plusDays(1), false)));
        var result = tools.summarizeWeek(from.toString(), to.toString());
        assertEquals(4, result.totalCount());
        assertEquals(Map.of("デザイン", 2L, "資格", 2L), result.categoryCounts());
        assertEquals(1, result.overdueCount());
    }

    @Test
    void omittedArgumentsUseCurrentWeekInJapan() {
        LocalDate monday = LocalDate.now(ZoneId.of("Asia/Tokyo")).with(DayOfWeek.MONDAY);
        when(service.search(null, null, "asc", monday, monday.plusDays(6))).thenReturn(List.of());
        assertEquals(new TodoTools.WeekSummary(0, Map.of(), 0), tools.summarizeWeek(null, null));
        verify(service).search(null, null, "asc", monday, monday.plusDays(6));
    }

    @Test
    void eitherBoundaryCanBeOmitted() {
        LocalDate from = LocalDate.of(2026, 4, 6);
        LocalDate to = LocalDate.of(2026, 4, 12);
        when(service.search(null, null, "asc", from, to)).thenReturn(List.of());
        tools.summarizeWeek("2026-04-06", null);
        tools.summarizeWeek(null, "2026-04-12");
        verify(service, times(2)).search(null, null, "asc", from, to);
    }

    @Test
    void rejectsReversedOrInvalidDatesWithoutSearching() {
        assertThrows(IllegalArgumentException.class,
                () -> tools.summarizeWeek("2026-04-12", "2026-04-06"));
        assertThrows(java.time.format.DateTimeParseException.class,
                () -> tools.summarizeWeek("2026-02-30", null));
        verifyNoInteractions(service);
    }

    private static Todo todo(String category, LocalDate dueDate, boolean completed) {
        Todo todo = new Todo();
        todo.setCategory(category);
        todo.setDueDate(dueDate);
        todo.setCompleted(completed);
        return todo;
    }
}
