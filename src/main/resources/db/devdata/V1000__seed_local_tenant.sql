INSERT INTO tenants (id, slug, display_name)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  'local-demo',
  'Local Demo Tenant'
)
ON CONFLICT (id) DO NOTHING;

-- Raw local-only key: em_dev_local_key_change_me
INSERT INTO tenant_api_keys (id, tenant_id, key_hash, label)
VALUES (
  '11111111-1111-1111-1111-111111111112',
  '11111111-1111-1111-1111-111111111111',
  '7d39abda7f79c867eef492a9884aaf7fdb1187cf5c6adb8818d7f069fc12a981',
  'Local development key'
)
ON CONFLICT (id) DO NOTHING;

