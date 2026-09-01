package com.livescreenlog.app.dto;

public record UserCreateRequest(String username, String password, String role, String email) {}
