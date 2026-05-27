package com.feedsystem.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedsystem.post.entity.Post;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface PostMapper extends BaseMapper<Post> {

    @Select("<script>" +
            "SELECT * FROM posts " +
            "WHERE user_id IN " +
            "<foreach item='id' collection='authorIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "<if test='before != null'> AND created_at &lt; #{before}</if>" +
            " ORDER BY created_at DESC LIMIT #{limit}" +
            "</script>")
    List<Post> selectRecentByAuthorIds(@Param("authorIds") List<Long> authorIds,
                                        @Param("before") LocalDateTime before,
                                        @Param("limit") int limit);

    @Update("UPDATE posts SET like_count = like_count + 1 WHERE id = #{postId}")
    void incrementLikeCount(@Param("postId") Long postId);

    @Update("UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{postId}")
    void decrementLikeCount(@Param("postId") Long postId);
}
