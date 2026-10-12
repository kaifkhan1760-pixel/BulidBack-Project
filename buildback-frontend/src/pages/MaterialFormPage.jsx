import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { FormField } from '../components/FormField'
import { Icon } from '../components/Icon'
import { MaterialImage } from '../components/MaterialImage'
import { PhotoPicker } from '../components/PhotoPicker'
import {
  ALLOWED_UNITS,
  CATEGORY_ICONS,
  CATEGORY_LABELS,
  EMPTY_MATERIAL_FORM,
  UNIT_LABELS,
  UNIT_PLURALS,
  formFromMaterial,
  formatPriceCompact,
  isValidPriceText,
  requestFromForm,
  validateMaterialForm,
} from '../materials/materialRules'
import '../styles/listing-form.css'

/** The server may report photo problems as "imageUrls" or per photo, e.g. "imageUrls[1]". */
function photoError(errors) {
  if (errors.imageUrls) return errors.imageUrls
  const key = Object.keys(errors).find((name) => name.startsWith('imageUrls['))
  return key ? errors[key] : null
}

/** Used for both /seller/materials/new and /seller/materials/:id/edit */
export default function MaterialFormPage({ mode }) {
  const editing = mode === 'edit'
  const { id } = useParams()
  const navigate = useNavigate()

  const [form, setForm] = useState(EMPTY_MATERIAL_FORM)
  const [version, setVersion] = useState(null) // which saved version this form is based on
  const [archived, setArchived] = useState(false)
  const [loadState, setLoadState] = useState(editing ? 'loading' : 'ready') // loading | ready | notfound | error
  const [loadError, setLoadError] = useState(null)
  const [reloadCount, setReloadCount] = useState(0)

  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [conflict, setConflict] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(0) // photos still being uploaded

  // Edit mode: load the seller's own listing (the server returns 404 if it is not theirs)
  useEffect(() => {
    if (!editing) return undefined
    const controller = new AbortController()
    api.sellerMaterials
      .get(id, controller.signal)
      .then((material) => {
        setForm(formFromMaterial(material))
        setVersion(material.version)
        setArchived(material.status === 'ARCHIVED')
        setErrors({})
        setFormError(null)
        setConflict(false)
        setLoadState('ready')
      })
      .catch((error) => {
        if (error.name === 'AbortError' || error.status === 401) return
        if (error.status === 404 || error.status === 400) setLoadState('notfound')
        else {
          setLoadError(error.message)
          setLoadState('error')
        }
      })
    return () => controller.abort()
  }, [editing, id, reloadCount])

  function reload() {
    setLoadState('loading')
    setReloadCount((n) => n + 1)
  }

  // When the seller changes a field, its old error message disappears
  function clearErrors(...fields) {
    setErrors((current) => {
      const next = { ...current }
      for (const key of Object.keys(next)) {
        if (fields.some((field) => key === field || key.startsWith(`${field}[`))) delete next[key]
      }
      return next
    })
  }

  // Functional updates: a photo finishing its upload never wipes out typing (and vice versa)
  const update = (field) => (event) => {
    const value = event.target.value
    setForm((current) => ({ ...current, [field]: value }))
    clearErrors(field)
  }

  function changeCategory(event) {
    const category = event.target.value
    setForm((current) => ({
      ...current,
      category,
      // Keep the chosen unit if the new category also allows it, otherwise use its usual unit
      unit: ALLOWED_UNITS[category]?.includes(current.unit) ? current.unit : (ALLOWED_UNITS[category]?.[0] ?? ''),
      // Leaving Tiles: clear the tile sizes so old values are never sent
      tileLengthFeet: category === 'TILES' ? current.tileLengthFeet : '',
      tileWidthFeet: category === 'TILES' ? current.tileWidthFeet : '',
    }))
    clearErrors('category', 'unit', 'tileLengthFeet', 'tileWidthFeet')
  }

  // ----- photos -----
  function addPhoto(url) {
    setForm((current) => ({ ...current, imageUrls: [...current.imageUrls, url] }))
    clearErrors('imageUrls')
  }
  const removePhoto = (index) =>
    setForm((current) => ({ ...current, imageUrls: current.imageUrls.filter((_, i) => i !== index) }))
  const makeCover = (index) =>
    setForm((current) => {
      const rest = current.imageUrls.filter((_, i) => i !== index)
      return { ...current, imageUrls: [current.imageUrls[index], ...rest] }
    })

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)
    const clientErrors = validateMaterialForm(form, { creating: !editing })
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      setFormError('Please correct the highlighted fields.')
      return
    }

    setSaving(true)
    try {
      const body = requestFromForm(form)
      if (editing) {
        await api.sellerMaterials.update(id, { ...body, version })
        navigate('/seller/materials', { state: { message: `“${body.title}” was updated.` } })
      } else {
        await api.sellerMaterials.create(body)
        navigate('/seller/materials', { state: { message: `“${body.title}” is now listed.` } })
      }
    } catch (error) {
      setSaving(false)
      if (error.status === 409) {
        // Someone (maybe another tab) saved first. Keep the typed values; let the user decide.
        setConflict(true)
        setFormError(error.message)
      } else if (error.status === 404) {
        setLoadState('notfound')
      } else {
        setErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      }
    }
  }

  const isTiles = form.category === 'TILES'
  const readOnly = archived || saving
  const unitLabel = UNIT_LABELS[form.unit]
  const unitChoices = ALLOWED_UNITS[form.category] ?? []

  return (
    <main className="bb-page bb-listing-page">
      <Link to="/seller/materials" className="bb-back-link">
        <Icon name="arrowLeft" size={16} /> Back to my listings
      </Link>

      {loadState === 'loading' && (
        <p className="bb-loading" role="status">
          Loading listing…
        </p>
      )}

      {loadState === 'notfound' && (
        <div className="bb-card bb-state" role="alert">
          <h1 className="bb-card-title">Listing not found</h1>
          <p className="bb-muted">It does not exist or does not belong to your account.</p>
          <Link to="/seller/materials" className="bb-btn bb-btn-primary">
            Back to my materials
          </Link>
        </div>
      )}

      {loadState === 'error' && (
        <div className="bb-card bb-state" role="alert">
          <p className="bb-alert bb-alert-error">{loadError}</p>
          <button type="button" className="bb-btn bb-btn-primary" onClick={reload}>
            Try again
          </button>
        </div>
      )}

      {loadState === 'ready' && (
        <>
          <header className="bb-listing-head">
            <h1 id="form-title">{editing ? 'Edit listing' : 'List a material'}</h1>
            <p className="bb-muted">
              Fields marked <span className="bb-required">*</span> are required. Prices are in Indian Rupees (₹) for
              one unit.
            </p>
          </header>

          {archived && (
            <p className="bb-alert bb-alert-info" role="status">
              This listing is archived and read-only.
            </p>
          )}

          {formError && (
            <div className="bb-alert bb-alert-error" role="alert">
              <p>{formError}</p>
              {conflict && (
                <button type="button" className="bb-btn bb-btn-secondary bb-btn-sm bb-conflict-button" onClick={reload}>
                  Load latest version (your unsaved changes will be replaced)
                </button>
              )}
            </div>
          )}

          <form className="bb-listing-layout" onSubmit={handleSubmit} noValidate aria-labelledby="form-title">
            <div className="bb-listing-main">
              <fieldset className="bb-plain-fieldset bb-listing-sections" disabled={readOnly}>
                {/* ---------- 1. Photos ---------- */}
                <section className="bb-card bb-form-section" aria-labelledby="sec-photos">
                  <SectionHead number={1} id="sec-photos" title="Photos" required>
                    Clear photos sell faster. Show the actual material, its label and any marks.
                  </SectionHead>
                  <PhotoPicker
                    id="m-photos"
                    photos={form.imageUrls}
                    onAdd={addPhoto}
                    onRemove={removePhoto}
                    onMakeCover={makeCover}
                    uploading={uploading}
                    setUploading={setUploading}
                    error={photoError(errors)}
                  />
                </section>

                {/* ---------- 2. Basic details ---------- */}
                <section className="bb-card bb-form-section" aria-labelledby="sec-details">
                  <SectionHead number={2} id="sec-details" title="Basic details">
                    What are you selling?
                  </SectionHead>

                  <fieldset
                    className="bb-category-picker"
                    aria-invalid={Boolean(errors.category)}
                    aria-describedby={errors.category ? 'm-category-error' : undefined}
                  >
                    <legend>
                      Category <span className="bb-required">*</span>
                    </legend>
                    <div className="bb-category-options">
                      {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
                        <label key={value} className="bb-category-option">
                          <input
                            type="radio"
                            name="category"
                            value={value}
                            checked={form.category === value}
                            onChange={changeCategory}
                          />
                          <Icon name={CATEGORY_ICONS[value]} size={24} />
                          <span className="bb-category-name">{label}</span>
                          <small>Per {ALLOWED_UNITS[value].map((unit) => UNIT_LABELS[unit]).join(' / ')}</small>
                        </label>
                      ))}
                    </div>
                    {errors.category && (
                      <p id="m-category-error" className="bb-field-error">
                        {errors.category}
                      </p>
                    )}
                  </fieldset>

                  {/* Only some categories (POP, waterproofing, interior) can be sold in more than one unit */}
                  {(unitChoices.length > 1 || errors.unit) && (
                    <fieldset
                      className="bb-unit-picker"
                      aria-invalid={Boolean(errors.unit)}
                      aria-describedby={errors.unit ? 'm-unit-error' : undefined}
                    >
                      <legend>
                        Sold per <span className="bb-required">*</span>
                      </legend>
                      <div className="bb-unit-options">
                        {unitChoices.map((unit) => (
                          <label key={unit} className="bb-unit-option">
                            <input
                              type="radio"
                              name="unit"
                              value={unit}
                              checked={form.unit === unit}
                              onChange={update('unit')}
                            />
                            {UNIT_LABELS[unit]}
                          </label>
                        ))}
                      </div>
                      {errors.unit && (
                        <p id="m-unit-error" className="bb-field-error">
                          {errors.unit}
                        </p>
                      )}
                    </fieldset>
                  )}

                  <FormField
                    id="m-title"
                    label="Title"
                    required
                    error={errors.title}
                    hint="e.g. Glossy vitrified floor tiles 2x2 ft"
                  >
                    {(field) => (
                      <input {...field} type="text" maxLength={120} value={form.title} onChange={update('title')} />
                    )}
                  </FormField>

                  <FormField
                    id="m-description"
                    label="Description"
                    required
                    error={errors.description}
                    hint={`Condition, brand, size and colour. For paint, mention the litres per can. ${form.description.trim().length}/2000`}
                  >
                    {(field) => (
                      <textarea
                        {...field}
                        rows={5}
                        maxLength={2000}
                        value={form.description}
                        onChange={update('description')}
                      />
                    )}
                  </FormField>
                </section>

                {/* ---------- 3. Price and stock ---------- */}
                <section className="bb-card bb-form-section" aria-labelledby="sec-price">
                  <SectionHead number={3} id="sec-price" title="Price and stock">
                    {unitLabel ? `You sell this per ${unitLabel}.` : 'Choose a category first to set the unit.'}
                  </SectionHead>

                  <div className="bb-form-row">
                    <FormField
                      id="m-price"
                      label={`Price per ${unitLabel ?? 'unit'}`}
                      required
                      error={errors.unitPrice}
                      hint="e.g. 45 or 45.50"
                    >
                      {(field) => (
                        <div className="bb-input-affix">
                          <span aria-hidden="true">₹</span>
                          <input
                            {...field}
                            type="text"
                            inputMode="decimal"
                            value={form.unitPrice}
                            onChange={update('unitPrice')}
                          />
                        </div>
                      )}
                    </FormField>

                    <FormField
                      id="m-quantity"
                      label="Quantity available"
                      required
                      error={errors.availableQuantity}
                      hint={editing ? 'Set 0 to show Out of stock.' : 'At least 1.'}
                    >
                      {(field) => (
                        <div className="bb-input-affix bb-input-affix-end">
                          <input
                            {...field}
                            type="text"
                            inputMode="numeric"
                            value={form.availableQuantity}
                            onChange={update('availableQuantity')}
                          />
                          <span aria-hidden="true">{UNIT_PLURALS[form.unit] ?? 'units'}</span>
                        </div>
                      )}
                    </FormField>
                  </div>

                  {isTiles && (
                    <div className="bb-form-row">
                      <FormField
                        id="m-tile-length"
                        label="Tile length (feet)"
                        required
                        error={errors.tileLengthFeet}
                        hint="e.g. 2 or 1.25. Used by the tile calculator."
                      >
                        {(field) => (
                          <input
                            {...field}
                            type="text"
                            inputMode="decimal"
                            value={form.tileLengthFeet}
                            onChange={update('tileLengthFeet')}
                          />
                        )}
                      </FormField>
                      <FormField id="m-tile-width" label="Tile width (feet)" required error={errors.tileWidthFeet}>
                        {(field) => (
                          <input
                            {...field}
                            type="text"
                            inputMode="decimal"
                            value={form.tileWidthFeet}
                            onChange={update('tileWidthFeet')}
                          />
                        )}
                      </FormField>
                    </div>
                  )}
                </section>

                {/* ---------- 4. Pickup location ---------- */}
                <section className="bb-card bb-form-section" aria-labelledby="sec-location">
                  <SectionHead number={4} id="sec-location" title="Pickup location">
                    Buyers collect from this city after paying.
                  </SectionHead>
                  <FormField id="m-city" label="City" required error={errors.city} hint="e.g. Pune">
                    {(field) => (
                      <input
                        {...field}
                        type="text"
                        autoComplete="address-level2"
                        value={form.city}
                        onChange={update('city')}
                      />
                    )}
                  </FormField>
                </section>
              </fieldset>

              {!archived && (
                <div className="bb-form-submit">
                  <Link to="/seller/materials" className="bb-btn bb-btn-secondary bb-btn-lg">
                    Cancel
                  </Link>
                  <button type="submit" className="bb-btn bb-btn-primary bb-btn-lg" disabled={saving || uploading > 0}>
                    {saving
                      ? 'Saving…'
                      : uploading > 0
                        ? 'Uploading photos…'
                        : editing
                          ? 'Save changes'
                          : 'Publish listing'}
                  </button>
                </div>
              )}
            </div>

            <aside className="bb-listing-aside" aria-label="Preview">
              <p className="bb-listing-aside-title">Preview</p>
              <ListingPreview form={form} unitLabel={unitLabel} />
              <div className="bb-card bb-listing-tips">
                <h2>Tips for a quick sale</h2>
                <ul>
                  <li>
                    <Icon name="check" size={16} /> Use daylight photos of the real stock
                  </li>
                  <li>
                    <Icon name="check" size={16} /> Mention brand, size, shade and batch
                  </li>
                  <li>
                    <Icon name="check" size={16} /> Price below the showroom rate
                  </li>
                  <li>
                    <Icon name="check" size={16} /> Keep the quantity up to date
                  </li>
                </ul>
              </div>
            </aside>
          </form>
        </>
      )}
    </main>
  )
}

function SectionHead({ number, id, title, required, children }) {
  return (
    <header className="bb-form-section-head">
      <span className="bb-form-step" aria-hidden="true">
        {number}
      </span>
      <div>
        <h2 id={id}>
          {title}
          {required && <span className="bb-required"> *</span>}
        </h2>
        <p>{children}</p>
      </div>
    </header>
  )
}

/** How the listing card will look in search results, updated as the seller types. */
function ListingPreview({ form, unitLabel }) {
  const price = form.unitPrice.trim()
  return (
    <div className="bb-mcard bb-preview-card" aria-hidden="true">
      <div className="bb-mcard-media">
        <MaterialImage src={form.imageUrls[0]} alt="" className="bb-mcard-img" />
      </div>
      <div className="bb-mcard-body">
        <p className="bb-mcard-cat">{CATEGORY_LABELS[form.category] ?? 'Category'}</p>
        <h3 className="bb-mcard-title">{form.title.trim() || 'Your listing title'}</h3>
        <p className="bb-mcard-price">
          <strong>{isValidPriceText(price) ? formatPriceCompact(price) : '₹ —'}</strong>
          <span>/ {unitLabel ?? 'unit'}</span>
        </p>
        <p className="bb-mcard-meta">
          <span>
            <Icon name="mapPin" size={14} />
            {form.city.trim() || 'City'}
          </span>
          {/^\d+$/.test(form.availableQuantity.trim()) && <span>{form.availableQuantity.trim()} in stock</span>}
        </p>
      </div>
    </div>
  )
}
