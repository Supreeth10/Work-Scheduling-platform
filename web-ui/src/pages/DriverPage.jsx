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

    // timers + abort controllers for polling loops
    const stateLoopRef = useRef({ timer: null })
    const assignLoopRef = useRef({ timer: null })
    const noticeTimerRef = useRef(null)

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

    // Normalize s.load => explicit null here so the rest of the UI can rely on it.
    const refreshState = async (driverId) => {
        const s = await getDriverState(driverId)
        const normalized = { ...s, load: s.load ?? null }
        setState(normalized)
        bumpLastUpdated() // <- update "Last updated" on every state poll
        return normalized
    }

    const doRefresh = async () => {
        try {
            setLoading(true); setError('')
            const id = getDriverId()
            if (!id) throw new Error('No driver logged in to refresh.')

            const s = await refreshState(id)

            // If on shift but no load, proactively try to reserve a new one
            if (s.driver?.onShift && !s.load) {
                const a = await getCurrentAssignment(id)
                if (a) {
                    setState(prev => ({ ...(prev || s), load: a }))
                    showNotice('A load has been reserved for you.', 'success')
                    bumpLastUpdated()
                } else {
                    showNotice('No assignment yet. We’ll keep checking…', 'info')
                }
            }

            console.log('[Driver] State refreshed at', new Date().toISOString())
        } catch (e) {
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
        stopPolling(); // safety

        // STATE LOOP
        const stateTick = async () => {
            try {
                const id = getDriverId()
                if (!id) return
                console.log('[poll] /state tick @', new Date().toISOString())
                await refreshState(id) // refreshState bumps lastUpdated
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
                // 1) Always get fresh state (authoritative), update UI timestamp
                const s = await getDriverState(id)
                const normalized = { ...s, load: s.load ?? null }
                setState(normalized)
                bumpLastUpdated()

                // 2) Only try reserving when on shift & currently unassigned
                if (normalized.driver?.onShift && !normalized.load) {
                    console.log('[poll] /assignment attempt @', new Date().toISOString())
                    const a = await getCurrentAssignment(id)
                    if (a) {
                        setState(prev => ({ ...(prev || normalized), load: a }))
                        showNotice('A load has been reserved for you.', 'success')
                        bumpLastUpdated()
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
    }

    useEffect(() => () => stopPolling(), [])
    // If a driver appears (e.g., after login, or if you restore a session later), start polling.
    // If driver goes away (logout), stop polling.
    useEffect(() => {
        if (driver?.id) {
            startPolling();
            return () => stopPolling();
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

            // Validate blanks before parse
            const latStr = String(lat).trim()
            const lngStr = String(lng).trim()
            if (latStr === '' || lngStr === '') throw new Error('Please provide valid start coordinates.')

            const latNum = parseFloat(latStr)
            const lngNum = parseFloat(lngStr)
            if (Number.isNaN(latNum) || Number.isNaN(lngNum)) {
                throw new Error('Please provide valid start coordinates.')
            }
            if (latNum < -90 || latNum > 90 || lngNum < -180 || lngNum > 180) {
                throw new Error('lat must be between -90 and 90, lng between -180 and 180.')
            }

            const id = getDriverId()
            await startShift(id, { lat: latNum, lng: lngNum })
            // Ensure polling is running after a shift starts (idempotent—startPolling() clears/restarts)
            startPolling();
            const a = await getCurrentAssignment(id)
            if (a) {
                setState(prev => ({ ...(prev || {}), driver: { ...(prev?.driver || {}), onShift: true }, load: a }))
                bumpLastUpdated()
                showNotice('Shift started. A load was reserved for you.', 'success')
            } else {
                await refreshState(id)
                showNotice('Shift started. Waiting for a load…', 'info')
            }
        } catch (e) {
            if (e instanceof ApiError) {
                if (e.is('SHIFT_ALREADY_ACTIVE')) {
                    setError('You already have an active shift.')
                } else if (e.is('VALIDATION_ERROR')) {
                    setError(e.message || 'Please check your coordinates and try again.')
                } else {
                    setError(e.message || 'Start shift failed')
                }
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

            // Decide message based on what we were about to complete
            const prevStop = current.currentStop // 'PICKUP' or 'DROPOFF'

            const { completed, nextAssignment } = await completeCurrentStop(id, current.id)

            // Refresh authoritative state
            const s = await refreshState(id)

            // If server already included a next assignment, show it right away
            if (nextAssignment) {
                setState(prev => ({ ...(prev || s), load: nextAssignment }))
                bumpLastUpdated()
            }

            // Message based on the stop we *just completed* (prevStop)
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
                // Clear stale load immediately
                setState(prev => prev ? { ...prev, load: null } : prev)
                setError(e.message || 'Reservation expired. Checking for a new assignment…')

                try {
                    const id = getDriverId()

                    // Refresh authoritative state (should reflect no load)
                    const s = await refreshState(id)

                    // Try to reserve a fresh assignment if still on shift
                    if (s.driver?.onShift) {
                        const a = await getCurrentAssignment(id)
                        if (a) {
                            setState(prev => ({ ...(prev || s), load: a }))
                            showNotice('A new load has been reserved for you.', 'success')
                            bumpLastUpdated()
                        } else {
                            showNotice('No assignment yet. We’ll keep checking…', 'info')
                        }
                    }

                    // Nudge Admin page (if open) to refresh immediately
                    window.dispatchEvent(new CustomEvent('loads:refresh'))
                } catch {
                    // polling will keep things in sync
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

            // Show the message first (it will remain visible after we "log out")
            showNotice('Load rejected and your shift has been ended.', 'info')

            // Nudge Admin page (if open) to refresh
            window.dispatchEvent(new CustomEvent('loads:refresh'))

            // Now take them back to the login screen
            logoutToLogin()
        } catch (e) {
            if (e instanceof ApiError) {
                if (e.is('ACCESS_DENIED')) {
                    setError('This load is not assigned to you.')
                } else if (e.is('LOAD_STATE_CONFLICT')) {
                    setError('This load cannot be rejected in its current state.')
                } else {
                    setError(e.message || 'Reject failed')
                }
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

            // Nudge Admin to refresh as well
            window.dispatchEvent(new CustomEvent('loads:refresh'))
        } catch (e) {
            if (e instanceof ApiError) {
                if (e.is('ACTIVE_LOAD_PRESENT')) {
                    setError('You cannot end your shift while you have an active load.')
                } else if (e.is('SHIFT_NOT_ACTIVE')) {
                    setError('Your shift is not active.')
                } else {
                    setError(e.message || 'End shift failed')
                }
            } else {
                setError('Network error while ending shift')
            }
        } finally {
            setLoading(false)
        }
    }

    const doGetAssignment = async () => {
        try {
            setLoading(true); setError('')
            const id = getDriverId()
            const a = await getCurrentAssignment(id)
            if (a) {
                setState(prev => ({ ...(prev || {}), load: a }))
                showNotice('A load was reserved for you.', 'success')
                bumpLastUpdated()
            } else {
                showNotice('No assignment yet. We’ll keep checking…', 'info')
            }
            bumpLastUpdated()
        } catch (e) {
            showApiError(e, 'Get assignment failed')
        } finally {
            setLoading(false)
        }
    }

    const logoutToLogin = () => {
        // stop timers
        stopPolling()
        // clear driver/session-ish state so the login form shows
        setDriver(null)
        setState(null)
        setLat('')
        setLng('')
        setLastUpdated(null)
        // keep the notice so the banner remains visible
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
