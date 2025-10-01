import React from 'react'
import { Routes, Route, Link, Navigate } from 'react-router-dom'
import DriverPage from './pages/DriverPage'
import AdminPage from './pages/AdminPage'

export default function App() {
    return (
        <div style={{ maxWidth: 1200, margin: '0 auto', padding: 16 }}>
            <header style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
                <h1 style={{ marginRight: 'auto' }}>Work Dispatcher</h1>
                <nav style={{ display: 'flex', gap: 12 }}>
                    <Link to="/driver">Driver</Link>
                    <Link to="/admin">Admin</Link>
                </nav>
            </header>
            <main style={{ marginTop: 16 }}>
                <Routes>
                    <Route path="*" element={<Navigate to="/driver" replace />} />
                    <Route path="/" element={<Navigate to="/driver" replace />} />
                    <Route path="/driver" element={<DriverPage />} />
                    <Route path="/admin" element={<AdminPage />} />
                </Routes>
            </main>
        </div>
    )
}
