import { useState } from 'react'
import { api } from '../api/client'
import { formatPrice } from '../materials/materialRules'

const ALLOWANCES = [0, 5, 10, 15, 20, 25]
const AREA_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

/**
 * "Does this listing cover my floor?"
 * The server does the maths (exact decimals) so the result is the same
 * number the backend would use; this component only collects input.
 */
export function TileCalculator({ material, onUseQuantity }) {
  const [area, setArea] = useState('')
  const [allowance, setAllowance] = useState('10')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function calculate(event) {
    event.preventDefault()
    setError(null)
    setResult(null)
    const text = area.trim()
    if (!AREA_PATTERN.test(text) || Number(text) <= 0) {
      setError('Enter the floor area in square feet, e.g. 60 or 72.5')
      return
    }
    setBusy(true)
    try {
      setResult(await api.materials.tileEstimate(material.id, { areaSqFt: text, allowancePercent: allowance }))
    } catch (err) {
      setError(Object.values(err.fieldErrors ?? {})[0] ?? err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="bb-panel" aria-labelledby="calc-title">
      <h2 id="calc-title" className="bb-section-title">
        Tile quantity calculator
      </h2>
      <p className="bb-muted bb-small">
        Tile size {material.tileLengthFeet} ft × {material.tileWidthFeet} ft. The allowance covers cutting and
        breakage.
      </p>

      <form className="bb-calc-form" onSubmit={calculate} noValidate>
        <div className="bb-field">
          <label htmlFor="calc-area">Floor area (sq ft)</label>
          <input
            id="calc-area"
            inputMode="decimal"
            value={area}
            onChange={(e) => setArea(e.target.value)}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'calc-error' : undefined}
          />
        </div>
        <div className="bb-field">
          <label htmlFor="calc-allowance">Allowance</label>
          <select id="calc-allowance" value={allowance} onChange={(e) => setAllowance(e.target.value)}>
            {ALLOWANCES.map((a) => (
              <option key={a} value={a}>
                {a}%
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className="bb-button" disabled={busy}>
          {busy ? 'Calculating…' : 'Calculate'}
        </button>
      </form>

      {error && (
        <p id="calc-error" className="bb-field-error" role="alert">
          {error}
        </p>
      )}

      <div aria-live="polite">
        {result && (
          <div className="bb-calc-result">
            <p className="bb-calc-formula">
              {result.areaSqFt} sq ft × {100 + result.allowancePercent}% = {result.areaWithAllowanceSqFt} sq ft ÷{' '}
              {result.tileAreaSqFt} sq ft per tile → round up
            </p>
            <p className="bb-calc-answer">
              You need <strong>{result.requiredTiles} tiles</strong> (about {formatPrice(result.estimatedCost)})
            </p>
            {result.enoughStock ? (
              <p className="bb-stock">✓ This listing has {result.availableQuantity} available — enough.</p>
            ) : (
              <p className="bb-stock-out">
                Only {result.availableQuantity} available — not enough for this area.
              </p>
            )}
            {result.enoughStock && onUseQuantity && (
              <button
                type="button"
                className="bb-button bb-button-outline bb-button-small"
                onClick={() => onUseQuantity(String(result.requiredTiles))}
              >
                Use {result.requiredTiles} tiles in my order
              </button>
            )}
          </div>
        )}
      </div>
    </section>
  )
}
