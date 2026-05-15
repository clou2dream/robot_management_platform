package com.robotmanagement.auth.dto;

public record LoginResponse(
    String accessToken,
    CurrentUserResponse currentUser
) {
}
