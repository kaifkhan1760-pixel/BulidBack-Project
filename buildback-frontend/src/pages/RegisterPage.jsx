import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { AuthLayout } from '../components/AuthLayout'
import { FormField, PasswordInput } from '../components/FormField'

const EMPTY_FORM = { name: '', email: '', password: '', confirmPassword: '', role: '' }

// Same rules as the backend (RegisterRequest.java). The server checks again anyway.
function validate(form) {
  const errors = {}
  const name = form.name.trim()
  const email = form.email.trim()

  if (name.length < 2 || name.length > 100) errors.name = 'Name must be 2 to 100 characters'
  if (!email) errors.email = 'Email is required'
  else if (!/^[^\s@]+@[^\s@]+$/.test(email) || email.length > 254) errors.email = 'Enter a valid email address'

  if (form.password.length < 8) errors.password = 'Password must be at least 8 characters'
  else if (new TextEncoder().encode(form.password).length > 72)
    errors.password = 'Password is too long (maximum 72 bytes)'

  if (form.confirmPassword !== form.password) errors.confirmPassword = 'Passwords do not match'
  if (form.role !== 'BUYER' && form.role !== 'SELLER') errors.role = 'Choose Buyer or Seller'
  return errors
}

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value })

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)

    const clientErrors = validate(form)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      setFormError('Please correct the highlighted fields.')
      return
    }

    setSubmitting(true)
    try {
      // confirmPassword stays in the browser; it is not sent
      const created = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      })
      navigate('/login', { state: { registered: true, email: created.email } })
    } catch (err) {
      setErrors(err.fieldErrors ?? {}) // 400 field errors, or 409 duplicate email
      setFormError(err.message)
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Give leftover materials a new purpose.">
      {formError && (
        <p className="bb-alert bb-alert-error" role="alert">
          {formError}
        </p>
      )}

      <form className="bb-form" onSubmit={handleSubmit} noValidate>
        <FormField id="reg-name" label="Full name" error={errors.name}>
          {(field) => (
            <input {...field} type="text" autoComplete="name" value={form.name} onChange={update('name')} />
          )}
        </FormField>

        <FormField id="reg-email" label="Email" error={errors.email}>
          {(field) => (
            <input {...field} type="email" autoComplete="email" value={form.email} onChange={update('email')} />
          )}
        </FormField>

        <FormField id="reg-password" label="Password" error={errors.password} hint="At least 8 characters.">
          {(field) => (
            <PasswordInput
              {...field}
              autoComplete="new-password"
              value={form.password}
              onChange={update('password')}
            />
          )}
        </FormField>

        <FormField id="reg-confirm" label="Confirm password" error={errors.confirmPassword}>
          {(field) => (
            <PasswordInput
              {...field}
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={update('confirmPassword')}
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

        <button type="submit" className="bb-button bb-button-full" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>

      <p className="bb-switch">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  )
}
