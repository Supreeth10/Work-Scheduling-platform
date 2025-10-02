import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export default function AdminLogin() {
    const [name, setName] = useState('admin')
    const navigate = useNavigate()

    const handleLogin = (e) => {
        e.preventDefault()
        // keep it super simple: session only, not persistent between browser restarts
        sessionStorage.setItem('adminLoggedIn', '1')
        sessionStorage.setItem('adminName', name || 'admin')
        navigate('/admin', { replace: true })
    }

    return (
        <div style={{ maxWidth: 360, margin: '40px auto', textAlign: 'left' }}>
            <h2>Admin Login</h2>
            <p style={{ color: '#666', marginTop: 0, fontSize: 14 }}>
            </p>
            <form onSubmit={handleLogin} style={{ display: 'grid', gap: 12 }}>
                <label>
                    Admin name
                    <input value={name} onChange={(e) => setName(e.target.value)}  />
                </label>
                <button type="submit">Login</button>
            </form>
        </div>
    )
}
