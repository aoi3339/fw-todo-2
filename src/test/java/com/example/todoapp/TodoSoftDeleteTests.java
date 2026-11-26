package com.example.todoapp;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.example.todoapp.api.TodoApiController;
import com.example.todoapp.api.TodoRequest;
import com.example.todoapp.mcp.TodoTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TodoSoftDeleteTests {

    @Autowired private TodoService service;
    @Autowired private TodoMapper mapper;
    @Autowired private TodoTools tools;
    @Autowired private TodoApiController api;
    @Autowired private HomeController home;

    private final String marker = "soft-delete-check-" + UUID.randomUUID();
    private final LocalDate dueDate = LocalDate.now(ZoneId.of("Asia/Tokyo")).minusDays(1);

    @Test
    void deletedRowRemainsInTrashButDisappearsFromEveryNormalRead() {
        var summaryBefore = tools.summarizeWeek(dueDate.toString(), dueDate.toString());
        Todo todo = create(marker, false, dueDate, "デザイン");
        service.delete(todo.getId());

        List<Todo> trash = service.searchTrashPage(marker, "", "asc", 10, 0);
        assertEquals(1, trash.size());
        assertEquals(todo.getId(), trash.get(0).getId());
        assertNotNull(trash.get(0).getDeletedAt());
        assertNull(service.findById(todo.getId()));
        assertTrue(service.search(marker, "", "asc").isEmpty());
        assertTrue(service.search(null, null, "asc", dueDate, dueDate).stream()
                .noneMatch(item -> item.getId().equals(todo.getId())));
        assertEquals(0, service.countForList(marker, "", true));
        assertTrue(service.searchPage(marker, "", "asc", true, 10, 0).isEmpty());
        assertTrue(service.searchForExport(marker, "", "asc", true).isEmpty());
        assertTrue(api.list(marker, "", "asc", null, null).isEmpty());
        assertEquals(404, api.get(todo.getId()).getStatusCode().value());
        assertFalse(new String(api.exportCsv(marker, "", "asc", true).getBody(),
                StandardCharsets.UTF_8).contains(marker));
        assertNull(tools.getTodo(todo.getId()));
        assertTrue(tools.listTodos(marker, null, null, null).isEmpty());
        assertEquals(summaryBefore, tools.summarizeWeek(dueDate.toString(), dueDate.toString()));

        var normalModel = new ExtendedModelMap();
        home.todos(marker, "", "asc", true, 1, 0, normalModel);
        assertTrue(((List<?>) normalModel.get("todos")).isEmpty());
        var trashModel = new ExtendedModelMap();
        home.todos(marker, "", "asc", false, 1, 1, trashModel);
        assertEquals(1, ((List<?>) trashModel.get("todos")).size());
        assertEquals(true, trashModel.get("trashView"));
        var otherModel = new ExtendedModelMap();
        home.todos(marker, "", "asc", true, 1, 2, otherModel);
        assertTrue(((List<?>) otherModel.get("todos")).isEmpty());
    }

    @Test
    void restoreClearsTheMarkAndPreservesIdDetailsAndCompletion() {
        Todo todo = create(marker, true, dueDate, "デザイン");
        service.delete(todo.getId());
        assertTrue(service.restore(todo.getId()));
        Todo restored = service.findById(todo.getId());
        assertEquals(todo.getId(), restored.getId());
        assertEquals(marker, restored.getTitle());
        assertEquals("verification detail", restored.getDetail());
        assertEquals(dueDate, restored.getDueDate());
        assertEquals(true, restored.getCompleted());
        assertNull(restored.getDeletedAt());
        assertEquals(0, service.countTrash(marker, ""));
        assertEquals(1, service.countForList(marker, "", true));
        assertFalse(service.restore(todo.getId()));
    }

    @Test
    void deletedItemsCannotBeEditedOrCompletedThroughNormalPaths() {
        Todo todo = create(marker, false, dueDate, "デザイン");
        service.delete(todo.getId());
        todo.setTitle("changed title");
        service.update(todo);
        assertFalse(service.complete(todo.getId()));
        assertEquals(404, api.replace(todo.getId(), new TodoRequest()).getStatusCode().value());
        assertEquals(404, api.delete(todo.getId()).getStatusCode().value());
        assertThrows(IllegalArgumentException.class, () -> tools.completeTodo(todo.getId()));
        assertThrows(IllegalArgumentException.class, () -> tools.deleteTodo(todo.getId()));
        Todo deleted = service.searchTrashPage(marker, "", "asc", 10, 0).get(0);
        assertEquals(marker, deleted.getTitle());
        assertFalse(deleted.getCompleted());
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void trashShowsBothCompletionStatesAndKeepsFilteringSortingAndPaging() {
        for (int i = 0; i < 12; i++) {
            Todo todo = create(marker + "-" + i, i % 2 == 0, dueDate.plusDays(i),
                    i % 2 == 0 ? "デザイン" : "資格");
            service.delete(todo.getId());
        }
        assertEquals(12, service.countTrash(marker, ""));
        assertEquals(6, service.countTrash(marker, "デザイン"));
        List<Todo> firstPage = service.searchTrashPage(marker, "", "asc", 10, 0);
        List<Todo> secondPage = service.searchTrashPage(marker, "", "asc", 10, 10);
        assertEquals(10, firstPage.size());
        assertEquals(2, secondPage.size());
        assertEquals(dueDate, firstPage.get(0).getDueDate());
        assertEquals(dueDate.plusDays(11), secondPage.get(1).getDueDate());
        assertTrue(firstPage.stream().anyMatch(Todo::getCompleted));
        assertTrue(firstPage.stream().anyMatch(item -> !item.getCompleted()));
        assertEquals(dueDate.plusDays(11),
                service.searchTrashPage(marker, "", "desc", 10, 0).get(0).getDueDate());
        assertEquals(0, service.countForList(marker, "", true));
    }

    private Todo create(String title, boolean completed, LocalDate dueDate, String category) {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setDetail("verification detail");
        todo.setCategory(category);
        todo.setPriority(2);
        todo.setDueDate(dueDate);
        todo.setCompleted(completed);
        service.create(todo);
        return todo;
    }
}
