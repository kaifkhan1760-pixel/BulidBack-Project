import { Outlet } from 'react-router'
import { AppHeader } from './AppHeader'
import { BackToTop } from './BackToTop'
import { SiteFooter } from './SiteFooter'

/** Shared frame for every page except login/register: header, page, footer. */
export function SiteLayout() {
  return (
    <div className="bb-site">
      <a href="#main" className="bb-skip-link">
        Skip to content
      </a>
      <AppHeader />
      <div id="main" className="bb-site-main" tabIndex={-1}>
        <Outlet />
      </div>
      <SiteFooter />
      <BackToTop />
    </div>
  )
}
