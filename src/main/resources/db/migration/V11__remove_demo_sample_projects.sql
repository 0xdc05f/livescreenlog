DELETE FROM user_projects
 WHERE project_id IN (
   SELECT id FROM projects WHERE api_key IN (
     'sl_98b8f37e2a604d1cb52cbc208bee98f5',
     'sl_0b999f054b564a05a47c3d8b90eae5e4',
     'sl_7fbfb2fab11d4944a10dd831c73b2815',
     'sl_c5f0082709034325ad17108f596c187b'
   )
 );

DELETE FROM projects WHERE api_key IN (
  'sl_98b8f37e2a604d1cb52cbc208bee98f5',
  'sl_0b999f054b564a05a47c3d8b90eae5e4',
  'sl_7fbfb2fab11d4944a10dd831c73b2815',
  'sl_c5f0082709034325ad17108f596c187b'
);
