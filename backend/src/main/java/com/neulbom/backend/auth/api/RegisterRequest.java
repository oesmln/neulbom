package com.neulbom.backend.auth.api;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @JsonProperty("email") String email,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty("password") String password,
        @NotBlank @Size(max = 100) @JsonProperty("name") String name,
        @NotBlank @Pattern(regexp = "elder|guardian") @JsonProperty("role") String role,
        @JsonProperty("birth_date") LocalDate birthDate,
        @Pattern(regexp = "60s|70s|80s_plus|unknown") @JsonProperty("age_group") String ageGroup,
        @Pattern(regexp = "male|female|other|unknown") @JsonProperty("gender") String gender,
        @Size(max = 30) @JsonProperty("phone") String phone
) {
}
