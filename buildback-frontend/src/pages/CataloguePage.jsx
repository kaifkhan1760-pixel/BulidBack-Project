import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { MaterialImage } from '../components/MaterialImage'
import { Pagination } from '../components/Pagination'
import {
  CATEGORY_LABELS,
  LIMITS,
  SORT_OPTIONS,
  isValidPriceText,
  priceWithUnit,
  quantityText,
  toHundredths,
} from '../materials/materialRules'

// The filters live in the URL (?keyword=tile&category=TILES&page=1), so Back,
// Refresh and shared links all show the same results. Names match the backend.
const FILTER_KEYS = ['keyword', 'category', 'city', 'minPrice', 'maxPrice', 'inStock']

export default function CataloguePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.toString()
  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = `${query}#${reloadCount}`

  // result.key tells us which request the data belongs to; if it is not the
  // current one, we are still loading
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const loading = result.key !== requestKey

  useEffect(() => {
    // Cancel this request if the filters change before it finishes, so an
    // older, slower answer can never replace newer results
    const controller = new AbortController()
    api.materials
      .search(Object.fromEntries(new URLSearchParams(query)), controller.signal)
      .then((data) => setResult({ key: requestKey, data, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setResult({ key: requestKey, data: null, error })
      })
    return () => controller.abort()
  }, [query, requestKey])

  function applyFilters(filters) {
    const next = new URLSearchParams()
    for (const key of FILTER_KEYS) if (filters[key]) next.set(key, filters[key])
    if (searchParams.get('sort')) next.set('sort', searchParams.get('sort'))
    setSearchParams(next) // no "page": new filters start again at page 0
  }

  function changeSort(sort) {
    const next = new URLSearchParams(searchParams)
    if (sort === 'newest') next.delete('sort')
    else next.set('sort', sort)
    next.delete('page')
    setSearchParams(next)
  }

  function goToPage(page) {
    const next = new URLSearchParams(searchParams)
    if (page > 0) next.set('page', String(page))
    else next.delete('page')
    setSearchParams(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const clearFilters = () => setSearchParams(new URLSearchParams())
  const data = loading ? null : result.data
  const error = loading ? null : result.error
  const hasFilters = FILTER_KEYS.some((key) => searchParams.get(key))

  return (
    <div className="bb-app">
      <AppHeader />
      <main className="bb-page">
        <div className="bb-page-heading">
          <h1 className="bb-card-title">Browse surplus materials</h1>
          <p className="bb-muted">Leftover tiles, paint, plywood and fittings from sellers near you.</p>
        </div>

        <div className="bb-catalogue">
          {/* key: when the URL changes (Back button), the form resets to match it */}
          <FilterForm
            key={query}
            initial={Object.fromEntries(searchParams)}
            onApply={applyFilters}
            onClear={clearFilters}
          />

          <section className="bb-results" aria-labelledby="results-heading" aria-busy={loading}>
            <div className="bb-results-bar">
              <h2 id="results-heading" className="bb-results-count" aria-live="polite">
                {loading ? 'Searching…' : data ? `${data.totalElements} material${data.totalElements === 1 ? '' : 's'} found` : 'Results'}
              </h2>
              <label className="bb-inline-field">
                Sort by{' '}
                <select value={searchParams.get('sort') ?? 'newest'} onChange={(e) => changeSort(e.target.value)}>
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {error && (
              <div className="bb-card bb-state" role="alert">
                <p className="bb-alert bb-alert-error">
                  {error.status === 400
                    ? 'Some search values are not valid. Clear the filters and try again.'
                    : error.message}
                </p>
                <div className="bb-actions">
                  {error.status === 400 ? (
                    <button type="button" className="bb-button" onClick={clearFilters}>
                      Clear filters
                    </button>
                  ) : (
                    <button type="button" className="bb-button" onClick={() => setReloadCount((n) => n + 1)}>
                      Try again
                    </button>
                  )}
                </div>
              </div>
            )}

            {data && data.content.length === 0 && (
              <div className="bb-card bb-state">
                <p>
                  <strong>No materials match your search.</strong>
                </p>
                <p className="bb-muted">
                  {data.totalElements > 0 ? 'This page is empty.' : 'Try fewer filters or a different city.'}
                </p>
                {(hasFilters || data.totalElements > 0) && (
                  <button type="button" className="bb-button" onClick={clearFilters}>
                    Clear filters
                  </button>
                )}
              </div>
            )}

            {data && data.content.length > 0 && (
              <>
                <ul className="bb-grid">
                  {data.content.map((material) => (
                    <li key={material.id}>
                      <MaterialCard material={material} />
                    </li>
                  ))}
                </ul>
                <Pagination page={data.page} totalPages={data.totalPages} onChange={goToPage} />
              </>
            )}
          </section>
        </div>
      </main>
    </div>
  )
}

function MaterialCard({ material }) {
  const location = useLocation()
  return (
    <article className="bb-material-card">
      <MaterialImage src={material.imageUrl} alt={material.title} className="bb-material-image" />
      <div className="bb-material-body">
        <p className="bb-eyebrow">{CATEGORY_LABELS[material.category]}</p>
        <h3 className="bb-material-title">{material.title}</h3>
        <p className="bb-price">{priceWithUnit(material.unitPrice, material.unit)}</p>
        <p className="bb-muted bb-small">{material.city}</p>
        <p className={material.inStock ? 'bb-stock' : 'bb-stock bb-stock-out'}>
          {quantityText(material.availableQuantity, material.unit)}
        </p>
        {/* Remember the current search so "Back to results" returns here */}
        <Link
          className="bb-button bb-button-outline bb-button-small"
          to={`/materials/${material.id}`}
          state={{ fromSearch: location.search }}
        >
          View details
          <span className="bb-visually-hidden"> for {material.title}</span>
        </Link>
      </div>
    </article>
  )
}

function FilterForm({ initial, onApply, onClear }) {
  const [filters, setFilters] = useState({
    keyword: initial.keyword ?? '',
    category: initial.category ?? '',
    city: initial.city ?? '',
    minPrice: initial.minPrice ?? '',
    maxPrice: initial.maxPrice ?? '',
    inStock: initial.inStock === 'true' ? 'true' : '',
  })
  const [errors, setErrors] = useState({})
  const update = (field) => (event) => setFilters({ ...filters, [field]: event.target.value })

  function handleSubmit(event) {
    event.preventDefault()
    const cleaned = {
      ...filters,
      keyword: filters.keyword.trim(),
      city: filters.city.trim(),
      minPrice: filters.minPrice.trim(),
      maxPrice: filters.maxPrice.trim(),
    }
    const found = {}
    if (cleaned.keyword.length > LIMITS.maxKeyword) found.keyword = 'Use at most 100 characters'
    if (cleaned.minPrice && !isValidPriceText(cleaned.minPrice)) found.minPrice = 'Enter a price like 100 or 99.50'
    if (cleaned.maxPrice && !isValidPriceText(cleaned.maxPrice)) found.maxPrice = 'Enter a price like 100 or 99.50'
    if (!found.minPrice && !found.maxPrice && cleaned.minPrice && cleaned.maxPrice &&
        toHundredths(cleaned.minPrice) > toHundredths(cleaned.maxPrice)) {
      found.minPrice = 'Minimum cannot be more than maximum'
    }
    setErrors(found)
    if (Object.keys(found).length === 0) onApply(cleaned)
  }

  return (
    <form className="bb-card bb-filters" onSubmit={handleSubmit} noValidate aria-label="Search filters">
      <div className="bb-field">
        <label htmlFor="f-keyword">Search</label>
        <input
          id="f-keyword"
          type="search"
          placeholder="e.g. vitrified tile"
          value={filters.keyword}
          onChange={update('keyword')}
          aria-invalid={Boolean(errors.keyword)}
        />
        {errors.keyword && <p className="bb-field-error">{errors.keyword}</p>}
      </div>

      <div className="bb-field">
        <label htmlFor="f-category">Category</label>
        <select id="f-category" value={filters.category} onChange={update('category')}>
          <option value="">All categories</option>
          {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      <div className="bb-field">
        <label htmlFor="f-city">City</label>
        <input id="f-city" type="text" placeholder="Exact city, e.g. Pune" value={filters.city} onChange={update('city')} />
      </div>

      <fieldset className="bb-price-range">
        <legend>Price per unit (₹)</legend>
        <div className="bb-field">
          <label htmlFor="f-min">Min</label>
          <input
            id="f-min"
            inputMode="decimal"
            value={filters.minPrice}
            onChange={update('minPrice')}
            aria-invalid={Boolean(errors.minPrice)}
            aria-describedby={errors.minPrice ? 'f-min-error' : undefined}
          />
        </div>
        <div className="bb-field">
          <label htmlFor="f-max">Max</label>
          <input
            id="f-max"
            inputMode="decimal"
            value={filters.maxPrice}
            onChange={update('maxPrice')}
            aria-invalid={Boolean(errors.maxPrice)}
            aria-describedby={errors.maxPrice ? 'f-max-error' : undefined}
          />
        </div>
        {errors.minPrice && <p id="f-min-error" className="bb-field-error">{errors.minPrice}</p>}
        {errors.maxPrice && <p id="f-max-error" className="bb-field-error">{errors.maxPrice}</p>}
      </fieldset>

      <label className="bb-checkbox">
        <input
          type="checkbox"
          checked={filters.inStock === 'true'}
          onChange={(e) => setFilters({ ...filters, inStock: e.target.checked ? 'true' : '' })}
        />
        In stock only
      </label>

      <div className="bb-actions">
        <button type="submit" className="bb-button">
          Apply
        </button>
        <button type="button" className="bb-button bb-button-outline" onClick={onClear}>
          Clear filters
        </button>
      </div>
    </form>
  )
}
