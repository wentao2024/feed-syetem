-- ============================================================
-- Post Service Schema
-- ============================================================

CREATE TABLE IF NOT EXISTS posts (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    content     TEXT,
    like_count  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_posts_user_id    ON posts(user_id);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);

-- ── Post images (ordered list per post) ───────────────────
CREATE TABLE IF NOT EXISTS post_images (
    post_id     BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    image_url   VARCHAR(500) NOT NULL,
    image_order INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, image_order)
);

-- ── Likes ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS likes (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    post_id     BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT  uq_like     UNIQUE (user_id, post_id)
);

CREATE INDEX IF NOT EXISTS idx_likes_post_id ON likes(post_id);
CREATE INDEX IF NOT EXISTS idx_likes_user_id ON likes(user_id);
