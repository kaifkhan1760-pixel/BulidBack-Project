// The ONE place that talks to the Spring Boot API.
// - Sends the session cookie on every request (credentials: 'include')
// - Adds the CSRF token header to every POST/PUT/PATCH/DELETE
// - Turns error responses into an ApiError with a readable message
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export class ApiError extends Error {
  constructor(message, status, fieldErrors = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status // 0 means "no response from the server at all"
    this.fieldErrors = fieldErrors // e.g. { email: 'Enter a valid email address' }
  }
}

// CSRF token for the current server session. Kept only in memory (never in
// localStorage). The server gives a new one after login and after logout.
let csrf = null // { headerName, token }

// AuthProvider registers a function here so a 401 can clear the logged-in UI
let unauthorizedHandler = () => {}
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

// Builds "/materials?keyword=tile&page=1", skipping empty values
function withQuery(path, params = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  }
  const text = query.toString()
  return text ? `${path}?${text}` : path
}

// signal: an AbortController signal, so an old search can be cancelled
// when a newer one starts (the old answer can never overwrite the new one)
async function send(path, { method = 'GET', json, form, file, signal } = {}) {
  if (!API_BASE_URL) {
    throw new ApiError('VITE_API_BASE_URL is missing. Add it to .env and restart Vite.', 0)
  }

  const headers = { Accept: 'application/json' }
  let body
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(json)
  } else if (form !== undefined) {
    // Spring Security's login filter reads normal HTML-form fields
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    body = new URLSearchParams(form)
  } else if (file !== undefined) {
    // Photo upload: multipart/form-data. No Content-Type header here; the
    // browser adds it together with the "boundary" that separates the parts.
    body = new FormData()
    body.append('file', file)
  }

  if (method !== 'GET') {
    if (!csrf) await refreshCsrfToken()
    headers[csrf.headerName] = csrf.token
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { method, headers, body, credentials: 'include', signal })
  } catch (error) {
    if (error.name === 'AbortError') throw error // cancelled on purpose; callers ignore it
    // fetch only throws when no HTTP response arrived (server down, wrong URL, blocked request)
    throw new ApiError('Cannot reach the BuildBack server. Make sure the backend is running, then try again.', 0)
  }

  const data = response.status === 204 ? null : await response.json().catch(() => null)

  if (!response.ok) {
    if (response.status === 401) {
      csrf = null // the server session is gone, and its CSRF token with it
      unauthorizedHandler(path)
    } else if (response.status === 403 && method !== 'GET') {
      csrf = null // token may be stale; the next attempt fetches a fresh one (we never auto-resend)
    }
    throw new ApiError(data?.message ?? `Request failed (HTTP ${response.status})`, response.status, data?.errors ?? {})
  }
  return data
}

/**
 * Photos uploaded to BuildBack are stored as "/api/images/abc.jpg" (a path on the
 * API server); outside links are full "https://..." URLs. This gives a usable src.
 */
export function imageSrc(url) {
  if (typeof url !== 'string') return null
  if (url.startsWith('https://')) return url
  if (url.startsWith('/api/images/') && API_BASE_URL) return new URL(url, API_BASE_URL).href
  return null
}

export async function refreshCsrfToken() {
  csrf = null
  const data = await send('/auth/csrf')
  csrf = { headerName: data.headerName, token: data.token }
}

export const api = {
  me: () => send('/auth/me'),

  register: (details) => send('/auth/register', { method: 'POST', json: details }),

  async login(email, password) {
    const user = await send('/auth/login', { method: 'POST', form: { email, password } })
    // Logging in replaces the session's CSRF token, so fetch the new one now
    await refreshCsrfToken().catch(() => {
      csrf = null // will be fetched again before the next POST
    })
    return user
  },

  async logout() {
    await send('/auth/logout', { method: 'POST' })
    // The old session (and its token) no longer exists
    await refreshCsrfToken().catch(() => {
      csrf = null
    })
  },

  dashboard: (role) => send(`/${role.toLowerCase()}/dashboard`),

  // Public catalogue (no login needed). Parameter names match the backend exactly.
  materials: {
    search: (params, signal) => send(withQuery('/materials', params), { signal }),
    get: (id, signal) => send(`/materials/${encodeURIComponent(id)}`, { signal }),
    // Tile calculator, done on the server: { areaSqFt, allowancePercent }
    tileEstimate: (id, params) => send(withQuery(`/materials/${encodeURIComponent(id)}/tile-estimate`, params)),
  },

  // Buyer orders. Price and buyer come from the server, never from these calls.
  orders: {
    create: (materialId, quantity) => send('/buyer/orders', { method: 'POST', json: { materialId, quantity } }),
    list: (params, signal) => send(withQuery('/buyer/orders', params), { signal }),
    get: (id, signal) => send(`/buyer/orders/${encodeURIComponent(id)}`, { signal }),
    cancel: (id) => send(`/buyer/orders/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),
    startPayment: (id) => send(`/buyer/orders/${encodeURIComponent(id)}/payments`, { method: 'POST' }),
  },

  // Payment attempts. The server verifies the gateway signature before an order is PAID.
  payments: {
    verify: (paymentId, result) =>
      send(`/buyer/payments/${encodeURIComponent(paymentId)}/verify`, { method: 'POST', json: result }),
    failure: (paymentId, reason) =>
      send(`/buyer/payments/${encodeURIComponent(paymentId)}/failure`, { method: 'POST', json: { reason } }),
    simulate: (paymentId, outcome) =>
      send(`/buyer/payments/${encodeURIComponent(paymentId)}/simulate`, { method: 'POST', json: { outcome } }),
  },

  // Orders placed on the seller's own listings
  sellerOrders: {
    list: (params, signal) => send(withQuery('/seller/orders', params), { signal }),
    collect: (id) => send(`/seller/orders/${encodeURIComponent(id)}/collect`, { method: 'POST' }),
  },

  admin: {
    summary: (signal) => send('/admin/summary', { signal }),
    users: (params, signal) => send(withQuery('/admin/users', params), { signal }),
    materials: (params, signal) => send(withQuery('/admin/materials', params), { signal }),
    archiveMaterial: (id) => send(`/admin/materials/${encodeURIComponent(id)}/archive`, { method: 'POST' }),
    orders: (params, signal) => send(withQuery('/admin/orders', params), { signal }),
  },

  // Listing photos: the server checks the file really is a JPG/PNG/WebP and returns { url }
  sellerImages: {
    upload: (file) => send('/seller/images', { method: 'POST', file }),
  },

  // The logged-in seller's own listings. The server decides who "own" is.
  sellerMaterials: {
    list: (params, signal) => send(withQuery('/seller/materials', params), { signal }),
    get: (id, signal) => send(`/seller/materials/${encodeURIComponent(id)}`, { signal }),
    create: (material) => send('/seller/materials', { method: 'POST', json: material }),
    update: (id, material) => send(`/seller/materials/${encodeURIComponent(id)}`, { method: 'PUT', json: material }),
    // "Delete" archives the listing; version proves we are not acting on stale data
    archive: (id, version) =>
      send(withQuery(`/seller/materials/${encodeURIComponent(id)}`, { version }), { method: 'DELETE' }),
  },
}
