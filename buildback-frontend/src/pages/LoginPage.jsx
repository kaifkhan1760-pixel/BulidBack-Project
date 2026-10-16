import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { dashboardPath, useAuth } from '../auth/authContext'
import { AuthLayout } from '../components/AuthLayout'
import { FormField, PasswordInput } from '../components/FormField'
import { ResendConfirmation } from '../components/ResendConfirmation'

export default function LoginPage() {
  const { login, notice, clearNotice } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  // Messages handed over by other pages (kept in router state, never in the URL)
  const justVerified = location.state?.verified === true
  const [authNotice] = useState(notice) // "logged out" / "session expired"

  const [email, setEmail] = useState(location.state?.email ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [needsConfirmation, setNeedsConfirmation] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // The notice was copied above; clear it so it does not show again later
  useEffect(() => {
    clearNotice()
  }, [clearNotice])

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setNeedsConfirmation(false)
    if (!email.trim() || !password) {
      setError('Enter your email and password.')
      return
    }

    setSubmitting(true)
    try {
      const user = await login(email, password)
      navigate(dashboardPath(user.role), { replace: true })
    } catch (err) {
      // 401 -> "Invalid email or password", 403 + code -> email not confirmed yet, 0 -> server unreachable
      setError(err.message)
      setNeedsConfirmation(err.code === 'EMAIL_NOT_VERIFIED')
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Log in" subtitle="Welcome back. Log in to continue.">
      <div role="status" aria-live="polite">
        {justVerified && !authNotice && (
          <p className="bb-alert bb-alert-success">Your email is confirmed. Log in to continue.</p>
        )}
        {authNotice && <p className="bb-alert bb-alert-info">{authNotice}</p>}
      </div>

      {error && (
        <div className="bb-alert bb-alert-error" role="alert">
          <p>{error}</p>
          {needsConfirmation && <ResendConfirmation email={email} compact />}
        </div>
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
