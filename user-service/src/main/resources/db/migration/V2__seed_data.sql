-- ============================================================
-- Seed data for local development / demo
-- Passwords are BCrypt of "password123"
-- ============================================================

INSERT INTO users (username, email, password, bio) VALUES
('alice',   'alice@example.com',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Frontend engineer @ NYC'),
('bob',     'bob@example.com',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Backend engineer, coffee addict'),
('carol',   'carol@example.com',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Product manager'),
('dave',    'dave@example.com',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Data scientist'),
('eve',     'eve@example.com',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'DevOps / SRE')
ON CONFLICT (username) DO NOTHING;

-- alice follows bob, carol; bob follows alice
INSERT INTO follows (follower_id, followee_id)
SELECT a.id, b.id FROM users a, users b
WHERE (a.username = 'alice' AND b.username = 'bob')
   OR (a.username = 'alice' AND b.username = 'carol')
   OR (a.username = 'bob'   AND b.username = 'alice')
ON CONFLICT (follower_id, followee_id) DO NOTHING;
