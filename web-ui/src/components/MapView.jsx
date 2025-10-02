import React, { useEffect, useMemo } from 'react'
import { MapContainer, TileLayer, Marker, Polyline, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'

// ---- Helpers to prevent Leaflet from receiving NaN ----
const isNum = (n) => typeof n === 'number' && Number.isFinite(n)
const validPoint = (pt) => (pt && isNum(pt.lat) && isNum(pt.lng)) ? pt : null

// Small, self-contained SVG pins so you don't depend on external assets
const svgPin = (fill, text) =>
    `data:image/svg+xml;utf8,` +
    encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="25" height="41" viewBox="0 0 25 41">
      <path d="M12.5 41c0 0 12.5-15.1 12.5-24.5C25 7.4 19.4 0 12.5 0S0 7.4 0 16.5C0 25.9 12.5 41 12.5 41z" fill="${fill}"/>
      <circle cx="12.5" cy="16" r="8" fill="white"/>
      <text x="12.5" y="19" text-anchor="middle" font-size="10" font-family="Arial" fill="${fill === '#e53935' ? 'white' : '#1b5e20'}" font-weight="700">${text}</text>
    </svg>`
    )

const pickupIcon = new L.Icon({
    iconUrl: svgPin('#34a853', 'P'), // green
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    tooltipAnchor: [0, -28]
})

const dropoffIcon = new L.Icon({
    iconUrl: svgPin('#e53935', 'D'), // red
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    tooltipAnchor: [0, -28]
})

function FitBounds({ points }) {
    const map = useMap()
    useEffect(() => {
        if (!points || points.length === 0) return
        const bounds = L.latLngBounds(points.map(p => [p.lat, p.lng]))
        map.fitBounds(bounds, { padding: [20, 20] })
    }, [points, map])
    return null
}

function DraggableMarker({ position, onDragEnd, kind }) {
    if (!position) return null
    const icon = kind === 'pickup' ? pickupIcon : dropoffIcon
    const label = kind === 'pickup' ? 'Pickup' : 'Dropoff'

    return (
        <Marker
            position={[position.lat, position.lng]}
            draggable={true}
            icon={icon}
            eventHandlers={{
                dragend: (e) => {
                    const m = e.target
                    const { lat, lng } = m.getLatLng()
                    onDragEnd({ lat: Number(lat.toFixed(6)), lng: Number(lng.toFixed(6)) })
                }
            }}
        >
            <Tooltip direction="top" offset={[0, -28]} permanent>
                {label}
            </Tooltip>
        </Marker>
    )
}

/**
 * Props:
 * - pickup, dropoff: { lat, lng } | '' allowed while typing
 * - onSetPickup(pt), onSetDropoff(pt)
 *
 * Behavior:
 * - Click map sets whichever point is missing (if both exist, no-op).
 * - Markers draggable; drag updates inputs.
 * - Leaflet is only given valid numeric coords.
 */
export default function MapView({ pickup, dropoff, onSetPickup, onSetDropoff }) {
    const P = validPoint(pickup)
    const D = validPoint(dropoff)

    const points = useMemo(() => [P, D].filter(Boolean), [P, D])
    const center = points[0] || { lat: 39.5, lng: -98.35 } // USA center fallback

    const handleMapClick = (e) => {
        const { lat, lng } = e.latlng
        const pt = { lat: Number(lat.toFixed(6)), lng: Number(lng.toFixed(6)) }

        // Only set the missing point; ignore clicks if both already exist
        if (!P && onSetPickup) return onSetPickup(pt)
        if (!D && onSetDropoff) return onSetDropoff(pt)
    }

    return (
        <MapContainer
            center={[center.lat, center.lng]}
            zoom={5}
            style={{ height: 360, width: '100%' }}
            eventHandlers={{ click: handleMapClick }}
        >
            <TileLayer
                attribution='&copy; OpenStreetMap contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />

            {/* Only render markers when coords are valid numbers */}
            {P && (
                <DraggableMarker
                    kind="pickup"
                    position={P}
                    onDragEnd={pt => onSetPickup && onSetPickup(pt)}
                />
            )}
            {D && (
                <DraggableMarker
                    kind="dropoff"
                    position={D}
                    onDragEnd={pt => onSetDropoff && onSetDropoff(pt)}
                />
            )}

            {P && D && (
                <Polyline positions={[[P.lat, P.lng], [D.lat, D.lng]]} />
            )}

            <FitBounds points={points} />
        </MapContainer>
    )
}
