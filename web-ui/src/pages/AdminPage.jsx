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

    const [pickup, setPickup] = useState({ lat: 39.7392, lng: -104.9903 }) // Denver
    const [dropoff, setDropoff] = useState({ lat: 33.4484, lng: -112.0740 }) // Phoenix
    const [loads, setLoads] = useState([])
    const [status, setStatus] = useState('') // '', AWAITING_DRIVER, RESERVED, IN_PROGRESS, COMPLETED
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')

    const refresh = async () => {
        try {
            setLoading(true); setError('')
            const data = await getLoads(status)
            setLoads(data || [])
        } catch (e) { setError(e.message) } finally { setLoading(false) }
    }

    useEffect(() => { refresh() }, [status])

    const submitLoad = async () => {
        try {
            setLoading(true); setError('')
            await createLoad({ pickup, dropoff })
            await refresh()
        } catch (e) { setError(e.message) } finally { setLoading(false) }
    }

    // ✅ Proper SPA logout: clear local state and navigate away
    const doLogout = () => {
        setLoads([])
        setPickup({ lat: 39.7392, lng: -104.9903 })
        setDropoff({ lat: 33.4484, lng: -112.0740 })
        setStatus('')
        setError('')
        // Send user to the landing route (your App redirects "/" -> "/driver")
        navigate('/', { replace: true })
    }

    return (
        <div>
            <h2>Admin</h2>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                {/* Left: Create */}
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                        <span>Admin: <strong>admin</strong></span>
                        <div style={{ marginLeft: 'auto' }}>
                            <button onClick={doLogout}>Logout</button>
                        </div>
                    </div>

                    <h3>New Load</h3>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                        <label>Pickup Lat
                            <input type="number" value={pickup.lat} onChange={e => setPickup(p => ({ ...p, lat: parseFloat(e.target.value) }))} />
                        </label>
                        <label>Pickup Lng
                            <input type="number" value={pickup.lng} onChange={e => setPickup(p => ({ ...p, lng: parseFloat(e.target.value) }))} />
                        </label>
                        <label>Dropoff Lat
                            <input type="number" value={dropoff.lat} onChange={e => setDropoff(d => ({ ...d, lat: parseFloat(e.target.value) }))} />
                        </label>
                        <label>Dropoff Lng
                            <input type="number" value={dropoff.lng} onChange={e => setDropoff(d => ({ ...d, lng: parseFloat(e.target.value) }))} />
                        </label>
                    </div>
                    <button style={{ marginTop: 8 }} onClick={submitLoad} disabled={loading}>Create Load</button>

                    <h4 style={{ marginTop: 16 }}>Preview</h4>
                    <MapView pickup={pickup} dropoff={dropoff} />
                    <p style={{ fontSize: 12, color: '#666' }}>Later we can add “click on map to set points”.</p>
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
