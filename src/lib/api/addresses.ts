import { api } from './client'

export interface Address {
  id: string
  label?: string
  firstName: string
  lastName?: string
  phone: string
  addressLine1: string
  addressLine2?: string
  city: string
  state: string
  country: string
  postalCode: string
  isDefault: boolean
}

export async function getAddresses() {
  const response = await api.get<{ success: boolean; data: Address[] }>('/api/v1/users/me/addresses')
  return response.data.data
}
