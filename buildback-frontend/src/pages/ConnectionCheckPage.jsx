import { useState } from 'react'
import { Link } from 'react-router'
import './ConnectionCheck.css'

// Read from buildback-frontend/.env  (VITE_API_BASE_URL=http://localhost:8081/api)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export default function ConnectionCheckPage() {
  const [loading, setLoading] = useState(false)
  const [health, setHealth] = useState(null) // successful JSON from the backend
  const [error, setError] = useState(null) // { title, message }

  async function checkConnection() {
    // Clear the previous result before every new request
    setLoading(true)
    setHealth(null)
    setError(null)

    if (!API_BASE_URL) {
      setError({
        title: 'Frontend not configured',
        message: 'VITE_API_BASE_URL is missing. Add it to the .env file and restart Vite.',
      })
      setLoading(false)
      return
    }

    try {
      let response
      try {
        response = await fetch(`${API_BASE_URL}/health`)
      } catch {
        // fetch only throws when no HTTP response arrived at all
        setError({
          title: 'Backend not reachable',
          message: `No response from ${API_BASE_URL}. Make sure Spring Boot is running in Eclipse on port 8081. If it is running, open the browser console (F12) for details.`,
        })
        return
      }

      const data = await response.json().catch(() => null)

      if (response.ok && data) {
        setHealth(data)
      } else if (response.status === 503) {
        // The backend answered, but it could not reach MySQL
        setError({
          title: 'Database unavailable',
          message: data?.message ?? 'The backend is running but could not reach MySQL.',
        })
      } else {
        setError({
          title: 'Unexpected response',
          message: `The backend answered with HTTP ${response.status}.`,
        })
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="connection-page">
      <section className="panel">
        <header className="brand">
          <h1>BuildBack</h1>
          <p className="subtitle">Construction Surplus Marketplace</p>
          <p className="tagline">Give leftover materials a new purpose.</p>
        </header>

        <h2>Project connection check</h2>
        <p className="hint">
          Sends a request from React to Spring Boot, which runs a test query on MySQL.
        </p>

        <button type="button" className="check-button" onClick={checkConnection} disabled={loading}>
          {loading ? 'Checking...' : 'Check Connection'}
        </button>

        <div className="status-area" role="status" aria-live="polite">
          {health && (
            <dl className="result result-success">
              <div>
                <dt>Application</dt>
                <dd>{health.application}</dd>
              </div>
              <div>
                <dt>Backend</dt>
                <dd>{health.backend}</dd>
              </div>
              <div>
                <dt>Database</dt>
                <dd>{health.database}</dd>
              </div>
            </dl>
          )}

          {error && (
            <div className="result result-error">
              <strong>{error.title}</strong>
              <p>{error.message}</p>
            </div>
          )}
        </div>

        <Link className="back-link" to="/">
          ← Back to BuildBack
        </Link>
      </section>
    </main>
  )
}

