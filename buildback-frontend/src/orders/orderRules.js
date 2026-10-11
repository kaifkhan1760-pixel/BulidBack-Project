import { formatPrice, toHundredths } from '../materials/materialRules'

export const ORDER_STATUS = {
  PENDING_PAYMENT: { label: 'Awaiting payment', tone: 'warn' },
  PAID: { label: 'Paid – ready to collect', tone: 'good' },
  COLLECTED: { label: 'Collected', tone: 'done' },
  CANCELLED: { label: 'Cancelled', tone: 'muted' },
  EXPIRED: { label: 'Expired', tone: 'muted' },
}

export const PAYMENT_STATUS = {
  CREATED: 'Started',
  SUCCESS: 'Successful',
  FAILED: 'Failed',
}

const dateFormat = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' })

export function formatDate(iso) {
  return iso ? dateFormat.format(new Date(iso)) : ''
}

/** Exact estimate: "45.00" x 17 -> "₹765.00", using whole paise (BigInt), no floats. */
export function estimateTotal(unitPrice, quantityText) {
  if (!/^\d{1,7}$/.test(quantityText) || Number(quantityText) < 1) return null
  const paise = toHundredths(unitPrice) * BigInt(quantityText)
  const rupees = `${paise / 100n}.${String(paise % 100n).padStart(2, '0')}`
  return formatPrice(rupees)
}

/** "12:05" style countdown until the reservation ends. */
export function timeLeft(expiresAt, now) {
  const ms = new Date(expiresAt).getTime() - now
  if (ms <= 0) return null
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}
