import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { api } from '../api/client'
import { BuyBox } from '../components/BuyBox'
import { Icon } from '../components/Icon'
import { MaterialRail } from '../components/MaterialRail'
import { PhotoGallery } from '../components/PhotoGallery'
import { TileCalculator } from '../components/TileCalculator'
import { useApiQuery } from '../hooks/useApiQuery'
import { CATEGORY_ICONS, CATEGORY_LABELS, UNIT_LABELS, formatPrice, quantityText } from '../materials/materialRules'
import { rememberViewed } from '../materials/recentlyViewed'

export default function MaterialDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const backTo = `/materials${location.state?.fromSearch ?? ''}`
  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = `${id}#${reloadCount}`
  const [result, setResult] = useState({ key: null, material: null, error: null })
  const [quantity, setQuantity] = useState('1') // shared by the calculator and the order form
  const loading = result.key !== requestKey

  useEffect(() => {
    const controller = new AbortController()
    api.materials
      .get(id, controller.signal)
      .then((material) => setResult({ key: requestKey, material, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setResult({ key: requestKey, material: null, error })
      })
    return () => controller.abort()
  }, [id, requestKey])

  const material = loading ? null : result.material
  const error = loading ? null : result.error

  // Remember this listing for the "Recently viewed" row on the home page
  useEffect(() => {
    if (material) rememberViewed(material)
  }, [material])
  // Archived, deleted or never existed (or a non-number id): all look the same to buyers
  const notFound = error && (error.status === 404 || error.status === 400)

  return (
    <main className="bb-page">
      <nav className="bb-breadcrumb" aria-label="Breadcrumb">
        <Link to={backTo}>Materials</Link>
        {material && (
          <>
            <Icon name="chevronRight" size={14} />
            <Link to={`/materials?category=${material.category}`}>{CATEGORY_LABELS[material.category]}</Link>
            <Icon name="chevronRight" size={14} />
            <span aria-current="page">{material.title}</span>
          </>
        )}
      </nav>

      {loading && (
        <p className="bb-loading" role="status">
          Loading material…
        </p>
      )}

      {notFound && (
        <div className="bb-card bb-state" role="alert">
          <Icon name="package" size={36} />
          <h1 className="bb-card-title">Material not found</h1>
          <p className="bb-muted">This listing does not exist or is no longer available.</p>
          <Link to="/materials" className="bb-btn bb-btn-primary">
            Browse all materials
          </Link>
        </div>
      )}

      {error && !notFound && (
        <div className="bb-card bb-state" role="alert">
          <p className="bb-alert bb-alert-error">{error.message}</p>
          <button type="button" className="bb-btn bb-btn-primary" onClick={() => setReloadCount((n) => n + 1)}>
            Try again
          </button>
        </div>
      )}

      {material && (
        <div className="bb-detail-layout">
          <div className="bb-detail-main">
            <article className="bb-card bb-detail-hero">
              <PhotoGallery
                photos={material.imageUrls ?? (material.imageUrl ? [material.imageUrl] : [])}
                title={material.title}
              />
              <div className="bb-detail-info">
                <span className="bb-chip">
                  <Icon name={CATEGORY_ICONS[material.category]} size={14} />
                  {CATEGORY_LABELS[material.category]}
                </span>
                <h1>{material.title}</h1>
                <p className="bb-detail-price-row">
                  <span className="bb-price bb-price-large">{formatPrice(material.unitPrice)}</span>
                  <span>per {UNIT_LABELS[material.unit]}</span>
                </p>
                <p className={material.inStock ? 'bb-stock' : 'bb-stock-out'}>
                  <Icon name={material.inStock ? 'checkCircle' : 'alert'} size={18} />
                  {quantityText(material.availableQuantity, material.unit)}
                </p>

                <div className="bb-seller">
                  <span className="bb-avatar" aria-hidden="true">
                    <Icon name="store" size={18} />
                  </span>
                  <span>
                    <strong>{material.sellerName}</strong>
                    <small>
                      <Icon name="mapPin" size={14} /> Pick-up in {material.city}
                    </small>
                  </span>
                </div>

                <dl className="bb-details">
                  <div>
                    <dt>Category</dt>
                    <dd>{CATEGORY_LABELS[material.category]}</dd>
                  </div>
                  <div>
                    <dt>Sold per</dt>
                    <dd>{UNIT_LABELS[material.unit]}</dd>
                  </div>
                  {material.category === 'TILES' && (
                    <div>
                      <dt>Tile size</dt>
                      <dd>
                        {material.tileLengthFeet} ft × {material.tileWidthFeet} ft
                      </dd>
                    </div>
                  )}
                </dl>
              </div>
            </article>

            <section className="bb-card" aria-labelledby="desc-title">
              <h2 id="desc-title" className="bb-section-title">
                Description
              </h2>
              <p className="bb-description">{material.description}</p>
            </section>

            {material.category === 'TILES' && <TileCalculator material={material} onUseQuantity={setQuantity} />}
          </div>

          <aside className="bb-detail-aside" aria-label="Order">
            <BuyBox material={material} quantity={quantity} onQuantityChange={setQuantity} />
            <p className="bb-buy-note">
              <Icon name="shield" size={18} />
              Payment is verified by our server before an order is confirmed. Collection is arranged with the
              seller after payment.
            </p>
          </aside>
        </div>
      )}
      {material && <SimilarMaterials material={material} />}
    </main>
  )
}

/** Other listings in the same category (the current one is left out). */
function SimilarMaterials({ material }) {
  const similar = useApiQuery(`similar-${material.category}-${material.id}`, (signal) =>
    api.materials.search({ category: material.category, size: 5 }, signal),
  )
  const items = similar.data
    ? similar.data.content.filter((item) => item.id !== material.id).slice(0, 4)
    : null
  return (
    <MaterialRail
      id="similar"
      title={`More in ${CATEGORY_LABELS[material.category]}`}
      to={`/materials?category=${material.category}`}
      items={items}
      error={similar.error}
    />
  )
}
