import { api } from './client'

export interface CartItemDto {
  id: string
  variantId: string
  productId: string
  productSlug: string
  productName: string
  variantName?: string
  sku: string
  image: string | null
  unitPrice: number
  quantity: number
  lineTotal: number
  availableStock: number
}

export interface CartDto {
  id: string
  items: CartItemDto[]
  subtotal: number
  totalItems: number
}

export async function getCart() {
  const response = await api.get<{ success: boolean; data: CartDto }>('/api/v1/cart')
  return response.data.data
}

export async function addCartItem(variantId: string, quantity: number) {
  const response = await api.post<{ success: boolean; data: CartDto }>('/api/v1/cart/items', {
    variantId,
    quantity,
  })
  return response.data.data
}

export async function updateCartItem(itemId: string, quantity: number) {
  const response = await api.patch<{ success: boolean; data: CartDto }>(
    `/api/v1/cart/items/${itemId}`,
    { quantity }
  )
  return response.data.data
}

export async function removeCartItem(itemId: string) {
  const response = await api.delete<{ success: boolean; data: CartDto }>(
    `/api/v1/cart/items/${itemId}`
  )
  return response.data.data
}

export async function clearCart() {
  const response = await api.delete<{ success: boolean; data: CartDto }>('/api/v1/cart')
  return response.data.data
}
