package com.feedsystem.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND(404, "User not found"),
    USER_ALREADY_EXISTS(409, "Username or email already exists"),
    POST_NOT_FOUND(404, "Post not found"),
    ALREADY_FOLLOWING(409, "Already following this user"),
    NOT_FOLLOWING(400, "Not following this user"),
    ALREADY_LIKED(409, "Already liked this post"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    INVALID_TOKEN(401, "Invalid or expired token");

    private final int code;
    private final String message;

    // Explicit constructor — @RequiredArgsConstructor is unreliable on enums
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
