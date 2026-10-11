import { createContext, useContext } from 'react'

// Shared login state. The value is provided by <AuthProvider> (AuthProvider.jsx).
export const AuthContext = createContext(null)

export function useAuth() {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}

// Where each role lands after login. Roles always come from the server.
const DASHBOARDS = {
  BUYER: '/buyer/dashboard',
  SELLER: '/seller/dashboard',
  ADMIN: '/admin/dashboard',
}

export function dashboardPath(role) {
  return DASHBOARDS[role] ?? '/login'
}

export const ROLE_LABELS = {
  BUYER: 'Buyer',
  SELLER: 'Seller',
  ADMIN: 'Admin',
}
