import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { FormField } from '../components/FormField'
import {
  ALLOWED_UNIT,
  CATEGORY_LABELS,
  EMPTY_MATERIAL_FORM,
  UNIT_LABELS,
  formFromMaterial,
  requestFromForm,
  validateMaterialForm,
} from '../materials/materialRules'

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

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value })

  function changeCategory(event) {
    const category = event.target.value
    setForm({
      ...form,
      category,
      unit: ALLOWED_UNIT[category] ?? '',
      // Leaving Tiles: clear the tile sizes so old values are never sent
      tileLengthFeet: category === 'TILES' ? form.tileLengthFeet : '',
      tileWidthFeet: category === 'TILES' ? form.tileWidthFeet : '',
    })
  }

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

  const title = editing ? 'Edit material' : 'Add material'
  const isTiles = form.category === 'TILES'
  const readOnly = archived || saving

  return (
    <div className="bb-app">
      <AppHeader />
      <main className="bb-page bb-page-narrow">
        <Link to="/seller/materials" className="bb-back-link">
          ← Back to my materials
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
            <Link to="/seller/materials" className="bb-button">
              Back to my materials
            </Link>
          </div>
        )}

        {loadState === 'error' && (
          <div className="bb-card bb-state" role="alert">
            <p className="bb-alert bb-alert-error">{loadError}</p>
            <button type="button" className="bb-button" onClick={reload}>
              Try again
            </button>
          </div>
        )}

        {loadState === 'ready' && (
          <section className="bb-card" aria-labelledby="form-title">
            <h1 id="form-title" className="bb-card-title">
              {title}
            </h1>
            <p className="bb-muted">Prices are in Indian Rupees (₹) for one unit.</p>

            {archived && (
              <p className="bb-alert bb-alert-info" role="status">
                This listing is archived and read-only.
              </p>
            )}

            {formError && (
              <div className="bb-alert bb-alert-error" role="alert">
                <p>{formError}</p>
                {conflict && (
                  <button type="button" className="bb-button bb-button-small bb-conflict-button" onClick={reload}>
                    Load latest version (your unsaved changes will be replaced)
                  </button>
                )}
              </div>
            )}

            <form className="bb-form" onSubmit={handleSubmit} noValidate>
              <fieldset className="bb-plain-fieldset" disabled={readOnly}>
                <FormField id="m-title" label="Title" error={errors.title}>
                  {(field) => <input {...field} type="text" maxLength={120} value={form.title} onChange={update('title')} />}
                </FormField>

                <FormField
                  id="m-description"
                  label="Description"
                  error={errors.description}
                  hint="Condition, brand, size, colour. For paint, mention the litres per can."
                >
                  {(field) => (
                    <textarea {...field} rows={5} maxLength={2000} value={form.description} onChange={update('description')} />
                  )}
                </FormField>

                <div className="bb-form-row">
                  <FormField id="m-category" label="Category" error={errors.category}>
                    {(field) => (
                      <select {...field} value={form.category} onChange={changeCategory}>
                        <option value="">Choose…</option>
                        {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
                          <option key={value} value={value}>
                            {label}
                          </option>
                        ))}
                      </select>
                    )}
                  </FormField>

                  <FormField id="m-unit" label="Sold per" error={errors.unit} hint="Set by the category.">
                    {(field) => (
                      <select {...field} value={form.unit} onChange={update('unit')} disabled={!form.category}>
                        {!form.category && <option value="">Choose a category first</option>}
                        {form.category && (
                          <option value={ALLOWED_UNIT[form.category]}>{UNIT_LABELS[ALLOWED_UNIT[form.category]]}</option>
                        )}
                      </select>
                    )}
                  </FormField>
                </div>

                <div className="bb-form-row">
                  <FormField id="m-price" label="Price per unit (₹)" error={errors.unitPrice} hint="e.g. 45.50">
                    {(field) => (
                      <input {...field} type="text" inputMode="decimal" value={form.unitPrice} onChange={update('unitPrice')} />
                    )}
                  </FormField>

                  <FormField
                    id="m-quantity"
                    label="Available quantity"
                    error={errors.availableQuantity}
                    hint={editing ? 'Set 0 to show Out of stock.' : 'At least 1.'}
                  >
                    {(field) => (
                      <input
                        {...field}
                        type="text"
                        inputMode="numeric"
                        value={form.availableQuantity}
                        onChange={update('availableQuantity')}
                      />
                    )}
                  </FormField>
                </div>

                {isTiles && (
                  <div className="bb-form-row">
                    <FormField id="m-tile-length" label="Tile length (feet)" error={errors.tileLengthFeet} hint="e.g. 2 or 1.25">
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
                    <FormField id="m-tile-width" label="Tile width (feet)" error={errors.tileWidthFeet}>
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

                <FormField id="m-city" label="City" error={errors.city}>
                  {(field) => (
                    <input {...field} type="text" autoComplete="address-level2" value={form.city} onChange={update('city')} />
                  )}
                </FormField>

                <FormField
                  id="m-image"
                  label="Image link (optional)"
                  error={errors.imageUrl}
                  hint="A full https:// link to a photo. Leave empty to show a placeholder."
                >
                  {(field) => (
                    <input {...field} type="url" inputMode="url" value={form.imageUrl} onChange={update('imageUrl')} />
                  )}
                </FormField>
              </fieldset>

              {!archived && (
                <button type="submit" className="bb-button bb-button-full" disabled={saving}>
                  {saving ? 'Saving...' : editing ? 'Save changes' : 'Create listing'}
                </button>
              )}
            </form>
          </section>
        )}
      </main>
    </div>
  )
}
