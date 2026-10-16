import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import {
  cleanName,
  cleanPhone,
  passwordChecks,
  passwordStrength,
  validateRegistration,
} from '../auth/registerRules'
import { AuthLayout } from '../components/AuthLayout'
import { FormField, PasswordInput } from '../components/FormField'
import { Icon } from '../components/Icon'

const EMPTY_FORM = { name: '', email: '', phone: '', password: '', confirmPassword: '', role: '' }
const STRENGTH_LABELS = ['', 'Weak', 'Good', 'Strong']

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const update = (field) => (event) => {
    const value = event.target.value
    setForm((current) => ({ ...current, [field]: value }))
    // Once a field shows an error, re-check it while the user fixes it
    if (errors[field]) {
      const next = validateRegistration({ ...form, [field]: value })
      const remaining = { ...errors, [field]: next[field] }
      setErrors(remaining)
      // Everything fixed: the "please correct" banner is no longer true
      if (Object.values(remaining).every((message) => !message)) setFormError(null)
    }
  }

  // Check a field when the user leaves it (not on every key press, which feels pushy)
  const checkOnBlur = (field) => () => {
    if (!form[field]) return
    const next = validateRegistration(form)
    setErrors((current) => ({ ...current, [field]: next[field] }))
    // Show a valid mobile number neatly: "+91 98765-43210" -> "98765 43210" (+91 is shown beside the box)
    if (field === 'phone' && !next.phone) {
      const digits = cleanPhone(form.phone).slice(3)
      setForm((current) => ({ ...current, phone: `${digits.slice(0, 5)} ${digits.slice(5)}` }))
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)

    const clientErrors = validateRegistration(form)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      setFormError('Please correct the highlighted fields.')
      return
    }

    setSubmitting(true)
    try {
      // confirmPassword stays in the browser; it is not sent
      const created = await register({
        name: cleanName(form.name),
        email: form.email.trim(),
        phone: cleanPhone(form.phone),
        password: form.password,
        role: form.role,
      })
      // The account is locked until the link in the confirmation email is clicked
      navigate('/check-email', { state: { email: created.email } })
    } catch (err) {
      setErrors(err.fieldErrors ?? {}) // 400 field errors, or 409 duplicate email
      setFormError(err.message)
      setSubmitting(false)
    }
  }

  const checks = passwordChecks(form.password)
  const strength = passwordStrength(form.password)

  return (
    <AuthLayout title="Create your account" subtitle="Buy and sell surplus building material near you.">
      {formError && (
        <p className="bb-alert bb-alert-error" role="alert">
          {formError}
        </p>
      )}

      <form className="bb-form" onSubmit={handleSubmit} noValidate>
        <FormField
          id="reg-name"
          label="Full name or business name"
          required
          error={errors.name}
          hint="Letters only, e.g. Kaif Khan or Patel Tiles & Sanitary."
        >
          {(field) => (
            <input
              {...field}
              type="text"
              autoComplete="name"
              autoCapitalize="words"
              maxLength={100}
              value={form.name}
              onChange={update('name')}
              onBlur={checkOnBlur('name')}
            />
          )}
        </FormField>

        <FormField id="reg-email" label="Email" required error={errors.email}>
          {(field) => (
            <input
              {...field}
              type="email"
              autoComplete="email"
              placeholder="name@example.com"
              maxLength={254}
              value={form.email}
              onChange={update('email')}
              onBlur={checkOnBlur('email')}
            />
          )}
        </FormField>

        <FormField
          id="reg-phone"
          label="Mobile number"
          required
          error={errors.phone}
          hint="Shared with the other person only after an order is paid, to arrange pickup."
        >
          {(field) => (
            <div className="bb-input-affix">
              <span aria-hidden="true">+91</span>
              <input
                {...field}
                type="tel"
                inputMode="tel"
                autoComplete="tel-national"
                placeholder="98765 43210"
                maxLength={16}
                value={form.phone}
                onChange={update('phone')}
                onBlur={checkOnBlur('phone')}
              />
            </div>
          )}
        </FormField>

        <FormField id="reg-password" label="Password" required error={errors.password}>
          {(field) => (
            <PasswordInput
              {...field}
              aria-describedby={[field['aria-describedby'], 'reg-password-rules'].filter(Boolean).join(' ')}
              autoComplete="new-password"
              value={form.password}
              onChange={update('password')}
              onBlur={checkOnBlur('password')}
            />
          )}
        </FormField>

        <div className="bb-password-help" id="reg-password-rules">
          <div className="bb-strength" data-level={strength}>
            <span className="bb-strength-bar" aria-hidden="true">
              <span />
              <span />
              <span />
            </span>
            <span className="bb-strength-label">
              {strength > 0 ? `Strength: ${STRENGTH_LABELS[strength]}` : 'Password strength'}
            </span>
          </div>
          <ul className="bb-password-rules">
            {checks.map((check) => (
              <li key={check.id} className={check.ok ? 'is-met' : undefined}>
                <Icon name={check.ok ? 'checkCircle' : 'close'} size={16} />
                {check.label}
                <span className="bb-visually-hidden">{check.ok ? ' (done)' : ' (not yet)'}</span>
              </li>
            ))}
          </ul>
        </div>

        <FormField id="reg-confirm" label="Confirm password" required error={errors.confirmPassword}>
          {(field) => (
            <PasswordInput
              {...field}
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={update('confirmPassword')}
              onBlur={checkOnBlur('confirmPassword')}
            />
          )}
        </FormField>

        <fieldset
          className="bb-roles"
          aria-invalid={Boolean(errors.role)}
          aria-describedby={errors.role ? 'reg-role-error' : undefined}
        >
          <legend>I want to</legend>
          <label className="bb-role-option">
            <input type="radio" name="role" value="BUYER" checked={form.role === 'BUYER'} onChange={update('role')} />
            <span>
              <strong>Buy materials</strong>
              <small>Buyer account</small>
            </span>
          </label>
          <label className="bb-role-option">
            <input type="radio" name="role" value="SELLER" checked={form.role === 'SELLER'} onChange={update('role')} />
            <span>
              <strong>Sell surplus</strong>
              <small>Seller account</small>
            </span>
          </label>
          {errors.role && (
            <p id="reg-role-error" className="bb-field-error">
              {errors.role}
            </p>
          )}
        </fieldset>

        <button type="submit" className="bb-btn bb-btn-primary bb-btn-lg bb-btn-block" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="bb-switch">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  )
}
