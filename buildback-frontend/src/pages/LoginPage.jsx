import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { dashboardPath, useAuth } from '../auth/authContext'
import { AuthLayout } from '../components/AuthLayout'
import { FormField, PasswordInput } from '../components/FormField'

export default function LoginPage() {
  const { login, notice, clearNotice } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  // Messages handed over by other pages (kept in router state, never in the URL)
  const justRegistered = location.state?.registered === true
  const [authNotice] = useState(notice) // "logged out" / "session expired"

  const [email, setEmail] = useState(location.state?.email ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  // The notice was copied above; clear it so it does not show again later
  useEffect(() => {
    clearNotice()
  }, [clearNotice])

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    if (!email.trim() || !password) {
      setError('Enter your email and password.')
      return
    }

    setSubmitting(true)
    try {
      const user = await login(email, password)
      navigate(dashboardPath(user.role), { replace: true })
    } catch (err) {
      // 401 -> "Invalid email or password", 0 -> server unreachable
      setError(err.message)
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Log in" subtitle="Welcome back. Log in to continue.">
      <div role="status" aria-live="polite">
        {justRegistered && !authNotice && (
          <p className="bb-alert bb-alert-success">Account created. You can log in now.</p>
        )}
        {authNotice && <p className="bb-alert bb-alert-info">{authNotice}</p>}
      </div>

      {error && (
        <p className="bb-alert bb-alert-error" role="alert">
          {error}
        </p>
      )}

      <form className="bb-form" onSubmit={handleSubmit} noValidate>
        <FormField id="login-email" label="Email">
          {(field) => (
            <input
              {...field}
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </FormField>

        <FormField id="login-password" label="Password">
          {(field) => (
            <PasswordInput
              {...field}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </FormField>

        <button type="submit" className="bb-btn bb-btn-primary bb-btn-lg bb-btn-block" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>

      <p className="bb-switch">
        New to BuildBack? <Link to="/register">Create an account</Link>
      </p>
    </AuthLayout>
  )
}
