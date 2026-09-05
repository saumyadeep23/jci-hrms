-- RO/DPC decommission task: capture who decommissioned a Regional Office or
-- DPC and why, alongside the existing deleted_at soft-delete timestamp.
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(150);
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(150);
ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);
