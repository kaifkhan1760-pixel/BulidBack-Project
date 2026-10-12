import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import { UNIT_LABELS, UNIT_PLURALS, formatPrice } from '../materials/materialRules'
import { Icon } from './Icon'
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
      <section className="bb-panel bb-buy-panel" aria-labelledby="buy-title">
        <PanelHead />
        <p className="bb-muted">Log in with a buyer account to reserve this material and pay online.</p>
        <Link to="/login" className="bb-btn bb-btn-primary bb-btn-block">
          Log in to order
        </Link>
        <Link to="/register" className="bb-btn bb-btn-secondary bb-btn-block">
          Create a buyer account
        </Link>
      </section>
    )
  }
  if (user.role !== 'BUYER') {
    return (
      <section className="bb-panel bb-buy-panel" aria-labelledby="buy-title">
        <PanelHead />
        <p className="bb-muted">Only buyer accounts can place orders. You are logged in as a {user.role.toLowerCase()}.</p>
      </section>
    )
  }
  if (!material.inStock) {
    return (
      <section className="bb-panel bb-buy-panel" aria-labelledby="buy-title">
        <PanelHead />
        <p className="bb-stock-out">
          <Icon name="alert" size={18} /> Out of stock
        </p>
        <Link to="/materials" className="bb-btn bb-btn-secondary bb-btn-block">
          Find similar materials
        </Link>
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
    <section className="bb-panel bb-buy-panel" aria-labelledby="buy-title">
      <PanelHead />
      <p className="bb-detail-price-row">
        <span className="bb-price bb-price-large">{formatPrice(material.unitPrice)}</span>
        <span>per {UNIT_LABELS[material.unit]}</span>
      </p>
      <form className="bb-buy-form" onSubmit={reserve} noValidate>
        <div className="bb-field">
          <label htmlFor="buy-qty">Quantity ({UNIT_PLURALS[material.unit]})</label>
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
        <p className="bb-buy-total">
          <span>Total</span>
          <strong>{total ?? '—'}</strong>
        </p>
        {error && (
          <p className="bb-field-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="bb-btn bb-btn-primary bb-btn-lg bb-btn-block" disabled={busy}>
          {busy ? 'Reserving…' : 'Reserve and pay'}
          {!busy && <Icon name="arrowRight" size={18} />}
        </button>
      </form>
    </section>
  )
}

function PanelHead() {
  return (
    <div className="bb-panel-head">
      <span className="bb-panel-icon">
        <Icon name="bag" size={20} />
      </span>
      <h2 id="buy-title">Order this material</h2>
    </div>
  )
}
