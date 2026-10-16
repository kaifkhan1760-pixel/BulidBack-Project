import { Link } from 'react-router'
import { CATEGORY_LABELS, UNIT_LABELS, formatPrice } from '../materials/materialRules'
import { formatDate } from '../orders/orderRules'
import { OrderStatusBadge } from './StatusBadge'

/**
 * Orders as cards. view = 'buyer' | 'seller' | 'admin' decides which
 * person is shown and which action buttons appear.
 */
export function OrderList({ orders, view, onCollect, onCancel, busyId }) {
  return (
    <ul className="bb-order-list">
      {orders.map((o) => (
        <li key={o.id} className="bb-order-row">
          <div className="bb-order-main">
            <p className="bb-material-title">
              {view === 'buyer' ? <Link to={`/buyer/orders/${o.id}`}>Order #{o.id}</Link> : `Order #${o.id}`} ·{' '}
              {o.material.title}
            </p>
            <p className="bb-muted bb-small">
              {CATEGORY_LABELS[o.material.category]} · {o.material.city} · {formatDate(o.createdAt)}
            </p>
            <p className="bb-small">
              {o.quantity} {UNIT_LABELS[o.material.unit]}
              {o.quantity === 1 ? '' : 's'} × {formatPrice(o.unitPrice)} = <strong>{formatPrice(o.totalAmount)}</strong>
            </p>
            {view !== 'buyer' && (
              <p className="bb-small">
                Buyer: {o.buyerName}
                {o.buyerEmail && (
                  <>
                    {' '}
                    · <a href={`mailto:${o.buyerEmail}`}>{o.buyerEmail}</a>
                  </>
                )}
                {o.buyerPhone && (
                  <>
                    {' '}
                    · <a href={`tel:${o.buyerPhone}`}>{o.buyerPhone}</a>
                  </>
                )}
              </p>
            )}
            {view !== 'seller' && (
              <p className="bb-small">
                Seller: {o.sellerName}
                {o.sellerEmail && <> · {o.sellerEmail}</>}
                {o.sellerPhone && (
                  <>
                    {' '}
                    · <a href={`tel:${o.sellerPhone}`}>{o.sellerPhone}</a>
                  </>
                )}
              </p>
            )}
          </div>
          <div className="bb-order-side">
            <OrderStatusBadge status={o.status} />
            {view === 'buyer' && o.status === 'PENDING_PAYMENT' && (
              <Link to={`/buyer/orders/${o.id}`} className="bb-button bb-button-small">
                Pay now
              </Link>
            )}
            {/* Unpaid: releases the stock. Paid but not collected: cancels and refunds. */}
            {view === 'buyer' && onCancel && (o.status === 'PENDING_PAYMENT' || o.status === 'PAID') && (
              <button
                type="button"
                className="bb-button bb-button-danger-outline bb-button-small"
                onClick={() => onCancel(o)}
                disabled={busyId === o.id}
              >
                Cancel order<span className="bb-visually-hidden"> #{o.id}</span>
              </button>
            )}
            {view === 'seller' && o.status === 'PAID' && (
              <button
                type="button"
                className="bb-button bb-button-small"
                onClick={() => onCollect(o)}
                disabled={busyId === o.id}
              >
                {busyId === o.id ? 'Saving…' : 'Mark collected'}
              </button>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}
