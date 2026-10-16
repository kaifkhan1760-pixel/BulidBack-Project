import { Link, Navigate, useLocation } from 'react-router'
import { AuthLayout } from '../components/AuthLayout'
import { Icon } from '../components/Icon'
import { ResendConfirmation } from '../components/ResendConfirmation'
import '../styles/invoice.css'

/** Shown right after sign-up: "we emailed you a link". */
export default function CheckEmailPage() {
  const email = useLocation().state?.email
  if (!email) return <Navigate to="/login" replace /> // opened directly, not after sign-up

  return (
    <AuthLayout title="Check your email" subtitle="One last step to activate your account.">
      <div className="bb-check-email">
        <span className="bb-check-email-icon" aria-hidden="true">
          <Icon name="checkCircle" size={36} />
        </span>
        <p>
          We sent a confirmation link to <strong>{email}</strong>. Open the email and click{' '}
          <strong>Confirm my email</strong>, then log in.
        </p>
        <ul className="bb-check-email-tips">
          <li>The link works once and expires in 24 hours.</li>
          <li>Can&apos;t find it? Check your spam or promotions folder.</li>
        </ul>
        <ResendConfirmation email={email} />
        <Link to="/login" state={{ email }} className="bb-btn bb-btn-primary bb-btn-block">
          Go to log in
        </Link>
      </div>
    </AuthLayout>
  )
}
