import { api } from './client'
import { OrderDto } from './orders'

export interface InitiatePaymentPayload {
  orderId: string
  method: 'RAZORPAY' | 'COD'
}

export interface PaymentInitiateResult {
  orderId: string
  gateway: 'razorpay' | 'cod'
  status: 'INITIATED' | 'SUCCESS'
  razorpayOrderId?: string
  razorpayKeyId?: string
  amount: number
  currency: string
}

export interface VerifyPaymentPayload {
  razorpayOrderId: string
  razorpayPaymentId: string
  razorpaySignature: string
}

export async function initiatePayment(payload: InitiatePaymentPayload) {
  const response = await api.post<{ success: boolean; data: PaymentInitiateResult }>(
    '/api/v1/payments/initiate',
    payload
  )
  return response.data.data
}

/** Returns the updated order (now PAYMENT_RECEIVED) once the signature checks out. */
export async function verifyPayment(payload: VerifyPaymentPayload) {
  const response = await api.post<{ success: boolean; data: OrderDto }>('/api/v1/payments/verify', payload)
  return response.data.data
}
