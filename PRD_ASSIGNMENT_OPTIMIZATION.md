# Product Requirements Document: Assignment Optimization

**Version:** 1.0  
**Date:** October 30, 2025  
**Status:** Approved for Implementation

---

## Executive Summary

This PRD outlines the refactoring of the Work Scheduling Platform's load assignment system to implement a globally-optimal assignment algorithm. The new system will eliminate duplicated code, minimize total empty-travel distance (deadhead miles), support chaining multiple loads per driver, and dynamically rebalance assignments when new drivers or loads enter the system.

**Key Benefits:**
- Single unified algorithm for all assignment decisions
- 10-30% reduction in deadhead miles through global optimization
- Support for assigning up to 2 loads per driver in sequence
- Automatic rebalancing when system state changes
- Cleaner, more maintainable codebase

---

## Problem Statement

### Current Shortcomings

#### 1. Duplicated Assignment Logic

The system currently has three separate processes for assignment:
- **When a driver requests a load:** Finds the nearest load for that specific driver
- **When a load is created:** Finds the nearest driver for that specific load  
- **When a driver completes a delivery:** Finds the nearest next load for that driver

Each process implements similar logic independently, making changes difficult and error-prone. Any improvement to the assignment algorithm must be replicated in all three places.

#### 2. Suboptimal Assignments (Greedy Algorithm)

The current approach makes one assignment at a time, always choosing the locally best option:
- Assigns Load A to Driver 1 because they're closest
- Then assigns Load B to Driver 2 because they're the next closest
- Cannot "undo" assignment of Load A if giving it to Driver 2 would be better globally

This greedy approach misses opportunities to minimize total deadhead across all drivers and loads simultaneously.

**Example:** Three drivers and three loads arranged where the greedy algorithm assigns based on immediate proximity, resulting in one driver traveling 45 miles while a global view would have all drivers traveling under 25 miles each.

#### 3. No Support for Load Chaining

Currently, each driver can only handle one load at a time. In reality, it's often efficient for a driver to complete two nearby loads in sequence (Load 1 → Load 2) rather than having two separate drivers handle them.

#### 4. No Dynamic Rebalancing

When a new driver starts their shift or a new load is created, the system only considers that single new entity. It doesn't reconsider whether existing pending assignments should be shuffled to accommodate the new arrival more efficiently.

#### 5. Assignment Logic in Database

Complex assignment logic is embedded in database queries, making it:
- Difficult to test independently
- Hard to extend with new features
- Challenging to debug when issues occur
- Tightly coupled to database technology

---

## Requirements

### Functional Requirements

#### FR1: Single Entry Point for Assignment Logic
**Requirement:** Create one unified algorithm that handles all assignment scenarios.

**Current State:** Three separate code paths  
**Desired State:** One algorithm called by all three triggers  
**Business Value:** Easier maintenance, consistent behavior, single point for improvements

#### FR2: Global Deadhead Minimization
**Requirement:** Minimize the total empty-travel distance across all drivers and loads simultaneously.

**Approach:** Consider all available drivers and all available loads together to find the best overall assignment, not just the best single pairing.

**Success Metric:** Demonstrably better than the current greedy approach (10-30% improvement expected)

#### FR3: Support Load Chaining (Up to 2 Loads)
**Requirement:** Allow drivers to be assigned two loads in sequence (L1 → L2).

**Workflow:**
1. Driver picks up Load 1, delivers Load 1
2. Driver picks up Load 2, delivers Load 2
3. System calculates total deadhead: `Driver location → L1 pickup + L1 dropoff → L2 pickup`

**Constraint:** Maximum of 2 loads per driver per assignment

#### FR4: Dynamic Rebalancing
**Requirement:** Automatically reconsider assignments when system state changes.

**Triggers:**
- New driver starts shift
- New load is created
- Driver completes a delivery

**Rebalancing Policy (Aggressive):**
- Can reassign loads that are RESERVED (not yet picked up)
- Cannot reassign loads that are IN_PROGRESS (already picked up)
- Will reassign if global improvement is significant

**Example:** Driver A is assigned Load X (but hasn't picked it up yet). New Load Y arrives that's much closer to Driver A. System reassigns: Driver A gets Load Y, another driver gets Load X.

#### FR5: Move Assignment Logic to Application Code
**Requirement:** Calculate assignments in application code rather than database queries.

**Rationale:**
- Easier to test and validate
- More flexible for complex algorithms
- Better separation of concerns
- Database used only for data storage and retrieval

### Non-Functional Requirements

#### NFR1: Performance
- **Target scale:** 10-50 concurrent drivers and loads
- **Response time:** Assignment calculation completes in under 500 milliseconds
- **Trigger model:** Event-driven (not continuous polling)

#### NFR2: Algorithm Quality
- Must be provably better than greedy (not just anecdotal)
- Cannot be brute-force (must scale reasonably)
- Should find optimal or near-optimal solution

#### NFR3: Maintainability
- Clear separation between optimization logic and business logic
- Well-documented design decisions
- Comprehensive test coverage

---

## Proposed Solution

### Optimization Algorithm: Mixed Integer Programming (MIP)

**What is MIP?**

Mixed Integer Programming is a mathematical optimization technique where you:
1. Define what you're deciding (which driver gets which load)
2. Define your constraints (each load assigned once, each driver gets at most 2 loads)
3. Define what you're optimizing (minimize total deadhead miles)
4. Let a solver find the best solution

**Why MIP?**

- **Finds globally optimal solutions** - Considers all drivers and loads together
- **Handles complex constraints naturally** - Load chaining, capacity limits, etc.
- **Fast for our scale** - Solves in milliseconds for 10-50 entities
- **Not brute force** - Uses intelligent search techniques (branch-and-bound)
- **Proven technology** - Widely used in logistics industry

**Alternative Considered: Greedy Algorithm**
- Rejected because it only considers local optimization
- Cannot guarantee globally best solution
- Doesn't naturally support multi-load assignments

### Solution Architecture

#### High-Level Flow

1. **Trigger Event** (driver starts shift, load created, delivery completed)
2. **Gather System State**
   - All available drivers (on-shift, location known)
   - All assignable loads (waiting or reserved but not picked up)
   - Loads in progress (cannot be reassigned)
3. **Generate Possible Assignments**
   - Driver 1 → Load A (single)
   - Driver 1 → Load A then Load B (chained)
   - Driver 2 → Load B (single)
   - ... etc for all valid combinations
4. **Calculate Costs**
   - For each possible assignment, calculate deadhead miles
5. **Optimize**
   - Use MIP solver to find assignment set that minimizes total deadhead
   - Respect constraints (each load assigned once, max 2 per driver, etc.)
6. **Apply Results**
   - Update database with new assignments
   - Release old reservations that are being reassigned
   - Create new reservations

#### Technology Choice

**Google OR-Tools** will be used as the MIP solver:
- Free, open-source library
- Excellent Java support
- Production-ready (used by Google internally)
- Includes native optimized code for fast solving

### Comparison: Before and After

#### Before (Greedy)
```
Scenario: 3 drivers at different locations, 3 loads available

Process:
1. Find closest driver-load pair → Driver 1 to Load A
2. Find next closest pair → Driver 2 to Load C  
3. Driver 3 forced to Load B (40 miles away)

Total: 50 miles deadhead
```

#### After (MIP)
```
Same scenario: 3 drivers, 3 loads

Process:
1. Consider ALL possible complete assignments
2. Find assignment with minimum total deadhead
3. Might assign Driver 1 to Load B, Driver 2 to Load A, Driver 3 to Load C

Total: 35 miles deadhead (30% improvement)
```

### Chaining Example

**Scenario:** Driver at Location A, two loads with pickups at Location B and C
- Load 1: Pickup at B (10 miles from A), dropoff at D
- Load 2: Pickup at C (2 miles from D), dropoff at E

**Without chaining:** Driver gets Load 1 only (10 miles deadhead)

**With chaining:** Driver gets both loads
- Deadhead: A → B (10 miles) + D → C (2 miles) = 12 miles total
- But both loads covered by one driver instead of two

### Dynamic Rebalancing Example

**Initial state:**
- Driver 1 assigned to Load X (reserved, 15 miles away)
- Driver 2 idle

**New load Y arrives (8 miles from Driver 1)**

**Without rebalancing:** Driver 2 gets Load Y

**With rebalancing:**
- Release Driver 1's reservation to Load X
- Assign Driver 1 to Load Y (closer)
- Assign Driver 2 to Load X
- Net result: Less total deadhead

---

## Technical Design

### New Components

#### 1. Optimization Service
**Purpose:** Core optimization engine  
**Responsibilities:**
- Generate all valid driver-load assignment possibilities
- Calculate deadhead cost for each possibility
- Build and solve MIP optimization model
- Return optimal assignment plan

**Input:** List of available drivers and loads  
**Output:** Optimal assignment plan (which driver gets which loads)

#### 2. Distance Calculator
**Purpose:** Calculate distance between geographic points  
**Method:** Haversine formula (great-circle distance on a sphere)  
**Replaces:** Database spatial queries

#### 3. Assignment Plan (Data Structure)
**Purpose:** Represent the result of optimization  
**Contains:**
- Map of driver IDs to their assigned load sequence
- Total deadhead miles
- List of changes from previous state

#### 4. Sequence Generator
**Purpose:** Create all possible assignment combinations  
**Generates:**
- Single-load assignments (Driver → Load)
- Chained assignments (Driver → Load1 → Load2)
- Empty assignments (Driver with no load)

### Integration Points

The optimization will be triggered from three existing service methods:

1. **When driver starts shift** (ShiftService)
   - After creating shift record, trigger optimization
   - Assign available loads to the new driver

2. **When load is created** (LoadService)  
   - After creating load record, trigger optimization
   - Assign the new load to best available driver

3. **When driver completes delivery** (AssignmentService)
   - After marking delivery complete, trigger optimization
   - Assign next load to the now-available driver

### Data Flow

**Current state:** Database → Service reads nearby entities → Makes single assignment

**New state:** Database → Service reads ALL available entities → Optimization Service → Optimal assignments → Service updates database

### Repository Changes

**Add methods to fetch in bulk:**
- Get all drivers that are eligible (on-shift, location known)
- Get all loads that are assignable (awaiting driver or reserved)
- Get all loads in progress (protected from reassignment)

**Deprecate (but keep for transition):**
- Methods that find single closest driver
- Methods that find single closest load

### Backward Compatibility

- Old API endpoints remain unchanged
- Internal implementation changes only
- Existing database schema unchanged
- Old query methods deprecated but still functional during transition

---

## Implementation Plan

### Phase 1: Foundation (Week 1)
**Deliverables:**
- Add OR-Tools library dependency
- Create optimization package structure
- Implement distance calculator with unit tests
- Create basic data structures (assignment plan, load sequence)

**Validation:** Distance calculator tested against known geographic distances

### Phase 2: Core Optimization (Week 2)
**Deliverables:**
- Implement sequence generator (all possible assignments)
- Build MIP model creation logic
- Integrate OR-Tools solver
- Create optimization service with basic tests

**Validation:** Can solve simple 2-driver, 2-load scenario and return correct result

### Phase 3: Integration (Week 3)
**Deliverables:**
- Add batch fetch methods to repositories
- Create unified assignment entry point
- Wire up trigger points (shift start, load create, delivery complete)
- Implement rebalancing logic

**Validation:** End-to-end test where optimization is triggered and database is updated

### Phase 4: Testing & Validation (Week 4)
**Deliverables:**
- Comprehensive unit tests
- Integration tests comparing MIP vs greedy
- Performance tests (10x10, 20x20, 50x50 scenarios)
- Rebalancing scenario tests

**Validation:** 
- All tests pass
- MIP demonstrably better than greedy
- Performance under 500ms for target scale

### Phase 5: Documentation & Deployment (Week 5)
**Deliverables:**
- Update API documentation
- Create operational runbook
- Deployment to staging environment
- Monitor performance and correctness

**Validation:**
- Staging tests pass
- Performance metrics within targets
- No increase in failed assignments

---

## Test Scenarios

### Test Scenario 1: MIP Beats Greedy
**Setup:**
- 3 drivers at positions A, B, C
- 3 loads at positions X, Y, Z
- Arranged so greedy makes suboptimal choice

**Greedy Result:** Total 65 miles deadhead

**MIP Result:** Total 50 miles deadhead (23% improvement)

**Success Criteria:** MIP finds assignment with measurably lower total deadhead

### Test Scenario 2: Load Chaining Efficiency
**Setup:**
- 1 driver at position A
- Load 1: Pickup at B (close to A), dropoff at C
- Load 2: Pickup at D (very close to C), dropoff at E
- Load 3: Pickup at F (far from A), dropoff at G

**Expected Result:** 
- Driver assigned Load 1 and Load 2 as chain
- Load 3 remains unassigned (or goes to different driver)
- Total deadhead minimized by efficient chaining

**Success Criteria:** System creates chain for nearby loads, doesn't chain far loads

### Test Scenario 3: Dynamic Rebalancing
**Setup:**
- Driver 1 assigned to Load X (reserved, 10 miles away)
- Driver 2 idle
- New Load Y arrives (2 miles from Driver 1)

**Expected Result:**
- System releases Driver 1 from Load X
- Assigns Driver 1 to Load Y (closer)
- Assigns Driver 2 to Load X
- Net result: Lower total deadhead

**Success Criteria:** Assignments are rebalanced, total deadhead improves

### Test Scenario 4: IN_PROGRESS Protection
**Setup:**
- Driver 1 has Load X (IN_PROGRESS - already picked up)
- New load Y arrives very close to Driver 1

**Expected Result:**
- Load X stays with Driver 1 (cannot reassign)
- Load Y assigned to different driver
- IN_PROGRESS status protects assignment

**Success Criteria:** IN_PROGRESS loads are never reassigned

### Test Scenario 5: Performance Test
**Setup:**
- 50 drivers at random positions
- 50 loads at random positions

**Expected Result:**
- Optimization completes in under 500 milliseconds
- Valid assignment returned (all loads assigned)

**Success Criteria:** Consistently meets 500ms performance target

### Test Scenario 6: No Available Resources
**Setup:**
- 5 loads waiting
- 0 drivers on shift

**Expected Result:**
- Optimization completes quickly
- No assignments made
- Loads remain in AWAITING_DRIVER status

**Success Criteria:** System handles edge case gracefully

### Test Scenario 7: More Loads Than Capacity
**Setup:**
- 2 drivers (capacity for 4 loads total)
- 5 loads available

**Expected Result:**
- 4 loads assigned (some as chains)
- 1 load remains unassigned
- Optimization chooses most efficient 4 loads to assign

**Success Criteria:** System makes best use of limited driver capacity

---

## Success Criteria

The refactoring is considered successful when:

1. ✅ **Code Consolidation**
   - Single entry point for all assignment logic
   - Old duplicated methods deprecated

2. ✅ **Performance Improvement**
   - Automated tests show MIP assignments have 10-30% lower deadhead than greedy
   - Assignment calculation completes in under 500ms for 50x50 scenario

3. ✅ **Chaining Functional**
   - System successfully creates 2-load chains
   - Chains only created when beneficial
   - Tests validate chaining logic

4. ✅ **Rebalancing Works**
   - Reserved assignments can be changed when better option appears
   - IN_PROGRESS assignments are protected
   - Total system deadhead improves after rebalancing

5. ✅ **Quality Gates**
   - All unit tests pass
   - All integration tests pass
   - Code review approved
   - No regression in existing functionality

6. ✅ **Documentation Complete**
   - Technical documentation updated
   - API documentation reflects any changes
   - Deployment guide created

---

## Risks & Mitigation

### Risk 1: OR-Tools Integration Issues
**Risk:** Native library may have compatibility issues  
**Probability:** Low  
**Mitigation:** Use official Maven package with bundled natives; test on all platforms early

### Risk 2: Performance Degradation
**Risk:** Optimization might be too slow for real-world scenarios  
**Probability:** Low  
**Mitigation:** Set solver time limits; accept "good enough" solutions if timeout; performance test continuously

### Risk 3: Algorithm Bugs
**Risk:** Complex optimization might produce invalid assignments  
**Probability:** Medium  
**Mitigation:** Extensive test suite; staged rollout; ability to fallback to old system

### Risk 4: Unexpected Reassignments
**Risk:** Aggressive rebalancing might confuse drivers  
**Probability:** Medium  
**Mitigation:** Add tunable threshold for reassignment; monitor driver feedback; implement "sticky" assignments

---

**Document End**

For questions or clarifications, please contact the technical team.

