import { Link } from 'react-router'
import { ROLE_LABELS, dashboardPath, useAuth } from '../auth/authContext'
import { SessionGate } from '../components/SessionGate'

export default function UnauthorizedPage() {
  return (
    <SessionGate>
      <UnauthorizedContent />
    </SessionGate>
  )
}

function UnauthorizedContent() {
  const { user } = useAuth()
  return (
    <main className="bb-screen">
      <section className="bb-card bb-card-narrow" aria-labelledby="denied-title">
        <h1 id="denied-title" className="bb-card-title">
          Access denied
        </h1>
        <p className="bb-muted">Your account does not have permission to open that page.</p>
        {user ? (
          <Link className="bb-button" to={dashboardPath(user.role)}>
            Go to my {ROLE_LABELS[user.role]} dashboard
          </Link>
        ) : (
          <Link className="bb-button" to="/login">
            Log in
          </Link>
        )}
      </section>
    </main>
  )
}
