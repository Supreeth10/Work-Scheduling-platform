CREATE TABLE drivers (
                         id UUID PRIMARY KEY,
                         name VARCHAR(255) UNIQUE NOT NULL,
                         current_location geography(Point,4326),
                         on_shift BOOLEAN NOT NULL
);

CREATE TABLE shifts (
                        id UUID PRIMARY KEY,
                        driver_id UUID UNIQUE NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
                        start_time TIMESTAMP NOT NULL,
                        end_time TIMESTAMP,
                        start_location geography(Point,4326) NOT NULL
);

CREATE TABLE loads (
                       id UUID PRIMARY KEY,
                       status VARCHAR(50) NOT NULL,
                       current_stop VARCHAR(50),
                       pickup geography(Point,4326) NOT NULL,
                       dropoff geography(Point,4326) NOT NULL,
                       driver_id UUID REFERENCES drivers(id),
                       shift_id UUID REFERENCES shifts(id),
                       created_at TIMESTAMP NOT NULL,
                       updated_at TIMESTAMP
);

CREATE TABLE stop_events (
                             id UUID PRIMARY KEY,
                             load_id UUID NOT NULL REFERENCES loads(id) ON DELETE CASCADE,
                             driver_id UUID NOT NULL REFERENCES drivers(id),
                             type VARCHAR(50) NOT NULL,
                             at TIMESTAMP NOT NULL,
                             location geography(Point,4326)
);
