package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TodoService {

    private final TodoMapper todoMapper;

    public TodoService(TodoMapper todoMapper) {
        this.todoMapper = todoMapper;
    }

    public List<Todo> search(String keyword, String category, String order) {
        return search(keyword, category, order, null, null);
    }

    public List<Todo> search(String keyword, String category, String order, LocalDate from, LocalDate to) {
        return todoMapper.search(keyword, category, order, from, to);
    }

    public long countForList(String keyword, String category, boolean showCompleted) {
        return todoMapper.countForList(keyword, category, showCompleted);
    }

    public List<Todo> searchPage(String keyword, String category, String order,
            boolean showCompleted, int limit, long offset) {
        return todoMapper.searchPage(keyword, category, order, showCompleted, limit, offset);
    }

    public List<Todo> searchForExport(String keyword, String category, String order,
            boolean showCompleted) {
        return todoMapper.searchForExport(keyword, category, order, showCompleted);
    }

    public Todo findById(Long id) {
        return todoMapper.findById(id);
    }

    public void create(Todo todo) {
        todoMapper.insert(todo);
        log.info("登録 id={}", todo.getId());
    }

    public void update(Todo todo) {
        todoMapper.update(todo);
        log.info("編集 id={}", todo.getId());
    }

    public boolean complete(Long id) {
        boolean completed = todoMapper.complete(id) > 0;
        if (completed) {
            log.info("完了 id={}", id);
        }
        return completed;
    }

    public void delete(Long id) {
        todoMapper.deleteById(id);
        log.info("削除 id={}", id);
    }
}
