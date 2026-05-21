DROP TABLE IF EXISTS legacy_seed_robot_ids;

CREATE TEMP TABLE legacy_seed_robot_ids AS
SELECT id
FROM robots
WHERE external_robot_id IN ('AUTH-RBT-1001', 'AUTH-RBT-1002', 'AUTH-RBT-1003')
   OR serial_number IN ('SN-001', 'SN-002', 'SN-003');

DELETE FROM operator_robot_access
WHERE robot_id IN (SELECT id FROM legacy_seed_robot_ids);

DELETE FROM orders
WHERE robot_id IN (SELECT id FROM legacy_seed_robot_ids)
   OR (
      order_id LIKE 'order-%'
      AND payload ->> 'serialNumber' IN ('SN-001', 'SN-002', 'SN-003')
   );

DELETE FROM robot_connections
WHERE robot_id IN (SELECT id FROM legacy_seed_robot_ids);

DELETE FROM robot_states
WHERE robot_id IN (SELECT id FROM legacy_seed_robot_ids)
   OR raw_payload ->> 'source' = 'demo';

DELETE FROM robot_positions
WHERE robot_id IN (SELECT id FROM legacy_seed_robot_ids);

DELETE FROM alerts
WHERE robot_id IN (SELECT id FROM legacy_seed_robot_ids)
   OR raw_error ->> 'source' = 'demo'
   OR error_type = 'SMOKE_TEST';

DELETE FROM robots
WHERE id IN (SELECT id FROM legacy_seed_robot_ids);

DROP TABLE IF EXISTS legacy_seed_robot_ids;
