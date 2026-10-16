import { useEffect, useState } from 'react'
import { api } from '../api/client'

const WAIT_SECONDS = 60 // same pause the server enforces between emails

/** "Send the confirmation email again" button with a 60-second pause. */
export function ResendConfirmation({ email, compact = false }) {
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [message, setMessage] = useState(null)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    if (secondsLeft <= 0) return undefined
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [secondsLeft])

  async function resend() {
    setSending(true)
    try {
      const result = await api.resendVerification(email.trim())
      setMessage(result.message)
    } catch (err) {
      setMessage(err.message)
    } finally {
      setSending(false)
      setSecondsLeft(WAIT_SECONDS)
    }
  }

  return (
    <div className={compact ? 'bb-resend bb-resend-compact' : 'bb-resend'}>
      <button
        type="button"
        className="bb-btn bb-btn-secondary bb-btn-sm"
        onClick={resend}
        disabled={sending || secondsLeft > 0 || !email.trim()}
      >
        {sending ? 'Sending…' : secondsLeft > 0 ? `Resend in ${secondsLeft}s` : 'Resend confirmation email'}
      </button>
      <p className="bb-small" role="status" aria-live="polite">
        {message}
      </p>
    </div>
  )
}
