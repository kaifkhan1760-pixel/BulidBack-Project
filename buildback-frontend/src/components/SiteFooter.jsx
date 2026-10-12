import { Link } from 'react-router'
import { CATEGORY_LABELS } from '../materials/materialRules'
import { Logo } from './Logo'

// Computed once when the app loads (not on every render)
const YEAR = new Date().getFullYear()

export function SiteFooter() {
  return (
    <footer className="bb-footer">
      <div className="bb-container bb-footer-grid">
        <div className="bb-footer-brand">
          <Logo light />
          <p>
            India&apos;s marketplace for surplus construction materials. Builders, contractors and homeowners
            sell leftover tiles, paint, plywood and fittings to buyers nearby.
          </p>
        </div>

        <div>
          <h2 className="bb-footer-title">Browse</h2>
          <ul>
            {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
              <li key={value}>
                <Link to={`/materials?category=${value}`}>{label}</Link>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h2 className="bb-footer-title">Sell</h2>
          <ul>
            <li>
              <Link to="/register">Become a seller</Link>
            </li>
            <li>
              <Link to="/seller/materials/new">List a material</Link>
            </li>
            <li>
              <Link to="/login">Log in</Link>
            </li>
          </ul>
        </div>

        <div>
          <h2 className="bb-footer-title">About</h2>
          <ul>
            <li>
              <a href="/#how-it-works">How it works</a>
            </li>
            <li>
              <a href="/#calculator">Tile calculator</a>
            </li>
            <li>
              <Link to="/connection-check">System status</Link>
            </li>
          </ul>
        </div>
      </div>
      <div className="bb-container bb-footer-bottom">
        <p>© {YEAR} BuildBack · Final-year project · Payments run in Razorpay test mode</p>
        <p>
          Photos from{' '}
          <a href="https://unsplash.com/license" target="_blank" rel="noreferrer">
            Unsplash
          </a>
        </p>
      </div>
    </footer>
  )
}
