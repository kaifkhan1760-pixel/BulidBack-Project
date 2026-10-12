import { useState } from 'react'
import { api } from '../api/client'
import { MAX_PHOTOS, PHOTO_TYPES, photoProblem } from '../materials/materialRules'
import { Icon } from './Icon'
import { MaterialImage } from './MaterialImage'

/**
 * Photo uploader for the listing form.
 * Each chosen file is uploaded straight away (POST /api/seller/images); the
 * server answers with the photo's URL, which we add to the form. The listing
 * itself is only saved when the seller submits the form.
 *
 * uploading / setUploading live in the form page, so it can block "Save"
 * while photos are still on their way.
 */
export function PhotoPicker({ id, photos, onAdd, onRemove, onMakeCover, uploading, setUploading, error }) {
  const [problems, setProblems] = useState([])
  const slotsLeft = MAX_PHOTOS - photos.length - uploading

  async function handleFiles(event) {
    const files = [...event.target.files]
    event.target.value = '' // lets the seller pick the same file again later

    const found = []
    const accepted = []
    for (const file of files) {
      const problem = photoProblem(file)
      if (problem) found.push(problem)
      else if (accepted.length < slotsLeft) accepted.push(file)
      else found.push(`Only ${MAX_PHOTOS} photos are allowed, so “${file.name}” was skipped.`)
    }
    setProblems(found)
    if (accepted.length === 0) return

    setUploading((n) => n + accepted.length)
    // One at a time, so photos keep the order the seller chose them in
    for (const file of accepted) {
      try {
        const { url } = await api.sellerImages.upload(file)
        onAdd(url)
      } catch (err) {
        const reason = err.fieldErrors?.file ?? err.message
        setProblems((list) => [...list, `“${file.name}” could not be uploaded: ${reason}`])
      } finally {
        setUploading((n) => n - 1)
      }
    }
  }

  const describedBy = [`${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ')

  return (
    <div className="bb-photos">
      <ul className="bb-photo-grid">
        {photos.map((url, index) => (
          <li key={url} className="bb-photo">
            <MaterialImage src={url} alt={`Photo ${index + 1}`} className="bb-photo-img" />
            {index === 0 && <span className="bb-photo-cover">Cover</span>}
            <button
              type="button"
              className="bb-photo-remove"
              onClick={() => onRemove(index)}
              aria-label={`Remove photo ${index + 1}`}
              title="Remove photo"
            >
              <Icon name="close" size={16} />
            </button>
            {index > 0 && (
              <button type="button" className="bb-photo-make-cover" onClick={() => onMakeCover(index)}>
                Make cover
              </button>
            )}
          </li>
        ))}

        {Array.from({ length: uploading }, (_, i) => (
          <li key={`uploading-${i}`} className="bb-photo bb-photo-uploading">
            <span className="bb-loading">Uploading…</span>
          </li>
        ))}

        {slotsLeft > 0 && (
          <li>
            <label className={error ? 'bb-photo-add is-invalid' : 'bb-photo-add'}>
              <Icon name="plus" size={30} strokeWidth={1.75} />
              <span className="bb-photo-add-text">{photos.length === 0 ? 'Add photos' : 'Add more'}</span>
              <small>
                {photos.length} of {MAX_PHOTOS}
              </small>
              <input
                id={id}
                type="file"
                accept={PHOTO_TYPES.join(',')}
                multiple
                className="bb-visually-hidden"
                onChange={handleFiles}
                aria-invalid={Boolean(error)}
                aria-describedby={describedBy}
              />
            </label>
          </li>
        )}
      </ul>

      <p id={`${id}-hint`} className="bb-hint">
        JPG, PNG or WebP, up to 5 MB each. The first photo is the cover shown in search results.
      </p>
      {error && (
        <p id={`${id}-error`} className="bb-field-error">
          {error}
        </p>
      )}
      {problems.length > 0 && (
        <ul className="bb-alert bb-alert-error bb-photo-problems" role="alert">
          {problems.map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      )}
      <p className="bb-visually-hidden" aria-live="polite">
        {uploading > 0 ? `Uploading ${uploading} photo${uploading === 1 ? '' : 's'}` : `${photos.length} photos added`}
      </p>
    </div>
  )
}
