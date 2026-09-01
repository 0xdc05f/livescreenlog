package com.livescreenlog.app.dto;
public record PasswordChangeRequest(String currentPassword, String newPassword) {}