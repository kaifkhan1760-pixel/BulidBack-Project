import { Link } from 'react-router'
import { STOCK_IMAGE_LARGE } from '../content/siteImages'
import { Icon } from './Icon'
import { Logo } from './Logo'

const POINTS = [
  'Sell leftover tiles, paint, plywood and fittings from any project',
  'Buy from local sellers at well below showroom prices',
  'Stock is held while you pay, and every payment is verified',
]

/** Login / register frame: brand panel on the left, form card on the right. */
export function AuthLayout({ title, subtitle, children }) {
  return (
    <div className="bb-auth-page">
      <aside className="bb-auth-aside" style={{ '--auth-image': `url("${STOCK_IMAGE_LARGE}")` }}>
        <Logo light />
        <div className="bb-auth-aside-body">
          <h2>India&apos;s marketplace for surplus building material</h2>
          <ul>
            {POINTS.map((point) => (
              <li key={point}>
                <span className="bb-auth-point-icon">
                  <Icon name="checkCircle" size={20} />
                </span>
                {point}
              </li>
            ))}
          </ul>
        </div>
        <p className="bb-auth-aside-foot">Payments run in Razorpay test mode — no real money.</p>
      </aside>

      <main className="bb-auth-main">
        <div className="bb-auth-mobile-logo">
          <Logo />
        </div>
        <section className="bb-auth-card" aria-labelledby="auth-title">
          <h1 id="auth-title" className="bb-auth-title">
            {title}
          </h1>
          {subtitle && <p className="bb-auth-subtitle">{subtitle}</p>}
          {children}
        </section>
        <Link to="/" className="bb-auth-home">
          <Icon name="arrowLeft" size={16} /> Back to home
        </Link>
      </main>
    </div>
  )
}
