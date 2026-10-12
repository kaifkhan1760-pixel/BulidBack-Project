// Labels, formatting and validation for materials.
// The rules mirror MaterialRequest.java + MaterialService.java on the backend.
// The server always checks again; these only give faster feedback.

export const CATEGORY_LABELS = {
  TILES: 'Tiles',
  PAINT: 'Paint',
  PLYWOOD: 'Plywood',
  FITTINGS: 'Fittings',
  FALSE_CEILING: 'POP & False Ceiling',
  WATERPROOFING: 'Waterproofing',
  INTERIOR: 'Interior & Decor',
}

// Icon shown for each category (names from components/Icon.jsx)
export const CATEGORY_ICONS = {
  TILES: 'tiles',
  PAINT: 'paint',
  PLYWOOD: 'plywood',
  FITTINGS: 'fittings',
  FALSE_CEILING: 'ceiling',
  WATERPROOFING: 'droplet',
  INTERIOR: 'sofa',
}

// Units each category may be sold in (same as MaterialCategory.java).
// The first one is the usual unit, so the form picks it by default.
export const ALLOWED_UNITS = {
  TILES: ['PIECE'],
  PAINT: ['CAN'],
  PLYWOOD: ['SHEET'],
  FITTINGS: ['PIECE'],
  FALSE_CEILING: ['SHEET', 'BAG', 'PIECE'],
  WATERPROOFING: ['CAN', 'BAG', 'ROLL'],
  INTERIOR: ['PIECE', 'ROLL', 'SHEET'],
}

export const UNIT_LABELS = { PIECE: 'piece', CAN: 'can', SHEET: 'sheet', BAG: 'bag', ROLL: 'roll' }
export const UNIT_PLURALS = { PIECE: 'pieces', CAN: 'cans', SHEET: 'sheets', BAG: 'bags', ROLL: 'rolls' }

export const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest first' },
  { value: 'priceAsc', label: 'Price: low to high' },
  { value: 'priceDesc', label: 'Price: high to low' },
]

export const LIMITS = {
  maxPriceCents: 1_000_000_000n, // ₹1,00,00,000.00
  maxQuantity: 1_000_000,
  maxTileFeetHundredths: 1000n, // 10.00 ft
  maxKeyword: 100,
}

const PRICE_PATTERN = /^\d{1,8}(\.\d{1,2})?$/ // e.g. 45 or 45.5 or 45.50
const FEET_PATTERN = /^\d{1,2}(\.\d{1,2})?$/
const WHOLE_NUMBER = /^\d{1,7}$/

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })

/** "1250.50" -> "₹1,250.50" (display only; the exact string is what we send). */
export function formatPrice(value) {
  return inr.format(Number(value))
}

const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

/** Shorter price for cards: "₹3,250" for whole rupees, "₹28.50" otherwise. */
export function formatPriceCompact(value) {
  const amount = Number(value)
  return Number.isInteger(amount) ? inrWhole.format(amount) : inr.format(amount)
}

/** Always show the unit next to a price: "₹45.50 per piece". */
export function priceWithUnit(value, unit) {
  return `${formatPrice(value)} per ${UNIT_LABELS[unit] ?? 'unit'}`
}

export function quantityText(quantity, unit) {
  if (quantity === 0) return 'Out of stock'
  const word = quantity === 1 ? UNIT_LABELS[unit] : UNIT_PLURALS[unit]
  return `${quantity.toLocaleString('en-IN')} ${word ?? 'units'} available`
}

/**
 * "45.5" -> 4550n. Exact whole-number maths with BigInt, no floating point,
 * so 0.1 + 0.2 style rounding errors can never affect a comparison.
 */
export function toHundredths(text) {
  const [whole, fraction = ''] = text.split('.')
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
}

export function isValidPriceText(text) {
  return PRICE_PATTERN.test(text)
}

// Listing photos: same limits as the backend (MaterialRequest + ImageStorageService)
export const MAX_PHOTOS = 5
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024
export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/** Quick check before uploading. The server checks the real file content again. */
export function photoProblem(file) {
  if (!PHOTO_TYPES.includes(file.type)) return `“${file.name}” is not a JPG, PNG or WebP photo.`
  if (file.size > MAX_PHOTO_BYTES) return `“${file.name}” is larger than 5 MB.`
  return null
}

export const EMPTY_MATERIAL_FORM = {
  title: '',
  description: '',
  category: '',
  unit: '',
  unitPrice: '',
  availableQuantity: '',
  city: '',
  imageUrls: [], // first one is the cover photo
  tileLengthFeet: '',
  tileWidthFeet: '',
}

/** Server data -> form strings (numbers stay as exact text). */
export function formFromMaterial(m) {
  return {
    title: m.title,
    description: m.description,
    category: m.category,
    unit: m.unit,
    unitPrice: m.unitPrice,
    availableQuantity: String(m.availableQuantity),
    city: m.city,
    imageUrls: m.imageUrls ?? (m.imageUrl ? [m.imageUrl] : []),
    tileLengthFeet: m.tileLengthFeet ?? '',
    tileWidthFeet: m.tileWidthFeet ?? '',
  }
}

/** Form strings -> JSON body. Money stays a string like "45.50" (no float). */
export function requestFromForm(form) {
  const isTiles = form.category === 'TILES'
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    category: form.category,
    unit: form.unit,
    unitPrice: form.unitPrice.trim(),
    availableQuantity: Number.parseInt(form.availableQuantity.trim(), 10),
    city: form.city.trim(),
    imageUrls: form.imageUrls,
    // Never send old tile sizes for a non-tile category
    tileLengthFeet: isTiles ? form.tileLengthFeet.trim() : null,
    tileWidthFeet: isTiles ? form.tileWidthFeet.trim() : null,
  }
}

function checkFeet(value, label, errors, field) {
  const text = value.trim()
  if (!text) errors[field] = `${label} (feet) is required for tiles`
  else if (!FEET_PATTERN.test(text)) errors[field] = `${label}: use a number like 2 or 1.25`
  else if (toHundredths(text) <= 0n) errors[field] = `${label} must be greater than 0`
  else if (toHundredths(text) > LIMITS.maxTileFeetHundredths) errors[field] = `${label} must be at most 10 feet`
}

export function validateMaterialForm(form, { creating }) {
  const errors = {}
  const title = form.title.trim()
  const description = form.description.trim()
  const city = form.city.trim()
  const price = form.unitPrice.trim()
  const quantity = form.availableQuantity.trim()

  if (title.length < 3 || title.length > 120) errors.title = 'Title must be 3 to 120 characters'
  if (description.length < 10 || description.length > 2000)
    errors.description = 'Description must be 10 to 2000 characters'
  if (!ALLOWED_UNITS[form.category]) errors.category = 'Choose a category'
  else if (!ALLOWED_UNITS[form.category].includes(form.unit)) errors.unit = 'Choose how this material is sold'

  // Blank stays blank: we never turn an empty box into 0
  if (!price) errors.unitPrice = 'Price is required'
  else if (!PRICE_PATTERN.test(price)) errors.unitPrice = 'Use a number with at most 2 decimal places, e.g. 45.50'
  else if (toHundredths(price) < 1n) errors.unitPrice = 'Price must be at least ₹0.01'
  else if (toHundredths(price) > LIMITS.maxPriceCents) errors.unitPrice = 'Price must be at most ₹1,00,00,000'

  if (!quantity) errors.availableQuantity = 'Quantity is required'
  else if (!WHOLE_NUMBER.test(quantity)) errors.availableQuantity = 'Quantity must be a whole number'
  else if (Number(quantity) > LIMITS.maxQuantity) errors.availableQuantity = 'Quantity must be at most 1,000,000'
  else if (creating && Number(quantity) < 1) errors.availableQuantity = 'A new listing needs a quantity of at least 1'

  if (city.length < 2 || city.length > 100) errors.city = 'City must be 2 to 100 characters'

  if (form.imageUrls.length === 0) errors.imageUrls = 'Add at least one photo of the material'
  else if (form.imageUrls.length > MAX_PHOTOS) errors.imageUrls = `Add at most ${MAX_PHOTOS} photos`

  if (form.category === 'TILES') {
    checkFeet(form.tileLengthFeet, 'Tile length', errors, 'tileLengthFeet')
    checkFeet(form.tileWidthFeet, 'Tile width', errors, 'tileWidthFeet')
  }
  return errors
}
