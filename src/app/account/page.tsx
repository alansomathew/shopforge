'use client'

import React, { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navbar } from '@/components/layout/Navbar'
import { Footer } from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { CartDrawer } from '@/components/cart/CartDrawer'
import { SearchModal } from '@/components/search/SearchModal'
import { useSession, signOut } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/Button'
import { User, ShoppingBag, MapPin, Heart, LogOut, CheckCircle2, Package, ArrowRight, ShieldCheck, Plus, Trash2, Edit2, KeyRound } from 'lucide-react'
import { formatPrice, formatDate } from '@/lib/utils'
import { getOrders } from '@/lib/api/orders'
import axios from 'axios'
import toast from 'react-hot-toast'

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

interface BackendAddress {
  id: string
  label: string
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

export default function AccountDashboard() {
  const { data: session, status, update } = useSession()
  const router = useRouter()
  const [activeSubTab, setActiveSubTab] = useState<'profile' | 'orders' | 'addresses' | 'wishlist'>('profile')

  // Profile Form States
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [phone, setPhone] = useState('')
  const [isUpdatingProfile, setIsUpdatingProfile] = useState(false)

  // Password Change Form States
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isChangingPassword, setIsChangingPassword] = useState(false)

  // Address States
  const [addresses, setAddresses] = useState<BackendAddress[]>([])
  const [isAddressesLoading, setIsAddressesLoading] = useState(false)
  const [showAddressForm, setShowAddressForm] = useState(false)
  const [editingAddress, setEditingAddress] = useState<BackendAddress | null>(null)

  // Address Form States
  const [addrLabel, setAddrLabel] = useState('Home')
  const [addrFirstName, setAddrFirstName] = useState('')
  const [addrLastName, setAddrLastName] = useState('')
  const [addrPhone, setAddrPhone] = useState('')
  const [addrLine1, setAddrLine1] = useState('')
  const [addrLine2, setAddrLine2] = useState('')
  const [addrCity, setAddrCity] = useState('')
  const [addrState, setAddrState] = useState('')
  const [addrPostalCode, setAddrPostalCode] = useState('')
  const [addrCountry, setAddrCountry] = useState('IN')
  const [addrIsDefault, setAddrIsDefault] = useState(false)
  const [isSavingAddress, setIsSavingAddress] = useState(false)

  const { data: ordersData, isLoading: isOrdersLoading } = useQuery({
    queryKey: ['myOrders'],
    queryFn: () => getOrders({ size: 20 }),
    enabled: status === 'authenticated',
  })
  const orders = ordersData?.content ?? []

  // Handle redirect if not authenticated
  useEffect(() => {
    if (status === 'unauthenticated') {
      toast.error('Please log in to access this page')
      router.push('/login')
    }
  }, [status, router])

  // Populate Profile Inputs from Session
  useEffect(() => {
    if (session?.user) {
      const u = session.user as any
      setFirstName(u.firstName || '')
      setLastName(u.lastName || '')
      setPhone(u.phone || '')
    }
  }, [session])

  // Fetch addresses and profile on active tab changes
  useEffect(() => {
    if (status === 'authenticated' && activeSubTab === 'addresses') {
      fetchAddresses()
    }
  }, [status, activeSubTab])

  const handleApiError = (err: any, defaultMessage: string) => {
    console.error(defaultMessage, err)
    if (err.response?.status === 401 || err.response?.status === 403) {
      toast.error('Session expired or unauthorized. Logging out...')
      signOut({ callbackUrl: '/login' })
      return
    }
    toast.error(err.response?.data?.message || defaultMessage)
  }

  const fetchAddresses = async () => {
    if (!session) return
    setIsAddressesLoading(true)
    try {
      const response = await axios.get(`${API_URL}/api/v1/users/me/addresses`, {
        headers: {
          Authorization: `Bearer ${(session as any).accessToken}`,
        },
      })
      if (response.data?.success && response.data?.data) {
        setAddresses(response.data.data)
      }
    } catch (err: any) {
      handleApiError(err, 'Failed to load addresses')
    } finally {
      setIsAddressesLoading(false)
    }
  }

  // Handle Profile Update
  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!session) return

    setIsUpdatingProfile(true)
    try {
      const response = await axios.put(
        `${API_URL}/api/v1/users/me`,
        { firstName, lastName, phone },
        {
          headers: {
            Authorization: `Bearer ${(session as any).accessToken}`,
          },
        }
      )
      if (response.status === 200 || response.data?.success) {
        toast.success('Profile updated successfully!')
        // Update NextAuth Session State
        await update({
          ...session,
          user: {
            ...session.user,
            firstName,
            lastName,
            phone,
          },
        })
      }
    } catch (err: any) {
      handleApiError(err, 'Failed to update profile')
    } finally {
      setIsUpdatingProfile(false)
    }
  }

  // Handle Password Change
  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!session) return

    if (newPassword !== confirmPassword) {
      toast.error('New passwords do not match')
      return
    }

    setIsChangingPassword(true)
    try {
      const response = await axios.post(
        `${API_URL}/api/v1/users/me/change-password`,
        { oldPassword, newPassword },
        {
          headers: {
            Authorization: `Bearer ${(session as any).accessToken}`,
          },
        }
      )
      if (response.status === 200 || response.data?.success) {
        toast.success('Password changed successfully!')
        setOldPassword('')
        setNewPassword('')
        setConfirmPassword('')
      }
    } catch (err: any) {
      handleApiError(err, 'Failed to change password')
    } finally {
      setIsChangingPassword(false)
    }
  }

  // Handle Save Address (Create or Edit)
  const handleSaveAddress = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!session) return

    setIsSavingAddress(true)
    const payload = {
      label: addrLabel,
      firstName: addrFirstName,
      lastName: addrLastName,
      phone: addrPhone,
      addressLine1: addrLine1,
      addressLine2: addrLine2,
      city: addrCity,
      state: addrState,
      postalCode: addrPostalCode,
      country: addrCountry,
      isDefault: addrIsDefault,
    }

    try {
      if (editingAddress) {
        // Edit Mode
        const response = await axios.put(
          `${API_URL}/api/v1/users/me/addresses/${editingAddress.id}`,
          payload,
          {
            headers: {
              Authorization: `Bearer ${(session as any).accessToken}`,
            },
          }
        )
        if (response.data?.success) {
          toast.success('Address updated successfully!')
        }
      } else {
        // Create Mode
        const response = await axios.post(
          `${API_URL}/api/v1/users/me/addresses`,
          payload,
          {
            headers: {
              Authorization: `Bearer ${(session as any).accessToken}`,
            },
          }
        )
        if (response.data?.success) {
          toast.success('New address added!')
        }
      }
      resetAddressForm()
      fetchAddresses()
    } catch (err: any) {
      handleApiError(err, 'Failed to save address')
    } finally {
      setIsSavingAddress(false)
    }
  }

  // Handle Edit Address Button Click
  const handleEditClick = (address: BackendAddress) => {
    setEditingAddress(address)
    setAddrLabel(address.label)
    setAddrFirstName(address.firstName)
    setAddrLastName(address.lastName || '')
    setAddrPhone(address.phone)
    setAddrLine1(address.addressLine1)
    setAddrLine2(address.addressLine2 || '')
    setAddrCity(address.city)
    setAddrState(address.state)
    setAddrPostalCode(address.postalCode)
    setAddrCountry(address.country)
    setAddrIsDefault(address.isDefault)
    setShowAddressForm(true)
  }

  // Handle Delete Address
  const handleDeleteAddress = async (id: string) => {
    if (!session || !confirm('Are you sure you want to delete this address?')) return

    try {
      const response = await axios.delete(`${API_URL}/api/v1/users/me/addresses/${id}`, {
        headers: {
          Authorization: `Bearer ${(session as any).accessToken}`,
        },
      })
      if (response.status === 200 || response.data?.success) {
        toast.success('Address deleted successfully')
        fetchAddresses()
      }
    } catch (err: any) {
      handleApiError(err, 'Failed to delete address')
    }
  }

  const resetAddressForm = () => {
    setEditingAddress(null)
    setAddrLabel('Home')
    setAddrFirstName('')
    setAddrLastName('')
    setAddrPhone('')
    setAddrLine1('')
    setAddrLine2('')
    setAddrCity('')
    setAddrState('')
    setAddrPostalCode('')
    setAddrCountry('IN')
    setAddrIsDefault(false)
    setShowAddressForm(false)
  }

  if (status === 'loading') {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <div className="w-10 h-10 border-4 border-navy border-t-saffron rounded-full animate-spin" />
      </div>
    )
  }

  if (!session) return null

  // @ts-ignore
  const user = session.user

  return (
    <div className="flex flex-col min-h-screen pb-16 md:pb-0 bg-surface">
      <Navbar />

      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <span className="text-xs text-slate-400 font-semibold uppercase tracking-wider">Dashboard</span>
          <h1 className="font-display font-black text-2xl sm:text-3xl text-navy mt-1">My Account</h1>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Navigation Sidebar */}
          <aside className="lg:col-span-3 bg-white border border-slate-100 rounded-2xl overflow-hidden p-4 space-y-2">
            {[
              { id: 'profile', label: 'Profile & Security', icon: User },
              { id: 'orders', label: 'Order History', icon: ShoppingBag },
              { id: 'addresses', label: 'Saved Addresses', icon: MapPin },
              { id: 'wishlist', label: 'My Wishlist', icon: Heart }
            ].map((tab) => {
              const Icon = tab.icon
              const isSelected = activeSubTab === tab.id
              return (
                <button
                  key={tab.id}
                  onClick={() => {
                    setActiveSubTab(tab.id as any)
                    resetAddressForm()
                  }}
                  className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-bold transition-all ${
                    isSelected
                      ? 'bg-navy text-saffron'
                      : 'text-slate hover:bg-slate-50 hover:text-navy'
                  }`}
                >
                  <Icon size={16} />
                  <span>{tab.label}</span>
                </button>
              )}
            )}
            <button
              onClick={() => signOut({ callbackUrl: '/' })}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-bold text-danger hover:bg-danger/5 transition-all mt-4 border-t border-slate-50 pt-4"
            >
              <LogOut size={16} />
              <span>Logout</span>
            </button>
          </aside>

          {/* Active Tab Panel */}
          <div className="lg:col-span-9 bg-white border border-slate-100 rounded-2xl p-6 sm:p-8 min-h-[400px]">
            {activeSubTab === 'profile' && (
              <div className="space-y-10 animate-[fadeUp_150ms_ease-out]">
                {/* Profile Edit Card */}
                <div className="space-y-6">
                  <h2 className="font-display font-bold text-navy text-lg border-b border-slate-50 pb-3">
                    Profile Details
                  </h2>
                  <form onSubmit={handleUpdateProfile} className="space-y-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate uppercase tracking-wider block">First Name</label>
                        <input
                          type="text"
                          value={firstName}
                          onChange={(e) => setFirstName(e.target.value)}
                          className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl px-4 py-2.5 text-xs text-navy outline-none w-full font-semibold transition-colors"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate uppercase tracking-wider block">Last Name</label>
                        <input
                          type="text"
                          value={lastName}
                          onChange={(e) => setLastName(e.target.value)}
                          className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl px-4 py-2.5 text-xs text-navy outline-none w-full font-semibold transition-colors"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate uppercase tracking-wider block">Phone Number</label>
                        <input
                          type="text"
                          value={phone}
                          onChange={(e) => setPhone(e.target.value)}
                          placeholder="+91 99999 99999"
                          className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl px-4 py-2.5 text-xs text-navy outline-none w-full font-semibold transition-colors"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate uppercase tracking-wider block">Email Address (Read-only)</label>
                        <div className="bg-slate-100 border border-slate-200 rounded-xl px-4 py-2.5 text-xs text-slate-500 font-semibold w-full cursor-not-allowed">
                          {user?.email || 'N/A'}
                        </div>
                      </div>
                    </div>
                    <Button
                      type="submit"
                      disabled={isUpdatingProfile}
                      className="px-6 py-2.5 text-xs bg-navy text-saffron hover:bg-navy-light font-display"
                    >
                      {isUpdatingProfile ? 'Saving...' : 'Save Profile Changes'}
                    </Button>
                  </form>
                </div>

                {/* Password Change Card */}
                {user?.passwordHash !== null && (
                  <div className="space-y-6 border-t border-slate-100 pt-8">
                    <h2 className="font-display font-bold text-navy text-lg flex items-center gap-2 border-b border-slate-50 pb-3">
                      <KeyRound size={18} className="text-saffron" />
                      <span>Security & Password</span>
                    </h2>
                    <form onSubmit={handleChangePassword} className="space-y-4 max-w-xl">
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate uppercase tracking-wider block">Current Password</label>
                        <input
                          type="password"
                          value={oldPassword}
                          onChange={(e) => setOldPassword(e.target.value)}
                          placeholder="••••••••"
                          className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl px-4 py-2.5 text-xs text-navy outline-none w-full font-mono transition-colors"
                          required
                        />
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                          <label className="text-xs font-bold text-slate uppercase tracking-wider block">New Password</label>
                          <input
                            type="password"
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            placeholder="••••••••"
                            className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl px-4 py-2.5 text-xs text-navy outline-none w-full font-mono transition-colors"
                            required
                          />
                        </div>
                        <div className="space-y-1.5">
                          <label className="text-xs font-bold text-slate uppercase tracking-wider block">Confirm New Password</label>
                          <input
                            type="password"
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            placeholder="••••••••"
                            className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl px-4 py-2.5 text-xs text-navy outline-none w-full font-mono transition-colors"
                            required
                          />
                        </div>
                      </div>
                      <Button
                        type="submit"
                        disabled={isChangingPassword}
                        className="px-6 py-2.5 text-xs bg-navy text-saffron hover:bg-navy-light font-display"
                      >
                        {isChangingPassword ? 'Changing...' : 'Update Password'}
                      </Button>
                    </form>
                  </div>
                )}
              </div>
            )}

            {activeSubTab === 'orders' && (
              <div className="space-y-6 animate-[fadeUp_150ms_ease-out]">
                <h2 className="font-display font-bold text-navy text-lg border-b border-slate-50 pb-3">
                  Order History
                </h2>
                {isOrdersLoading ? (
                  <div className="flex items-center justify-center py-8">
                    <div className="w-6 h-6 border-2 border-navy border-t-saffron rounded-full animate-spin" />
                  </div>
                ) : orders.length === 0 ? (
                  <p className="text-sm text-slate">You haven&apos;t placed any orders yet.</p>
                ) : (
                  <div className="space-y-4">
                    {orders.map((order) => (
                      <div key={order.id} className="border border-slate-100 rounded-xl p-4 sm:p-5 space-y-4">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-50 pb-3 text-xs sm:text-sm">
                          <div className="flex gap-4">
                            <div>
                              <span className="text-slate block text-[10px] uppercase font-bold tracking-wider">Date</span>
                              <span className="font-semibold text-navy">{formatDate(order.createdAt)}</span>
                            </div>
                            <div>
                              <span className="text-slate block text-[10px] uppercase font-bold tracking-wider">Order Number</span>
                              <span className="font-mono font-bold text-navy">{order.orderNumber}</span>
                            </div>
                            <div>
                              <span className="text-slate block text-[10px] uppercase font-bold tracking-wider">Total</span>
                              <span className="font-bold text-saffron">{formatPrice(order.totalAmount)}</span>
                            </div>
                          </div>
                          <div>
                            <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold ${
                              order.status === 'DELIVERED' || order.status === 'COMPLETED' ? 'bg-emerald-50 text-emerald-600' : 'bg-saffron/10 text-saffron-dark'
                            }`}>
                              {order.status}
                            </span>
                          </div>
                        </div>

                        {order.items.map((item) => (
                          <div key={item.id} className="flex gap-3 items-center">
                            <img
                              src={item.image || undefined}
                              alt={item.productName}
                              className="w-12 h-12 object-cover rounded-lg bg-slate-50 flex-shrink-0"
                            />
                            <div className="text-xs">
                              <p className="font-bold text-navy">{item.productName}</p>
                              <p className="text-slate mt-0.5">Quantity: {item.quantity}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeSubTab === 'addresses' && (
              <div className="space-y-6 animate-[fadeUp_150ms_ease-out]">
                <div className="flex items-center justify-between border-b border-slate-50 pb-3">
                  <h2 className="font-display font-bold text-navy text-lg">
                    Saved Addresses
                  </h2>
                  {!showAddressForm && (
                    <button
                      onClick={() => {
                        resetAddressForm()
                        setShowAddressForm(true)
                      }}
                      className="inline-flex items-center gap-1 text-xs font-bold text-saffron hover:underline"
                    >
                      <Plus size={14} /> Add New Address
                    </button>
                  )}
                </div>

                {/* Add / Edit Address Form */}
                {showAddressForm && (
                  <form onSubmit={handleSaveAddress} className="border border-slate-100 rounded-xl p-5 bg-slate-50/20 space-y-4 animate-[fadeUp_150ms_ease-out]">
                    <h3 className="font-display font-bold text-navy text-sm">
                      {editingAddress ? 'Edit Address' : 'Add New Address'}
                    </h3>
                    
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Address Tag (e.g. Home, Work)</label>
                        <input
                          type="text"
                          value={addrLabel}
                          onChange={(e) => setAddrLabel(e.target.value)}
                          placeholder="Home"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Phone Number</label>
                        <input
                          type="text"
                          value={addrPhone}
                          onChange={(e) => setAddrPhone(e.target.value)}
                          placeholder="+91 99999 99999"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">First Name</label>
                        <input
                          type="text"
                          value={addrFirstName}
                          onChange={(e) => setAddrFirstName(e.target.value)}
                          placeholder="First Name"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Last Name</label>
                        <input
                          type="text"
                          value={addrLastName}
                          onChange={(e) => setAddrLastName(e.target.value)}
                          placeholder="Last Name"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                        />
                      </div>
                      <div className="sm:col-span-2 space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Address Line 1</label>
                        <input
                          type="text"
                          value={addrLine1}
                          onChange={(e) => setAddrLine1(e.target.value)}
                          placeholder="Flat, House no., Building, Company, Apartment"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="sm:col-span-2 space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Address Line 2 (Optional)</label>
                        <input
                          type="text"
                          value={addrLine2}
                          onChange={(e) => setAddrLine2(e.target.value)}
                          placeholder="Area, Street, Sector, Village"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">City</label>
                        <input
                          type="text"
                          value={addrCity}
                          onChange={(e) => setAddrCity(e.target.value)}
                          placeholder="Bengaluru"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">State</label>
                        <input
                          type="text"
                          value={addrState}
                          onChange={(e) => setAddrState(e.target.value)}
                          placeholder="Karnataka"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Postal Code</label>
                        <input
                          type="text"
                          value={addrPostalCode}
                          onChange={(e) => setAddrPostalCode(e.target.value)}
                          placeholder="560038"
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold"
                          required
                        />
                      </div>
                      <div className="space-y-1.5">
                        <label className="text-[10px] font-bold text-slate uppercase tracking-wider block">Country (2 Letter Code)</label>
                        <input
                          type="text"
                          value={addrCountry}
                          onChange={(e) => setAddrCountry(e.target.value)}
                          maxLength={2}
                          className="bg-white border border-slate-200 focus:border-saffron rounded-xl px-4 py-2 text-xs text-navy outline-none w-full font-semibold uppercase"
                          required
                        />
                      </div>
                    </div>

                    <div className="flex items-center gap-2 py-2">
                      <input
                        type="checkbox"
                        id="defaultAddr"
                        checked={addrIsDefault}
                        onChange={(e) => setAddrIsDefault(e.target.checked)}
                        className="rounded border-slate-300 text-navy focus:ring-saffron w-4 h-4 cursor-pointer"
                      />
                      <label htmlFor="defaultAddr" className="text-xs font-bold text-navy cursor-pointer">Set as default delivery address</label>
                    </div>

                    <div className="flex items-center gap-3">
                      <Button
                        type="submit"
                        disabled={isSavingAddress}
                        className="px-5 py-2 text-xs bg-navy text-saffron hover:bg-navy-light font-display"
                      >
                        {isSavingAddress ? 'Saving...' : 'Save Address'}
                      </Button>
                      <button
                        type="button"
                        onClick={resetAddressForm}
                        className="text-xs font-bold text-slate hover:text-navy"
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                )}

                {/* Addresses List */}
                {isAddressesLoading ? (
                  <div className="flex items-center justify-center py-8">
                    <div className="w-6 h-6 border-2 border-navy border-t-saffron rounded-full animate-spin" />
                  </div>
                ) : addresses.length === 0 ? (
                  <p className="text-sm text-slate py-4">No saved addresses found. Click &quot;Add New Address&quot; above to add one.</p>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {addresses.map((addr) => (
                      <div key={addr.id} className="p-4 rounded-xl border border-slate-100 relative bg-slate-50/20 flex flex-col justify-between">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="bg-navy text-white text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded">
                              {addr.label}
                            </span>
                            {addr.isDefault && (
                              <span className="bg-saffron/20 text-saffron-dark text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded">
                                Default
                              </span>
                            )}
                          </div>
                          <h3 className="font-bold text-navy text-sm mt-2.5">{addr.firstName} {addr.lastName}</h3>
                          <p className="text-xs text-slate-600 mt-1.5 leading-relaxed">
                            {addr.addressLine1}
                            {addr.addressLine2 ? `, ${addr.addressLine2}` : ''}
                            <br />
                            {addr.city}, {addr.state} - {addr.postalCode}
                            <br />
                            {addr.country}
                          </p>
                          <p className="text-xs text-slate-400 font-mono mt-1.5">{addr.phone}</p>
                        </div>
                        <div className="flex items-center justify-end gap-3 mt-4 pt-3 border-t border-slate-100">
                          <button
                            onClick={() => handleEditClick(addr)}
                            className="inline-flex items-center gap-1 text-[10px] font-bold text-slate hover:text-navy"
                          >
                            <Edit2 size={12} /> Edit
                          </button>
                          <button
                            onClick={() => handleDeleteAddress(addr.id)}
                            className="inline-flex items-center gap-1 text-[10px] font-bold text-danger hover:text-danger-dark"
                          >
                            <Trash2 size={12} /> Delete
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeSubTab === 'wishlist' && (
              <div className="space-y-6 animate-[fadeUp_150ms_ease-out]">
                <h2 className="font-display font-bold text-navy text-lg border-b border-slate-50 pb-3">
                  My Wishlist
                </h2>
                <p className="text-sm text-slate-500 text-center py-12">
                  No wishlisted items found. Continue browsing and tap the ❤️ icon to save items.
                </p>
              </div>
            )}
          </div>
        </div>
      </main>

      <Footer />

      <CartDrawer />
      <SearchModal />
      <BottomNav />
    </div>
  )
}
