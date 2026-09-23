package io.roadmap.todolistmonorepo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 4, max = 30, message = "Имя от 6 до 30 символов")
        String login,

        @NotBlank(message = "Mail is required")
        @Email(message = "Некорректный email")
        String email,

        //TODO усилить проверку пароля регуляркой и длиной
        @NotBlank(message = "Password is required")
        @Size(min = 3, max = 30, message = "Пароль от 6 до 30 символов")
        String password
) {
}
