package com.example.todoapp.mcp;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.example.todoapp.Todo;
import com.example.todoapp.TodoService;
import com.example.todoapp.api.TodoDto;
import com.example.todoapp.api.HolidayClient;

@Component
public class TodoTools {

    private final TodoService todoService;
    private final HolidayClient holidayClient;
    private static final Set<String> CATEGORIES = Set.of(
            "デザイン", "マーケティング", "プログラミング", "資格", "就職活動");

    public TodoTools(TodoService todoService, HolidayClient holidayClient) {
        this.todoService = todoService;
        this.holidayClient = holidayClient;
    }

    @McpTool(name = "list_todos", description = "やることの一覧を返す（期間・ジャンルで絞れる）")
    public List<TodoDto> listTodos(
            @McpToolParam(description = "やることの名前の一部で絞り込むキーワード", required = false) String keyword,
            @McpToolParam(description = "ジャンルで絞り込む条件", required = false) String category,
            @McpToolParam(description = "開始日（yyyy-MM-dd）", required = false) String from,
            @McpToolParam(description = "終了日（yyyy-MM-dd）", required = false) String to) {
        LocalDate fromDate = from == null ? null : LocalDate.parse(from);
        LocalDate toDate = to == null ? null : LocalDate.parse(to);

        return todoService.search(keyword, category, "asc", fromDate, toDate)
                .stream()
                .map(TodoDto::from)
                .toList();
    }

    @McpTool(name = "get_todo", description = "やることを1件返す")
    public TodoDto getTodo(
            @McpToolParam(description = "取得するやることのID", required = true) Long id) {
        Todo todo = todoService.findById(id);
        return todo == null ? null : TodoDto.from(todo);
    }

    @McpTool(name = "add_todo", description = "やることを1件足す")
    public TodoDto addTodo(
            @McpToolParam(description = "やることの名前", required = true) String title,
            @McpToolParam(description = "ジャンル（デザイン・マーケティング・プログラミング・資格・就職活動）", required = true) String category,
            @McpToolParam(description = "優先度（1:高、2:中、3:低）", required = true) Integer priority,
            @McpToolParam(description = "メモ", required = false) String detail,
            @McpToolParam(description = "期限（yyyy-MM-dd）。省略すると期限なし", required = false) String dueDate) {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setCategory(category);
        todo.setPriority(priority);
        todo.setDetail(detail);
        todo.setDueDate(dueDate == null ? null : LocalDate.parse(dueDate));
        todo.setCompleted(false);
        validateTodo(todo);
        todoService.create(todo);
        return TodoDto.from(todoService.findById(todo.getId()));
    }

    @McpTool(name = "update_todo", description = "やることを1件直す（期限を変えるのもこれ）")
    public TodoDto updateTodo(
            @McpToolParam(description = "変更するやることのID", required = true) Long id,
            @McpToolParam(description = "新しい名前。省略すると変更しない", required = false) String title,
            @McpToolParam(description = "新しいジャンル（デザイン・マーケティング・プログラミング・資格・就職活動）。省略すると変更しない", required = false) String category,
            @McpToolParam(description = "新しい優先度（1:高、2:中、3:低）。省略すると変更しない", required = false) Integer priority,
            @McpToolParam(description = "新しいメモ。空文字で消す。省略すると変更しない", required = false) String detail,
            @McpToolParam(description = "新しい期限（yyyy-MM-dd）。空文字で期限なし。省略すると変更しない", required = false) String dueDate) {
        Todo todo = requireTodo(id);
        if (title != null) todo.setTitle(title);
        if (category != null) todo.setCategory(category);
        if (priority != null) todo.setPriority(priority);
        if (detail != null) todo.setDetail(detail);
        if (dueDate != null) {
            todo.setDueDate(dueDate.isBlank() ? null : LocalDate.parse(dueDate));
        }
        validateTodo(todo);
        todoService.update(todo);
        return TodoDto.from(todoService.findById(id));
    }

    @McpTool(name = "complete_todo", description = "やることを完了にする")
    public TodoDto completeTodo(
            @McpToolParam(description = "完了にするやることのID", required = true) Long id) {
        Todo todo = requireTodo(id);
        todo.setCompleted(true);
        todoService.update(todo);
        return TodoDto.from(todoService.findById(id));
    }

    @McpTool(name = "delete_todo", description = "やることを1件消す")
    public String deleteTodo(
            @McpToolParam(description = "削除するやることのID", required = true) Long id) {
        requireTodo(id);
        todoService.delete(id);
        return "やること（ID: " + id + "）を削除しました。";
    }

    @McpTool(name = "find_free_days", description = "期間の中で、期限のやることが無く、土日でも祝日でもない「空いている日」を返す。やることの期限を動かす先を決めるのに使う")
    public List<LocalDate> findFreeDays(
            @McpToolParam(description = "開始日（yyyy-MM-dd、この日を含む）", required = true) String from,
            @McpToolParam(description = "終了日（yyyy-MM-dd、この日を含む）", required = true) String to) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("開始日は終了日以前にしてください。");
        }
        Set<LocalDate> busyDays = todoService.search(null, null, "asc", fromDate, toDate)
                .stream()
                .map(Todo::getDueDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        HolidayClient.HolidayResult result = holidayClient.getHolidays();
        if (result.unavailable() || result.holidays() == null) {
            throw new IllegalStateException("祝日を取得できないため、空いている日を確認できません。");
        }
        return fromDate.datesUntil(toDate.plusDays(1))
                .filter(day -> !busyDays.contains(day))
                .filter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY
                        && day.getDayOfWeek() != DayOfWeek.SUNDAY)
                .filter(day -> !result.holidays().containsKey(day.toString()))
                .toList();
    }

    private Todo requireTodo(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("IDを指定してください。");
        }
        Todo todo = todoService.findById(id);
        if (todo == null) {
            throw new IllegalArgumentException("指定されたやることが見つかりません（ID: " + id + "）。");
        }
        return todo;
    }

    private void validateTodo(Todo todo) {
        if (todo.getTitle() == null || todo.getTitle().isBlank() || todo.getTitle().length() > 255) {
            throw new IllegalArgumentException("やることの名前は1〜255文字で入力してください。");
        }
        if (todo.getCategory() == null || !CATEGORIES.contains(todo.getCategory())) {
            throw new IllegalArgumentException("ジャンルはデザイン・マーケティング・プログラミング・資格・就職活動から選んでください。");
        }
        if (todo.getPriority() == null || todo.getPriority() < 1 || todo.getPriority() > 3) {
            throw new IllegalArgumentException("優先度は1（高）・2（中）・3（低）から選んでください。");
        }
        if (todo.getDetail() != null && todo.getDetail().length() > 255) {
            throw new IllegalArgumentException("メモは255文字以内で入力してください。");
        }
    }
}
