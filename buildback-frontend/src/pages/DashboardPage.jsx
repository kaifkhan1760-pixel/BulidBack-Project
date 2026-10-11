import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import { ROLE_LABELS } from '../auth/authContext'
import { AppHeader } from '../components/AppHeader'

// Short guidance per role (the numbers above it come from the database)
const HINTS = {
  BUYER: 'Tip: open a tile listing and use the calculator to find how many tiles your floor needs.',
  SELLER: 'Paid orders appear under Orders. Mark them collected after the buyer picks them up.',
  ADMIN: 'Use the admin console to review users and orders, and archive listings that break the rules.',
}

// Real, working links for each role
const ACTIONS = {
  BUYER: [
    { to: '/materials', label: 'Browse materials', primary: true },
    { to: '/buyer/orders', label: 'My orders' },
  ],
  SELLER: [
    { to: '/seller/materials', label: 'Manage my materials', primary: true },
    { to: '/seller/orders', label: 'Orders received' },
    { to: '/seller/materials/new', label: 'Add material' },
  ],
  ADMIN: [
    { to: '/admin/console', label: 'Admin console', primary: true },
    { to: '/materials', label: 'Browse materials' },
  ],
}

/**
 * One component for all three dashboards. The content comes from the
 * role's own API endpoint, so the server confirms the role on every visit.
 */
export default function DashboardPage({ role }) {
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(
    () =>
      api
        .dashboard(role)
        .then((result) => {
          setData(result)
          setError(null)
        })
        .catch((err) => {
          if (err.status === 403) navigate('/unauthorized', { replace: true })
          // 401 is handled globally: the session expired and we return to login
          else if (err.status !== 401) setError(err.message)
        })
        .finally(() => setLoading(false)),
    [role, navigate],
  )

  useEffect(() => {
    load()
  }, [load])

  function retry() {
    setLoading(true)
    setError(null)
    load()
  }

  const user = data?.user

  return (
    <div className="bb-app">
      <AppHeader />
      <main className="bb-dashboard-main">
        {loading && (
          <p className="bb-loading" role="status">
            Loading your dashboard…
          </p>
        )}

        {error && (
          <div className="bb-card" role="alert">
            <p className="bb-alert bb-alert-error">{error}</p>
            <button type="button" className="bb-button" onClick={retry}>
              Try again
            </button>
          </div>
        )}

        {data && (
          <section className="bb-card" aria-labelledby="dashboard-title">
            <p className="bb-eyebrow">{ROLE_LABELS[data.dashboard]} dashboard</p>
            <h1 id="dashboard-title" className="bb-card-title">
              {data.message}
            </h1>
            <dl className="bb-details">
              <div>
                <dt>Email</dt>
                <dd>{user.email}</dd>
              </div>
              <div>
                <dt>Account type</dt>
                <dd>{ROLE_LABELS[user.role]}</dd>
              </div>
            </dl>

            <ul className="bb-stats" aria-label="Your totals">
              {data.stats.map((stat) => (
                <li key={stat.label} className="bb-stat">
                  <span className="bb-stat-value">{stat.value}</span>
                  <span className="bb-stat-label">{stat.label}</span>
                </li>
              ))}
            </ul>

            <div className="bb-actions">
              {ACTIONS[data.dashboard].map((action) => (
                <Link
                  key={action.to}
                  to={action.to}
                  className={action.primary ? 'bb-button' : 'bb-button bb-button-outline'}
                >
                  {action.label}
                </Link>
              ))}
            </div>

            <p className="bb-next">{HINTS[data.dashboard]}</p>
          </section>
        )}
      </main>
    </div>
  )
}
