-- V3__create_shifts_table.sql
-- History-friendly shifts (many-to-one Driver -> Shifts), JTS-compatible geometry.

CREATE TABLE IF NOT EXISTS shifts (
                                      id             UUID PRIMARY KEY,
                                      driver_id      UUID NOT NULL,
                                      start_time     TIMESTAMPTZ NOT NULL,
                                      end_time       TIMESTAMPTZ,
                                      start_location geometry(Point,4326) NOT NULL,

    CONSTRAINT fk_shifts_driver
    FOREIGN KEY (driver_id) REFERENCES drivers(id)
    ON DELETE RESTRICT,

    CONSTRAINT chk_shift_time
    CHECK (end_time IS NULL OR end_time >= start_time)
    );

-- Lookups
CREATE INDEX IF NOT EXISTS idx_shifts_driver_id ON shifts(driver_id);

-- Fast “active shift?” checks
CREATE INDEX IF NOT EXISTS idx_shifts_driver_active
    ON shifts(driver_id)
    WHERE end_time IS NULL;

-- Spatial index for KNN / distance
CREATE INDEX IF NOT EXISTS idx_shifts_start_location_gix
    ON shifts
    USING GIST (start_location);
