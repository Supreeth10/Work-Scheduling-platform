import React, { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import MapView from '../components/MapView'
import LoadCard from '../components/LoadCard'
import {
    login,
    startShift,
    endShift,
    completeCurrentStop,
    rejectCurrentLoad,
    getDriverState,
    getCurrentAssignment,
    ApiError,
} from '../services/api'

// Poll cadences
const STATE_POLL_MS = 12000;  // refresh driver state
const ASSIGN_POLL_MS = 8000;  // try to reserve assignment when unassigned

export default function DriverPage() {
    const [username, setUsername] = useState('alex')
    const [driver, setDriver] = useState(null) // DriverDto
    const [state, setState] = useState(null)   // DriverStateResponse (normalized load)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [lastUpdated, setLastUpdated] = useState(null)

    // success/info banner
    const [notice, setNotice] = useState('')
    const [noticeType, setNoticeType] = useState('info') // 'info' | 'success'

    // start-shift location inputs
    const [lat, setLat] = useState('')
    const [lng, setLng] = useState('')

    const navigate = useNavigate()

    // timers
    const stateLoopRef = useRef({ timer: null })
    const assignLoopRef = useRef({ timer: null })
    const noticeTimerRef = useRef(null)

    //  track the "current driver id" to ignore stale responses
    const currentDriverIdRef = useRef(null)
    useEffect(() => { currentDriverIdRef.current = driver?.id ?? null }, [driver?.id])

    // AbortControllers to cancel in-flight requests on user switch/stopPolling
    const stateAbortRef = useRef(null)
    const assignAbortRef = useRef(null)

    // single-flight guard for /assignment (prevents duplicate calls) //
    const assignInFlightRef = useRef(false)

    const getDriverId = (d = driver) => d?.id

    const showNotice = (msg, type = 'info') => {
        setNotice(msg)
        setNoticeType(type)
        if (noticeTimerRef.current) clearTimeout(noticeTimerRef.current)
        noticeTimerRef.current = setTimeout(() => setNotice(''), 5000)
    }

    const showApiError = (e, fallback = 'Something went wrong') => {
        if (e instanceof ApiError) setError(e.message || fallback)
        else setError(e?.message || fallback)
    }

    const bumpLastUpdated = () => setLastUpdated(new Date())

    const loadToMapPoints = (load) => {
        if (!load) return { pickup: null, dropoff: null }
        return {
            pickup:  load.pickup  ? { lat: load.pickup.lat,  lng: load.pickup.lng }  : null,
            dropoff: load.dropoff ? { lat: load.dropoff.lat, lng: load.dropoff.lng } : null
        }
    }

    // Helper: apply state only if response belongs to current driver
    const applyIfCurrent = (forDriverId, fn) => {                          // <-- CHANGED
        if (currentDriverIdRef.current !== forDriverId) return               // ignore stale responses
        fn()
    }

    // Normalize s.load => explicit null here so the rest of the UI can rely on it.
    const refreshState = async (driverId, opts) => {                       // <-- CHANGED (pass signal)
        const s = await getDriverState(driverId, opts)
        const normalized = { ...s, load: s.load ?? null }
        applyIfCurrent(driverId, () => {                                     // <-- CHANGED
            setState(normalized)
            bumpLastUpdated()
        })
        return normalized
    }

    const doRefresh = async () => {
        try {
            setLoading(true); setError('')
            const id = getDriverId()
            if (!id) throw new Error('No driver logged in to refresh.')

            // pass AbortController signal
            const ac = new AbortController(); stateAbortRef.current = ac
            const s = await refreshState(id, { signal: ac.signal })

            // Guard proactive assignment with single-flight + "still current user"
            if (s.driver?.onShift && !s.load && !assignInFlightRef.current && currentDriverIdRef.current === id) {
                assignInFlightRef.current = true
                try {
                    const a = await getCurrentAssignment(id, { signal: ac.signal })
                    applyIfCurrent(id, () => {
                        if (a) {
                            setState(prev => ({ ...(prev || s), load: a }))
                            showNotice('A load has been reserved for you.', 'success')
                            bumpLastUpdated()
                        } else {
                            showNotice('No assignment yet. We’ll keep checking…', 'info')
                        }
                    })
                } finally {
                    assignInFlightRef.current = false
                }
            }

            console.log('[Driver] State refreshed at', new Date().toISOString())
        } catch (e) {
            if (e.name === 'AbortError') return
            setError(e.message || 'Failed to refresh state')
            console.error('[Driver] Refresh failed:', e)
        } finally {
            setLoading(false)
        }
    }

    const doLogin = async () => {
        try {
            setLoading(true); setError('')
            const d = await login(username.trim())
            setDriver(d)
            await refreshState(d.id)
            startPolling()
        } catch (e) {
            showApiError(e, 'Login failed')
        } finally {
            setLoading(false)
        }
    }

    const doLogout = () => {
        stopPolling()
        setDriver(null)
        setState(null)
        setError('')
        setNotice('')
        setLastUpdated(null)
        setLat(''); setLng('')
        navigate('/driver', { replace: true })
    }

    // -------- Polling (self-scheduling setTimeout loops) --------
    const startPolling = () => {
        stopPolling(); // safety (also aborts in-flight)                         // <-- CHANGED

        // STATE LOOP
        const stateTick = async () => {
            try {
                const id = getDriverId()
                if (!id) return
                console.log('[poll] /state tick @', new Date().toISOString())

                // create a fresh AbortController per tick                        // <-- CHANGED
                const ac = new AbortController(); stateAbortRef.current = ac          // <-- CHANGED
                await refreshState(id, { signal: ac.signal })                          // <-- CHANGED
            } catch (e) {
                if (e.name !== 'AbortError') console.warn('[Driver] state poll failed:', e)
            } finally {
                stateLoopRef.current.timer = setTimeout(stateTick, STATE_POLL_MS)
            }
        }
        stateTick()

        // ASSIGNMENT LOOP (only acts when on-shift & unassigned)
        const assignTick = async () => {
            try {
                const id = getDriverId()
                if (!id) return
                console.log('[poll] /state (pre-assign) tick @', new Date().toISOString())

                // Always get fresh state (authoritative)
                const ac = new AbortController(); assignAbortRef.current = ac
                const s = await getDriverState(id, { signal: ac.signal })
                const normalized = { ...s, load: s.load ?? null }

                applyIfCurrent(id, () => {
                    setState(normalized)
                    bumpLastUpdated()
                })

                // Only try reserving when on shift & currently unassigned
                if (normalized.driver?.onShift && !normalized.load
                    && !assignInFlightRef.current
                    && currentDriverIdRef.current === id) {
                    console.log('[poll] /assignment attempt @', new Date().toISOString())
                    assignInFlightRef.current = true
                    try {
                        const a = await getCurrentAssignment(id, { signal: ac.signal })
                        applyIfCurrent(id, () => {
                            if (a) {
                                setState(prev => ({ ...(prev || normalized), load: a }))
                                showNotice('A load has been reserved for you.', 'success')
                                bumpLastUpdated()
                            }
                        })
                    } finally {
                        assignInFlightRef.current = false
                    }
                }
            } catch (e) {
                if (e.name !== 'AbortError') console.warn('[Driver] assignment poll failed:', e)
            } finally {
                assignLoopRef.current.timer = setTimeout(assignTick, ASSIGN_POLL_MS)
            }
        }
        assignTick()
    }

    const stopPolling = () => {
        if (stateLoopRef.current?.timer) clearTimeout(stateLoopRef.current.timer)
        if (assignLoopRef.current?.timer) clearTimeout(assignLoopRef.current.timer)
        if (noticeTimerRef.current) clearTimeout(noticeTimerRef.current)
        stateLoopRef.current = { timer: null }
        assignLoopRef.current = { timer: null }
        noticeTimerRef.current = null

        // NEW: abort any in-flight fetches so their responses can't set stale state  // <-- CHANGED
        try { stateAbortRef.current?.abort() } catch {}                              // <-- CHANGED
        try { assignAbortRef.current?.abort() } catch {}                             // <-- CHANGED
        stateAbortRef.current = null
        assignAbortRef.current = null
        assignInFlightRef.current = false
    }

    useEffect(() => () => stopPolling(), [])
    useEffect(() => {
        if (driver?.id) {
            startPolling();
            return () => stopPolling();
        } else {
            stopPolling(); // ensure old in-flight calls are aborted when driver clears // <-- CHANGED
        }
    }, [driver?.id]);

    const getBrowserLocation = async () => {
        if (!('geolocation' in navigator)) throw new Error('Geolocation not supported by this browser')
        return new Promise((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(
                (pos) => resolve(pos.coords),
                (err) => reject(new Error(err.message || 'Failed to get location')),
                { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
            )
        })
    }

    const doUseMyLocation = async () => {
        try {
            setLoading(true); setError('')
            const coords = await getBrowserLocation()
            setLat(coords.latitude.toFixed(6))
            setLng(coords.longitude.toFixed(6))
        } catch (e) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    const doStartShift = async () => {
        try {
            setLoading(true); setError('')

            const latStr = String(lat).trim()
            const lngStr = String(lng).trim()
            if (latStr === '' || lngStr === '') throw new Error('Please provide valid start coordinates.')

            const latNum = parseFloat(latStr)
            const lngNum = parseFloat(lngStr)
            if (Number.isNaN(latNum) || Number.isNaN(lngNum)) throw new Error('Please provide valid start coordinates.')
            if (latNum < -90 || latNum > 90 || lngNum < -180 || lngNum > 180) {
                throw new Error('lat must be between -90 and 90, lng between -180 and 180.')
            }

            const id = getDriverId()
            await startShift(id, { lat: latNum, lng: lngNum })
            startPolling();                 // idempotent—clears/restarts loops
            await refreshState(id)          // show latest state
            showNotice('Shift started. Waiting for a load…', 'info')
        } catch (e) {
            if (e instanceof ApiError) {
                if (e.is('SHIFT_ALREADY_ACTIVE')) setError('You already have an active shift.')
                else if (e.is('VALIDATION_ERROR')) setError(e.message || 'Please check your coordinates and try again.')
                else setError(e.message || 'Start shift failed')
            } else {
                setError(e.message || 'Network error while starting shift')
            }
        } finally {
            setLoading(false)
        }
    }

    /** Complete next stop with success banners for pickup/drop-off outcomes */
    const doCompleteNext = async () => {
        try {
            setLoading(true); setError('')

            const id = getDriverId()
            const current = state?.load
            if (!current?.id) throw new Error('No active load to complete.')

            const prevStop = current.currentStop // 'PICKUP' or 'DROPOFF'
            const { completed, nextAssignment } = await completeCurrentStop(id, current.id)

            // Refresh authoritative state
            const s = await refreshState(id)

            // If server already included a next assignment, show it right away
            if (nextAssignment) {
                applyIfCurrent(id, () => {                                  // <-- CHANGED
                    setState(prev => ({ ...(prev || s), load: nextAssignment }))
                    bumpLastUpdated()
                })
            }

            if (prevStop === 'PICKUP') {
                showNotice('Pickup complete. Proceed to the drop-off.', 'success')
            } else if (prevStop === 'DROPOFF') {
                if (nextAssignment) {
                    showNotice('Drop-off complete. A new load has been assigned to you.', 'success')
                } else {
                    showNotice('Drop-off complete. Waiting for your next assignment…', 'success')
                }
            }
        } catch (e) {
            if (e instanceof ApiError && e.is('RESERVATION_EXPIRED')) {
                applyIfCurrent(getDriverId(), () => {                       // <-- CHANGED
                    setState(prev => prev ? { ...prev, load: null } : prev)
                })
                setError(e.message || 'Reservation expired. Checking for a new assignment…')

                try {
                    const id = getDriverId()
                    const s = await refreshState(id)

                    if (s.driver?.onShift && !assignInFlightRef.current && currentDriverIdRef.current === id) { // <-- CHANGED
                        assignInFlightRef.current = true
                        try {
                            const a = await getCurrentAssignment(id)
                            applyIfCurrent(id, () => {                             // <-- CHANGED
                                if (a) {
                                    setState(prev => ({ ...(prev || s), load: a }))
                                    showNotice('A new load has been reserved for you.', 'success')
                                    bumpLastUpdated()
                                } else {
                                    showNotice('No assignment yet. We’ll keep checking…', 'info')
                                }
                            })
                        } finally {
                            assignInFlightRef.current = false
                        }
                    }

                    // REMOVED: window.dispatchEvent('loads:refresh') — polling is enough  // <-- CHANGED
                } catch {
                    /* noop; polling will keep things in sync */
                }
            } else if (e instanceof ApiError && e.is('LOAD_STATE_CONFLICT')) {
                setError(e.message || 'This load changed state. Refreshing…')
                try { await refreshState(getDriverId()) } catch {}
            } else if (e instanceof ApiError && e.is('ACCESS_DENIED')) {
                setError('You are not allowed to modify this load.')
            } else if (e instanceof ApiError) {
                setError(e.message || 'Complete next stop failed')
            } else {
                setError('Network error while completing stop')
            }
        } finally {
            setLoading(false)
        }
    }

    const doReject = async (load) => {
        try {
            setLoading(true); setError('')
            const id = getDriverId()
            const loadId = load?.id
            await rejectCurrentLoad(id, loadId)

            showNotice('Load rejected and your shift has been ended.', 'info')

            // REMOVED: window.dispatchEvent('loads:refresh') — polling is enough     // <-- CHANGED

            logoutToLogin()
        } catch (e) {
            if (e instanceof ApiError) {
                if (e.is('ACCESS_DENIED')) setError('This load is not assigned to you.')
                else if (e.is('LOAD_STATE_CONFLICT')) setError('This load cannot be rejected in its current state.')
                else setError(e.message || 'Reject failed')
            } else {
                setError('Network error while rejecting load')
            }
        } finally {
            setLoading(false)
        }
    }

    const doEndShift = async () => {
        try {
            setLoading(true); setError('')
            const id = getDriverId()
            await endShift(id)
            await refreshState(id)
            showNotice('Shift ended.', 'info')

            // REMOVED: window.dispatchEvent('loads:refresh') — polling is enough     // <-- CHANGED
        } catch (e) {
            if (e instanceof ApiError) {
                if (e.is('ACTIVE_LOAD_PRESENT')) setError('You cannot end your shift while you have an active load.')
                else if (e.is('SHIFT_NOT_ACTIVE')) setError('Your shift is not active.')
                else setError(e.message || 'End shift failed')
            } else {
                setError('Network error while ending shift')
            }
        } finally {
            setLoading(false)
        }
    }

    const doGetAssignment = async () => {
        if (assignInFlightRef.current) return
        assignInFlightRef.current = true
        try {
            setLoading(true); setError('')
            const id = getDriverId()
            const a = await getCurrentAssignment(id)
            applyIfCurrent(id, () => {                                     // <-- CHANGED
                if (a) {
                    setState(prev => ({ ...(prev || {}), load: a }))
                    showNotice('A load was reserved for you.', 'success')
                    bumpLastUpdated()
                } else {
                    showNotice('No assignment yet. We’ll keep checking…', 'info')
                }
                bumpLastUpdated()
            })
        } catch (e) {
            showApiError(e, 'Get assignment failed')
        } finally {
            assignInFlightRef.current = false
            setLoading(false)
        }
    }

    const logoutToLogin = () => {
        stopPolling()
        setDriver(null)
        setState(null)
        setLat('')
        setLng('')
        setLastUpdated(null)
        navigate('/driver', { replace: true })
    }

    const assigned = state?.load ?? null
    const isOnShift = !!state?.driver?.onShift
    const { pickup, dropoff } = loadToMapPoints(assigned)

    const fmtTime = (d) =>
        d ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'

    return (
        <div>
            <h2>Driver</h2>

            {/* Notice banner */}
            {notice && (
                <div
                    role="status"
                    style={{
                        margin: '8px 0 12px',
                        padding: '10px 12px',
                        borderRadius: 8,
                        border: '1px solid',
                        borderColor: noticeType === 'success' ? '#b7ebc6' : '#cde1ff',
                        background: noticeType === 'success' ? '#f6ffed' : '#f0f7ff',
                        color: '#222',
                        fontSize: 14
                    }}
                >
                    {notice}
                </div>
            )}

            {!driver ? (
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <input value={username} onChange={e => setUsername(e.target.value)} placeholder="username" />
                    <button onClick={doLogin} disabled={loading}>Login</button>
                </div>
            ) : (
                <>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
                        <span>Driver: <strong>{driver.name || username}</strong></span>
                        <button onClick={doRefresh} disabled={loading}>Refresh</button>
                        <div style={{ marginLeft: 'auto', fontSize: 12, color: '#666' }}>
                            {lastUpdated ? `Last updated: ${fmtTime(lastUpdated)}` : 'Not updated yet'}
                        </div>
                        <div style={{ marginLeft: 12 }}>
                            <button onClick={doLogout} disabled={loading}>Logout</button>
                        </div>
                    </div>

                    {!isOnShift && (
                        <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, marginBottom: 12 }}>
                            <h3 style={{ marginTop: 0 }}>Start Shift</h3>
                            <p style={{ marginTop: 0, color: '#555', fontSize: 14 }}>
                                Provide your current location to begin receiving assignments.
                            </p>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 8, alignItems: 'end' }}>
                                <label>Latitude
                                    <input
                                        type="number"
                                        value={lat}
                                        placeholder="e.g. 40.01499"
                                        onChange={e => setLat(e.target.value)}
                                        step="0.000001"
                                    />
                                </label>
                                <label>Longitude
                                    <input
                                        type="number"
                                        value={lng}
                                        placeholder="-105.27055"
                                        onChange={e => setLng(e.target.value)}
                                        step="0.000001"
                                    />
                                </label>
                                <button onClick={doUseMyLocation} disabled={loading}>Use my location</button>
                            </div>
                            <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
                                <button
                                    onClick={doStartShift}
                                    disabled={
                                        loading ||
                                        String(lat).trim() === '' ||
                                        String(lng).trim() === ''
                                    }
                                >
                                    Start Shift
                                </button>
                            </div>
                        </div>
                    )}

                    {isOnShift && !assigned && (
                        <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
                            <button onClick={doGetAssignment} disabled={loading}>Get Assignment</button>
                            <button onClick={doEndShift} disabled={loading}>
                                End Shift
                            </button>
                        </div>
                    )}

                    {isOnShift && assigned && (
                        <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
                            {assigned.status === 'RESERVED' && (
                                <button onClick={() => doReject(assigned)} disabled={loading}>Reject</button>
                            )}
                            <button onClick={doCompleteNext} disabled={loading}>
                                Complete Next Stop ({assigned.currentStop})
                            </button>
                            <button onClick={doEndShift} disabled={loading || !!assigned}>
                                End Shift
                            </button>
                        </div>
                    )}

                    {assigned && (
                        <>
                            <LoadCard
                                load={assigned}
                                onReject={assigned.status === 'RESERVED' ? () => doReject(assigned) : null}
                            />
                            <MapView pickup={pickup} dropoff={dropoff} />
                        </>
                    )}

                    {!assigned && isOnShift && <p>No assignment yet. We’ll keep checking…</p>}
                </>
            )}

            {error && <p style={{ color: 'crimson' }}>{error}</p>}
        </div>
    )
}
