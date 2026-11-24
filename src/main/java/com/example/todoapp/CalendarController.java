package com.example.todoapp;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CalendarController {

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month, Model model) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth selectedMonth = YearMonth.of(
                year == null ? currentMonth.getYear() : year,
                month == null ? currentMonth.getMonthValue() : month);

        LocalDate firstDay = selectedMonth.atDay(1);
        LocalDate lastDay = selectedMonth.atEndOfMonth();
        int leadingEmptyCells = firstDay.getDayOfWeek().getValue() % 7;
        int cellCount = ((leadingEmptyCells + selectedMonth.lengthOfMonth() + 6) / 7) * 7;

        List<LocalDate> cells = new ArrayList<>();
        for (int position = 0; position < cellCount; position++) {
            int dayOfMonth = position - leadingEmptyCells + 1;
            cells.add(dayOfMonth < 1 || dayOfMonth > selectedMonth.lengthOfMonth()
                    ? null
                    : selectedMonth.atDay(dayOfMonth));
        }

        List<List<LocalDate>> weeks = new ArrayList<>();
        for (int start = 0; start < cells.size(); start += 7) {
            weeks.add(cells.subList(start, start + 7));
        }

        YearMonth previousMonth = selectedMonth.minusMonths(1);
        YearMonth nextMonth = selectedMonth.plusMonths(1);
        model.addAttribute("year", selectedMonth.getYear());
        model.addAttribute("month", selectedMonth.getMonthValue());
        model.addAttribute("weeks", weeks);
        model.addAttribute("weekdays", List.of("日", "月", "火", "水", "木", "金", "土"));
        model.addAttribute("dataFrom", firstDay);
        model.addAttribute("dataTo", lastDay);
        model.addAttribute("previousYear", previousMonth.getYear());
        model.addAttribute("previousMonth", previousMonth.getMonthValue());
        model.addAttribute("nextYear", nextMonth.getYear());
        model.addAttribute("nextMonth", nextMonth.getMonthValue());
        return "calendar";
    }
}
