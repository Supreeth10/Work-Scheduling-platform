-- One RESERVED or IN_PROGRESS load per driver, enforced by DB.
CREATE UNIQUE INDEX IF NOT EXISTS ux_loads_one_open_per_driver
    ON loads(assigned_driver_id)
    WHERE status IN ('RESERVED','IN_PROGRESS');
