-- V4__create_loads_table.sql
-- Loads table using geometry(Point,4326) + soft reservation (RESERVED with 60s TTL handled in code)
-- 1) Ensure enum types exist (and include RESERVED)
DO $$ BEGIN -- load_status: AWAITING_DRIVER, RESERVED, IN_PROGRESS, COMPLETED
IF NOT EXISTS (
  SELECT
    1
  FROM
    pg_type
  WHERE
    typname = 'load_status'
) THEN CREATE TYPE load_status AS ENUM (
  'AWAITING_DRIVER', 'RESERVED', 'IN_PROGRESS',
  'COMPLETED'
);
ELSE -- add RESERVED if the type exists but value is missing
IF NOT EXISTS (
  SELECT
    1
  FROM
    pg_enum e
    JOIN pg_type t ON t.oid = e.enumtypid
  WHERE
    t.typname = 'load_status'
    AND e.enumlabel = 'RESERVED'
) THEN ALTER TYPE load_status
    ADD
    VALUE 'RESERVED';
END IF;
END IF;
-- stop_kind: PICKUP, DROPOFF
IF NOT EXISTS (
  SELECT
    1
  FROM
    pg_type
  WHERE
    typname = 'stop_kind'
) THEN CREATE TYPE stop_kind AS ENUM ('PICKUP', 'DROPOFF');
END IF;
END$$;
-- 2) Create table (idempotent)
CREATE TABLE IF NOT EXISTS loads (
    id UUID PRIMARY KEY,
    pickup geometry(Point, 4326) NOT NULL,
    dropoff geometry(Point, 4326) NOT NULL,
    status load_status NOT NULL DEFAULT 'AWAITING_DRIVER',
    current_stop stop_kind NOT NULL DEFAULT 'PICKUP',
    assigned_driver_id UUID NULL,
    assigned_shift_id UUID NULL,
    -- When a load is RESERVED, this holds the expiry; NULL otherwise
    reservation_expires_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_loads_driver FOREIGN KEY (assigned_driver_id) REFERENCES drivers(id) ON DELETE
    SET
    NULL,
    CONSTRAINT fk_loads_shift FOREIGN KEY (assigned_shift_id) REFERENCES shifts(id) ON DELETE
    SET
    NULL
    );
-- 3) Useful indexes
-- Spatial
CREATE INDEX IF NOT EXISTS idx_loads_pickup_gix ON loads USING GIST (pickup);
CREATE INDEX IF NOT EXISTS idx_loads_dropoff_gix ON loads USING GIST (dropoff);
-- Status / assignment
CREATE INDEX IF NOT EXISTS idx_loads_status ON loads(status);
CREATE INDEX IF NOT EXISTS idx_loads_assigned_driver ON loads(assigned_driver_id);
CREATE INDEX IF NOT EXISTS idx_loads_assigned_shift ON loads(assigned_shift_id);
CREATE INDEX IF NOT EXISTS idx_loads_reservation_exp ON loads(reservation_expires_at);
