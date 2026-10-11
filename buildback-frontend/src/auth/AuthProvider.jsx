import { useCallback, useEffect, useState } from 'react'
import { api, setUnauthorizedHandler } from '../api/client'
import { AuthContext } from './authContext'

/**
 * Keeps track of who is logged in.
 * The truth lives on the server (the session). On every page load we ask
 * GET /api/auth/me instead of trusting anything stored in the browser.
 */
export function AuthProvider({ children }) {
  const [status, setStatus] = useState('loading') // 'loading' | 'ready' | 'error'
  const [user, setUser] = useState(null) // { id, name, email, role } from the server
  const [loadError, setLoadError] = useState(null)
  const [notice, setNotice] = useState(null) // message for the login page

  const loadSession = useCallback(
    () =>
      api
        .me()
        .then((me) => {
          setUser(me)
          setStatus('ready')
        })
        .catch((error) => {
          if (error.status === 401) {
            // Not logged in: a normal answer, not an error
            setUser(null)
            setStatus('ready')
          } else {
            // Server down or broken: do NOT pretend the user is logged out
            setLoadError(error.message)
            setStatus('error')
          }
        }),
    [],
  )

  useEffect(() => {
    loadSession()
  }, [loadSession])

  // Any 401 from a protected request means the session expired on the server
  useEffect(() => {
    setUnauthorizedHandler((path) => {
      if (path === '/auth/login' || path === '/auth/me') return
      setUser(null)
      setNotice('Your session has expired. Please log in again.')
    })
  }, [])

  const clearNotice = useCallback(() => setNotice(null), [])

  const retry = () => {
    setStatus('loading')
    setLoadError(null)
    loadSession()
  }

  const login = async (email, password) => {
    const loggedInUser = await api.login(email, password)
    setNotice(null)
    setUser(loggedInUser)
    return loggedInUser
  }

  // Throws if the server did not confirm the logout; the caller shows the error
  const logout = async () => {
    await api.logout()
    setUser(null)
    setNotice('You have been logged out.')
  }

  const value = {
    status,
    user,
    loadError,
    notice,
    clearNotice,
    retry,
    login,
    logout,
    register: api.register,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
