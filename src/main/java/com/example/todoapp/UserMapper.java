package com.example.todoapp;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("SELECT id, username, password, role FROM users WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT COUNT(*) FROM users")
    long countUsers();

    // The fixed primary key makes simultaneous first-time setup submissions mutually exclusive.
    @Insert("INSERT INTO users (id, username, password, role) VALUES (1, #{username}, #{password}, 'USER')")
    void insertInitialUser(@Param("username") String username, @Param("password") String password);
}
