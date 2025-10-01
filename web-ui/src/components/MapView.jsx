import React, { useEffect } from 'react'
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'

const DefaultIcon = new L.Icon({
    iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
    shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41]
})
L.Marker.prototype.options.icon = DefaultIcon

function FitBounds({ points }) {
    const map = useMap()
    useEffect(() => {
        if (!points || points.length === 0) return
        const bounds = L.latLngBounds(points.map(p => [p.lat, p.lng]))
        map.fitBounds(bounds, { padding: [20, 20] })
    }, [points, map])
    return null
}

export default function MapView({ pickup, dropoff }) {
    const points = [pickup, dropoff].filter(Boolean)
    const center = points[0] || { lat: 39.5, lng: -98.35 } // USA center fallback

    return (
        <MapContainer center={[center.lat, center.lng]} zoom={5} style={{ height: 360, width: '100%' }}>
            <TileLayer
                attribution='&copy; OpenStreetMap contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {pickup && <Marker position={[pickup.lat, pickup.lng]} />}
            {dropoff && <Marker position={[dropoff.lat, dropoff.lng]} />}
            {pickup && dropoff && (
                <Polyline positions={[[pickup.lat, pickup.lng], [dropoff.lat, dropoff.lng]]} />
            )}
            <FitBounds points={points} />
        </MapContainer>
    )
}
