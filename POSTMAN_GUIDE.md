# Postman Collection Guide - Testing MIP Optimization

**Simple API Testing with Postman**

---

## 📦 Setup (One-Time)

### 1. Extract ZIP and Navigate to Project

```bash
# Extract the ZIP file
unzip Work-Scheduling-platform.zip

# Navigate to project root
cd Work-Scheduling-platform

# Verify you're in the right place
ls
# You should see: docker-compose.yml, work-dispatcher/, README.md, etc.
```

### 2. Import Postman Collection

1. Open Postman
2. Click **Import** button (top left)
3. Navigate to the extracted folder
4. Select `Vorto challenge.postman_collection.json` from the root directory
5. Collection "Vorto challenge" appears in left sidebar

### 3. Start the Application

From the project root directory in Terminal:

```bash
docker-compose up --build
```

**Wait for:** Startup message showing "★★★ MIP IS ~72% MORE EFFICIENT! ★★★" (30-60 seconds)

---

## 🧪 Test Scenarios

### **Scenario 1: See Pre-Optimized Assignments**

The application automatically runs MIP optimization on startup and assigns loads.

#### Test 1: View All Drivers
```
Collection: Driver → GET Driver state
OR manually: GET http://localhost:8080/api/drivers
```

**You'll see:** 3 drivers (Driver-1, Driver-2, Driver-3) all on-shift

#### Test 2: View All Loads
```
Collection: Load → Get All loads by Status optional
OR manually: GET http://localhost:8080/api/loads
```

**You'll see:**
- Load-1: RESERVED to Driver-1
- Load-2: RESERVED to Driver-1 (chained!)
- Load-3: RESERVED to Driver-2

**Key observation:** Driver-1 has 2 loads (chaining!)

---

### **Scenario 2: Driver Gets Assignment**

From the `/api/drivers` response, copy a driver ID (e.g., Driver-1's ID).

#### Step 1: Set Variable in Postman
- Click "Environments" or use Quick Look (eye icon)
- Add variable: `driver_id` = `<paste-driver-1-id>`

#### Step 2: Get Assignment
```
Collection: Driver → get driver assignment
Endpoint: GET /api/drivers/{{driver_id}}/assignment
```

**Expected Response:**
```json
{
  "loadId": "...",
  "pickupLat": 1.0,
  "pickupLng": 0.0,
  "dropoffLat": 2.0,
  "dropoffLng": 0.0,
  "status": "RESERVED",
  "nextStop": "PICKUP"
}
```

**This is Load-1 - assigned by MIP optimization!**

---

### **Scenario 3: Complete Delivery Workflow**

Shows automatic next-load assignment after dropoff (demonstrates chaining).

#### Step 1: Complete Pickup
```
Collection: Driver → complete next stop  
Endpoint: POST /api/drivers/{{driver_id}}/loads/{{load_id}}/stops/complete
```

**Response:**
```json
{
  "completed": {
    "status": "IN_PROGRESS",  // Now en route to dropoff
    "nextStop": "DROPOFF"
  },
  "nextAssignment": null  // No new assignment yet
}
```

#### Step 2: Complete Dropoff (Triggers Optimization!)
```
Same request again:
POST /api/drivers/{{driver_id}}/loads/{{load_id}}/stops/complete
```

**Response:**
```json
{
  "completed": {
    "status": "COMPLETED"  // Delivery done!
  },
  "nextAssignment": {
    "loadId": "...",       // Load-2 automatically assigned!
    "pickupLat": 3.0,
    "status": "RESERVED"
  }
}
```

**✓ MIP automatically assigned the chained Load-2!**

---

### **Scenario 4: Create New Load (Triggers Rebalancing)**

#### Step 1: Create a Load
```
Collection: Load → POST new load
Endpoint: POST /api/loads

Body:
{
  "pickup": {"lat": 31.4484, "lng": -110.074},
  "dropoff": {"lat": 34.2226, "lng": -115.9747}
}
```

#### Step 2: Check Backend Logs
```bash
docker-compose logs backend | tail -30
```

**You'll see:**
```
INFO: Load <id> created, triggering optimization
INFO: Optimizing assignments: trigger=LOAD_CREATED
INFO: MIP solver finished in X ms with status: OPTIMAL
INFO: Optimization complete: N assignments made
```

**MIP automatically assigned the new load to best available driver!**

---

## 📊 What Each Test Proves

| Test | What It Shows |
|------|---------------|
| View Loads | MIP pre-assigned loads optimally on startup |
| Get Assignment | Drivers have loads waiting (72% efficient routing) |
| Complete Delivery | MIP automatically assigns next load (chaining) |
| Create Load | MIP dynamically rebalances when new load appears |

---

## 🎯 Key Observations for Reviewers

### ✅ **Chaining in Action**
- Driver-1 has Load-1 AND Load-2 assigned
- After completing Load-1 dropoff, Load-2 automatically appears
- This is **impossible with greedy** (one load at a time)

### ✅ **Dynamic Rebalancing**
- Create a new load → MIP immediately reassigns if better
- Existing RESERVED loads can be reshuffled
- IN_PROGRESS loads stay protected

### ✅ **Global Optimization**
- Total deadhead: 159 mi (MIP) vs 573 mi (greedy)
- Same 3 deliveries completed
- **72% more efficient**

---

## 🔧 Troubleshooting

**Port Already in Use:**
```bash
docker-compose down
# Wait 10 seconds
docker-compose up
```

**Database Connection Issues:**
```bash
docker-compose down -v  # Remove volumes
docker-compose up --build
```

**Can't See Logs:**
```bash
docker-compose logs -f backend  # Follow logs in real-time
```

---

## 📝 Quick Reference

### All Commands (Run from Project Root):

```bash
# First time setup
unzip Work-Scheduling-platform.zip
cd Work-Scheduling-platform

# Start application
docker-compose up --build

# View logs (in new terminal, from project root)
cd Work-Scheduling-platform
docker-compose logs -f backend              # Live logs
docker-compose logs backend | tail -100     # Last 100 lines
docker-compose logs backend | grep "Optimization"  # Search logs

# Run tests (in new terminal, from project root)
cd Work-Scheduling-platform
docker-compose exec backend mvn test -Dtest=GreedyVsMipDemonstrationTest

# Stop application
docker-compose down
```

### Key Endpoints (Use in Postman):

```
GET  /api/drivers                          # List all drivers
GET  /api/loads                            # List all loads and assignments
GET  /api/drivers/{id}/assignment          # Get driver's current load
POST /api/drivers/{id}/loads/{id}/stops/complete  # Complete pickup/dropoff
```

---

## 💡 Tip: Viewing Logs While Testing

Keep one terminal showing live logs while you test in Postman:

**Terminal 1:**
```bash
cd Work-Scheduling-platform
docker-compose logs -f backend
```

**Postman:** Run API requests

**Terminal 1 shows:** Real-time optimization events, MIP solver performance

---

**For full scenario details, see `REVIEW_GUIDE.md`**
