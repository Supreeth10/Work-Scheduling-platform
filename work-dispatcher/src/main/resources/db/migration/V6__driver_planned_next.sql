-- Soft plan: remember which load a driver should take next after current completes.
ALTER TABLE drivers
    ADD COLUMN IF NOT EXISTS planned_next_load_id UUID NULL;

CREATE INDEX IF NOT EXISTS idx_drivers_planned_next
    ON drivers(planned_next_load_id);
