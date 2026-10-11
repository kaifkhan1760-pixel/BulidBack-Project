import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { api } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { BuyBox } from '../components/BuyBox'
import { MaterialImage } from '../components/MaterialImage'
import { TileCalculator } from '../components/TileCalculator'
import { CATEGORY_LABELS, priceWithUnit, quantityText } from '../materials/materialRules'

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
  // Archived, deleted or never existed (or a non-number id): all look the same to buyers
  const notFound = error && (error.status === 404 || error.status === 400)

  return (
    <div className="bb-app">
      <AppHeader />
      <main className="bb-page">
        <Link to={backTo} className="bb-back-link">
          ← Back to results
        </Link>

        {loading && (
          <p className="bb-loading" role="status">
            Loading material…
          </p>
        )}

        {notFound && (
          <div className="bb-card bb-state" role="alert">
            <h1 className="bb-card-title">Material not found</h1>
            <p className="bb-muted">This listing does not exist or is no longer available.</p>
            <Link to="/materials" className="bb-button">
              Browse all materials
            </Link>
          </div>
        )}

        {error && !notFound && (
          <div className="bb-card bb-state" role="alert">
            <p className="bb-alert bb-alert-error">{error.message}</p>
            <button type="button" className="bb-button" onClick={() => setReloadCount((n) => n + 1)}>
              Try again
            </button>
          </div>
        )}

        {material && (
          <article className="bb-card bb-detail">
            <MaterialImage src={material.imageUrl} alt={material.title} className="bb-detail-image" />
            <div className="bb-detail-info">
              <p className="bb-eyebrow">{CATEGORY_LABELS[material.category]}</p>
              <h1 className="bb-card-title">{material.title}</h1>
              <p className="bb-price bb-price-large">{priceWithUnit(material.unitPrice, material.unit)}</p>
              <p className={material.inStock ? 'bb-stock' : 'bb-stock bb-stock-out'}>
                {quantityText(material.availableQuantity, material.unit)}
              </p>

              <dl className="bb-details">
                <div>
                  <dt>City</dt>
                  <dd>{material.city}</dd>
                </div>
                <div>
                  <dt>Seller</dt>
                  <dd>{material.sellerName}</dd>
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

              <h2 className="bb-section-title">Description</h2>
              <p className="bb-description">{material.description}</p>
            </div>
          </article>
        )}

        {material && (
          <div className="bb-detail-tools">
            {material.category === 'TILES' && (
              <TileCalculator material={material} onUseQuantity={setQuantity} />
            )}
            <BuyBox material={material} quantity={quantity} onQuantityChange={setQuantity} />
          </div>
        )}
      </main>
    </div>
  )
}
