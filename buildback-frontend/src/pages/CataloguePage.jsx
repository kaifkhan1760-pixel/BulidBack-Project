import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { Icon } from '../components/Icon'
import { MaterialCard, MaterialCardSkeleton } from '../components/MaterialCard'
import { Pagination } from '../components/Pagination'
import { CATEGORY_LABELS, LIMITS, SORT_OPTIONS, isValidPriceText, toHundredths } from '../materials/materialRules'

// The filters live in the URL (?keyword=tile&category=TILES&page=1), so Back,
// Refresh and shared links all show the same results. Names match the backend.
const FILTER_KEYS = ['keyword', 'category', 'city', 'minPrice', 'maxPrice', 'inStock']

// 4 cards per row x 2 rows
const PAGE_SIZE = 8

export default function CataloguePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.toString()
  const [reloadCount, setReloadCount] = useState(0)
  const [filtersOpen, setFiltersOpen] = useState(false) // phones only: filters start collapsed
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
      .search({ size: PAGE_SIZE, ...Object.fromEntries(new URLSearchParams(query)) }, controller.signal)
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
    setFiltersOpen(false)
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

  const activeCategory = searchParams.get('category') ?? ''
  const keyword = searchParams.get('keyword') ?? ''
  const city = searchParams.get('city') ?? ''
  const heading = CATEGORY_LABELS[activeCategory] ?? 'All materials'

  return (
    <main>
      <header className="bb-catalogue-head">
        <div className="bb-container">
          <nav className="bb-breadcrumb" aria-label="Breadcrumb">
            <Link to="/">Home</Link>
            <Icon name="chevronRight" size={14} />
            {activeCategory ? (
              <>
                <Link to="/materials">Materials</Link>
                <Icon name="chevronRight" size={14} />
                <span aria-current="page">{heading}</span>
              </>
            ) : (
              <span aria-current="page">Materials</span>
            )}
          </nav>
          <h1>
            {keyword ? <>Results for &ldquo;{keyword}&rdquo;</> : heading}
            {city && <span className="bb-muted"> in {city}</span>}
          </h1>
          <p>Surplus stock from builders, contractors and homeowners. Prices are per unit, in INR.</p>
        </div>
      </header>

      <div className="bb-page">
        <div className="bb-catalogue">
          {/* key: when the URL changes (Back button), the form resets to match it */}
          <FilterForm
            key={query}
            initial={Object.fromEntries(searchParams)}
            open={filtersOpen}
            onApply={applyFilters}
            onClear={clearFilters}
          />

          <section className="bb-results" aria-labelledby="results-heading" aria-busy={loading}>
            <div className="bb-results-bar">
              <h2 id="results-heading" className="bb-results-count" aria-live="polite">
                {loading ? (
                  'Searching…'
                ) : data ? (
                  <>
                    {data.totalElements.toLocaleString('en-IN')} material{data.totalElements === 1 ? '' : 's'}{' '}
                    <span>found</span>
                  </>
                ) : (
                  'Results'
                )}
              </h2>
              <button
                type="button"
                className="bb-btn bb-btn-secondary bb-btn-sm bb-filters-toggle"
                aria-expanded={filtersOpen}
                aria-controls="catalogue-filters"
                onClick={() => setFiltersOpen((open) => !open)}
              >
                <Icon name="filter" size={16} /> Filters
              </button>
              <label className="bb-inline-field">
                Sort by
                <select value={searchParams.get('sort') ?? 'newest'} onChange={(e) => changeSort(e.target.value)}>
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {hasFilters && (
              <div className="bb-active-filters" role="group" aria-label="Active filters">
                {activeFilterChips(searchParams).map((chip) => (
                  <Link
                    key={chip.key}
                    to={linkWithout(searchParams, chip.key)}
                    className="bb-filter-chip"
                    aria-label={`Remove filter: ${chip.label}`}
                  >
                    {chip.label}
                    <Icon name="close" size={14} />
                  </Link>
                ))}
                <button type="button" className="bb-link bb-link-button" onClick={clearFilters}>
                  Clear all
                </button>
              </div>
            )}

            {loading && (
              <div className="bb-card-grid">
                {Array.from({ length: 8 }, (_, i) => (
                  <MaterialCardSkeleton key={i} />
                ))}
              </div>
            )}

            {error && (
              <div className="bb-card bb-state" role="alert">
                <Icon name="alert" size={32} />
                <p className="bb-alert bb-alert-error">
                  {error.status === 400
                    ? 'Some search values are not valid. Clear the filters and try again.'
                    : error.message}
                </p>
                <div className="bb-actions">
                  {error.status === 400 ? (
                    <button type="button" className="bb-btn bb-btn-primary" onClick={clearFilters}>
                      Clear filters
                    </button>
                  ) : (
                    <button type="button" className="bb-btn bb-btn-primary" onClick={() => setReloadCount((n) => n + 1)}>
                      Try again
                    </button>
                  )}
                </div>
              </div>
            )}

            {data && data.content.length === 0 && (
              <div className="bb-empty">
                <Icon name="search" size={32} />
                <p>
                  <strong>No materials match your search.</strong>
                </p>
                <p>{data.totalElements > 0 ? 'This page is empty.' : 'Try fewer filters or a different city.'}</p>
                {(hasFilters || data.totalElements > 0) && (
                  <button type="button" className="bb-btn bb-btn-primary" onClick={clearFilters}>
                    Clear filters
                  </button>
                )}
              </div>
            )}

            {data && data.content.length > 0 && (
              <>
                <ul className="bb-card-grid">
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
      </div>
    </main>
  )
}

/** One removable chip per filter in the URL, e.g. "Tiles", "City: Pune", "Max ₹500". */
function activeFilterChips(params) {
  const chips = []
  const add = (key, label) => params.get(key) && chips.push({ key, label })
  add('keyword', `“${params.get('keyword')}”`)
  add('category', CATEGORY_LABELS[params.get('category')] ?? params.get('category'))
  add('city', `City: ${params.get('city')}`)
  add('minPrice', `Min ₹${params.get('minPrice')}`)
  add('maxPrice', `Max ₹${params.get('maxPrice')}`)
  if (params.get('inStock') === 'true') chips.push({ key: 'inStock', label: 'In stock only' })
  return chips
}

/** The current catalogue URL without one filter (and back on page 1). */
function linkWithout(params, key) {
  const next = new URLSearchParams(params)
  next.delete(key)
  next.delete('page')
  const text = next.toString()
  return text ? `/materials?${text}` : '/materials'
}

function FilterForm({ initial, open, onApply, onClear }) {
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
    <form
      id="catalogue-filters"
      className={open ? 'bb-card bb-filters is-open' : 'bb-card bb-filters'}
      onSubmit={handleSubmit}
      noValidate
      aria-label="Search filters"
    >
      <h2 className="bb-filters-title">
        Filters
        <Icon name="filter" size={18} />
      </h2>
      <div className="bb-filter-group bb-field">
        <label htmlFor="f-keyword" className="bb-filter-label">
          Keyword
        </label>
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

      <fieldset className="bb-filter-group">
        <legend>Category</legend>
        <ul className="bb-filter-options">
          {[['', 'All categories'], ...Object.entries(CATEGORY_LABELS)].map(([value, label]) => (
            <li key={value}>
              <label className="bb-filter-option">
                <input
                  type="radio"
                  name="f-category"
                  value={value}
                  checked={filters.category === value}
                  onChange={update('category')}
                />
                {label}
              </label>
            </li>
          ))}
        </ul>
      </fieldset>

      <div className="bb-filter-group bb-field">
        <label htmlFor="f-city" className="bb-filter-label">
          City
        </label>
        <input id="f-city" type="text" placeholder="e.g. Pune" value={filters.city} onChange={update('city')} />
      </div>

      <fieldset className="bb-price-range">
        <legend className="bb-filter-label">Price per unit (₹)</legend>
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

      <div className="bb-filter-group">
        <label className="bb-checkbox">
          <input
            type="checkbox"
            checked={filters.inStock === 'true'}
            onChange={(e) => setFilters({ ...filters, inStock: e.target.checked ? 'true' : '' })}
          />
          In stock only
        </label>
      </div>

      <div className="bb-actions">
        <button type="submit" className="bb-btn bb-btn-primary">
          Apply
        </button>
        <button type="button" className="bb-btn bb-btn-secondary" onClick={onClear}>
          Clear
        </button>
      </div>
    </form>
  )
}
