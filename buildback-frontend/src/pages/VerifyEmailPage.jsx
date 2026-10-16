import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { AuthLayout } from '../components/AuthLayout'
import { Icon } from '../components/Icon'
import { ResendConfirmation } from '../components/ResendConfirmation'
import '../styles/invoice.css'

// One request per code, even though React (in development) runs effects twice:
// a second request for the same code would be refused as "already used".
const requests = new Map()
function confirmOnce(token) {
  if (!requests.has(token)) requests.set(token, api.verifyEmail(token))
  return requests.get(token)
}

/** Opened from the link in the confirmation email: /verify-email#token=... */
export default function VerifyEmailPage() {
  // The code is after "#", which browsers never send to a server. Read it once, then
  // remove it from the address bar so it is not left in the browser history.
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') ?? '')
  const [state, setState] = useState(token ? 'checking' : 'error') // checking | done | error
  const [message, setMessage] = useState(token ? null : 'This confirmation link is incomplete.')
  const [email, setEmail] = useState('')

  useEffect(() => {
    if (!token) return
    window.history.replaceState(null, '', window.location.pathname)
    confirmOnce(token)
      .then(() => setState('done'))
      .catch((err) => {
        setState('error')
        setMessage(err.fieldErrors?.token ?? err.message)
      })
  }, [token])

  return (
    <AuthLayout title="Confirm your email" subtitle="Activating your BuildBack account.">
      {state === 'checking' && (
        <p className="bb-loading" role="status">
          Confirming your email…
        </p>
      )}

      {state === 'done' && (
        <div className="bb-check-email" role="status">
          <span className="bb-check-email-icon" aria-hidden="true">
            <Icon name="checkCircle" size={36} />
          </span>
          <p>
            <strong>Your email is confirmed.</strong> Your account is active.
          </p>
          <Link to="/login" state={{ verified: true }} className="bb-btn bb-btn-primary bb-btn-block">
            Log in
          </Link>
        </div>
      )}

      {state === 'error' && (
        <div className="bb-check-email">
          <p className="bb-alert bb-alert-error" role="alert">
            {message}
          </p>
          <p>Enter your email to get a new confirmation link:</p>
          <div className="bb-field">
            <label htmlFor="resend-email">Email</label>
            <input
              id="resend-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <ResendConfirmation email={email} />
          <Link to="/login" className="bb-link">
            Back to log in
          </Link>
        </div>
      )}
    </AuthLayout>
  )
}
