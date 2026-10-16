import { useEffect, useState } from 'react'
import { Icon } from './Icon'

/** Round "back to top" button that appears after scrolling down a long page. */
export function BackToTop() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > 700)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  if (!visible) return null
  return (
    <button
      type="button"
      className="bb-back-to-top"
      aria-label="Back to top"
      title="Back to top"
      onClick={() => {
        const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
        window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
      }}
    >
      <Icon name="chevronRight" size={22} className="bb-rotate-up" />
    </button>
  )
}
