import { useEffect, useState } from 'react'
import { Icon } from './Icon'

const SLIDE_MS = 6000

/**
 * Home page banner with several slides.
 * - Moves to the next slide every 6 seconds, but pauses while the mouse is over
 *   it or keyboard focus is inside it, and never moves for people who ask the
 *   system for reduced motion.
 * - Hidden slides are "inert", so keyboard users never tab into an invisible link.
 * slides: [{ id, image, kicker, title, text, actions }]
 */
export function HeroCarousel({ slides, footer }) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const [reducedMotion] = useState(() => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false)
  const count = slides.length

  useEffect(() => {
    if (paused || reducedMotion || count < 2) return undefined
    const timer = setInterval(() => setIndex((current) => (current + 1) % count), SLIDE_MS)
    return () => clearInterval(timer)
  }, [paused, reducedMotion, count])

  const show = (next) => setIndex((next + count) % count)

  return (
    <section
      className="bb-hero"
      aria-roledescription="carousel"
      aria-label="Featured"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false)
      }}
    >
      {slides.map((slide, i) => {
        const active = i === index
        return (
          <div
            key={slide.id}
            className={active ? 'bb-slide is-active' : 'bb-slide'}
            style={{ '--hero-image': `url("${slide.image}")` }}
            role="group"
            aria-roledescription="slide"
            aria-label={`${i + 1} of ${count}`}
            aria-hidden={!active}
            inert={!active}
          >
            <div className="bb-hero-copy">
              <p className="bb-hero-kicker">{slide.kicker}</p>
              {/* The page has one h1: the first slide's title */}
              {i === 0 ? <h1>{slide.title}</h1> : <h2 className="bb-hero-title">{slide.title}</h2>}
              <p className="bb-hero-lead">{slide.text}</p>
              <div className="bb-hero-actions">{slide.actions}</div>
              {footer}
            </div>
          </div>
        )
      })}

      {count > 1 && (
        <div className="bb-carousel-controls">
          <button type="button" className="bb-carousel-arrow" onClick={() => show(index - 1)} aria-label="Previous slide">
            <Icon name="chevronLeft" size={18} />
          </button>
          <div className="bb-carousel-dots">
            {slides.map((slide, i) => (
              <button
                key={slide.id}
                type="button"
                className="bb-carousel-dot"
                onClick={() => show(i)}
                aria-label={`Show slide ${i + 1}: ${slide.kicker}`}
                aria-current={i === index ? 'true' : undefined}
              />
            ))}
          </div>
          <button type="button" className="bb-carousel-arrow" onClick={() => show(index + 1)} aria-label="Next slide">
            <Icon name="chevronRight" size={18} />
          </button>
        </div>
      )}
    </section>
  )
}
