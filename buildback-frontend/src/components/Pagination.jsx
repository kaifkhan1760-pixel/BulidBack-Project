/** Previous / Next buttons. page is zero-based like the API; people see "Page 1". */
export function Pagination({ page, totalPages, onChange, disabled }) {
  if (totalPages <= 1) return null
  return (
    <nav className="bb-pagination" aria-label="Pages">
      <button
        type="button"
        className="bb-button bb-button-outline"
        onClick={() => onChange(page - 1)}
        disabled={disabled || page <= 0}
      >
        ← Previous
      </button>
      <span aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="bb-button bb-button-outline"
        onClick={() => onChange(page + 1)}
        disabled={disabled || page >= totalPages - 1}
      >
        Next →
      </button>
    </nav>
  )
}
