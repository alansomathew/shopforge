// Razorpay doesn't ship an npm package with TypeScript types for checkout.js -- it's loaded as a
// plain <script> tag that attaches a `Razorpay` constructor to `window`. These types describe
// just the subset this app actually uses, so `any` doesn't leak into the checkout page.

export interface RazorpayPaymentResponse {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

export interface RazorpayCheckoutOptions {
  key: string
  amount: number
  currency: string
  name: string
  description?: string
  order_id: string
  handler: (response: RazorpayPaymentResponse) => void
  prefill?: { name?: string; email?: string; contact?: string }
  theme?: { color?: string }
  modal?: { ondismiss?: () => void }
}

interface RazorpayInstance {
  open: () => void
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayInstance
  }
}

let scriptLoadPromise: Promise<boolean> | null = null

/**
 * Injects checkout.js once and resolves true/false depending on whether it loaded -- safe to
 * call multiple times (e.g. on every "Place Order" click); later calls just reuse the same
 * in-flight or already-resolved promise instead of adding duplicate <script> tags.
 */
export function loadRazorpayScript(): Promise<boolean> {
  if (typeof window === 'undefined') return Promise.resolve(false)
  if (window.Razorpay) return Promise.resolve(true)
  if (scriptLoadPromise) return scriptLoadPromise

  scriptLoadPromise = new Promise((resolve) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.onload = () => resolve(true)
    script.onerror = () => resolve(false)
    document.body.appendChild(script)
  })
  return scriptLoadPromise
}
