import { useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router'
import { ROLE_LABELS, dashboardPath, useAuth } from '../auth/authContext'
import { CATEGORY_LABELS } from '../materials/materialRules'
import { Icon } from './Icon'
import { Logo } from './Logo'

/** Account links depend on the role the SERVER reported for this session. */
function accountLinksFor(user) {
  if (!user) return []
  const links = [{ to: dashboardPath(user.role), label: 'Dashboard', icon: 'dashboard' }]
  if (user.role === 'BUYER') links.push({ to: '/buyer/orders', label: 'My orders', icon: 'bag' })
  if (user.role === 'SELLER') {
    links.push({ to: '/seller/materials', label: 'My listings', icon: 'package' })
    links.push({ to: '/seller/orders', label: 'Seller orders', icon: 'receipt' })
  }
  if (user.role === 'ADMIN') links.push({ to: '/admin/console', label: 'Admin console', icon: 'shield' })
  return links
}

/** "Kaif Khan" -> "KK"; ignores symbols such as "[Test]" brackets. */
function initials(name) {
  const letters = name
    .split(/\s+/)
    .map((word) => word.match(/\p{L}/u)?.[0])
    .filter(Boolean)
  return letters.slice(0, 2).join('').toUpperCase() || '?'
}

export function AppHeader() {
  const { user, status, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState(null)
  const accountLinks = accountLinksFor(user)
  const closeMenu = () => setMenuOpen(false)

  const onCatalogue = location.pathname === '/materials'
  const params = new URLSearchParams(location.search)
  const activeCategory = onCatalogue ? (params.get('category') ?? '') : null

  async function handleLogout() {
    setLoggingOut(true)
    setLogoutError(null)
    try {
      await logout()
      closeMenu()
      navigate('/login', { replace: true })
    } catch (err) {
      setLogoutError(`Logout failed: ${err.message} You are still logged in.`)
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <>
      <header className="bb-header">
        <div className="bb-topbar">
          <div className="bb-container bb-topbar-inner">
            <p>Surplus tiles, paint, plywood and fittings from sellers across India</p>
            <nav aria-label="Help">
              <a href="/#how-it-works">How it works</a>
              {user?.role !== 'BUYER' && user?.role !== 'ADMIN' && (
                <Link to={user ? '/seller/materials/new' : '/register'}>Sell on BuildBack</Link>
              )}
            </nav>
          </div>
        </div>

        <div className="bb-mainbar">
          <div className="bb-container bb-mainbar-inner">
            <Logo />

            {/* key: the search box resets to match the URL after Back/Forward */}
            <HeaderSearch
              key={location.pathname + location.search}
              onCatalogue={onCatalogue}
              params={params}
            />

            <div className="bb-header-actions">
              {user && (
                <>
                  {user.role === 'SELLER' && (
                    <Link to="/seller/materials/new" className="bb-btn bb-btn-primary bb-btn-sm">
                      <Icon name="plus" size={18} /> List material
                    </Link>
                  )}
                  <Link to={dashboardPath(user.role)} className="bb-user-chip" title="Go to your dashboard">
                    <span className="bb-avatar" aria-hidden="true">
                      {initials(user.name)}
                    </span>
                    <span className="bb-user-text">
                      <span className="bb-user-name">{user.name}</span>
                      <span className="bb-user-role">{ROLE_LABELS[user.role]} account</span>
                    </span>
                  </Link>
                  <button
                    type="button"
                    className="bb-icon-btn"
                    onClick={handleLogout}
                    disabled={loggingOut}
                    aria-label={loggingOut ? 'Logging out' : 'Log out'}
                    title="Log out"
                  >
                    <Icon name="logOut" size={20} />
                  </button>
                </>
              )}
              {!user && status !== 'loading' && (
                <>
                  <Link to="/login" className="bb-header-login">
                    <Icon name="user" size={20} />
                    <span>
                      <small>Hello, sign in</small>
                      Account
                    </span>
                  </Link>
                  <Link to="/register" className="bb-btn bb-btn-primary bb-btn-sm">
                    Register
                  </Link>
                </>
              )}
            </div>

            <button
              type="button"
              className="bb-menu-toggle"
              aria-expanded={menuOpen}
              aria-controls="mobile-menu"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              onClick={() => setMenuOpen((open) => !open)}
            >
              <Icon name={menuOpen ? 'close' : 'menu'} size={22} />
            </button>
          </div>
        </div>

        <nav className="bb-catbar" aria-label="Categories">
          <div className="bb-container bb-catbar-inner">
            <Link
              to="/materials"
              className="bb-catbar-link"
              aria-current={activeCategory === '' ? 'page' : undefined}
            >
              All materials
            </Link>
            {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
              <Link
                key={value}
                to={`/materials?category=${value}`}
                className="bb-catbar-link"
                aria-current={activeCategory === value ? 'page' : undefined}
              >
                {label}
              </Link>
            ))}
            <span className="bb-catbar-spacer" />
            {accountLinks.map((link) => (
              <NavLink key={link.to} to={link.to} className="bb-catbar-link bb-catbar-account">
                <Icon name={link.icon} size={16} />
                {link.label}
              </NavLink>
            ))}
          </div>
        </nav>

        {menuOpen && (
          <div id="mobile-menu" className="bb-mobile-menu">
            {user && (
              <div className="bb-mobile-user">
                <span className="bb-avatar" aria-hidden="true">
                  {initials(user.name)}
                </span>
                <span>
                  <strong>{user.name}</strong>
                  <span className="bb-muted"> · {ROLE_LABELS[user.role]}</span>
                </span>
              </div>
            )}
            <nav aria-label="Account">
              {accountLinks.map((link) => (
                <NavLink key={link.to} to={link.to} onClick={closeMenu}>
                  <Icon name={link.icon} size={18} />
                  {link.label}
                </NavLink>
              ))}
              {user?.role === 'SELLER' && (
                <NavLink to="/seller/materials/new" onClick={closeMenu}>
                  <Icon name="plus" size={18} />
                  List material
                </NavLink>
              )}
            </nav>
            <div className="bb-mobile-actions">
              {user ? (
                <button type="button" className="bb-btn bb-btn-secondary bb-btn-block" onClick={handleLogout} disabled={loggingOut}>
                  <Icon name="logOut" size={18} />
                  {loggingOut ? 'Logging out…' : 'Log out'}
                </button>
              ) : (
                <>
                  <Link to="/login" className="bb-btn bb-btn-secondary bb-btn-block" onClick={closeMenu}>
                    Log in
                  </Link>
                  <Link to="/register" className="bb-btn bb-btn-primary bb-btn-block" onClick={closeMenu}>
                    Create an account
                  </Link>
                </>
              )}
            </div>
          </div>
        )}
      </header>
      {logoutError && (
        <div className="bb-container">
          <p className="bb-alert bb-alert-error" role="alert">
            {logoutError}
          </p>
        </div>
      )}
    </>
  )
}

/**
 * Search box in the header. On the catalogue it keeps the other filters
 * (city, price, sort); elsewhere it starts a fresh search.
 */
function HeaderSearch({ onCatalogue, params }) {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState(onCatalogue ? (params.get('keyword') ?? '') : '')
  const [category, setCategory] = useState(onCatalogue ? (params.get('category') ?? '') : '')

  function handleSubmit(event) {
    event.preventDefault()
    const next = onCatalogue ? new URLSearchParams(params) : new URLSearchParams()
    next.delete('page')
    const text = keyword.trim().slice(0, 100)
    if (text) next.set('keyword', text)
    else next.delete('keyword')
    if (category) next.set('category', category)
    else next.delete('category')
    const query = next.toString()
    navigate(query ? `/materials?${query}` : '/materials')
  }

  return (
    <form className="bb-search" role="search" onSubmit={handleSubmit}>
      <label htmlFor="hs-category" className="bb-visually-hidden">
        Category
      </label>
      <select id="hs-category" className="bb-search-category" value={category} onChange={(e) => setCategory(e.target.value)}>
        <option value="">All</option>
        {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      <label htmlFor="hs-keyword" className="bb-visually-hidden">
        Search materials
      </label>
      <input
        id="hs-keyword"
        type="search"
        placeholder="Search for tiles, paint, plywood, taps…"
        value={keyword}
        maxLength={100}
        onChange={(e) => setKeyword(e.target.value)}
      />
      <button type="submit" className="bb-search-submit" aria-label="Search">
        <Icon name="search" size={20} />
      </button>
    </form>
  )
}
