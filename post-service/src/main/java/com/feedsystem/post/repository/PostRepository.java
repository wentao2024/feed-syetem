package com.feedsystem.post.repository;

import com.feedsystem.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Post> findByIdIn(List<Long> ids);
}
