package com.feedsystem.feed.dto;

import com.feedsystem.common.dto.PostDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPageResponse {
    private List<PostDTO> posts;
    private Long nextCursor;   // null means no more pages
    private boolean hasMore;
    private int size;
}
