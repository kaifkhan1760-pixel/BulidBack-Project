import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { OrderList } from '../components/OrderList'
import { Pagination } from '../components/Pagination'
import { useApiQuery } from '../hooks/useApiQuery'
import { formatPrice } from '../materials/materialRules'
import { ORDER_STATUS } from '../orders/orderRules'

/**
 * /buyer/orders  (view="buyer"):  my purchases
 * /seller/orders (view="seller"): orders on my listings, with "Mark collected"
 * Status filter and page live in the URL.
 */
export default function OrdersPage({ view }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const page = searchParams.get('page') ?? '0'
  const source = view === 'seller' ? api.sellerOrders : api.orders

  const { data, error, loading, reload } = useApiQuery(`${view}|${status}|${page}`, (signal) =>
    source.list({ status, page, size: 10 }, signal),
  )

  const [busyId, setBusyId] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [message, setMessage] = useState(null)

  // Buyer: the order waiting for "Yes, cancel" in the dialog
  const [cancelTarget, setCancelTarget] = useState(null)
  const [cancelError, setCancelError] = useState(null)

  async function cancel() {
    const order = cancelTarget
    setBusyId(order.id)
    setCancelError(null)
    try {
      const updated = await api.orders.cancel(order.id)
      setCancelTarget(null)
      setMessage(
        updated.status !== 'CANCELLED'
          ? `Order #${order.id} had already expired.`
          : order.status === 'PAID'
            ? `Order #${order.id} cancelled. A refund of ${formatPrice(order.totalAmount)} has been started.`
            : `Order #${order.id} cancelled. The reserved stock was released.`,
      )
      reload()
    } catch (err) {
      setCancelError(err.message)
    } finally {
      setBusyId(null)
    }
  }

  async function collect(order) {
    setBusyId(order.id)
    setActionError(null)
    try {
      await api.sellerOrders.collect(order.id)
      setMessage(`Order #${order.id} marked as collected.`)
      reload()
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusyId(null)
    }
  }

  function changeStatus(value) {
    setSearchParams(value ? { status: value } : {})
  }

  function goToPage(newPage) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(newPage))
    setSearchParams(next)
  }

  const title = view === 'seller' ? 'Orders for my listings' : 'My orders'

  return (
    <div className="bb-app">
      <main className="bb-page">
        <div className="bb-page-heading">
          <h1 className="bb-card-title">{title}</h1>
          <p className="bb-muted">
            {view === 'seller'
              ? 'Hand over paid orders to the buyer, then mark them as collected.'
              : 'Unpaid orders hold your stock for 15 minutes.'}
          </p>
        </div>

        <div role="status" aria-live="polite">
          {message && <p className="bb-alert bb-alert-success">{message}</p>}
        </div>
        {actionError && (
          <p className="bb-alert bb-alert-error" role="alert">
            {actionError}
          </p>
        )}

        <div className="bb-results-bar">
          <label className="bb-inline-field">
            Show{' '}
            <select value={status} onChange={(e) => changeStatus(e.target.value)}>
              <option value="">All orders</option>
              {Object.entries(ORDER_STATUS).map(([value, info]) => (
                <option key={value} value={value}>
                  {info.label}
                </option>
              ))}
            </select>
          </label>
          {data && <span className="bb-muted">{data.totalElements} total</span>}
        </div>

        {loading && (
          <p className="bb-loading" role="status">
            Loading orders…
          </p>
        )}
        {error && (
          <div className="bb-card bb-state" role="alert">
            <p className="bb-alert bb-alert-error">{error.message}</p>
            <button type="button" className="bb-button" onClick={reload}>
              Try again
            </button>
          </div>
        )}
        {data && data.content.length === 0 && (
          <div className="bb-card bb-state">
            <p>
              <strong>No orders {status ? 'with this status' : 'yet'}.</strong>
            </p>
            {view === 'buyer' && (
              <Link to="/materials" className="bb-button">
                Browse materials
              </Link>
            )}
          </div>
        )}
        {data && data.content.length > 0 && (
          <>
            <OrderList
              orders={data.content}
              view={view}
              onCollect={collect}
              onCancel={(order) => {
                setCancelError(null)
                setCancelTarget(order)
              }}
              busyId={busyId}
            />
            <Pagination page={data.page} totalPages={data.totalPages} onChange={goToPage} />
          </>
        )}
        <ConfirmDialog
          open={cancelTarget !== null}
          title={cancelTarget ? `Cancel order #${cancelTarget.id}?` : 'Cancel order?'}
          confirmLabel="Yes, cancel order"
          cancelLabel="Keep order"
          busy={cancelTarget !== null && busyId === cancelTarget.id}
          error={cancelError}
          onConfirm={cancel}
          onCancel={() => setCancelTarget(null)}
        >
          {cancelTarget?.status === 'PAID' ? (
            <p>
              The full amount of <strong>{formatPrice(cancelTarget.totalAmount)}</strong> will be refunded to your
              payment method, and the material goes back on sale.
            </p>
          ) : (
            <p>The reserved stock will be released for other buyers.</p>
          )}
        </ConfirmDialog>
      </main>
    </div>
  )
}
