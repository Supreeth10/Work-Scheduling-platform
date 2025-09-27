-- V3__seed_data.sql
-- Seed sample data for drivers, shifts, loads, and stop events.
-- Requires V1 (postgis extension) and V2 (tables) to have run.

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- Drivers
-- ─────────────────────────────────────────────────────────────────────────────
-- Note: POINT(longitude latitude). All coordinates are WGS84.
-- Alice - Denver, CO (on shift)
INSERT INTO drivers (id, name, current_location, on_shift) VALUES
    ('11111111-1111-1111-1111-111111111111', 'alice',
     ST_SetSRID(ST_MakePoint(-104.9903, 39.7392), 4326)::geography, TRUE);

-- Bob - Boulder, CO (on shift)
INSERT INTO drivers (id, name, current_location, on_shift) VALUES
    ('22222222-2222-2222-2222-222222222222', 'bob',
     ST_SetSRID(ST_MakePoint(-105.27055, 40.01499), 4326)::geography, TRUE);

-- Carol - Phoenix, AZ (off shift)
INSERT INTO drivers (id, name, current_location, on_shift) VALUES
    ('33333333-3333-3333-3333-333333333333', 'carol',
     ST_SetSRID(ST_MakePoint(-112.0740, 33.4484), 4326)::geography, FALSE);

-- ─────────────────────────────────────────────────────────────────────────────
-- Shifts (1:1 with driver; only for on-shift drivers)
-- ─────────────────────────────────────────────────────────────────────────────
-- Alice's shift started 2 hours ago at downtown Denver
INSERT INTO shifts (id, driver_id, start_time, end_time, start_location) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '11111111-1111-1111-1111-111111111111',
     NOW() - INTERVAL '2 hours',
     NULL,
     ST_SetSRID(ST_MakePoint(-104.9903, 39.7392), 4326)::geography
    );

-- Bob's shift started 1 hour ago near Boulder
INSERT INTO shifts (id, driver_id, start_time, end_time, start_location) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     '22222222-2222-2222-2222-222222222222',
     NOW() - INTERVAL '1 hours',
     NULL,
     ST_SetSRID(ST_MakePoint(-105.27055, 40.01499), 4326)::geography
    );

-- ─────────────────────────────────────────────────────────────────────────────
-- Loads
-- ─────────────────────────────────────────────────────────────────────────────
-- l1: Awaiting driver (Denver Airport -> Aurora)
INSERT INTO loads (
    id, status, current_stop, pickup, dropoff, driver_id, shift_id, created_at, updated_at
) VALUES (
             '44444444-4444-4444-4444-444444444444',
             'AWAITING_DRIVER',
             'PICKUP',
             ST_SetSRID(ST_MakePoint(-104.6737, 39.8561), 4326)::geography, -- Denver Intl Airport
             ST_SetSRID(ST_MakePoint(-104.8319, 39.7294), 4326)::geography, -- Aurora
             NULL,
             NULL,
             NOW() - INTERVAL '30 minutes',
             NULL
         );

-- l2: In progress, assigned to Alice (Pickup in Boulder -> Drop at Denver)
INSERT INTO loads (
    id, status, current_stop, pickup, dropoff, driver_id, shift_id, created_at, updated_at
) VALUES (
             '55555555-5555-5555-5555-555555555555',
             'IN_PROGRESS',
             'DROPOFF',  -- pickup already done
             ST_SetSRID(ST_MakePoint(-105.27055, 40.01500), 4326)::geography, -- Boulder
             ST_SetSRID(ST_MakePoint(-104.9903, 39.7392), 4326)::geography,   -- Denver
             '11111111-1111-1111-1111-111111111111',                          -- Alice
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',                          -- Alice's shift
             NOW() - INTERVAL '1 hours',
             NOW() - INTERVAL '15 minutes'
         );

-- l3: Awaiting driver (Phoenix pickup -> Scottsdale drop)
INSERT INTO loads (
    id, status, current_stop, pickup, dropoff, driver_id, shift_id, created_at, updated_at
) VALUES (
             '66666666-6666-6666-6666-666666666666',
             'AWAITING_DRIVER',
             'PICKUP',
             ST_SetSRID(ST_MakePoint(-112.0740, 33.4484), 4326)::geography,   -- Phoenix
             ST_SetSRID(ST_MakePoint(-111.9261, 33.4942), 4326)::geography,   -- Scottsdale
             NULL,
             NULL,
             NOW() - INTERVAL '10 minutes',
             NULL
         );

-- ─────────────────────────────────────────────────────────────────────────────
-- Stop Events (audit log)
-- ─────────────────────────────────────────────────────────────────────────────
-- l2: Pickup completed by Alice at Boulder 45 minutes ago
INSERT INTO stop_events (id, load_id, driver_id, type, at, location) VALUES
    ('77777777-7777-7777-7777-777777777777',
     '55555555-5555-5555-5555-555555555555',
     '11111111-1111-1111-1111-111111111111',
     'PICKUP',
     NOW() - INTERVAL '45 minutes',
     ST_SetSRID(ST_MakePoint(-105.27055, 40.01500), 4326)::geography
    );

COMMIT;
