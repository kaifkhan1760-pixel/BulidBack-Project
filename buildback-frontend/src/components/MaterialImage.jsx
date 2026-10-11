import { useState } from 'react'

// Our own local file in /public (served as a normal file, never a data: URL)
const placeholder = '/material-placeholder.svg'

/**
 * Shows the seller's https image, or our own local placeholder when there is
 * no image or it fails to load. The placeholder is only set once, so a broken
 * link cannot cause an endless error -> retry loop.
 * referrerPolicy="no-referrer": the image host does not learn which page we are on.
 */
export function MaterialImage({ src, alt, className }) {
  const [failedSrc, setFailedSrc] = useState(null)
  const usable = typeof src === 'string' && src.startsWith('https://')
  const showPlaceholder = !usable || failedSrc === src

  return (
    <img
      className={className}
      src={showPlaceholder ? placeholder : src}
      alt={showPlaceholder ? '' : alt}
      referrerPolicy="no-referrer"
      loading="lazy"
      decoding="async"
      onError={() => {
        if (!showPlaceholder) setFailedSrc(src)
      }}
    />
  )
}
