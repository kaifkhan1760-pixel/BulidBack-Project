import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { MaterialImage } from '../components/MaterialImage'
import { Pagination } from '../components/Pagination'
import { CATEGORY_LABELS, priceWithUnit, quantityText } from '../materials/materialRules'

const PAGE_SIZE = 10

export default function SellerMaterialsPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const page = searchParams.get('page') ?? '0'

  // "Material created." etc. handed over by the form page
  const [flash, setFlash] = useState(location.state?.message ?? null)
  useEffect(() => {
    // Forget the message in browser history so a refresh does not show it again
    if (location.state?.message) navigate({ search: location.search }, { replace: true, state: null })
  }, [location.state, location.search, navigate])

  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = `${status}|${page}|${reloadCount}`
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const loading = result.key !== requestKey

  useEffect(() => {
    const controller = new AbortController()
    api.sellerMaterials
      .list({ status, page, size: PAGE_SIZE }, controller.signal)
      .then((data) => {
        // e.g. archiving the only item on the last page: go back to the last real page
        if (data.content.length === 0 && data.page > 0) {
          setSearchParams(
            (prev) => {
              const next = new URLSearchParams(prev)
              next.set('page', String(Math.max(0, data.totalPages - 1)))
              return next
            },
            { replace: true },
          )
          return
        }
        setResult({ key: requestKey, data, error: null })
      })
      .catch((error) => {
        // 401 = session expired: the app sends the user to login
        if (error.name !== 'AbortError' && error.status !== 401) setResult({ key: requestKey, data: null, error })
      })
    return () => controller.abort()
  }, [status, page, requestKey, setSearchParams])

  // ----- archive -----
  const [toArchive, setToArchive] = useState(null)
  const [archiving, setArchiving] = useState(false)
  const [archiveError, setArchiveError] = useState(null)

  async function confirmArchive() {
    setArchiving(true)
    setArchiveError(null)
    try {
      await api.sellerMaterials.archive(toArchive.id, toArchive.version)
      // Only now, after the server agreed, do we change what is shown
      setFlash(`“${toArchive.title}” was archived. Buyers can no longer see it.`)
      setToArchive(null)
      setReloadCount((n) => n + 1)
    } catch (error) {
      setArchiveError(
        error.status === 409
          ? `${error.message} Close this box and reload the list.`
          : error.message,
      )
    } finally {
      setArchiving(false)
    }
  }

  function changeStatus(value) {
    const next = new URLSearchParams()
    if (value) next.set('status', value)
    setSearchParams(next)
  }

  function goToPage(newPage) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(newPage))
    setSearchParams(next)
  }

  const data = loading ? null : result.data
  const error = loading ? null : result.error

  return (
    <div className="bb-app">
      <main className="bb-page">
        <div className="bb-page-heading bb-page-heading-row">
          <div>
            <h1 className="bb-card-title">My materials</h1>
            <p className="bb-muted">Add, edit and archive your surplus listings.</p>
          </div>
          <Link to="/seller/materials/new" className="bb-button">
            + Add material
          </Link>
        </div>

        <div role="status" aria-live="polite">
          {flash && <p className="bb-alert bb-alert-success">{flash}</p>}
        </div>

        <div className="bb-results-bar">
          <label className="bb-inline-field">
            Show{' '}
            <select value={status} onChange={(e) => changeStatus(e.target.value)}>
              <option value="">All listings</option>
              <option value="ACTIVE">Active</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          </label>
          {data && <span className="bb-muted">{data.totalElements} total</span>}
        </div>

        {loading && (
          <p className="bb-loading" role="status">
            Loading your listings…
          </p>
        )}

        {error && (
          <div className="bb-card bb-state" role="alert">
            <p className="bb-alert bb-alert-error">{error.message}</p>
            <button type="button" className="bb-button" onClick={() => setReloadCount((n) => n + 1)}>
              Try again
            </button>
          </div>
        )}

        {data && data.content.length === 0 && (
          <div className="bb-card bb-state">
            <p>
              <strong>{status ? 'No listings with this status.' : 'You have not listed any materials yet.'}</strong>
            </p>
            <Link to="/seller/materials/new" className="bb-button">
              Add your first material
            </Link>
          </div>
        )}

        {data && data.content.length > 0 && (
          <>
            <ul className="bb-inventory">
              {data.content.map((m) => {
                const archived = m.status === 'ARCHIVED'
                return (
                  <li key={m.id} className={archived ? 'bb-inventory-row bb-archived' : 'bb-inventory-row'}>
                    <MaterialImage src={m.imageUrl} alt={m.title} className="bb-thumb" />
                    <div className="bb-inventory-info">
                      <p className="bb-material-title">
                        {m.title}{' '}
                        {archived ? (
                          <span className="bb-badge bb-badge-muted">Archived</span>
                        ) : (
                          <span className="bb-badge">Active</span>
                        )}
                      </p>
                      <p className="bb-muted bb-small">
                        {CATEGORY_LABELS[m.category]} · {m.city}
                      </p>
                      <p className="bb-small">
                        <strong>{priceWithUnit(m.unitPrice, m.unit)}</strong> ·{' '}
                        <span className={m.availableQuantity === 0 ? 'bb-stock-out' : undefined}>
                          {quantityText(m.availableQuantity, m.unit)}
                        </span>
                        {m.reservedQuantity > 0 && <> · {m.reservedQuantity} reserved for unpaid orders</>}
                      </p>
                    </div>
                    <div className="bb-inventory-actions">
                      {archived ? (
                        <>
                          <button type="button" className="bb-button bb-button-outline bb-button-small" disabled>
                            Edit
                          </button>
                          <button type="button" className="bb-button bb-button-outline bb-button-small" disabled>
                            Archive
                          </button>
                        </>
                      ) : (
                        <>
                          <Link to={`/seller/materials/${m.id}/edit`} className="bb-button bb-button-outline bb-button-small">
                            Edit<span className="bb-visually-hidden"> {m.title}</span>
                          </Link>
                          <button
                            type="button"
                            className="bb-button bb-button-danger-outline bb-button-small"
                            onClick={() => {
                              setArchiveError(null)
                              setToArchive(m)
                            }}
                          >
                            Archive<span className="bb-visually-hidden"> {m.title}</span>
                          </button>
                        </>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={goToPage} />
          </>
        )}

        <ConfirmDialog
          open={toArchive !== null}
          title="Archive this listing?"
          confirmLabel="Archive"
          busy={archiving}
          error={archiveError}
          onConfirm={confirmArchive}
          onCancel={() => {
            const hadConflict = archiveError !== null
            setToArchive(null)
            if (hadConflict) setReloadCount((n) => n + 1) // fetch the latest versions
          }}
        >
          <p>
            <strong>“{toArchive?.title}”</strong> will be hidden from buyers. It stays in your history but cannot
            be edited or restored.
          </p>
        </ConfirmDialog>
      </main>
    </div>
  )
}
