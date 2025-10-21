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
    getCurrentAssignment
} from '../services/api'

export default function DriverPage() {
    const [username, setUsername] = useState('alex')
    const [driver, setDriver] = useState(null) // DriverDto
    const [state, setState] = useState(null)   // DriverStateResponse (normalized load)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [lastUpdated, setLastUpdated] = useState(null)

    // NEW: success/info banner
    const [notice, setNotice] = useState('')
    const [noticeType, setNoticeType] = useState('info') // 'info' | 'success'

    // start-shift location inputs
    const [lat, setLat] = useState('')
    const [lng, setLng] = useState('')

    const navigate = useNavigate()
    const statePollRef = useRef(null)
    const assignmentPollRef = useRef(null)
    const noticeTimerRef = useRef(null)

    const getDriverId = (d = driver) => d?.id

    const showNotice = (msg, type = 'info') => {
        setNotice(msg)
        setNoticeType(type)
        if (noticeTimerRef.current) clearTimeout(noticeTimerRef.current)
        noticeTimerRef.current = setTimeout(() => setNotice(''), 5000)
    }

    const loadToMapPoints = (load) => {
        if (!load) return { pickup: null, dropoff: null }
        return {
            pickup: load.pickup ? { lat: load.pickup.lat, lng: load.pickup.lng } : null,
            dropoff: load.dropoff ? { lat: load.dropoff.lat, lng: load.dropoff.lng } : null
        }
    }

    const refreshState = async (driverId) => {
        const s = await getDriverState(driverId)
        setState(s)
        setLastUpdated(new Date())
        return s
    }

    const doRefresh = async () => {
        try {
            setLoading(true)
            setError('')
            const id = getDriverId()
            if (!id) throw new Error('No driver logged in to refresh.')
            const s = await refreshState(id)
            if (!s.load) setState(prev => prev ? { ...prev, load: null } : s)
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
            const d = await login(username)
            setDriver(d)
            await refreshState(d.id)
            startPolling()
        } catch (e) {
            setError(e.message)
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

    const startPolling = () => {
        stopPolling()

        // Poll /state every ~12s
        statePollRef.current = setInterval(async () => {
            try {
                const id = getDriverId()
                if (!id) return
                await refreshState(id)
            } catch (e) {
                console.warn('[Driver] state poll failed:', e)
            }
        }, 12000)

        // Poll /assignment every ~23s when on-shift & unassigned
        assignmentPollRef.current = setInterval(async () => {
            try {
                const id = getDriverId()
                if (!id) return
                const s = await getDriverState(id)
                setState(s)
                setLastUpdated(new Date())
                if (!s?.driver?.onShift || s?.load) return
                const a = await getCurrentAssignment(id)
                if (a) setState(prev => ({ ...(prev || {}), load: a }))
            } catch (e) {
                console.warn('[Driver] assignment poll failed:', e)
            }
        }, 23000)
    }

    const stopPolling = () => {
        if (statePollRef.current) clearInterval(statePollRef.current)
        if (assignmentPollRef.current) clearInterval(assignmentPollRef.current)
        if (noticeTimerRef.current) clearTimeout(noticeTimerRef.current)
        statePollRef.current = null
        assignmentPollRef.current = null
        noticeTimerRef.current = null
    }

    useEffect(() => () => stopPolling(), [])

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

            const latNum = parseFloat(lat)
            const lngNum = parseFloat(lng)
            if (Number.isNaN(latNum) || Number.isNaN(lngNum)) {
                throw new Error('Please provide valid numeric lat and lng (or use "Use my location").')
            }
            if (latNum < -90 || latNum > 90 || lngNum < -180 || lngNum > 180) {
                throw new Error('lat must be between -90 and 90, lng between -180 and 180.')
            }

            const id = getDriverId()
            await startShift(id, { lat: latNum, lng: lngNum })

            const a = await getCurrentAssignment(id)
            if (a) {
                setState(prev => ({ ...(prev || {}), driver: { ...(prev?.driver || {}), onShift: true }, load: a }))
                setLastUpdated(new Date())
                showNotice('Shift started. A load was reserved for you.', 'success')
            } else {
                await refreshState(id)
                showNotice('Shift started. Waiting for a load…', 'info')
            }
        } catch (e) {
            setError(e.message)
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

            // 👇 Decide message based on what we were about to complete
            const prevStop = current.currentStop // 'PICKUP' or 'DROPOFF'

            const { completed, nextAssignment } = await completeCurrentStop(id, current.id)

            // Refresh authoritative state
            const s = await refreshState(id)

            // If server already included a next assignment, show it right away
            if (nextAssignment) {
                setState(prev => ({ ...(prev || s), load: nextAssignment }))
            }

            // ✅ Message based on the stop we *just completed* (prevStop)
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
            if (e.status === 409 || /reservation expired/i.test(e.message)) {
                setError('Your reservation expired because pickup was not completed in time. Refreshing and checking for a new assignment…')
                try {
                    const id = getDriverId()
                    const s = await refreshState(id)
                    if (!s.load && s.driver?.onShift) {
                        const a = await getCurrentAssignment(id)
                        if (a) setState(prev => ({ ...(prev || s), load: a }))
                    }
                } catch { /* polling will keep things in sync */ }
            } else {
                setError(e.message || 'Complete next stop failed')
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

            // Now take them back to the login screen
            logoutToLogin()
        } catch (e) {
            setError(e.message)
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
            } else {
                setError('No suitable load available yet.')
            }
            setLastUpdated(new Date())
        } catch (e) {
            setError(e.message)
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
        // (we don't clear `notice` here)
        navigate('/driver', { replace: true })
    }

    const assigned = state?.load ?? null
    const isOnShift = !!state?.driver?.onShift
    const { pickup, dropoff } = loadToMapPoints(assigned)

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
                            {lastUpdated ? `Last updated: ${lastUpdated.toLocaleTimeString()}` : 'Not updated yet'}
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
                                <button onClick={doStartShift} disabled={loading}>Start Shift</button>
                            </div>
                        </div>
                    )}

                    {isOnShift && !assigned && (
                        <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
                            <button onClick={doGetAssignment} disabled={loading}>Get Assignment</button>
                            <button
                                onClick={() => endShift(getDriverId()).then(() => refreshState(getDriverId()))}
                                disabled={loading}
                            >
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
                            <button
                                onClick={() => endShift(getDriverId()).then(() => refreshState(getDriverId()))}
                                disabled={loading || !!assigned}
                            >
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
