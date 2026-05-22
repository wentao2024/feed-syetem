package com.feedsystem.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 500)
    private String avatarUrl;

    @Size(max = 300)
    private String bio;
}
