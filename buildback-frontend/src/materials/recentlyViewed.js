// "Recently viewed" listings, remembered in this browser only (localStorage).
// It is a convenience: if storage is blocked (private mode), it simply stays empty.
const KEY = 'bb-recently-viewed'
const MAX = 8

// Only what a listing card needs; never anything about the user
const FIELDS = ['id', 'title', 'category', 'unitPrice', 'unit', 'availableQuantity', 'inStock', 'city', 'imageUrl']

export function readRecentlyViewed() {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(list) ? list.filter((item) => item && typeof item.id === 'number') : []
  } catch {
    return []
  }
}

export function rememberViewed(material) {
  try {
    const card = Object.fromEntries(FIELDS.map((field) => [field, material[field]]))
    const others = readRecentlyViewed().filter((item) => item.id !== material.id)
    localStorage.setItem(KEY, JSON.stringify([card, ...others].slice(0, MAX)))
  } catch {
    // storage full or blocked: nothing to do
  }
}

export function clearRecentlyViewed() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // nothing stored, nothing to clear
  }
}
