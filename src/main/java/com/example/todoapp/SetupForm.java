package com.example.todoapp;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupForm {
    @NotBlank(message = "ユーザー名を入力してください")
    @Size(max = 50, message = "ユーザー名は50文字以内で入力してください")
    @Pattern(regexp = "\\S+", message = "ユーザー名に空白は使えません")
    private String username;

    @NotBlank(message = "パスワードを入力してください")
    @Size(min = 8, max = 72, message = "パスワードは8文字以上、72文字以内にしてください")
    private String password;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String passwordConfirmation;

    @AssertTrue(message = "確認用パスワードが一致しません")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(passwordConfirmation);
    }

    @AssertTrue(message = "パスワードが長すぎます。日本語などを使う場合は文字数を減らしてください（UTF-8で72バイト以内）")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
