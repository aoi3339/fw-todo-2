package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TodoMapper {

    List<Todo> search(@Param("keyword") String keyword,
            @Param("category") String category,
            @Param("order") String order,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    long countForList(@Param("keyword") String keyword,
            @Param("category") String category,
            @Param("showCompleted") boolean showCompleted);

    List<Todo> searchPage(@Param("keyword") String keyword,
            @Param("category") String category,
            @Param("order") String order,
            @Param("showCompleted") boolean showCompleted,
            @Param("limit") int limit,
            @Param("offset") long offset);

    List<Todo> searchForExport(@Param("keyword") String keyword,
            @Param("category") String category,
            @Param("order") String order,
            @Param("showCompleted") boolean showCompleted);

    Todo findById(Long id);

    long countTrash(@Param("keyword") String keyword, @Param("category") String category);

    List<Todo> searchTrashPage(@Param("keyword") String keyword,
            @Param("category") String category,
            @Param("order") String order,
            @Param("limit") int limit,
            @Param("offset") long offset);

    int restore(Long id);

    void insert(Todo todo);

    void update(Todo todo);

    int complete(Long id);

    void deleteById(Long id);
}
