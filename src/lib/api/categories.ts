import { api } from './client'

export interface CategoryNode {
  id: string
  name: string
  slug: string
  description?: string
  imageUrl?: string
  children: CategoryNode[]
}

export async function getCategoryTree() {
  const response = await api.get<{ success: boolean, message: string, data: CategoryNode[] }>('/api/v1/categories/tree')
  return response.data.data
}

// --- Admin-only catalog settings below: flat listing (including inactive rows) + CRUD ---

export interface Category {
  id: string
  parentId: string | null
  name: string
  slug: string
  description?: string
  imageUrl?: string
  sortOrder: number
  isActive: boolean
}

export interface CategoryPayload {
  name: string
  parentId?: string | null
  description?: string
  imageUrl?: string
  sortOrder?: number
}

export async function getAllCategories() {
  const response = await api.get<{ success: boolean; data: Category[] }>('/api/v1/categories')
  return response.data.data
}

export async function createCategory(payload: CategoryPayload) {
  const response = await api.post<{ success: boolean; data: Category }>('/api/v1/categories', payload)
  return response.data.data
}

export async function updateCategory(id: string, payload: CategoryPayload) {
  const response = await api.put<{ success: boolean; data: Category }>(`/api/v1/categories/${id}`, payload)
  return response.data.data
}

/** Deactivates the category (is_active = false) -- see the backend's CategoryService for why. */
export async function deactivateCategory(id: string) {
  await api.delete(`/api/v1/categories/${id}`)
}
