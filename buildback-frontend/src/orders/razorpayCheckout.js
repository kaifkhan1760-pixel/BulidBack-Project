// Loads Razorpay's official Checkout script only when a Razorpay payment starts.
// Card/UPI details are typed into Razorpay's own popup, never into BuildBack.
const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'
let loading = null

function loadScript() {
  if (window.Razorpay) return Promise.resolve()
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SCRIPT_URL
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => {
        loading = null
        reject(new Error('Could not load the Razorpay checkout. Check your internet connection.'))
      }
      document.body.appendChild(script)
    })
  }
  return loading
}

/**
 * Opens the Razorpay popup for one payment attempt.
 * onSuccess gets { providerOrderId, providerPaymentId, signature } for the SERVER to verify.
 * onFailure gets a readable reason (declined, popup closed...).
 */
export async function openRazorpayCheckout(checkout, { onSuccess, onFailure }) {
  await loadScript()
  let finished = false

  const razorpay = new window.Razorpay({
    key: checkout.publicKey, // public key id only; the secret stays on the server
    amount: checkout.amountInPaise,
    currency: checkout.currency,
    order_id: checkout.providerOrderId,
    name: 'BuildBack',
    description: checkout.description,
    prefill: { name: checkout.buyerName, email: checkout.buyerEmail },
    theme: { color: '#167a59' },
    handler: (response) => {
      finished = true
      onSuccess({
        providerOrderId: response.razorpay_order_id,
        providerPaymentId: response.razorpay_payment_id,
        signature: response.razorpay_signature,
      })
    },
    modal: {
      ondismiss: () => {
        if (!finished) onFailure('Checkout was closed before paying')
      },
    },
  })
  razorpay.on('payment.failed', (response) => {
    onFailure(response?.error?.description || 'The payment failed')
  })
  razorpay.open()
}
