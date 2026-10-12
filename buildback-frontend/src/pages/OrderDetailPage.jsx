import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { api } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Icon } from '../components/Icon'
import { OrderTimeline } from '../components/OrderTimeline'
import { OrderStatusBadge } from '../components/StatusBadge'
import { CATEGORY_LABELS, UNIT_LABELS, formatPrice } from '../materials/materialRules'
import { PAYMENT_STATUS, formatDate, timeLeft } from '../orders/orderRules'
import { openRazorpayCheckout } from '../orders/razorpayCheckout'

/** 76550 paise -> "765.50" using whole-number maths. */
function paiseToRupees(paise) {
  return `${Math.floor(paise / 100)}.${String(paise % 100).padStart(2, '0')}`
}

/**
 * One order of the logged-in buyer: reservation countdown, payment, cancel.
 * Payment flow: start attempt (server) -> gateway popup -> server verifies -> PAID.
 */
export default function OrderDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const [order, setOrder] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [notFound, setNotFound] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  const [paying, setPaying] = useState(false)
  const [message, setMessage] = useState(location.state?.justCreated ? 'Stock reserved. Complete the payment before the timer ends.' : null)
  const [payError, setPayError] = useState(null)
  const [simulator, setSimulator] = useState(null) // checkout data while the simulator dialog is open
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState(null)

  const load = useCallback(
    (signal) =>
      api.orders
        .get(id, signal)
        .then((data) => {
          setOrder(data)
          setLoadError(null)
        })
        .catch((err) => {
          if (err.name === 'AbortError' || err.status === 401) return
          if (err.status === 404 || err.status === 400) setNotFound(true)
          else setLoadError(err.message)
        }),
    [id],
  )

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  // Countdown tick while the order waits for payment
  const pending = order?.status === 'PENDING_PAYMENT'
  useEffect(() => {
    if (!pending) return undefined
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [pending])

  const remaining = pending ? timeLeft(order.expiresAt, now) : null
  const timeIsUp = pending && remaining === null

  // When the timer reaches zero, ask the server: it marks the order EXPIRED and releases the stock
  useEffect(() => {
    if (timeIsUp) load()
  }, [timeIsUp, load])

  // ----- payment -----
  async function verify(paymentId, result) {
    try {
      const updated = await api.payments.verify(paymentId, result)
      setOrder(updated)
      setMessage(updated.status === 'PAID' ? 'Payment verified. Your order is paid.' : null)
      if (updated.status !== 'PAID') setPayError('The payment arrived after the order had ended. It is flagged for a refund.')
    } catch (err) {
      setPayError(`Payment could not be verified: ${err.message}`)
    } finally {
      setPaying(false)
    }
  }

  async function recordFailure(paymentId, reason) {
    try {
      setOrder(await api.payments.failure(paymentId, reason))
    } catch {
      // Recording the failure is informational; the order is unchanged either way
    }
    setPayError(`Payment not completed: ${reason}. Your stock is still reserved — you can try again.`)
    setPaying(false)
  }

  async function startPayment() {
    setPaying(true)
    setPayError(null)
    setMessage(null)
    let checkout
    try {
      checkout = await api.orders.startPayment(id)
    } catch (err) {
      setPayError(err.message)
      setPaying(false)
      if (err.status === 409) load()
      return
    }

    if (checkout.provider === 'SIMULATOR') {
      setSimulator(checkout)
      return
    }
    try {
      await openRazorpayCheckout(checkout, {
        onSuccess: (result) => verify(checkout.paymentId, result),
        onFailure: (reason) => recordFailure(checkout.paymentId, reason),
      })
    } catch (err) {
      recordFailure(checkout.paymentId, err.message)
    }
  }

  async function simulate(outcome) {
    const checkout = simulator
    setSimulator(null)
    try {
      const result = await api.payments.simulate(checkout.paymentId, outcome)
      if (result.error) await recordFailure(checkout.paymentId, result.error)
      else await verify(checkout.paymentId, result)
    } catch (err) {
      setPayError(err.message)
      setPaying(false)
    }
  }

  async function cancelOrder() {
    setCancelling(true)
    setCancelError(null)
    try {
      const updated = await api.orders.cancel(id)
      setOrder(updated)
      setConfirmCancel(false)
      setMessage(updated.status === 'CANCELLED' ? 'Order cancelled. The stock was released.' : 'This order had already expired.')
    } catch (err) {
      setCancelError(err.message)
    } finally {
      setCancelling(false)
    }
  }

  const refundFlag = order?.payments?.some((p) => p.refundRequired)

  return (
    <div className="bb-app">
      <main className="bb-page">
        <Link to="/buyer/orders" className="bb-back-link">
          <Icon name="arrowLeft" size={16} /> My orders
        </Link>

        {!order && !notFound && !loadError && (
          <p className="bb-loading" role="status">
            Loading order…
          </p>
        )}
        {notFound && (
          <div className="bb-card bb-state" role="alert">
            <h1 className="bb-card-title">Order not found</h1>
            <Link to="/buyer/orders" className="bb-btn bb-btn-primary">
              Back to my orders
            </Link>
          </div>
        )}
        {loadError && (
          <div className="bb-card bb-state" role="alert">
            <p className="bb-alert bb-alert-error">{loadError}</p>
            <button type="button" className="bb-btn bb-btn-primary" onClick={() => load()}>
              Try again
            </button>
          </div>
        )}

        {order && (
          <>
            <div className="bb-order-head">
              <div>
                <p className="bb-eyebrow">Order</p>
                <h1 id="order-title" className="bb-page-title">
                  Order #{order.id}
                </h1>
              </div>
              <OrderStatusBadge status={order.status} />
            </div>

            <div role="status" aria-live="polite">
              {message && <p className="bb-alert bb-alert-success">{message}</p>}
            </div>
            {payError && (
              <p className="bb-alert bb-alert-error" role="alert">
                {payError}
              </p>
            )}
            {refundFlag && (
              <p className="bb-alert bb-alert-info">
                A payment was received after this order ended. It is recorded for a manual refund.
              </p>
            )}

            <section className="bb-card" aria-label="Order progress">
              <OrderTimeline status={order.status} />
            </section>

            <div className="bb-order-layout">
              <section className="bb-card" aria-labelledby="summary-title">
                <h2 id="summary-title" className="bb-section-title">
                  Order summary
                </h2>
                <dl className="bb-details">
                  <div>
                    <dt>Material</dt>
                    <dd>
                      <Link to={`/materials/${order.material.id}`}>{order.material.title}</Link>
                    </dd>
                  </div>
                  <div>
                    <dt>Category</dt>
                    <dd>{CATEGORY_LABELS[order.material.category]}</dd>
                  </div>
                  <div>
                    <dt>Quantity</dt>
                    <dd>
                      {order.quantity} {UNIT_LABELS[order.material.unit]}
                      {order.quantity === 1 ? '' : 's'} × {formatPrice(order.unitPrice)}
                    </dd>
                  </div>
                  <div>
                    <dt>Seller</dt>
                    <dd>
                      {order.sellerName}
                      {order.sellerEmail && (
                        <>
                          <br />
                          <a href={`mailto:${order.sellerEmail}`}>{order.sellerEmail}</a>
                        </>
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>Pick-up city</dt>
                    <dd>{order.material.city}</dd>
                  </div>
                  <div>
                    <dt>Ordered</dt>
                    <dd>{formatDate(order.createdAt)}</dd>
                  </div>
                  {order.paidAt && (
                    <div>
                      <dt>Paid</dt>
                      <dd>{formatDate(order.paidAt)}</dd>
                    </div>
                  )}
                  {order.collectedAt && (
                    <div>
                      <dt>Collected</dt>
                      <dd>{formatDate(order.collectedAt)}</dd>
                    </div>
                  )}
                </dl>

                {order.payments?.length > 0 && (
                  <>
                    <h2 className="bb-section-title">Payment attempts</h2>
                    <ul className="bb-payments">
                      {order.payments.map((p) => (
                        <li key={p.id}>
                          <span>
                            {formatDate(p.createdAt)} · {p.provider === 'SIMULATOR' ? 'Test simulator' : 'Razorpay (test)'}
                          </span>
                          <span className={p.status === 'SUCCESS' ? 'bb-stock' : p.status === 'FAILED' ? 'bb-stock-out' : 'bb-muted'}>
                            {PAYMENT_STATUS[p.status]}
                            {p.failureReason ? ` – ${p.failureReason}` : ''}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </section>

              <aside className="bb-panel bb-buy-panel" aria-label="Payment">
                <p className="bb-muted">Order total</p>
                <p className="bb-price bb-price-large">{formatPrice(order.totalAmount)}</p>

                {pending && (
                  <div className="bb-pay-box">
                    <p>
                      <Icon name="clock" size={18} /> Stock reserved for
                    </p>
                    <p className="bb-countdown" aria-live="off">
                      {remaining ?? '0:00'}
                    </p>
                    <button
                      type="button"
                      className="bb-btn bb-btn-primary bb-btn-lg bb-btn-block"
                      onClick={startPayment}
                      disabled={paying || timeIsUp}
                    >
                      <Icon name="card" size={18} />
                      {paying ? 'Processing…' : `Pay ${formatPrice(order.totalAmount)}`}
                    </button>
                    <button
                      type="button"
                      className="bb-btn bb-button-danger-outline bb-btn-block"
                      onClick={() => {
                        setCancelError(null)
                        setConfirmCancel(true)
                      }}
                      disabled={paying}
                    >
                      Cancel order
                    </button>
                  </div>
                )}

                {order.status === 'PAID' && (
                  <p className="bb-next">
                    <Icon name="truck" size={20} />
                    Contact the seller to arrange collection. The seller marks the order collected after hand-over.
                  </p>
                )}
                {order.status === 'COLLECTED' && (
                  <p className="bb-next">
                    <Icon name="checkCircle" size={20} /> Collected. Thank you for giving this material a second life.
                  </p>
                )}
                {(order.status === 'CANCELLED' || order.status === 'EXPIRED') && (
                  <Link to={`/materials/${order.material.id}`} className="bb-btn bb-btn-secondary bb-btn-block">
                    View the listing again
                  </Link>
                )}
                <p className="bb-buy-note">
                  <Icon name="shield" size={18} /> Payments are verified by the BuildBack server. Test mode only.
                </p>
              </aside>
            </div>
          </>
        )}

        {/* Clearly labelled stand-in for a gateway when no Razorpay test keys are configured */}
        <ConfirmDialog
          open={simulator !== null}
          title="Test payment simulator"
          confirmLabel="Simulate successful payment"
          cancelLabel="Simulate declined payment"
          confirmTone="primary"
          busy={false}
          onConfirm={() => simulate('SUCCESS')}
          onCancel={() => simulate('FAILURE')}
        >
          <p className="bb-alert bb-alert-info">
            TEST MODE — no real money and no card details. This stands in for the payment gateway in the classroom
            demo. The server still verifies a signed confirmation before marking the order paid.
          </p>
          {simulator && (
            <p>
              Amount: <strong>{formatPrice(paiseToRupees(simulator.amountInPaise))}</strong> for{' '}
              {simulator.description}
            </p>
          )}
        </ConfirmDialog>

        <ConfirmDialog
          open={confirmCancel}
          title="Cancel this order?"
          confirmLabel="Cancel order"
          busy={cancelling}
          error={cancelError}
          onConfirm={cancelOrder}
          onCancel={() => setConfirmCancel(false)}
        >
          <p>The reserved stock will be released for other buyers.</p>
        </ConfirmDialog>
      </main>
    </div>
  )
}
