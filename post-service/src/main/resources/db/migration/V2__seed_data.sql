-- ============================================================
-- Seed posts for local development / demo
-- user_id references are matched by convention (alice=1, bob=2, etc.)
-- Run AFTER user-service seed (or use fixed IDs)
-- ============================================================

INSERT INTO posts (user_id, content, like_count, created_at) VALUES
(1, 'Just shipped a new feature! Cursor-based pagination is 🔥 for infinite scroll performance.', 5,  NOW() - INTERVAL '2 hours'),
(2, 'Redis ZSET is the perfect data structure for a feed system. O(log N) insert + range query.',  8,  NOW() - INTERVAL '3 hours'),
(1, 'Spring Cloud OpenFeign makes inter-service communication so clean. Declarative HTTP clients FTW.', 3,  NOW() - INTERVAL '5 hours'),
(3, 'Product tip: always validate your fan-out strategy before launch. Push vs Pull matters at scale.', 12, NOW() - INTERVAL '6 hours'),
(2, 'Running LocalStack locally to simulate AWS SQS and S3 — zero cost, full fidelity.', 7,  NOW() - INTERVAL '8 hours')
ON CONFLICT DO NOTHING;
