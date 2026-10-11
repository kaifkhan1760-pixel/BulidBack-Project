import { useAuth } from '../auth/authContext'

/**
 * Waits for GET /api/auth/me before showing anything, so protected content
 * never flashes on screen. Shows a retry button if the server cannot be reached.
 */
export function SessionGate({ children }) {
  const { status, loadError, retry } = useAuth()

  if (status === 'loading') {
    return (
      <div className="bb-screen">
        <p className="bb-loading" role="status">
          Checking your session…
        </p>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="bb-screen">
        <div className="bb-card bb-card-narrow" role="alert">
          <h1 className="bb-card-title">Can’t connect to BuildBack</h1>
          <p className="bb-muted">{loadError}</p>
          <button type="button" className="bb-button" onClick={retry}>
            Try again
          </button>
        </div>
      </div>
    )
  }

  return children
}
