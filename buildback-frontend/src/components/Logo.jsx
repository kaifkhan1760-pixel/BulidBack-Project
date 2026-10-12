import { Link } from 'react-router'

/** BuildBack mark: stacked blocks with a return arrow (material given a second life). */
export function LogoMark({ size = 32 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="9" fill="var(--brand-600)" />
      <rect x="7" y="16" width="8" height="8" rx="1.5" fill="#fff" />
      <rect x="17" y="16" width="8" height="8" rx="1.5" fill="#fff" opacity="0.85" />
      <rect x="12" y="7" width="8" height="8" rx="1.5" fill="var(--accent-500)" />
    </svg>
  )
}

export function Logo({ to = '/', light = false }) {
  return (
    <Link to={to} className={light ? 'bb-logo bb-logo-light' : 'bb-logo'} aria-label="BuildBack home">
      <LogoMark />
      <span className="bb-logo-text">
        Build<span>Back</span>
      </span>
    </Link>
  )
}
