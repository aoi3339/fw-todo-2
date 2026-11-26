package com.example.todoapp;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AttachmentMapper {
    @Select("SELECT id FROM todos WHERE id = #{todoId} AND deleted_at IS NULL FOR UPDATE")
    Long lockActiveTodo(Long todoId);

    @Select("""
            SELECT a.* FROM todo_attachments a JOIN todos t ON t.id = a.todo_id
            WHERE a.todo_id = #{todoId} AND t.deleted_at IS NULL
            ORDER BY a.uploaded_at DESC, a.id DESC
            """)
    List<TodoAttachment> findByTodoId(Long todoId);

    @Select("""
            SELECT a.* FROM todo_attachments a JOIN todos t ON t.id = a.todo_id
            WHERE a.id = #{id} AND a.todo_id = #{todoId} AND t.deleted_at IS NULL
            """)
    TodoAttachment findVisibleById(@Param("todoId") Long todoId, @Param("id") Long id);

    @Insert("""
            INSERT INTO todo_attachments
            (todo_id, original_filename, stored_filename, content_type, file_size)
            VALUES (#{todoId}, #{originalFilename}, #{storedFilename}, #{contentType}, #{fileSize})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TodoAttachment attachment);

    @Delete("DELETE FROM todo_attachments WHERE id = #{id} AND todo_id = #{todoId}")
    int delete(@Param("todoId") Long todoId, @Param("id") Long id);
}
