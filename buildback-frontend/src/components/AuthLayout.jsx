/** Shared frame for the login and register pages. */
export function AuthLayout({ title, subtitle, children }) {
  return (
    <main className="bb-screen">
      <div className="bb-auth">
        <header className="bb-brand">
          <p className="bb-brand-name">BuildBack</p>
          <p className="bb-brand-tagline">Construction Surplus Marketplace</p>
        </header>
        <section className="bb-card" aria-labelledby="auth-title">
          <h1 id="auth-title" className="bb-card-title">
            {title}
          </h1>
          {subtitle && <p className="bb-muted">{subtitle}</p>}
          {children}
        </section>
      </div>
    </main>
  )
}
