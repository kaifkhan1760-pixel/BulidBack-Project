import { Link } from 'react-router'
import { Icon } from './Icon'
import { MaterialCard, MaterialCardSkeleton } from './MaterialCard'

/**
 * One row of listing cards with a heading and a "See all" link.
 * items: the listings, or null while they are loading (grey placeholders are shown).
 * Renders nothing when the row is empty.
 */
export function MaterialRail({ id, title, to, linkText = 'See all', items, error, size = 4, action }) {
  if (items && items.length === 0) return null
  return (
    <section className="bb-rail" aria-labelledby={`rail-${id}`}>
      <div className="bb-block-head">
        <h2 id={`rail-${id}`}>{title}</h2>
        {action}
        {to && (
          <Link to={to} className="bb-link">
            {linkText} <Icon name="chevronRight" size={16} />
          </Link>
        )}
      </div>
      {error && (
        <p className="bb-alert bb-alert-error" role="alert">
          Listings could not be loaded: {error.message}
        </p>
      )}
      <ul className="bb-rail-list">
        {items
          ? items.map((material) => (
              <li key={material.id}>
                <MaterialCard material={material} />
              </li>
            ))
          : !error &&
            Array.from({ length: size }, (_, i) => (
              <li key={i}>
                <MaterialCardSkeleton />
              </li>
            ))}
      </ul>
    </section>
  )
}
