# Work Scheduling Platform - High-Level Design

## 1. System Overview

### Purpose
The Work Scheduling Platform is a lightweight logistics dispatcher that connects drivers with delivery loads (pickup and drop-off jobs). The system enables real-time assignment, tracking, and completion of delivery tasks based on geospatial proximity and driver availability.

### Key Actors
- **Drivers**: Workers who start shifts, receive load assignments, and complete pickups/drop-offs
- **Admins**: Supervisors who create loads and monitor system status through a dashboard

### Core Value Proposition
The platform automates load dispatching by:
- Reserving loads for available on-shift drivers based on geographic proximity
- Tracking driver location and shift status
- Managing the complete load lifecycle from creation through completion
- Providing real-time visibility into system state

---

## 2. Architecture

### 2.1 High-Level Components

The system follows a **3-tier architecture**:

```
┌─────────────────────────────────────┐
│   Frontend (React + Vite + Nginx)   │  Port 3000
│   - Driver Interface                │
│   - Admin Dashboard                 │
└──────────────┬──────────────────────┘
               │ HTTP/REST
               ▼
┌─────────────────────────────────────┐
│   Backend (Spring Boot + Java 21)   │  Port 8080
│   - REST API Controllers            │
│   - Business Logic Services         │
│   - JPA/Hibernate Data Layer        │
└──────────────┬──────────────────────┘
               │ JDBC
               ▼
┌─────────────────────────────────────┐
│   Database (PostgreSQL + PostGIS)   │  Port 5433
│   - Drivers, Shifts, Loads          │
│   - Geospatial Indexing (GIST)      │
└─────────────────────────────────────┘
```

### 2.2 Deployment Architecture

**Docker Compose** orchestrates three containerized services:

1. **Database Container** (`postgis/postgis:16-3.4`)
   - Runs PostgreSQL with PostGIS extension
   - Health checks ensure readiness before dependent services start
   - Persistent volume for data durability

2. **Backend Container** (Spring Boot)
   - Depends on healthy database
   - Auto-runs Flyway migrations on startup
   - Exposes RESTful API

3. **Frontend Container** (Nginx)
   - Serves static Vite-built React app
   - Depends on backend availability
   - Proxies API requests

---

## 3. Technology Stack

### Backend Technologies
| Component | Technology | Purpose |
|-----------|-----------|---------|
| Framework | Spring Boot (Java 21) | Web application framework |
| ORM | JPA/Hibernate | Database abstraction |
| Migrations | Flyway | Version-controlled schema changes |
| Geospatial | PostGIS + JTS | Location-based queries |
| API Docs | Swagger/OpenAPI | Interactive API documentation |
| Build Tool | Maven | Dependency management |

### Frontend Technologies
| Component | Technology | Purpose |
|-----------|-----------|---------|
| Framework | React 19 | UI component library |
| Build Tool | Vite | Fast development & bundling |
| Router | React Router | Client-side navigation |
| Maps | Leaflet + React-Leaflet | Geographic visualization |
| Web Server | Nginx | Static file serving |

### Infrastructure
- **Database**: PostgreSQL 16 + PostGIS 3.4
- **Containerization**: Docker + Docker Compose
- **Version Control**: Git

---

## 4. Data Model

### 4.1 Core Entities

#### Driver
Represents a worker who can accept and complete loads.

```java
@Entity @Table(name = "drivers")
class Driver {
    UUID id;                    // Primary key
    String name;                // Unique username
    Point currentLocation;      // PostGIS Point (lat/lng)
    boolean onShift;            // Shift status flag
    List<Shift> shifts;         // Historical shifts
}
```

**Key Characteristics:**
- Unique name constraint for login
- Location stored as PostGIS geometry for spatial queries
- `onShift` flag denormalizes active shift state for performance

#### Shift
Tracks discrete work periods for drivers.

```java
@Entity @Table(name = "shifts")
class Shift {
    UUID id;                    // Primary key
    Driver driver;              // Many-to-one relationship
    Instant startTime;          // Shift start (immutable)
    Instant endTime;            // Null while active
    Point startLocation;        // Where driver began shift
}
```

**Key Characteristics:**
- `endTime IS NULL` indicates active shift
- Start location captures initial position
- One active shift per driver (business rule)

#### Load
Represents a delivery job with pickup and drop-off locations.

```java
@Entity @Table(name = "loads")
class Load {
    UUID id;                        // Primary key
    Point pickup;                   // Pickup location (PostGIS)
    Point dropoff;                  // Drop-off location (PostGIS)
    Status status;                  // State machine
    StopKind currentStop;           // Next required action
    Driver assignedDriver;          // Current assignee (nullable)
    Shift assignedShift;            // Shift context (nullable)
    Instant reservationExpiresAt;   // TTL for RESERVED state
    
    enum Status { 
        AWAITING_DRIVER,  // Available for assignment
        RESERVED,         // Soft-locked to driver (120s TTL)
        IN_PROGRESS,      // Driver accepted, en route
        COMPLETED         // Both stops finished
    }
    
    enum StopKind { PICKUP, DROPOFF }
}
```

**Key Characteristics:**
- Status progression enforces state machine
- Reservation mechanism prevents double-assignment
- Geospatial columns indexed with GIST for proximity queries
- One active load per driver constraint (V5 migration)

### 4.2 Entity Relationships

```
Driver 1──────┤ Shift (many shifts per driver)
              │
              │
Driver 1──────┤ Load (zero or one active load)
              │
Shift 1───────┤ Load (multiple loads per shift)
```

---

## 5. Key Business Flows

### 5.1 Driver Lifecycle

#### Login/Registration
1. Driver provides username
2. System checks for existing driver record
3. If new: Create driver with `onShift = false`
4. Return driver DTO with UUID

#### Start Shift
**Prerequisites:** Driver exists, not currently on shift

**Flow:**
1. Driver submits current location (lat/lng)
2. System validates no active shift exists
3. Create `Shift` record with `endTime = null`
4. Set `driver.onShift = true` and `driver.currentLocation`
5. Driver becomes eligible for load assignments

**Error Cases:**
- `SHIFT_ALREADY_ACTIVE`: Driver already has open shift
- `VALIDATION_ERROR`: Invalid coordinates

#### End Shift
**Prerequisites:** Driver has active shift, no active load

**Flow:**
1. System verifies active shift exists
2. Check no RESERVED or IN_PROGRESS loads assigned
3. Set `shift.endTime = now()`
4. Set `driver.onShift = false` and clear location
5. Shift ends

**Error Cases:**
- `SHIFT_NOT_ACTIVE`: No open shift found
- `ACTIVE_LOAD_PRESENT`: Cannot end with pending load

### 5.2 Load Assignment Flow

#### Automatic Reservation (Pull Model)
The system uses a **pull-based** assignment model where drivers request loads.

**Trigger:** Driver polls `GET /api/drivers/{id}/assignment`

**Prerequisites:** Driver on active shift with location

**Algorithm:**
```
1. Release expired reservations (RESERVED loads past TTL)
2. Check if driver already has RESERVED/IN_PROGRESS load
   → If yes: Return existing assignment (idempotent)
3. Query nearest AWAITING_DRIVER load using PostGIS spatial index:
   ORDER BY ST_Distance(load.pickup, driver.currentLocation)
4. Lock selected load (SELECT FOR UPDATE)
5. Atomically reserve:
   - Set status = RESERVED
   - Set assignedDriver = driver
   - Set assignedShift = activeShift
   - Set reservationExpiresAt = now() + 120 seconds
6. Return LoadAssignmentResponse
```

**Concurrency Handling:**
- Pessimistic locking prevents double-assignment
- Unique constraint ensures one active load per driver
- Expired reservation cleanup runs before each assignment

**Frontend Polling:**
- State refresh: Every 12 seconds
- Assignment check: Every 8 seconds (only when on-shift and unassigned)
- Single-flight guard prevents duplicate requests

#### Push Assignment on Load Creation
When an admin creates a new load, the system proactively assigns it.

**Flow:**
```
1. Admin submits CreateLoadRequest (pickup/dropoff)
2. System creates load with status = AWAITING_DRIVER
3. Trigger tryAssignNewlyCreatedLoad(loadId):
   a. Find closest on-shift driver with no active load
   b. Reserve load for that driver (120s TTL)
4. Return LoadSummaryDto (may show RESERVED if assigned)
```

### 5.3 Stop Completion Flow

**State Machine Transitions:**

```
┌──────────────────┐
│ AWAITING_DRIVER  │
└────────┬─────────┘
         │ (Driver requests assignment)
         ▼
┌──────────────────┐
│    RESERVED      │ (120s TTL, currentStop = PICKUP)
└────────┬─────────┘
         │ (Complete PICKUP)
         ▼
┌──────────────────┐
│  IN_PROGRESS     │ (currentStop = DROPOFF)
└────────┬─────────┘
         │ (Complete DROPOFF)
         ▼
┌──────────────────┐
│   COMPLETED      │
└──────────────────┘
```

#### Complete Pickup
**Endpoint:** `POST /api/drivers/{driverId}/loads/{loadId}/stops/complete`

**Prerequisites:** Load status = RESERVED, currentStop = PICKUP, not expired

**Flow:**
1. Validate driver owns load
2. Check reservation not expired (else release and error)
3. Transition: `status → IN_PROGRESS`, `currentStop → DROPOFF`
4. Snap driver location to pickup point
5. Clear `reservationExpiresAt`
6. Return updated load (no new assignment yet)

#### Complete Drop-off
**Prerequisites:** Load status = IN_PROGRESS, currentStop = DROPOFF

**Flow:**
1. Validate driver owns load
2. Transition: `status → COMPLETED`
3. Snap driver location to dropoff point
4. Clear assignment: `assignedDriver = null`, `assignedShift = null`
5. **Auto-assign next load**: Call `reserveClosestFrom()` based on new location
6. Return `CompleteStopResult` with:
   - `completed`: The finished load
   - `nextAssignment`: New reserved load (or null)

**Error Handling:**
- `RESERVATION_EXPIRED`: Reservation TTL passed, must fetch new assignment
- `ACCESS_DENIED`: Load not assigned to requesting driver
- `LOAD_STATE_CONFLICT`: Invalid state for operation

### 5.4 Load Rejection Flow

**Endpoint:** `POST /api/drivers/{driverId}/loads/{loadId}/reject`

**Prerequisites:** Load status = RESERVED, owned by driver

**Business Rule:** Rejection immediately ends the driver's shift.

**Flow:**
1. Validate driver owns load
2. Verify load status = RESERVED (only reserved loads can be rejected)
3. Release reservation:
   - `status → AWAITING_DRIVER`
   - Clear `assignedDriver`, `assignedShift`, `reservationExpiresAt`
4. End driver's shift:
   - Set `shift.endTime = now()`
   - Set `driver.onShift = false`
   - Clear `driver.currentLocation`
5. Return `RejectOutcome` with shift end timestamp

**Idempotency:** If already released and off-shift, return NO_OP result.

---

## 6. API Structure

The REST API is organized by functional domains, with comprehensive OpenAPI documentation.

### 6.1 API Endpoints

#### Drivers (`DriverController`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/drivers/login` | Login or create driver |
| GET | `/api/drivers/{id}` | Get driver details |
| GET | `/api/drivers/{id}/state` | Get complete driver state (driver + shift + load) |

#### Shifts (`ShiftController`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/drivers/{id}/shift/start` | Start new shift with location |
| POST | `/api/drivers/{id}/shift/end` | End active shift |

#### Assignments (`AssignmentController`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/drivers/{id}/assignment` | Get or reserve load for driver |
| POST | `/api/drivers/{id}/loads/{loadId}/stops/complete` | Complete next stop (pickup/dropoff) |
| POST | `/api/drivers/{id}/loads/{loadId}/reject` | Reject reserved load and end shift |

#### Loads (`LoadController`)
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/loads` | List all loads (optional status filter) |
| GET | `/api/loads/{id}` | Get single load details |
| POST | `/api/loads` | Create new load (admin) |

### 6.2 API Documentation
- **Interactive UI**: Available at `/swagger-ui` when backend is running
- **OpenAPI Spec**: Available at `/v3/api-docs`
- **Comprehensive Examples**: Each endpoint includes request/response examples and error scenarios

### 6.3 Error Handling

**Standardized Error Response:**
```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description",
  "status": 400,
  "path": "/api/endpoint",
  "correlationId": "uuid",
  "timestamp": "2025-10-19T19:00:00Z",
  "details": { /* contextual info */ }
}
```

**Common Error Codes:**
- `DRIVER_NOT_FOUND` (404): Driver UUID not in system
- `LOAD_NOT_FOUND` (404): Load UUID not in system
- `SHIFT_NOT_ACTIVE` (409): Operation requires active shift
- `SHIFT_ALREADY_ACTIVE` (409): Cannot start shift when one exists
- `ACTIVE_LOAD_PRESENT` (409): Cannot end shift with pending load
- `ACCESS_DENIED` (403): Resource not owned by requester
- `LOAD_STATE_CONFLICT` (409): Invalid operation for current load state
- `RESERVATION_EXPIRED` (409): Load reservation TTL elapsed
- `VALIDATION_ERROR` (400): Input validation failed

**Correlation IDs:**
- Generated by `CorrelationIdFilter` for request tracing
- Included in all log entries via MDC
- Returned in error responses for debugging

---

## 7. Database Schema

### 7.1 Migration Strategy
**Flyway** manages schema evolution with versioned SQL scripts in `db/migration/`:

| Migration | Purpose |
|-----------|---------|
| V1 | Enable PostGIS extension |
| V2 | Create `drivers` table with geometry column |
| V3 | Create `shifts` table with driver FK |
| V4 | Create `loads` table with status enums and geospatial columns |
| V5 | Add unique constraint: one active load per driver |

### 7.2 Key Schema Features

#### Geospatial Support
```sql
-- PostGIS geometry columns
pickup geometry(Point, 4326)    -- SRID 4326 = WGS84 (lat/lng)
dropoff geometry(Point, 4326)

-- GIST spatial indexes for proximity queries
CREATE INDEX idx_loads_pickup_gix ON loads USING GIST (pickup);
CREATE INDEX idx_loads_dropoff_gix ON loads USING GIST (dropoff);
```

**Query Pattern:**
```sql
-- Find nearest available load
SELECT id FROM loads
WHERE status = 'AWAITING_DRIVER'
ORDER BY ST_Distance(pickup, ST_SetSRID(ST_MakePoint(lng, lat), 4326))
LIMIT 1;
```

#### Custom Enum Types
```sql
CREATE TYPE load_status AS ENUM (
  'AWAITING_DRIVER', 'RESERVED', 'IN_PROGRESS', 'COMPLETED'
);

CREATE TYPE stop_kind AS ENUM ('PICKUP', 'DROPOFF');
```

#### Data Integrity Constraints
```sql
-- One active load per driver (V5)
CREATE UNIQUE INDEX idx_one_active_load_per_driver 
ON loads (assigned_driver_id) 
WHERE status IN ('RESERVED', 'IN_PROGRESS');

-- Foreign keys with appropriate cascade behavior
CONSTRAINT fk_loads_driver 
  FOREIGN KEY (assigned_driver_id) 
  REFERENCES drivers(id) 
  ON DELETE SET NULL
```

### 7.3 Indexing Strategy

**Performance-Critical Indexes:**
1. **Spatial Indexes (GIST)**: Fast proximity searches on pickup/dropoff
2. **Status Index**: Efficient filtering by load state
3. **Driver Assignment Index**: Quick lookup of driver's active loads
4. **Reservation Expiry Index**: Fast cleanup of expired reservations

---

## 8. Service Layer Architecture

### 8.1 Service Organization

The backend follows a **layered architecture**:

```
Controllers (HTTP boundary)
    ↓
Service Interfaces
    ↓
Service Implementations (business logic)
    ↓
Repositories (JPA)
    ↓
Database
```

### 8.2 Core Services

#### DriverService
**Responsibilities:**
- Driver authentication (login/create)
- Retrieve driver state (combines driver + shift + load)
- Driver CRUD operations

**Key Methods:**
- `loginOrCreate(LoginRequest)`: Returns existing or new driver
- `getDriverState(UUID)`: Aggregates current driver state

#### ShiftService
**Responsibilities:**
- Shift lifecycle management
- Validation of shift constraints

**Key Methods:**
- `startShift(driverId, lat, lng)`: Begins new shift
- `endShift(driverId)`: Terminates active shift (if allowed)

#### LoadService
**Responsibilities:**
- Load CRUD operations
- Load creation with auto-assignment

**Key Methods:**
- `create(CreateLoadRequest)`: Creates load, triggers auto-assignment
- `getAll(status)`: Lists loads with optional filtering
- `getOne(id)`: Retrieves single load

#### AssignmentService
**Responsibilities:**
- Core assignment logic (reservation algorithm)
- Stop completion state machine
- Load rejection handling

**Key Methods:**
- `getOrReserveLoad(driverId)`: Idempotent assignment fetch/reserve
- `completeNextStop(driverId, loadId)`: Advances load state
- `rejectReservedLoadAndEndShift(driverId, loadId)`: Releases load + ends shift
- `tryAssignNewlyCreatedLoad(loadId)`: Push-based assignment on creation

**Critical Implementation Details:**
- `RESERVATION_SECONDS = 120`: TTL for RESERVED state
- `reserveClosestFrom()`: Internal method for spatial assignment
- Pessimistic locking (`SELECT FOR UPDATE`) prevents races
- Constraint violation handling for one-active-load-per-driver rule

### 8.3 Transaction Management
- All service methods use `@Transactional` for ACID guarantees
- Optimistic assumption: Low contention, retry on constraint violations
- Explicit lock acquisition only for load selection

---

## 9. Frontend Architecture

### 9.1 Application Structure

**Single-Page Application (SPA):**
```
App.jsx (root component + routing)
├── pages/
│   ├── DriverPage.jsx      (Driver interface)
│   ├── AdminPage.jsx       (Admin dashboard)
│   └── AdminLogin.jsx      (Simple session guard)
├── components/
│   ├── LoadCard.jsx        (Load detail display)
│   └── MapView.jsx         (Leaflet map wrapper)
└── services/
    └── api.js              (HTTP client abstraction)
```

### 9.2 Key Frontend Patterns

#### Authentication
**Driver:** No authentication (username-based login/create)
**Admin:** Session storage guard (`adminLoggedIn` flag)

```jsx
function AdminGuard({ children }) {
    const ok = sessionStorage.getItem('adminLoggedIn') === '1';
    return ok ? children : <Navigate to="/admin/login" replace />;
}
```

#### Polling Architecture
**DriverPage** implements two independent polling loops:

1. **State Loop (12s interval):**
   - Fetches `/api/drivers/{id}/state`
   - Updates driver, shift, and load state
   - Always runs when driver is logged in

2. **Assignment Loop (8s interval):**
   - Pre-checks state, then calls `/api/drivers/{id}/assignment`
   - Only attempts reservation when:
     - Driver is on-shift
     - No current load assignment
     - Not already in-flight
   - Single-flight guard prevents concurrent requests

**Stale Response Protection:**
```javascript
const currentDriverIdRef = useRef(null);

const applyIfCurrent = (forDriverId, fn) => {
    if (currentDriverIdRef.current !== forDriverId) return;
    fn(); // Only apply if user hasn't switched
};
```

#### AbortController Integration
Prevents stale responses from setting state after user switches:
```javascript
const ac = new AbortController();
stateAbortRef.current = ac;
await fetchDriverState(id, { signal: ac.signal });

// On cleanup/logout:
stateAbortRef.current?.abort();
```

#### Map Visualization
**Leaflet** displays pickup and drop-off markers:
- Green marker: Pickup location
- Blue marker: Drop-off location
- Auto-centers on first marker
- Responsive to load changes

### 9.3 User Experience Features

**Driver Interface:**
- Real-time shift status display
- Geolocation API integration ("Use my location")
- Load reservation notifications (info/success banners)
- Auto-refresh with last-updated timestamp
- Manual refresh button
- Next-stop completion flow

**Admin Interface:**
- All loads table with status filtering
- Create new load form with coordinate input
- Real-time status updates (via polling or user-triggered refresh)
- Visual status badges (color-coded)

---

## 10. Deployment & Operations

### 10.1 Container Orchestration

**Docker Compose Configuration:**
```yaml
services:
  db:          # PostgreSQL + PostGIS
  backend:     # Spring Boot app
  frontend:    # Nginx + Vite build
```

**Startup Sequence:**
1. `db` starts with health check (pg_isready)
2. `backend` waits for healthy db, runs Flyway migrations
3. `frontend` depends on backend (runtime API dependency)

**Health Check Configuration:**
```yaml
healthcheck:
  test: pg_isready -U workuser -d workdb
  interval: 5s
  timeout: 3s
  retries: 30
  start_period: 40s
```

### 10.2 Environment Configuration

**Database Credentials** (overridable via env vars):
- `POSTGRES_USER`: Default `workuser`
- `POSTGRES_PASSWORD`: Default `workpass`
- `POSTGRES_DB`: Default `workdb`

**Backend Configuration:**
- Database URL, username, password via env vars
- Flyway auto-runs migrations on startup
- Connection pool retry logic for startup race conditions

### 10.3 Port Mappings
| Service | Internal Port | Host Port |
|---------|---------------|-----------|
| Database | 5432 | 5433 |
| Backend | 8080 | 8080 |
| Frontend | 80 | 3000 |

### 10.4 Data Persistence
- Database data stored in Docker volume `dbdata`
- Survives container restarts
- Destroyed with `docker compose down -v`

### 10.5 Seed Data
Backend includes `CommandLineRunner` that seeds 4 sample loads on first startup:
- Denver → Colorado Springs
- Boulder → Denver
- Phoenix → Tucson
- Denver → Phoenix

Skips seeding if loads already exist (idempotent).

---

## 11. Scalability Considerations

### Current Limitations (Single-Instance Design)
1. **No Load Balancing**: Single backend instance
2. **In-Memory State**: No distributed session management
3. **Polling Overhead**: All clients poll periodically
4. **Spatial Query Performance**: Bounded by PostgreSQL instance

### Future Scalability Paths

#### Horizontal Scaling
- **Stateless Backend**: Already supports horizontal scaling (no shared state)
- **Database Connection Pool**: Configure HikariCP for multiple instances
- **Load Balancer**: Add nginx/HAProxy for traffic distribution

#### Performance Optimization
- **WebSocket Push**: Replace polling with server-sent events or WebSockets
- **Caching Layer**: Redis for driver state, load listings
- **Read Replicas**: PostgreSQL replicas for read-heavy operations
- **Spatial Index Tuning**: Optimize GIST parameters for larger datasets

#### Observability
- **Correlation ID**: Already implemented for request tracing
- **Metrics**: Add Micrometer/Prometheus for monitoring
- **Distributed Tracing**: Integrate OpenTelemetry for microservices readiness

---

## 12. Security Considerations

### Current Implementation
- **No Authentication**: Username-based driver access (trust-based)
- **Admin Guard**: Client-side session storage check (not secure)
- **SQL Injection**: Protected by JPA parameterized queries
- **CORS**: Not explicitly configured (same-origin deployment)

### Production Hardening Recommendations
1. **Authentication**: Implement JWT or OAuth2 for driver/admin access
2. **Authorization**: Role-based access control (RBAC)
3. **HTTPS**: TLS termination at reverse proxy
4. **Input Validation**: Already robust via Jakarta Validation
5. **Rate Limiting**: Add per-client rate limits to prevent abuse
6. **API Gateway**: Centralized security, throttling, and monitoring

---

## 13. Testing Strategy

### Current State
- Minimal test coverage (single smoke test in `WorkDispatcherApplicationTests`)
- Manual testing via Swagger UI and web interface

### Recommended Test Pyramid

**Unit Tests:**
- Service layer business logic
- Utility classes (JtsGeo, LoadMappers, TextNormalizer)
- DTO validation rules

**Integration Tests:**
- Repository geospatial queries
- Flyway migration scripts
- Controller endpoints (MockMvc)

**End-to-End Tests:**
- Complete workflows (shift → assignment → completion)
- Concurrent assignment scenarios
- Reservation expiration handling

---

## 14. Summary

The Work Scheduling Platform demonstrates a well-structured, geospatially-aware logistics system with:

**Strengths:**
- Clean separation of concerns (3-tier architecture)
- Robust state machine for load lifecycle
- Geospatial query optimization via PostGIS
- Comprehensive API documentation
- Containerized deployment with health checks
- Idempotent operations for reliability

**Current Design Decisions:**
- Pull-based assignment (driver-initiated polling)
- Soft reservation with TTL (120s) to handle unresponsive clients
- Denormalized `onShift` flag for query performance
- Auto-assignment after drop-off completion

**Architectural Patterns:**
- Repository pattern for data access
- Service layer for business logic encapsulation
- DTO pattern for API contracts
- Flyway migrations for schema versioning
- Correlation IDs for request tracing

The system is production-ready for single-instance deployment and provides a solid foundation for future enhancements in scalability, security, and real-time communication.

