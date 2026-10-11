import { useEffect, useRef } from 'react'

/**
 * A real modal using the browser's <dialog> element: it traps keyboard focus
 * and closes with Escape. Shown while `open` is true.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Cancel',
  confirmTone = 'danger',
  busy,
  error,
  onConfirm,
  onCancel,
}) {
  const dialogRef = useRef(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={dialogRef}
      className="bb-dialog"
      aria-labelledby="confirm-title"
      onCancel={(event) => {
        event.preventDefault() // Escape key: let React state decide
        if (!busy) onCancel()
      }}
    >
      <h2 id="confirm-title" className="bb-card-title">
        {title}
      </h2>
      <div className="bb-dialog-body">{children}</div>
      {error && (
        <p className="bb-alert bb-alert-error" role="alert">
          {error}
        </p>
      )}
      <div className="bb-dialog-actions">
        <button type="button" className="bb-button bb-button-outline" onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </button>
        <button
          type="button"
          className={confirmTone === 'danger' ? 'bb-button bb-button-danger' : 'bb-button'}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? 'Working...' : confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
