import { Navigate } from 'react-router'
import { dashboardPath, useAuth } from '../auth/authContext'
import { SessionGate } from './SessionGate'

/*
 * These guards only improve the user experience (no flashing pages, sensible
 * redirects). The real protection is on the server: every /api/buyer, /api/seller
 * and /api/admin request is checked by Spring Security.
 */

/** Logged-in users with the right role only. */
export function ProtectedRoute({ role, children }) {
  return (
    <SessionGate>
      <RoleCheck role={role}>{children}</RoleCheck>
    </SessionGate>
  )
}

function RoleCheck({ role, children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== role) return <Navigate to="/unauthorized" replace />
  return children
}

/** Login and register pages: already logged-in users go to their dashboard. */
export function PublicOnlyRoute({ children }) {
  return (
    <SessionGate>
      <RedirectIfLoggedIn>{children}</RedirectIfLoggedIn>
    </SessionGate>
  )
}

function RedirectIfLoggedIn({ children }) {
  const { user } = useAuth()
  if (user) return <Navigate to={dashboardPath(user.role)} replace />
  return children
}

/** "/" sends you to your dashboard, or to login. */
export function HomeRedirect() {
  return (
    <SessionGate>
      <HomeTarget />
    </SessionGate>
  )
}

function HomeTarget() {
  const { user } = useAuth()
  return <Navigate to={user ? dashboardPath(user.role) : '/login'} replace />
}
