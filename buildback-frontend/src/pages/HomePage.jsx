import { Link } from 'react-router'
import { api } from '../api/client'
import { dashboardPath, useAuth } from '../auth/authContext'
import { Icon } from '../components/Icon'
import { MaterialCard, MaterialCardSkeleton } from '../components/MaterialCard'
import { CALCULATOR_IMAGE, CATEGORY_IMAGES, HERO_IMAGE, SELLER_IMAGE, STOCK_IMAGE } from '../content/siteImages'
import { useApiQuery } from '../hooks/useApiQuery'
import { CATEGORY_LABELS } from '../materials/materialRules'
import '../styles/home.css'

const RAIL_SIZE = 4

const CATEGORY_TEXT = {
  TILES: 'Vitrified, ceramic, mosaic and cladding',
  PAINT: 'Emulsions, enamels and primers',
  PLYWOOD: 'Marine ply, block boards and panels',
  FITTINGS: 'Taps, pipes, wires and sanitaryware',
  FALSE_CEILING: 'Gypsum boards, POP and channels',
  WATERPROOFING: 'Coatings, membranes and compounds',
  INTERIOR: 'Lights, panels, wallpaper and mirrors',
}

const RAIL_TITLES = {
  TILES: 'Tiles from finished projects',
  PAINT: 'Sealed paint and primers',
  PLYWOOD: 'Plywood and boards',
  FITTINGS: 'Plumbing, sanitary and electrical',
  FALSE_CEILING: 'POP and false ceiling material',
  WATERPROOFING: 'Waterproofing for roofs and bathrooms',
  INTERIOR: 'Interior and decor pieces',
}

const TRUST = [
  { icon: 'shield', title: 'Secure payments', text: 'Razorpay, verified by our server' },
  { icon: 'clock', title: 'Stock held for you', text: 'Reserved for 15 minutes at checkout' },
  { icon: 'store', title: 'Buy from local sellers', text: 'Collect directly from the site or shop' },
  { icon: 'calculator', title: 'Exact tile quantity', text: 'Built-in calculator on every tile listing' },
]

const CITIES = ['Mumbai', 'Pune', 'Bengaluru', 'Hyderabad', 'New Delhi', 'Chennai', 'Kolkata', 'Ahmedabad', 'Jaipur', 'Kochi', 'Chandigarh', 'Nashik']

const STEPS = [
  { title: 'Sellers list leftover stock', text: 'Builders, contractors and homeowners add photos, price, quantity and city in a couple of minutes.' },
  { title: 'Buyers find and reserve', text: 'Search by category, city and price. Reserving holds the stock for 15 minutes while you pay.' },
  { title: 'Pay online, collect locally', text: 'Payment is verified on our server before the order is confirmed. Then collect from the seller.' },
]

export default function HomePage() {
  const { user } = useAuth()

  // Real data only: the newest listings, plus a row and a count for each category
  const latest = useApiQuery('home-latest', (signal) => api.materials.search({ size: RAIL_SIZE }, signal))
  const rails = useApiQuery('home-rails', (signal) =>
    Promise.all(
      Object.keys(CATEGORY_LABELS).map((category) =>
        api.materials.search({ category, size: RAIL_SIZE }, signal).then((page) => [category, page]),
      ),
    ).then(Object.fromEntries),
  )

  const sellHref = user?.role === 'SELLER' ? '/seller/materials/new' : user ? dashboardPath(user.role) : '/register'

  return (
    <main className="bb-home">
      {/* ---------- Banner + side promotions ---------- */}
      <section className="bb-container bb-hero-area" aria-label="Featured">
        <div className="bb-hero" style={{ '--hero-image': `url("${HERO_IMAGE}")` }}>
          <div className="bb-hero-copy">
            <p className="bb-hero-kicker">Construction surplus marketplace</p>
            <h1>Good building material shouldn&apos;t go to waste</h1>
            <p className="bb-hero-lead">
              Buy unused tiles, paint, plywood and fittings left over from projects near you, at well below
              showroom prices.
            </p>
            <div className="bb-hero-actions">
              <Link to="/materials" className="bb-btn bb-btn-accent bb-btn-lg">
                Shop materials
              </Link>
              <Link to={sellHref} className="bb-btn bb-btn-outline-light bb-btn-lg">
                Sell your surplus
              </Link>
            </div>
            {latest.data && (
              <p className="bb-hero-stat">
                <strong>{latest.data.totalElements.toLocaleString('en-IN')}</strong> listings live now
              </p>
            )}
          </div>
        </div>

        <div className="bb-promos">
          <Link to={sellHref} className="bb-promo" style={{ '--promo-image': `url("${STOCK_IMAGE}")` }}>
            <span className="bb-promo-label">For builders &amp; contractors</span>
            <span className="bb-promo-title">Turn leftover stock into cash</span>
            <span className="bb-promo-link">
              Start selling <Icon name="arrowRight" size={16} />
            </span>
          </Link>
          <Link
            to="/materials?category=TILES"
            id="calculator"
            className="bb-promo"
            style={{ '--promo-image': `url("${CALCULATOR_IMAGE}")` }}
          >
            <span className="bb-promo-label">Tile calculator</span>
            <span className="bb-promo-title">Know exactly how many tiles you need</span>
            <span className="bb-promo-link">
              Find tiles <Icon name="arrowRight" size={16} />
            </span>
          </Link>
        </div>
      </section>

      {/* ---------- Trust strip ---------- */}
      <section className="bb-container" aria-label="Why BuildBack">
        <ul className="bb-trust">
          {TRUST.map((item) => (
            <li key={item.title}>
              <Icon name={item.icon} size={26} strokeWidth={1.75} />
              <span>
                <strong>{item.title}</strong>
                <small>{item.text}</small>
              </span>
            </li>
          ))}
        </ul>
      </section>

      {/* ---------- Categories ---------- */}
      <section className="bb-container bb-block" aria-labelledby="cat-title">
        <div className="bb-block-head">
          <h2 id="cat-title">Shop by category</h2>
          <Link to="/materials" className="bb-link">
            View all <Icon name="chevronRight" size={16} />
          </Link>
        </div>
        <ul className="bb-cat-grid">
          {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
            <li key={value}>
              <Link to={`/materials?category=${value}`} className="bb-cat-card">
                <img src={CATEGORY_IMAGES[value]} alt="" loading="lazy" referrerPolicy="no-referrer" />
                <span className="bb-cat-body">
                  <span className="bb-cat-name">{label}</span>
                  <span className="bb-cat-text">{CATEGORY_TEXT[value]}</span>
                  <span className="bb-cat-count">
                    {rails.data
                      ? `${rails.data[value].totalElements} listing${rails.data[value].totalElements === 1 ? '' : 's'}`
                      : ' '}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {/* ---------- Product rows ---------- */}
      <Rail id="latest" title="Fresh arrivals" to="/materials" query={latest} page={latest.data} />

      {Object.keys(CATEGORY_LABELS)
        .slice(0, 3)
        .map((category) => (
          <Rail
            key={category}
            id={category}
            title={RAIL_TITLES[category]}
            to={`/materials?category=${category}`}
            query={rails}
            page={rails.data?.[category]}
          />
        ))}

      {/* ---------- Seller banner ---------- */}
      <section className="bb-container bb-block" aria-labelledby="sell-title">
        <div className="bb-sell" style={{ '--sell-image': `url("${SELLER_IMAGE}")` }}>
          <div className="bb-sell-copy">
            <h2 id="sell-title">Finished a project with material left over?</h2>
            <ul>
              <li>
                <Icon name="check" size={18} /> Listing is free and takes two minutes
              </li>
              <li>
                <Icon name="check" size={18} /> Buyers pay online before they collect
              </li>
              <li>
                <Icon name="check" size={18} /> Free up godown space and recover cost
              </li>
            </ul>
            <Link to={sellHref} className="bb-btn bb-btn-accent bb-btn-lg">
              {user?.role === 'SELLER' ? 'List a material' : 'Start selling'}
            </Link>
          </div>
        </div>
      </section>

      {Object.keys(CATEGORY_LABELS)
        .slice(3)
        .map((category) => (
          <Rail
            key={category}
            id={category}
            title={RAIL_TITLES[category]}
            to={`/materials?category=${category}`}
            query={rails}
            page={rails.data?.[category]}
          />
        ))}

      {/* ---------- Cities ---------- */}
      <section className="bb-container bb-block" aria-labelledby="city-title">
        <div className="bb-block-head">
          <h2 id="city-title">Shop by city</h2>
        </div>
        <ul className="bb-cities">
          {CITIES.map((city) => (
            <li key={city}>
              <Link to={`/materials?city=${encodeURIComponent(city)}`}>
                <Icon name="mapPin" size={16} />
                {city}
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {/* ---------- How it works ---------- */}
      <section className="bb-container bb-block" id="how-it-works" aria-labelledby="how-title">
        <div className="bb-how">
          <h2 id="how-title">How BuildBack works</h2>
          <ol>
            {STEPS.map((step) => (
              <li key={step.title}>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>
    </main>
  )
}

/** One horizontal row of listing cards with a "See all" link. */
function Rail({ id, title, to, query, page }) {
  if (page && page.content.length === 0) return null
  return (
    <section className="bb-container bb-block" aria-labelledby={`rail-${id}`}>
      <div className="bb-rail">
        <div className="bb-block-head">
          <h2 id={`rail-${id}`}>{title}</h2>
          <Link to={to} className="bb-link">
            See all <Icon name="chevronRight" size={16} />
          </Link>
        </div>
        {query.error && (
          <p className="bb-alert bb-alert-error" role="alert">
            Listings could not be loaded: {query.error.message}
          </p>
        )}
        <ul className="bb-rail-list">
          {page
            ? page.content.map((material) => (
                <li key={material.id}>
                  <MaterialCard material={material} />
                </li>
              ))
            : !query.error &&
              Array.from({ length: RAIL_SIZE }, (_, i) => (
                <li key={i}>
                  <MaterialCardSkeleton />
                </li>
              ))}
        </ul>
      </div>
    </section>
  )
}
