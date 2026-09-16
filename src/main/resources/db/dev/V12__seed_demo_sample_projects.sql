INSERT INTO projects (name, description, api_key, recording_mode, target_users, created_at)
VALUES
  ('Demo ALL', '샘플 페이지(/sample.html, /sample-all.html)용 전체 수집 데모 프로젝트', 'sl_98b8f37e2a604d1cb52cbc208bee98f5', 'ALL', NULL, CURRENT_TIMESTAMP),
  ('Demo A', '샘플 페이지(/sample-a.html)용 지정 사용자 녹화 데모 프로젝트', 'sl_0b999f054b564a05a47c3d8b90eae5e4', 'A', 'user001,user002', CURRENT_TIMESTAMP),
  ('Demo B', '샘플 페이지(/sample-b.html)용 원격 트리거 데모 프로젝트', 'sl_7fbfb2fab11d4944a10dd831c73b2815', 'B', NULL, CURRENT_TIMESTAMP),
  ('Demo C', '샘플 페이지(/sample-c.html)용 에러 자동 트리거 데모 프로젝트', 'sl_c5f0082709034325ad17108f596c187b', 'C', NULL, CURRENT_TIMESTAMP)
ON CONFLICT (api_key) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  recording_mode = EXCLUDED.recording_mode,
  target_users = EXCLUDED.target_users;
