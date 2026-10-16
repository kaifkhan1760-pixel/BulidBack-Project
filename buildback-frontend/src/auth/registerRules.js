// Sign-up rules. They mirror RegisterRequest.java + UserService.java on the
// backend, which checks everything again (the browser can always be bypassed).

// Letters (any language) between spaces, dots, apostrophes, hyphens or "&":
// "Kaif Khan", "R. K. D'Souza", "Patel Tiles & Sanitary". No digits or other symbols.
const NAME_PATTERN = /^[\p{L}\p{M}]+(?:[ .'&-]{1,3}[\p{L}\p{M}]+)*\.?$/u

// Needs a domain ending such as .com or .in ("name@gmail" is not enough)
const EMAIL_PATTERN =
  /^[a-z0-9_%+-]+(?:\.[a-z0-9_%+-]+)*@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\.[a-z]{2,}$/

const MOBILE_PATTERN = /^\+91[6-9]\d{9}$/

/** "Kaif   Khan " -> "Kaif Khan" */
export function cleanName(name) {
  return name.trim().replace(/\s+/g, ' ')
}

/** "+91 98765-43210", "098765 43210" -> "+919876543210" (same as PhoneUtils.java) */
export function cleanPhone(phone) {
  let digits = phone.replace(/[\s().-]/g, '')
  if (digits.startsWith('+91')) digits = digits.slice(3)
  else if (digits.length === 12 && digits.startsWith('91')) digits = digits.slice(2)
  else if (digits.length === 11 && digits.startsWith('0')) digits = digits.slice(1)
  return /^\d{10}$/.test(digits) ? `+91${digits}` : digits
}

/** Each password rule with whether it is met, for the live checklist. */
export function passwordChecks(password) {
  return [
    { id: 'length', label: 'At least 8 characters', ok: password.length >= 8 },
    { id: 'case', label: 'Upper and lower case letters', ok: /\p{Lu}/u.test(password) && /\p{Ll}/u.test(password) },
    { id: 'number', label: 'At least one number', ok: /\d/.test(password) },
  ]
}

/** 0 = nothing typed, 1 = weak, 2 = good, 3 = strong */
export function passwordStrength(password) {
  if (!password) return 0
  const met = passwordChecks(password).filter((check) => check.ok).length
  if (met < 3) return 1
  const hasSymbol = /[^\p{L}\d]/u.test(password)
  return password.length >= 12 || hasSymbol ? 3 : 2
}

/** Returns { field: message } for every field that breaks a rule. */
export function validateRegistration(form) {
  const errors = {}
  const name = cleanName(form.name)
  const email = form.email.trim().toLowerCase()
  const phone = cleanPhone(form.phone)
  const password = form.password

  if (!name) errors.name = 'Name is required'
  else if (name.length < 2 || name.length > 100) errors.name = 'Name must be 2 to 100 characters'
  else if (!NAME_PATTERN.test(name))
    errors.name = 'Use letters only. Spaces, dots, apostrophes, hyphens and & are allowed between words'

  if (!email) errors.email = 'Email is required'
  else if (email.length > 254 || !EMAIL_PATTERN.test(email))
    errors.email = 'Enter a valid email address, like name@example.com'

  if (!form.phone.trim()) errors.phone = 'Mobile number is required'
  else if (!MOBILE_PATTERN.test(phone)) errors.phone = 'Enter a valid 10-digit Indian mobile number'

  if (!password) errors.password = 'Password is required'
  else if (passwordChecks(password).some((check) => !check.ok))
    errors.password = 'Use at least 8 characters with upper and lower case letters and a number'
  else if (/^\s|\s$/.test(password)) errors.password = 'Remove the space at the start or end of the password'
  else if (new TextEncoder().encode(password).length > 72) errors.password = 'Password is too long (maximum 72 bytes)'
  else if (password.trim().toLowerCase() === email) errors.password = 'Password must not be the same as your email'

  if (!form.confirmPassword) errors.confirmPassword = 'Type the password again'
  else if (form.confirmPassword !== password) errors.confirmPassword = 'Passwords do not match'

  if (form.role !== 'BUYER' && form.role !== 'SELLER') errors.role = 'Choose Buyer or Seller'
  return errors
}
