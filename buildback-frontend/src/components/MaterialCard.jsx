import { Link, useLocation } from 'react-router'
import { CATEGORY_LABELS, UNIT_LABELS, formatPriceCompact } from '../materials/materialRules'
import { Icon } from './Icon'
import { MaterialImage } from './MaterialImage'

// At or below this many units we show an "Only N left" badge
const LOW_STOCK = 5

/**
 * One listing card. The title link is "stretched" over the whole card, so the
 * card is one big click target but screen readers hear a single clear link.
 */
export function MaterialCard({ material }) {
  const location = useLocation()
  const lowStock = material.inStock && material.availableQuantity <= LOW_STOCK
  return (
    <article className="bb-mcard">
      <div className="bb-mcard-media">
        <MaterialImage src={material.imageUrl} alt="" className="bb-mcard-img" />
        {!material.inStock && <span className="bb-mcard-badge bb-mcard-badge-out">Out of stock</span>}
        {lowStock && <span className="bb-mcard-badge">Only {material.availableQuantity} left</span>}
      </div>
      <div className="bb-mcard-body">
        <p className="bb-mcard-cat">{CATEGORY_LABELS[material.category]}</p>
        <h3 className="bb-mcard-title">
          <Link to={`/materials/${material.id}`} state={{ fromSearch: location.search }} className="bb-stretched">
            {material.title}
          </Link>
        </h3>
        <p className="bb-mcard-price">
          <strong>{formatPriceCompact(material.unitPrice)}</strong>
          <span>/ {UNIT_LABELS[material.unit]}</span>
        </p>
        <p className="bb-mcard-meta">
          <span>
            <Icon name="mapPin" size={14} />
            {material.city}
          </span>
          {material.inStock && <span>{material.availableQuantity.toLocaleString('en-IN')} in stock</span>}
        </p>
      </div>
    </article>
  )
}

/** Grey placeholder shown while listings load (keeps the layout from jumping). */
export function MaterialCardSkeleton() {
  return (
    <div className="bb-mcard bb-skeleton-card" aria-hidden="true">
      <div className="bb-mcard-media bb-skeleton" />
      <div className="bb-mcard-body">
        <div className="bb-skeleton bb-skeleton-line" style={{ width: '30%' }} />
        <div className="bb-skeleton bb-skeleton-line" style={{ width: '85%' }} />
        <div className="bb-skeleton bb-skeleton-line" style={{ width: '45%', height: 20 }} />
      </div>
    </div>
  )
}
