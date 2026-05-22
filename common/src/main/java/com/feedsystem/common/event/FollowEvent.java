package com.feedsystem.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowEvent {
    private Long followerId;
    private Long followeeId;
    private String action; // "FOLLOW" or "UNFOLLOW"
}
