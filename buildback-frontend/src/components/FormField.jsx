import { useState } from 'react'

/**
 * A labelled input with its own error message.
 * aria-invalid / aria-describedby let screen readers announce the error.
 */
export function FormField({ id, label, error, hint, children }) {
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined
  return (
    <div className="bb-field">
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && (
        <p id={`${id}-hint`} className="bb-hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="bb-field-error">
          {error}
        </p>
      )}
    </div>
  )
}

/** Password input with a Show/Hide button. */
export function PasswordInput({ value, onChange, autoComplete, ...inputProps }) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="bb-password">
      <input
        {...inputProps}
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        autoComplete={autoComplete}
        spellCheck={false}
      />
      <button
        type="button"
        className="bb-password-toggle"
        onClick={() => setVisible((v) => !v)}
        aria-pressed={visible}
        aria-controls={inputProps.id}
        aria-label={visible ? 'Hide password' : 'Show password'}
      >
        {visible ? 'Hide' : 'Show'}
      </button>
    </div>
  )
}
