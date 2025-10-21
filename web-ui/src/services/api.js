const JSON_HEADERS = { 'Content-Type': 'application/json' }

//  helper to force no-cache GETs
function noCache(url) {
    const u = new URL(url, window.location.origin)
    u.searchParams.set('_t', Date.now().toString())
    return u.pathname + u.search
}

/** Normalize any load-like DTO to a single UI shape
 * {
 *   id, status, currentStop,
 *   pickup: { lat, lng } | null,
 *   dropoff: { lat, lng } | null,
 *   assignedDriver: { id?, name? } | null
 * }
 */
function normalizeLoad(dto) {
    if (!dto) return null;

    // unify id + currentStop naming across endpoints
    const id = dto.id ?? dto.loadId ?? null;
    const status = dto.status ?? null;
    const currentStop = dto.currentStop ?? dto.nextStop ?? null;

    // backend returns canonical nested { lat, lng }
    const toLoc = (p) =>
        p && p.lat != null && p.lng != null
            ? { lat: Number(p.lat), lng: Number(p.lng) }
            : null;

    const pickup = toLoc(dto.pickup);
    const dropoff = toLoc(dto.dropoff);

    // assignedDriver should already be { id, name }
    const assignedDriver =
        dto.assignedDriver && (dto.assignedDriver.id || dto.assignedDriver.name)
            ? { id: dto.assignedDriver.id ?? null, name: dto.assignedDriver.name ?? null }
            : null;

    return { id, status, currentStop, pickup, dropoff, assignedDriver };
}


export async function login(username) {
    const res = await fetch('/api/drivers/login', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ username })
    })
    if (!res.ok) throw new Error('Login failed')
    return res.json()
}

export async function startShift(driverId, { lat, lng }) {
    const res = await fetch(`/api/drivers/${driverId}/shift/start`, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ lat, lng })
    })
    if (!res.ok) throw new Error('Start shift failed')
    return res.json().catch(() => ({}))
}

export async function endShift(driverId) {
    const res = await fetch(`/api/drivers/${driverId}/shift/end`, { method: 'POST' })
    if (!res.ok) {
        if (res.status === 409) throw new Error('Cannot end shift while on a load.')
        throw new Error('End shift failed')
    }
    return res.json().catch(() => ({}))
}

export async function getDriverState(driverId) {
    const res = await fetch(noCache(`/api/drivers/${driverId}/state`), {
        method: 'GET',
        cache: 'no-store',           // <- prevent stale data
        headers: { 'Cache-Control': 'no-cache' }
    })
    if (!res.ok) throw new Error('Driver state fetch failed')
    const s = await res.json()
    return { ...s, load: normalizeLoad(s.load) }
}

export async function getCurrentAssignment(driverId) {
    const res = await fetch(noCache(`/api/drivers/${driverId}/assignment`), {
        method: 'GET',
        cache: 'no-store',
        headers: { 'Cache-Control': 'no-cache' }
    })
    if (res.status === 204) return null
    if (!res.ok) throw new Error('Assignment fetch failed')
    const raw = await res.json()
    return normalizeLoad(raw)
}

export async function completeCurrentStop(driverId, loadId) {
    const res = await fetch(`/api/drivers/${driverId}/loads/${loadId}/stops/complete`, {
        method: 'POST'
    })

    if (res.status === 409) {
        let msg = 'Reservation expired. Fetch assignment again.'
        try { const t = await res.text(); if (t) msg = t } catch { /* empty */ }
        const err = new Error(msg); err.status = 409; throw err
    }

    if (!res.ok) {
        let msg = 'Complete next stop failed'
        try { const t = await res.text(); if (t) msg = t } catch { /* empty */ }
        const err = new Error(msg); err.status = res.status; throw err
    }

    const raw = await res.json()
    return {
        completed: normalizeLoad(raw.completed),
        nextAssignment: normalizeLoad(raw.nextAssignment)
    }
}

export async function rejectCurrentLoad(driverId, loadId) {
    const res = await fetch(`/api/drivers/${driverId}/loads/${loadId}/reject`, { method: 'POST' })
    if (!res.ok) throw new Error('Reject load failed')
    return res.json().catch(() => ({}))
}

export async function getLoads(status) {
    const qs = status ? `?status=${encodeURIComponent(status)}` : ''
    const res = await fetch(noCache(`/api/loads${qs}`), {
        method: 'GET',
        cache: 'no-store',
        headers: { 'Cache-Control': 'no-cache' }
    })
    if (!res.ok) throw new Error('Loads fetch failed')
    const arr = await res.json()
    return (arr || []).map(normalizeLoad)
}

export async function createLoad({ pickup, dropoff }) {
    const res = await fetch('/api/loads', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ pickup, dropoff })
    })

    if (!res.ok) {
        // Try to decode standardized ErrorResponse from backend
        let payload = null
        try { payload = await res.json() } catch { /* ignore */ }

        const backendMsg =
            payload?.details?.fields?.distinctStops ||
            payload?.message ||
            'Create load failed'

        const err = new Error(backendMsg)
        err.status = res.status
        err.code = payload?.code
        err.details = payload?.details
        err.path = payload?.path
        throw err
    }

    const created = await res.json().catch(() => null)
    return created ? normalizeLoad(created) : null
}
