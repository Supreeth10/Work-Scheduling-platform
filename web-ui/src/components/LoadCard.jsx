import React from 'react'

export default function LoadCard({ load, onReject }) {
    // load: { id, status, currentStop, pickup{lat,lng}, dropoff{lat,lng}, assignedDriver? }
    return (
        <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <strong>Load {String(load.id).slice(0, 8)}</strong>
                <span>Status: {load.status}</span>
            </div>
            <div style={{ fontSize: 14, marginTop: 6 }}>
                <div>Pickup: ({load.pickup?.lat}, {load.pickup?.lng})</div>
                <div>Dropoff: ({load.dropoff?.lat}, {load.dropoff?.lng})</div>
                {load.currentStop && <div>Next Stop: {load.currentStop}</div>}
                {load.assignedDriver?.name && <div>Driver: {load.assignedDriver.name}</div>}
            </div>
            {onReject && load.status === 'RESERVED' && (
                <button style={{ marginTop: 8 }} onClick={onReject}>Reject & End Shift</button>
            )}
        </div>
    )
}
