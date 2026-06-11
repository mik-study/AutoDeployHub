package com.proj.autodeploy.auth.dto;

import com.proj.autodeploy.user.domain.User;

public record SignupResponse(Long userId, String email, String name) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getName());
    }
}
