import { api } from './client'

export interface Brand {
  id: string
  name: string
  slug: string
  logoUrl?: string
  description?: string
  isActive: boolean
}

export interface BrandPayload {
  name: string
  logoUrl?: string
  description?: string
}

export async function getBrands() {
  const response = await api.get<{ success: boolean; data: Brand[] }>('/api/v1/brands')
  return response.data.data
}

export async function createBrand(payload: BrandPayload) {
  const response = await api.post<{ success: boolean; data: Brand }>('/api/v1/brands', payload)
  return response.data.data
}

export async function updateBrand(id: string, payload: BrandPayload) {
  const response = await api.put<{ success: boolean; data: Brand }>(`/api/v1/brands/${id}`, payload)
  return response.data.data
}

/** Deactivates the brand (is_active = false) -- see the backend's BrandService for why. */
export async function deactivateBrand(id: string) {
  await api.delete(`/api/v1/brands/${id}`)
}
