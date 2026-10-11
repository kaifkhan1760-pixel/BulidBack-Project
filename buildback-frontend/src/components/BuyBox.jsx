import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import { UNIT_LABELS } from '../materials/materialRules'
import { estimateTotal } from '../orders/orderRules'

/**
 * Choose a quantity and reserve it. Only buyers can order; the server checks
 * the role, the stock and the price again, so this is just the form.
 */
export function BuyBox({ material, quantity, onQuantityChange }) {
  const { user, status } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (status === 'loading') return null

  if (!user) {
    return (
      <section className="bb-panel">
        <p>
          <Link to="/login">Log in</Link> or <Link to="/register">create a buyer account</Link> to order this
          material.
        </p>
      </section>
    )
  }
  if (user.role !== 'BUYER') {
    return (
      <section className="bb-panel">
        <p className="bb-muted">Only buyer accounts can place orders.</p>
      </section>
    )
  }
  if (!material.inStock) {
    return (
      <section className="bb-panel">
        <p className="bb-stock-out">Out of stock</p>
      </section>
    )
  }

  const total = estimateTotal(material.unitPrice, quantity.trim())

  async function reserve(event) {
    event.preventDefault()
    setError(null)
    const text = quantity.trim()
    if (!/^\d{1,7}$/.test(text) || Number(text) < 1) {
      setError('Enter a whole number of at least 1')
      return
    }
    if (Number(text) > material.availableQuantity) {
      setError(`Only ${material.availableQuantity} available`)
      return
    }
    setBusy(true)
    try {
      const order = await api.orders.create(material.id, Number(text))
      navigate(`/buyer/orders/${order.id}`, { state: { justCreated: true } })
    } catch (err) {
      setError(err.fieldErrors?.quantity ?? err.message)
      setBusy(false)
    }
  }

  return (
    <section className="bb-panel" aria-labelledby="buy-title">
      <h2 id="buy-title" className="bb-section-title">
        Order
      </h2>
      <form className="bb-buy-form" onSubmit={reserve} noValidate>
        <div className="bb-field">
          <label htmlFor="buy-qty">Quantity ({UNIT_LABELS[material.unit]}s)</label>
          <input
            id="buy-qty"
            inputMode="numeric"
            value={quantity}
            onChange={(e) => onQuantityChange(e.target.value)}
            aria-invalid={Boolean(error)}
            aria-describedby="buy-help"
          />
        </div>
        <p id="buy-help" className="bb-hint">
          Max {material.availableQuantity}. Stock is held for you for 15 minutes while you pay.
        </p>
        {total && (
          <p className="bb-buy-total">
            Total: <strong>{total}</strong>
          </p>
        )}
        {error && (
          <p className="bb-field-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="bb-button bb-button-full" disabled={busy}>
          {busy ? 'Reserving…' : 'Reserve and pay'}
        </button>
      </form>
    </section>
  )
}
