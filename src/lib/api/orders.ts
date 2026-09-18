import { api } from './client'

export type OrderStatus =
  | 'PENDING'
  | 'PAYMENT_RECEIVED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'RETURN_REQUESTED'
  | 'RETURN_APPROVED'
  | 'RETURN_REJECTED'
  | 'REFUND_INITIATED'
  | 'REFUNDED'

export interface OrderItemDto {
  id: string
  variantId: string
  productName: string
  productSlug: string
  variantName?: string
  sku: string
  image: string | null
  quantity: number
  unitPrice: number
  totalPrice: number
  fulfillmentStatus: string
}

export interface ShippingAddressDto {
  firstName: string
  lastName?: string
  phone: string
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  country: string
  postalCode: string
}

export interface OrderDto {
  id: string
  orderNumber: string
  status: OrderStatus
  shippingAddress: ShippingAddressDto
  shippingMethod: string
  shippingCost: number
  subtotal: number
  discountAmount: number
  taxAmount: number
  totalAmount: number
  notes?: string
  createdAt: string
  items: OrderItemDto[]
}

export interface PaginatedOrders {
  content: OrderDto[]
  pageNumber: number
  pageSize: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface CheckoutPayload {
  addressId: string
  shippingMethod?: string
  notes?: string
}

export async function checkout(payload: CheckoutPayload) {
  const response = await api.post<{ success: boolean; data: OrderDto }>('/api/v1/orders', payload)
  return response.data.data
}

export async function getOrders(params: { page?: number; size?: number } = {}) {
  const response = await api.get<{ success: boolean; data: PaginatedOrders }>('/api/v1/orders', {
    params,
  })
  return response.data.data
}

export async function getOrder(id: string) {
  const response = await api.get<{ success: boolean; data: OrderDto }>(`/api/v1/orders/${id}`)
  return response.data.data
}
