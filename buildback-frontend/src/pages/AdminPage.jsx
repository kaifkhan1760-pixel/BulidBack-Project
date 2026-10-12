import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Icon } from '../components/Icon'
import { OrderList } from '../components/OrderList'
import { Pagination } from '../components/Pagination'
import { useApiQuery } from '../hooks/useApiQuery'
import { CATEGORY_LABELS, formatPrice, priceWithUnit } from '../materials/materialRules'
import { ORDER_STATUS, formatDate } from '../orders/orderRules'

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'listings', label: 'Listings' },
  { id: 'orders', label: 'Orders' },
  { id: 'users', label: 'Users' },
]

/** Admin console: totals, listing moderation, all orders, all users. Tab is kept in the URL. */
export default function AdminPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = TABS.some((t) => t.id === searchParams.get('tab')) ? searchParams.get('tab') : 'overview'

  return (
    <div className="bb-app">
      <main className="bb-page">
        <div className="bb-page-heading">
          <h1 className="bb-card-title">Admin console</h1>
          <p className="bb-muted">Review users, listings, orders and totals.</p>
        </div>

        <nav className="bb-tabs" aria-label="Admin sections">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              className={t.id === tab ? 'bb-tab bb-tab-active' : 'bb-tab'}
              aria-current={t.id === tab ? 'page' : undefined}
              onClick={() => setSearchParams({ tab: t.id })}
            >
              {t.label}
            </button>
          ))}
        </nav>

        {tab === 'overview' && <Overview />}
        {tab === 'listings' && <Listings searchParams={searchParams} setSearchParams={setSearchParams} />}
        {tab === 'orders' && <Orders searchParams={searchParams} setSearchParams={setSearchParams} />}
        {tab === 'users' && <Users searchParams={searchParams} setSearchParams={setSearchParams} />}
      </main>
    </div>
  )
}

function LoadState({ loading, error, reload }) {
  if (loading)
    return (
      <p className="bb-loading" role="status">
        Loading…
      </p>
    )
  if (error)
    return (
      <div className="bb-card bb-state" role="alert">
        <p className="bb-alert bb-alert-error">{error.message}</p>
        <button type="button" className="bb-button" onClick={reload}>
          Try again
        </button>
      </div>
    )
  return null
}

function Overview() {
  const { data, error, loading, reload } = useApiQuery('summary', (signal) => api.admin.summary(signal))
  if (!data) return <LoadState loading={loading} error={error} reload={reload} />

  const tiles = [
    ['Paid value', formatPrice(data.paidRevenue), 'rupee'],
    ['Buyers', data.buyers, 'user'],
    ['Sellers', data.sellers, 'store'],
    ['Admins', data.admins, 'shield'],
    ['Active listings', data.activeListings, 'package'],
    ['Archived listings', data.archivedListings, 'archive'],
    ['Awaiting payment', data.pendingOrders, 'clock'],
    ['Paid (to collect)', data.paidOrders, 'card'],
    ['Collected', data.collectedOrders, 'truck'],
    ['Cancelled', data.cancelledOrders, 'close'],
    ['Expired', data.expiredOrders, 'alert'],
  ]
  return (
    <ul className="bb-stats">
      {tiles.map(([label, value, icon]) => (
        <li key={label} className="bb-stat">
          <span className="bb-stat-icon">
            <Icon name={icon} size={22} />
          </span>
          <span className="bb-stat-value">{value}</span>
          <span className="bb-stat-label">{label}</span>
        </li>
      ))}
    </ul>
  )
}

/** Shared helper: change one URL filter and go back to page 0. */
function setFilter(searchParams, setSearchParams, key, value) {
  const next = new URLSearchParams(searchParams)
  if (value) next.set(key, value)
  else next.delete(key)
  next.delete('page')
  setSearchParams(next)
}

function setPage(searchParams, setSearchParams, page) {
  const next = new URLSearchParams(searchParams)
  next.set('page', String(page))
  setSearchParams(next)
}

function Listings({ searchParams, setSearchParams }) {
  const keyword = searchParams.get('keyword') ?? ''
  const status = searchParams.get('status') ?? ''
  const page = searchParams.get('page') ?? '0'
  const { data, error, loading, reload } = useApiQuery(`m|${keyword}|${status}|${page}`, (signal) =>
    api.admin.materials({ keyword, status, page, size: 10 }, signal),
  )
  const [search, setSearch] = useState(keyword)
  const [target, setTarget] = useState(null)
  const [busy, setBusy] = useState(false)
  const [dialogError, setDialogError] = useState(null)
  const [message, setMessage] = useState(null)

  async function archive() {
    setBusy(true)
    setDialogError(null)
    try {
      await api.admin.archiveMaterial(target.id)
      setMessage(`“${target.title}” was archived and is hidden from buyers.`)
      setTarget(null)
      reload()
    } catch (err) {
      setDialogError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="Listings">
      <form
        className="bb-admin-filters"
        onSubmit={(e) => {
          e.preventDefault()
          setFilter(searchParams, setSearchParams, 'keyword', search.trim())
        }}
      >
        <label className="bb-inline-field">
          Title{' '}
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label className="bb-inline-field">
          Status{' '}
          <select value={status} onChange={(e) => setFilter(searchParams, setSearchParams, 'status', e.target.value)}>
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>
        <button type="submit" className="bb-button bb-button-small">
          Search
        </button>
      </form>

      <div role="status" aria-live="polite">
        {message && <p className="bb-alert bb-alert-success">{message}</p>}
      </div>
      <LoadState loading={loading} error={error} reload={reload} />
      {data && data.content.length === 0 && <p className="bb-card bb-state">No listings found.</p>}
      {data && data.content.length > 0 && (
        <>
          <div className="bb-table-wrap">
            <table className="bb-table">
              <thead>
                <tr>
                  <th scope="col">Listing</th>
                  <th scope="col">Seller</th>
                  <th scope="col">Price</th>
                  <th scope="col">Stock</th>
                  <th scope="col">Status</th>
                  <th scope="col">
                    <span className="bb-visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((m) => (
                  <tr key={m.id}>
                    <td>
                      {m.status === 'ACTIVE' ? <Link to={`/materials/${m.id}`}>{m.title}</Link> : m.title}
                      <div className="bb-muted bb-small">
                        {CATEGORY_LABELS[m.category]} · {m.city}
                      </div>
                    </td>
                    <td>
                      {m.sellerName}
                      <div className="bb-muted bb-small">{m.sellerEmail}</div>
                    </td>
                    <td>{priceWithUnit(m.unitPrice, m.unit)}</td>
                    <td>
                      {m.availableQuantity} available
                      {m.reservedQuantity > 0 && <div className="bb-muted bb-small">{m.reservedQuantity} reserved</div>}
                    </td>
                    <td>{m.status === 'ACTIVE' ? 'Active' : 'Archived'}</td>
                    <td>
                      {m.status === 'ACTIVE' && (
                        <button
                          type="button"
                          className="bb-button bb-button-danger-outline bb-button-small"
                          onClick={() => {
                            setDialogError(null)
                            setTarget(m)
                          }}
                        >
                          Archive<span className="bb-visually-hidden"> {m.title}</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onChange={(p) => setPage(searchParams, setSearchParams, p)}
          />
        </>
      )}

      <ConfirmDialog
        open={target !== null}
        title="Archive this listing?"
        confirmLabel="Archive"
        busy={busy}
        error={dialogError}
        onConfirm={archive}
        onCancel={() => setTarget(null)}
      >
        <p>
          <strong>“{target?.title}”</strong> by {target?.sellerName} will be hidden from buyers. Existing orders
          are kept.
        </p>
      </ConfirmDialog>
    </section>
  )
}

function Orders({ searchParams, setSearchParams }) {
  const status = searchParams.get('status') ?? ''
  const page = searchParams.get('page') ?? '0'
  const { data, error, loading, reload } = useApiQuery(`o|${status}|${page}`, (signal) =>
    api.admin.orders({ status, page, size: 10 }, signal),
  )
  return (
    <section aria-label="Orders">
      <div className="bb-admin-filters">
        <label className="bb-inline-field">
          Status{' '}
          <select value={status} onChange={(e) => setFilter(searchParams, setSearchParams, 'status', e.target.value)}>
            <option value="">All</option>
            {Object.entries(ORDER_STATUS).map(([value, info]) => (
              <option key={value} value={value}>
                {info.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <LoadState loading={loading} error={error} reload={reload} />
      {data && data.content.length === 0 && <p className="bb-card bb-state">No orders found.</p>}
      {data && data.content.length > 0 && (
        <>
          <OrderList orders={data.content} view="admin" />
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onChange={(p) => setPage(searchParams, setSearchParams, p)}
          />
        </>
      )}
    </section>
  )
}

function Users({ searchParams, setSearchParams }) {
  const keyword = searchParams.get('keyword') ?? ''
  const role = searchParams.get('role') ?? ''
  const page = searchParams.get('page') ?? '0'
  const { data, error, loading, reload } = useApiQuery(`u|${keyword}|${role}|${page}`, (signal) =>
    api.admin.users({ keyword, role, page, size: 10 }, signal),
  )
  const [search, setSearch] = useState(keyword)

  return (
    <section aria-label="Users">
      <form
        className="bb-admin-filters"
        onSubmit={(e) => {
          e.preventDefault()
          setFilter(searchParams, setSearchParams, 'keyword', search.trim())
        }}
      >
        <label className="bb-inline-field">
          Name or email{' '}
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label className="bb-inline-field">
          Role{' '}
          <select value={role} onChange={(e) => setFilter(searchParams, setSearchParams, 'role', e.target.value)}>
            <option value="">All</option>
            <option value="BUYER">Buyer</option>
            <option value="SELLER">Seller</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
        <button type="submit" className="bb-button bb-button-small">
          Search
        </button>
      </form>
      <LoadState loading={loading} error={error} reload={reload} />
      {data && data.content.length === 0 && <p className="bb-card bb-state">No users found.</p>}
      {data && data.content.length > 0 && (
        <>
          <div className="bb-table-wrap">
            <table className="bb-table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Email</th>
                  <th scope="col">Role</th>
                  <th scope="col">Joined</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((u) => (
                  <tr key={u.id}>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td>{u.role}</td>
                    <td>{formatDate(u.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onChange={(p) => setPage(searchParams, setSearchParams, p)}
          />
        </>
      )}
    </section>
  )
}
