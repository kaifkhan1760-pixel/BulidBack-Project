import { useState } from 'react'
import { MaterialImage } from './MaterialImage'

/** Big photo plus clickable thumbnails (only shown when there is more than one photo). */
export function PhotoGallery({ photos, title }) {
  const [active, setActive] = useState(0)
  const list = photos.length > 0 ? photos : [null] // null -> our placeholder image
  const current = Math.min(active, list.length - 1)

  return (
    <div className="bb-gallery">
      <MaterialImage
        key={list[current]}
        src={list[current]}
        alt={list.length > 1 ? `${title}, photo ${current + 1} of ${list.length}` : title}
        className="bb-detail-image"
      />
      {list.length > 1 && (
        <ul className="bb-gallery-thumbs" aria-label="More photos">
          {list.map((url, index) => (
            <li key={url}>
              <button
                type="button"
                className="bb-gallery-thumb"
                onClick={() => setActive(index)}
                aria-label={`Show photo ${index + 1}`}
                aria-current={index === current ? 'true' : undefined}
              >
                <MaterialImage src={url} alt="" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
