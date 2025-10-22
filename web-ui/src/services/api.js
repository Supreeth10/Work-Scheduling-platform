// --- Config & helpers -------------------------------------------------------

export const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Safe access to Vite env; falls back to same-origin when not set.
const API_BASE = (typeof window !== 'undefined' && (import.meta?.env?.VITE_API_URL ?? '')) || '';

function addNoCacheParam(urlStr) {
    const isAbs = /^https?:\/\//i.test(urlStr);
    const u = new URL(urlStr, window.location.origin);
    u.searchParams.set('_t', Date.now().toString());
    // Keep absolute if the input was absolute, otherwise return relative (works with dev proxy)
    return isAbs ? u.href : (u.pathname + u.search);
}

/**
 * Mirrors backend ErrorResponse exactly, plus inherits from Error.
 * {
 *   code, message, status, path, correlationId, timestamp, details
 * }
 */
export class ApiError extends Error {
    constructor(message, meta = {}) {
        super(message);
        this.name = 'ApiError';
        this.code = meta.code ?? null;
        this.status = meta.status ?? null;
        this.path = meta.path ?? null;
        this.correlationId = meta.correlationId ?? null;
        this.timestamp = meta.timestamp ?? null;
        this.details = meta.details ?? null;
    }
    is(code) { return this.code === code; }
}

/**
 * Uniform fetch wrapper
 */
async function request(path, {
    method = 'GET',
    headers,
    body,
    noCacheGet = false,
    signal
} = {}) {
    const isAbs = /^https?:\/\//i.test(path);
    let url = isAbs ? path : `${API_BASE}${path}`;
    if (method === 'GET' && noCacheGet) url = addNoCacheParam(url);

    const res = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        cache: noCacheGet ? 'no-store' : 'default',
        signal
    });

    if (res.status === 204) return { ok: true, status: 204, data: null };

    const ct = res.headers.get('Content-Type') || '';
    let parsed = null, text = null;
    if (ct.includes('application/json')) {
        try { parsed = await res.json(); } catch { /* empty */ }
    } else {
        try { text = await res.text(); } catch { /* empty */ }
    }

    if (!res.ok) {
        if (parsed && typeof parsed === 'object') {
            throw new ApiError(parsed.message || `HTTP ${res.status}`, {
                code: parsed.code,
                status: parsed.status ?? res.status,
                path: parsed.path,
                correlationId: parsed.correlationId,
                timestamp: parsed.timestamp,
                details: parsed.details
            });
        }
        throw new ApiError(text || `HTTP ${res.status}`, { status: res.status });
    }

    return { ok: true, status: res.status, data: parsed ?? text ?? null };
}

// --- DTO normalization ------------------------------------------------------

function normalizeLoad(dto) {
    if (!dto) return null;

    const id = dto.id ?? dto.loadId ?? null;
    const status = dto.status ?? null;
    const currentStop = dto.currentStop ?? dto.nextStop ?? null;

    const toLoc = (p) =>
        p && p.lat != null && p.lng != null
            ? { lat: Number(p.lat), lng: Number(p.lng) }
            : null;

    const pickup = toLoc(dto.pickup);
    const dropoff = toLoc(dto.dropoff);

    const assignedDriver =
        dto.assignedDriver && (dto.assignedDriver.id || dto.assignedDriver.name)
            ? { id: dto.assignedDriver.id ?? null, name: dto.assignedDriver.name ?? null }
            : null;

    return { id, status, currentStop, pickup, dropoff, assignedDriver };
}

// --- Auth-free driver endpoints --------------------------------------------

export async function login(username, opts) {
    const { data } = await request('/api/drivers/login', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: { username },
        ...opts
    });
    return data;
}

export async function startShift(driverId, { lat, lng }, opts) {
    const { data } = await request(`/api/drivers/${driverId}/shift/start`, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: { lat, lng },
        ...opts
    });
    return data ?? {};
}

export async function endShift(driverId, opts) {
    const { data } = await request(`/api/drivers/${driverId}/shift/end`, {
        method: 'POST',
        ...opts
    });
    return data ?? {};
}

// --- Driver state / assignment ---------------------------------------------

export async function getDriverState(driverId, opts) {
    const { data } = await request(`/api/drivers/${driverId}/state`, {
        method: 'GET',
        noCacheGet: true,
        ...opts
    });
    return { ...data, load: normalizeLoad(data.load) };
}

export async function getCurrentAssignment(driverId, opts) {
    const { status, data } = await request(`/api/drivers/${driverId}/assignment`, {
        method: 'GET',
        noCacheGet: true,
        ...opts
    });
    if (status === 204) return null;
    return normalizeLoad(data);
}

// --- Load progression -------------------------------------------------------

export async function completeCurrentStop(driverId, loadId, opts) {
    try {
        const { data } = await request(
            `/api/drivers/${driverId}/loads/${loadId}/stops/complete`,
            { method: 'POST', ...opts }
        );
        return {
            completed: normalizeLoad(data.completed),
            nextAssignment: normalizeLoad(data.nextAssignment)
        };
    } catch (e) {
        if (e instanceof ApiError && e.is('RESERVATION_EXPIRED')) {
            throw e;
        }
        throw e;
    }
}

export async function rejectCurrentLoad(driverId, loadId, opts) {
    const { data } = await request(
        `/api/drivers/${driverId}/loads/${loadId}/reject`,
        { method: 'POST', ...opts }
    );
    return data ?? {};
}

// --- Admin loads ------------------------------------------------------------

export async function getLoads(status, opts) {
    const qs = status ? `?status=${encodeURIComponent(status)}` : '';
    const { data } = await request(`/api/loads${qs}`, {
        method: 'GET',
        noCacheGet: true,
        ...opts
    });
    return (data || []).map(normalizeLoad);
}

export async function createLoad({ pickup, dropoff }, opts) {
    const { data } = await request('/api/loads', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: { pickup, dropoff },
        ...opts
    });
    return data ? normalizeLoad(data) : null;
}
