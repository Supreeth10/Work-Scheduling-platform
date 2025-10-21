import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import MapView from '../components/MapView'
import { createLoad, getLoads } from '../services/api'

function fmt(n) {
    if (n == null || Number.isNaN(Number(n))) return '—'
    return Number(n).toFixed(5)
}

export default function AdminPage() {
    const navigate = useNavigate()

    // notice banner
    const [notice, setNotice] = useState('')
    const [noticeType, setNoticeType] = useState('success') // 'success' | 'info' | 'error'
    const [noticeTimer, setNoticeTimer] = useState(null)

    // allow temporary '' while typing to avoid NaN -> Leaflet crash
    const [pickup, setPickup] = useState({ lat: 39.7392, lng: -104.9903 }) // Denver
    const [dropoff, setDropoff] = useState({ lat: 33.4484, lng: -112.0740 }) // Phoenix

    const [loads, setLoads] = useState([])
    const [status, setStatus] = useState('')
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')

    const showNotice = (msg, type = 'success', ms = 4000) => {
        setNotice(msg)
        setNoticeType(type)
        if (noticeTimer) clearTimeout(noticeTimer)
        const t = setTimeout(() => setNotice(''), ms)
        setNoticeTimer(t)
    }

    const refresh = async () => {
        try {
            setLoading(true); setError('')
            const data = await getLoads(status)
            setLoads(data || [])
        } catch (e) { setError(e.message) } finally { setLoading(false) }
    }

    useEffect(() => { refresh() }, [status])

    // ---------- validation helpers ----------
    const isNum = (v) => typeof v === 'number' && Number.isFinite(v)
    const inLat = (v) => isNum(v) && v >= -90 && v <= 90
    const inLng = (v) => isNum(v) && v >= -180 && v <= 180

    const pickupLatErr =
        pickup.lat === '' ? '' : (inLat(pickup.lat) ? '' : 'Latitude must be between -90 and 90')
    const pickupLngErr =
        pickup.lng === '' ? '' : (inLng(pickup.lng) ? '' : 'Longitude must be between -180 and 180')
    const dropoffLatErr =
        dropoff.lat === '' ? '' : (inLat(dropoff.lat) ? '' : 'Latitude must be between -90 and 90')
    const dropoffLngErr =
        dropoff.lng === '' ? '' : (inLng(dropoff.lng) ? '' : 'Longitude must be between -180 and 180')

    const allFilled = isNum(pickup.lat) && isNum(pickup.lng) && isNum(dropoff.lat) && isNum(dropoff.lng)
    const allValid = inLat(pickup.lat) && inLng(pickup.lng) && inLat(dropoff.lat) && inLng(dropoff.lng)
    const sameCoords =
        allFilled &&
        Number(pickup.lat) === Number(dropoff.lat) &&
        Number(pickup.lng) === Number(dropoff.lng)
    const canSubmit = allFilled && allValid && !sameCoords && !loading

    const submitLoad = async () => {
        try {
            setLoading(true); setError('')

            // local guard (should be disabled already, but double-check)
            if (sameCoords) {
                showNotice('Pickup and dropoff cannot be the same coordinates', 'error', 5000)
                return
            }

            const created = await createLoad({
                pickup: { lat: Number(pickup.lat), lng: Number(pickup.lng) },
                dropoff: { lat: Number(dropoff.lat), lng: Number(dropoff.lng) }
            })
            await refresh()

            const shortId = created?.id ? String(created.id).slice(0, 8) : ''
            showNotice(
                `New load ${shortId ? `(${shortId}) ` : ''}created. Dispatch will run automatically.`,
                'success'
            )
        } catch (e) {
            // 👇 This will show the backend message if present (from apis.js)
            const msg = e?.message || 'Failed to create load.'
            setError(msg)
            showNotice(msg, 'error', 6000)
        } finally {
            setLoading(false)
        }
    }

    const doLogout = () => {
        sessionStorage.removeItem('adminLoggedIn')
        sessionStorage.removeItem('adminName')

        setLoads([])
        setPickup({ lat: 39.7392, lng: -104.9903 })
        setDropoff({ lat: 33.4484, lng: -112.0740 })
        setStatus('')
        setError('')
        navigate('/admin/login', { replace: true })
    }

    // small helper to style error text
    const errStyle = { color: '#c62828', fontSize: 12, marginTop: 4 }

    return (
        <div>
            <h2>Admin</h2>

            {notice && (
                <div
                    role="status"
                    style={{
                        margin: '8px 0 12px',
                        padding: '10px 12px',
                        borderRadius: 8,
                        border: '1px solid',
                        borderColor:
                            noticeType === 'success' ? '#b7ebc6' :
                                noticeType === 'error'   ? '#ffcccc' :
                                    '#cde1ff',
                        background:
                            noticeType === 'success' ? '#f6ffed' :
                                noticeType === 'error'   ? '#fff5f5' :
                                    '#f0f7ff',
                        color: '#222',
                        fontSize: 14,
                        textAlign: 'left'
                    }}
                >
                    {notice}
                </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                {/* Left: Create */}
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                        <span>Admin: <strong>{sessionStorage.getItem('adminName') || 'admin'}</strong></span>
                        <div style={{ marginLeft: 'auto' }}>
                            <button onClick={doLogout}>Logout</button>
                        </div>
                    </div>

                    <h3>New Load</h3>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                        <label>Pickup Lat
                            <input
                                type="number"
                                name="lat"
                                value={pickup.lat}
                                onChange={(e) =>
                                    setPickup(p => ({ ...p, lat: e.target.value === '' ? '' : Number(e.target.value) }))
                                }
                                step="0.000001"
                            />
                            {pickupLatErr && <div style={errStyle}>{pickupLatErr}</div>}
                        </label>

                        <label>Pickup Lng
                            <input
                                type="number"
                                name="lng"
                                value={pickup.lng}
                                onChange={(e) =>
                                    setPickup(p => ({ ...p, lng: e.target.value === '' ? '' : Number(e.target.value) }))
                                }
                                step="0.000001"
                            />
                            {pickupLngErr && <div style={errStyle}>{pickupLngErr}</div>}
                        </label>

                        <label>Dropoff Lat
                            <input
                                type="number"
                                name="lat"
                                value={dropoff.lat}
                                onChange={(e) =>
                                    setDropoff(d => ({ ...d, lat: e.target.value === '' ? '' : Number(e.target.value) }))
                                }
                                step="0.000001"
                            />
                            {dropoffLatErr && <div style={errStyle}>{dropoffLatErr}</div>}
                        </label>

                        <label>Dropoff Lng
                            <input
                                type="number"
                                name="lng"
                                value={dropoff.lng}
                                onChange={(e) =>
                                    setDropoff(d => ({ ...d, lng: e.target.value === '' ? '' : Number(e.target.value) }))
                                }
                                step="0.000001"
                            />
                            {dropoffLngErr && <div style={errStyle}>{dropoffLngErr}</div>}
                        </label>
                    </div>

                    <button
                        style={{ marginTop: 8, opacity: canSubmit ? 1 : 0.6, cursor: canSubmit ? 'pointer' : 'not-allowed' }}
                        onClick={submitLoad}
                        disabled={!canSubmit}
                        title={
                            !allFilled ? 'Enter valid coordinates for all fields' :
                                !allValid ? 'One or more coordinates are out of range' :
                                    sameCoords ? 'Pickup and dropoff cannot be the same coordinates' :
                                        'Create Load'
                        }
                    >
                        Create Load
                    </button>

                    <h4 style={{ marginTop: 16 }}>Preview</h4>
                    <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 8 }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 12, height: 12, background: '#34a853', borderRadius: 2, display: 'inline-block' }} />
              Pickup
            </span>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 12, height: 12, background: '#e53935', borderRadius: 2, display: 'inline-block' }} />
              Dropoff
            </span>
                    </div>

                    <MapView
                        pickup={pickup}
                        dropoff={dropoff}
                        onSetPickup={(pt) => setPickup(pt)}
                        onSetDropoff={(pt) => setDropoff(pt)}
                    />

                    <p style={{ fontSize: 12, color: '#666' }}>
                        Click the map to set whichever point is missing, or drag markers to fine-tune. Inputs stay in sync.
                    </p>
                </div>

                {/* Right: List */}
                <div>
                    <h3>All Loads</h3>

                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
                        <label>Status filter:</label>
                        <select value={status} onChange={e => setStatus(e.target.value)}>
                            <option value="">(any)</option>
                            <option value="AWAITING_DRIVER">AWAITING_DRIVER</option>
                            <option value="RESERVED">RESERVED</option>
                            <option value="IN_PROGRESS">IN_PROGRESS</option>
                            <option value="COMPLETED">COMPLETED</option>
                        </select>
                        <button onClick={refresh} disabled={loading}>Refresh</button>
                    </div>

                    <div>
                        {loads.map(l => (
                            <div
                                key={l.id}
                                style={{
                                    border: '1px solid #ddd',
                                    borderRadius: 8,
                                    padding: 12,
                                    marginBottom: 12,
                                    display: 'grid',
                                    gridTemplateColumns: '1fr 1fr',
                                    gap: 8
                                }}
                            >
                                <div>
                                    <div style={{ fontWeight: 600, marginBottom: 6 }}>
                                        Load {String(l.id).slice(0, 8)}
                                    </div>
                                    <div style={{ fontSize: 14 }}>
                                        <div>Pickup: ({fmt(l.pickup?.lat)}, {fmt(l.pickup?.lng)})</div>
                                        <div>Dropoff: ({fmt(l.dropoff?.lat)}, {fmt(l.dropoff?.lng)})</div>
                                    </div>
                                </div>

                                <div style={{ textAlign: 'right' }}>
                                    <div
                                        style={{
                                            display: 'inline-block',
                                            padding: '2px 8px',
                                            borderRadius: 999,
                                            fontSize: 12,
                                            border: '1px solid #ccc',
                                            background: '#fafafa'
                                        }}
                                        title="Status"
                                    >
                                        {l.status || '—'}
                                    </div>
                                    <div style={{ marginTop: 8, fontSize: 14 }}>
                                        Driver:&nbsp;
                                        <strong>{l.assignedDriver?.name ?? '— (unassigned)'}</strong>
                                    </div>
                                </div>
                            </div>
                        ))}
                        {loads.length === 0 && <p>No loads to show.</p>}
                    </div>
                </div>
            </div>

            {error && <p style={{ color: 'crimson' }}>{error}</p>}
        </div>
    )
}
