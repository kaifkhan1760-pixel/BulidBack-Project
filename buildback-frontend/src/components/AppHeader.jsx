import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { ROLE_LABELS, dashboardPath, useAuth } from '../auth/authContext'

/**
 * Top bar shared by dashboards, the catalogue and seller pages.
 * Which links appear depends on the role the SERVER reported for this session.
 */
export function AppHeader() {
  const { user, status, logout } = useAuth()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState(null)

  async function handleLogout() {
    setLoggingOut(true)
    setLogoutError(null)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch (err) {
      setLogoutError(`Logout failed: ${err.message} You are still logged in.`)
      setLoggingOut(false)
    }
  }

  return (
    <>
      <header className="bb-topbar">
        <Link to="/" className="bb-topbar-brand">
          BuildBack
        </Link>

        <nav className="bb-nav" aria-label="Main">
          <NavLink to="/materials" end>
            Browse materials
          </NavLink>
          {user?.role === 'BUYER' && <NavLink to="/buyer/orders">My orders</NavLink>}
          {user?.role === 'SELLER' && <NavLink to="/seller/materials">My materials</NavLink>}
          {user?.role === 'SELLER' && <NavLink to="/seller/orders">Orders</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin/console">Admin console</NavLink>}
          {user && <NavLink to={dashboardPath(user.role)}>Dashboard</NavLink>}
        </nav>

        <div className="bb-topbar-user">
          {user && (
            <>
              <span className="bb-topbar-name">
                {user.name} <span className="bb-badge">{ROLE_LABELS[user.role]}</span>
              </span>
              <button
                type="button"
                className="bb-button bb-button-outline"
                onClick={handleLogout}
                disabled={loggingOut}
              >
                {loggingOut ? 'Logging out...' : 'Log out'}
              </button>
            </>
          )}
          {!user && status !== 'loading' && (
            <>
              <Link to="/login" className="bb-link">
                Log in
              </Link>
              <Link to="/register" className="bb-button bb-button-small">
                Create account
              </Link>
            </>
          )}
        </div>
      </header>
      {logoutError && (
        <p className="bb-alert bb-alert-error bb-header-alert" role="alert">
          {logoutError}
        </p>
      )}
    </>
  )
}
