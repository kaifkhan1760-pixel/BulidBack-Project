import { Link, useParams } from 'react-router'
import { api, apiUrl } from '../api/client'
import { useAuth } from '../auth/authContext'
import { Icon } from '../components/Icon'
import { Logo } from '../components/Logo'
import { useApiQuery } from '../hooks/useApiQuery'
import { CATEGORY_LABELS, UNIT_LABELS, formatPrice } from '../materials/materialRules'
import { formatDate } from '../orders/orderRules'
import '../styles/invoice.css'

/**
 * Printable invoice for a paid order (/buyer/orders/:id/invoice).
 * "Download PDF" uses the browser's own print dialog ("Save as PDF"), so no
 * extra library is needed. Only the invoice itself is printed.
 */
export default function InvoicePage() {
  const { id } = useParams()
  const { user } = useAuth()
  const { data: order, error, loading, reload } = useApiQuery(`invoice-${id}`, (signal) =>
    api.orders.get(id, signal),
  )

  if (loading) {
    return (
      <main className="bb-page">
        <p className="bb-loading" role="status">
          Loading invoice…
        </p>
      </main>
    )
  }

  if (error || !order.invoiceNumber) {
    return (
      <main className="bb-page bb-page-narrow">
        <div className="bb-card bb-state" role="alert">
          <h1 className="bb-card-title">Invoice not available</h1>
          <p className="bb-muted">
            {error ? error.message : 'An invoice is created once the order has been paid.'}
          </p>
          <div className="bb-actions">
            <Link to={`/buyer/orders/${id}`} className="bb-btn bb-btn-primary">
              Back to the order
            </Link>
            {error && (
              <button type="button" className="bb-btn bb-btn-secondary" onClick={reload}>
                Try again
              </button>
            )}
          </div>
        </div>
      </main>
    )
  }

  const payment = order.payments?.find((p) => p.status === 'SUCCESS')
  const unit = UNIT_LABELS[order.material.unit]

  return (
    <main className="bb-page bb-invoice-page">
      <div className="bb-invoice-toolbar">
        <Link to={`/buyer/orders/${order.id}`} className="bb-back-link">
          <Icon name="arrowLeft" size={16} /> Back to order #{order.id}
        </Link>
        <div className="bb-actions">
          {/* The session cookie goes with this link, and the server checks the order is this buyer's */}
          <a href={apiUrl(`/buyer/orders/${order.id}/invoice.pdf`)} className="bb-btn bb-btn-primary" download>
            <Icon name="receipt" size={18} /> Download PDF
          </a>
          <button type="button" className="bb-btn bb-btn-secondary" onClick={() => window.print()}>
            Print
          </button>
        </div>
      </div>

      <article className="bb-invoice" aria-labelledby="invoice-title">
        <header className="bb-invoice-head">
          <div>
            <Logo />
            <p className="bb-muted bb-small">Construction surplus marketplace</p>
          </div>
          <div className="bb-invoice-meta">
            <h1 id="invoice-title">Invoice</h1>
            <dl>
              <div>
                <dt>Invoice no.</dt>
                <dd>{order.invoiceNumber}</dd>
              </div>
              <div>
                <dt>Order</dt>
                <dd>#{order.id}</dd>
              </div>
              <div>
                <dt>Paid on</dt>
                <dd>{formatDate(order.paidAt)}</dd>
              </div>
            </dl>
          </div>
        </header>

        <section className="bb-invoice-parties">
          <div>
            <h2>Billed to</h2>
            <p>
              <strong>{order.buyerName}</strong>
              {user?.email && <br />}
              {user?.email}
              {user?.phone && <br />}
              {user?.phone}
            </p>
          </div>
          <div>
            <h2>Sold by</h2>
            <p>
              <strong>{order.sellerName}</strong>
              {order.sellerEmail && <br />}
              {order.sellerEmail}
              {order.sellerPhone && <br />}
              {order.sellerPhone}
              <br />
              Pickup: {order.material.city}
            </p>
          </div>
        </section>

        <table className="bb-invoice-items">
          <thead>
            <tr>
              <th scope="col">Item</th>
              <th scope="col">Qty</th>
              <th scope="col">Rate</th>
              <th scope="col">Amount</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                {order.material.title}
                <span className="bb-muted bb-small">
                  {CATEGORY_LABELS[order.material.category]} · per {unit}
                </span>
              </td>
              <td>{order.quantity}</td>
              <td>{formatPrice(order.unitPrice)}</td>
              <td>{formatPrice(order.totalAmount)}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th scope="row" colSpan={3}>
                Total paid
              </th>
              <td>{formatPrice(order.totalAmount)}</td>
            </tr>
          </tfoot>
        </table>

        <section className="bb-invoice-payment">
          <h2>Payment</h2>
          <p>
            {payment ? (
              <>
                Paid online via {payment.provider === 'RAZORPAY' ? 'Razorpay' : 'the BuildBack simulator'} · Payment
                ID <span className="bb-mono">{payment.providerPaymentId}</span>
              </>
            ) : (
              'Paid online'
            )}
          </p>
        </section>

        <footer className="bb-invoice-foot">
          Prices are as listed by the seller. This is a final-year project: payments run in Razorpay test mode and
          no real money is charged.
        </footer>
      </article>
    </main>
  )
}
