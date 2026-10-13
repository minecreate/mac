package com.linenexus.asset.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
