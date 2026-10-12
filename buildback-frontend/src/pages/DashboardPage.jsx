import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import { ROLE_LABELS } from '../auth/authContext'
import { Icon } from '../components/Icon'

// Short guidance per role (the numbers above it come from the database)
const HINTS = {
  BUYER: 'Tip: open a tile listing and use the calculator to find how many tiles your floor needs.',
  SELLER: 'Paid orders appear under Orders. Mark them collected after the buyer picks them up.',
  ADMIN: 'Use the admin console to review users and orders, and archive listings that break the rules.',
}

// Real, working links for each role
const ACTIONS = {
  BUYER: [
    { to: '/materials', label: 'Browse materials', icon: 'search', text: 'Search tiles, paint, plywood and fittings' },
    { to: '/buyer/orders', label: 'My orders', icon: 'bag', text: 'Pay, track and review your purchases' },
    { to: '/materials?category=TILES', label: 'Tile calculator', icon: 'calculator', text: 'Open a tile listing to check the quantity' },
  ],
  SELLER: [
    { to: '/seller/materials', label: 'Manage my listings', icon: 'package', text: 'Edit stock, prices and photos' },
    { to: '/seller/orders', label: 'Orders received', icon: 'receipt', text: 'Hand over paid orders and mark them collected' },
    { to: '/seller/materials/new', label: 'Add a listing', icon: 'plus', text: 'List leftover material in a minute' },
  ],
  ADMIN: [
    { to: '/admin/console', label: 'Admin console', icon: 'shield', text: 'Users, listings, orders and totals' },
    { to: '/admin/console?tab=listings', label: 'Moderate listings', icon: 'archive', text: 'Archive listings that break the rules' },
    { to: '/materials', label: 'Browse materials', icon: 'search', text: 'See the marketplace as buyers do' },
  ],
}

// Icon for each stat tile, in the order the server sends them
const STAT_ICONS = {
  BUYER: ['clock', 'bag', 'checkCircle', 'rupee'],
  SELLER: ['package', 'clock', 'truck', 'rupee'],
  ADMIN: ['users', 'package', 'receipt', 'rupee'],
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
    <main className="bb-dashboard-main">
      {loading && (
        <p className="bb-loading" role="status">
          Loading your dashboard…
        </p>
      )}

      {error && (
        <div className="bb-card bb-state" role="alert">
          <p className="bb-alert bb-alert-error">{error}</p>
          <button type="button" className="bb-btn bb-btn-primary" onClick={retry}>
            Try again
          </button>
        </div>
      )}

      {data && (
        <>
          <section className="bb-welcome" aria-labelledby="dashboard-title">
            <div>
              <p className="bb-eyebrow">{ROLE_LABELS[data.dashboard]} dashboard</p>
              <h1 id="dashboard-title">{data.message}</h1>
              <p>
                Signed in as {user.email} · {ROLE_LABELS[user.role]} account
              </p>
            </div>
            <Link to={ACTIONS[data.dashboard][0].to} className="bb-btn bb-btn-primary bb-btn-lg">
              {ACTIONS[data.dashboard][0].label}
              <Icon name="arrowRight" size={18} />
            </Link>
          </section>

          <section aria-labelledby="totals-title">
            <h2 id="totals-title" className="bb-visually-hidden">
              Your totals
            </h2>
            <ul className="bb-stats">
              {data.stats.map((stat, index) => (
                <li key={stat.label} className="bb-stat">
                  <span className="bb-stat-icon">
                    <Icon name={STAT_ICONS[data.dashboard][index] ?? 'info'} size={22} />
                  </span>
                  <span className="bb-stat-value">{stat.value}</span>
                  <span className="bb-stat-label">{stat.label}</span>
                </li>
              ))}
            </ul>
          </section>

          <section aria-labelledby="actions-title" className="bb-page-heading">
            <h2 id="actions-title" className="bb-section-title">
              Quick actions
            </h2>
            <ul className="bb-action-grid">
              {ACTIONS[data.dashboard].map((action) => (
                <li key={action.to}>
                  <Link to={action.to} className="bb-action-card">
                    <span className="bb-action-icon">
                      <Icon name={action.icon} size={22} />
                    </span>
                    <span>
                      <strong>{action.label}</strong>
                      <small>{action.text}</small>
                    </span>
                    <Icon name="chevronRight" size={20} className="bb-action-arrow" />
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <p className="bb-next">
            <Icon name="info" size={20} />
            {HINTS[data.dashboard]}
          </p>
        </>
      )}
    </main>
  )
}
