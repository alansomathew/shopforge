'use client'

import React, { useState, useMemo } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Navbar } from '@/components/layout/Navbar'
import { Footer } from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { Button } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/Skeleton'
import { getProducts, Product } from '@/lib/api/products'
import {
  getCategoryTree,
  getAllCategories,
  createCategory,
  updateCategory,
  deactivateCategory,
  Category,
} from '@/lib/api/categories'
import { getBrands, createBrand, updateBrand, deactivateBrand, Brand } from '@/lib/api/brands'
import { api } from '@/lib/api/client'
import { useSession } from 'next-auth/react'
import { getAllUsers } from '@/lib/api/users'
import {
  ShieldCheck,
  BarChart3,
  Layers,
  ShoppingBag,
  Plus,
  Edit,
  Package,
  Check,
  X,
  TrendingUp,
  AlertTriangle,
  FolderOpen,
  ArrowUpRight,
  User,
  Activity,
  DollarSign,
  Users,
  Trash2,
  Tag,
  Settings,
  Image as ImageIcon
} from 'lucide-react'
import { formatPrice } from '@/lib/utils'
import toast from 'react-hot-toast'

interface VariantInput {
  sku: string
  name: string
  price: string
  salePrice: string
  stock: string
}

export default function AdminDashboard() {
  const { data: session } = useSession()
  const queryClient = useQueryClient()
  const [adminTab, setAdminTab] = useState<'overview' | 'products' | 'orders' | 'catalog' | 'users'>('overview')
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  
  // Modals state
  const [showAddModal, setShowAddModal] = useState(false)
  const [showEditModal, setShowEditModal] = useState(false)
  const [showStockModal, setShowStockModal] = useState(false)

  // Add Product form state
  const [addForm, setAddForm] = useState({
    name: '',
    brand: '',
    categorySlug: '',
    description: '',
    shortDescription: '',
    basePrice: '',
    salePrice: '',
    status: 'ACTIVE',
    isFeatured: false,
    hasVariants: false,
  })
  const [addVariants, setAddVariants] = useState<VariantInput[]>([
    { sku: '', name: 'Standard', price: '', salePrice: '', stock: '10' }
  ])
  const [addImages, setAddImages] = useState<string[]>([
    'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=600&auto=format&fit=crop'
  ])

  // Edit Product form state
  const [editForm, setEditForm] = useState({
    id: '',
    name: '',
    brand: '',
    categorySlug: '',
    description: '',
    shortDescription: '',
    basePrice: '',
    salePrice: '',
    status: 'ACTIVE',
    isFeatured: false,
    hasVariants: false,
  })
  const [editVariants, setEditVariants] = useState<VariantInput[]>([])
  const [editImages, setEditImages] = useState<string[]>([])

  // Stock Adjustment form state
  const [selectedVariantId, setSelectedVariantId] = useState('')
  const [stockDelta, setStockDelta] = useState('10')

  // Category modal state
  const [showCategoryModal, setShowCategoryModal] = useState(false)
  const [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const [categoryForm, setCategoryForm] = useState({
    name: '',
    parentId: '',
    description: '',
    imageUrl: '',
    sortOrder: '0',
  })
  const [isSavingCategory, setIsSavingCategory] = useState(false)

  // Brand modal state
  const [showBrandModal, setShowBrandModal] = useState(false)
  const [editingBrand, setEditingBrand] = useState<Brand | null>(null)
  const [brandForm, setBrandForm] = useState({ name: '', logoUrl: '', description: '' })
  const [isSavingBrand, setIsSavingBrand] = useState(false)

  // Queries
  const { data: productsData, refetch: refetchProducts, isLoading: isProductsLoading } = useQuery({
    queryKey: ['adminProducts'],
    queryFn: () => getProducts({ size: 100 }),
  })

  const { data: categoriesData } = useQuery({
    queryKey: ['adminCategories'],
    queryFn: getCategoryTree,
  })

  const {
    data: flatCategoriesData,
    refetch: refetchFlatCategories,
    isLoading: isCatalogCategoriesLoading,
  } = useQuery({
    queryKey: ['adminCategoriesFlat'],
    queryFn: getAllCategories,
  })

  const {
    data: brandsData,
    refetch: refetchBrands,
    isLoading: isBrandsLoading,
  } = useQuery({
    queryKey: ['adminBrands'],
    queryFn: getBrands,
  })

  const userRoles = session?.user?.roles || []
  const isAdmin = userRoles.includes('ADMIN')

  const { data: usersList, isLoading: isUsersLoading } = useQuery({
    queryKey: ['adminUsers'],
    queryFn: getAllUsers,
    enabled: isAdmin,
  })

  const productsList = productsData?.content || []

  const checkRole = (roles: string[], target: string) => {
    return (roles || []).some(r => {
      const val = r.toUpperCase()
      return val === target || val === `ROLE_${target}`
    })
  }

  const sellers = useMemo(() => {
    if (!usersList) return []
    return usersList.filter(u => checkRole(u.roles, 'SELLER'))
  }, [usersList])

  const customers = useMemo(() => {
    if (!usersList) return []
    return usersList.filter(u => checkRole(u.roles, 'CUSTOMER') || (!checkRole(u.roles, 'SELLER') && !checkRole(u.roles, 'ADMIN')))
  }, [usersList])

  const sidebarTabs = useMemo(() => {
    const tabs = [
      { id: 'overview', label: 'KPI Overview', icon: BarChart3 },
      { id: 'products', label: 'Products & Quantities', icon: Layers },
      { id: 'orders', label: 'Fulfillment Queue', icon: ShoppingBag }
    ]
    if (isAdmin) {
      tabs.push({ id: 'catalog', label: 'Catalog Settings', icon: Settings })
      tabs.push({ id: 'users', label: 'Users & Sellers', icon: Users })
    }
    return tabs
  }, [isAdmin])

  // Extract flat category list for dropdown selection
  const flatCategories = useMemo(() => {
    if (!categoriesData) return []
    const list: { name: string; slug: string }[] = []
    const traverse = (nodes: any[]) => {
      for (const node of nodes) {
        list.push({ name: node.name, slug: node.slug })
        if (node.children && node.children.length > 0) {
          traverse(node.children)
        }
      }
    }
    traverse(categoriesData)
    return list
  }, [categoriesData])

  // Mock Admin Orders List
  const [adminOrders] = useState([
    { id: 'SF-582914', customer: 'Aravind K.', total: 12499, status: 'DELIVERED', date: '24 Jun 2026' },
    { id: 'SF-104938', customer: 'Neha Sharma', total: 5998, status: 'SHIPPED', date: '25 Jun 2026' },
    { id: 'SF-930485', customer: 'Kabir Dev', total: 3499, status: 'PENDING', date: '25 Jun 2026' }
  ])

  // Dynamic Add State Operations
  const addVariantRow = () => {
    setAddVariants([...addVariants, { sku: '', name: '', price: '', salePrice: '', stock: '10' }])
  }
  const removeVariantRow = (index: number) => {
    if (addVariants.length <= 1) return
    setAddVariants(addVariants.filter((_, idx) => idx !== index))
  }
  const updateAddVariant = (index: number, field: keyof VariantInput, val: string) => {
    setAddVariants(
      addVariants.map((item, idx) => (idx === index ? { ...item, [field]: val } : item))
    )
  }

  const addImageRow = () => {
    setAddImages([...addImages, ''])
  }
  const removeImageRow = (index: number) => {
    if (addImages.length <= 1) return
    setAddImages(addImages.filter((_, idx) => idx !== index))
  }
  const updateAddImage = (index: number, val: string) => {
    setAddImages(
      addImages.map((img, idx) => (idx === index ? val : img))
    )
  }

  // Dynamic Edit State Operations
  const editVariantRow = () => {
    setEditVariants([...editVariants, { sku: '', name: '', price: '', salePrice: '', stock: '10' }])
  }
  const removeEditVariantRow = (index: number) => {
    if (editVariants.length <= 1) return
    setEditVariants(editVariants.filter((_, idx) => idx !== index))
  }
  const updateEditVariant = (index: number, field: keyof VariantInput, val: string) => {
    setEditVariants(
      editVariants.map((item, idx) => (idx === index ? { ...item, [field]: val } : item))
    )
  }

  const editImageRow = () => {
    setEditImages([...editImages, ''])
  }
  const removeEditImageRow = (index: number) => {
    if (editImages.length <= 1) return
    setEditImages(editImages.filter((_, idx) => idx !== index))
  }
  const updateEditImage = (index: number, val: string) => {
    setEditImages(
      editImages.map((img, idx) => (idx === index ? val : img))
    )
  }

  // Handlers
  const handleAddSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!addForm.name || !addForm.categorySlug || !addForm.basePrice) {
      toast.error('Please fill in required fields.')
      return
    }

    try {
      const payload = {
        name: addForm.name,
        brandSlug: addForm.brand.toLowerCase().replace(/\s+/g, '-'),
        categorySlug: addForm.categorySlug,
        description: addForm.description || addForm.name,
        shortDescription: addForm.shortDescription || addForm.name,
        basePrice: Number(addForm.basePrice),
        salePrice: addForm.salePrice ? Number(addForm.salePrice) : null,
        status: addForm.status,
        isFeatured: addForm.isFeatured,
        hasVariants: addForm.hasVariants,
        imageUrls: addImages.filter(img => img.trim() !== ''),
        variants: addVariants.map((v, index) => ({
          sku: v.sku || `${addForm.name.substring(0, 3).toUpperCase()}-${Date.now().toString().slice(-4)}-${index}`,
          name: v.name || 'Standard',
          price: Number(v.price || addForm.basePrice),
          salePrice: v.salePrice ? Number(v.salePrice) : null,
          attributes: { size: v.name },
          stock: Number(v.stock || '0')
        }))
      }

      await api.post('/api/v1/products', payload)
      toast.success('Product created with variants & images!')
      setShowAddModal(false)
      refetchProducts()
      // reset
      setAddForm({
        name: '',
        brand: '',
        categorySlug: '',
        description: '',
        shortDescription: '',
        basePrice: '',
        salePrice: '',
        status: 'ACTIVE',
        isFeatured: false,
        hasVariants: false,
      })
      setAddVariants([{ sku: '', name: 'Standard', price: '', salePrice: '', stock: '10' }])
      setAddImages(['https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=600&auto=format&fit=crop'])
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to create product.')
    }
  }

  const openEditModal = (prod: Product) => {
    setSelectedProduct(prod)
    setEditForm({
      id: prod.id,
      name: prod.name,
      brand: prod.brand || '',
      categorySlug: prod.categorySlug,
      description: prod.description || '',
      shortDescription: prod.shortDescription || '',
      basePrice: String(prod.basePrice),
      salePrice: prod.salePrice ? String(prod.salePrice) : '',
      status: 'ACTIVE',
      isFeatured: prod.badge === 'HOT',
      hasVariants: prod.variants.length > 1,
    })

    setEditVariants(
      prod.variants.map(v => ({
        sku: v.sku,
        name: v.name || 'Standard',
        price: String(v.price),
        salePrice: v.salePrice ? String(v.salePrice) : '',
        stock: String(v.stock || 0)
      }))
    )

    setEditImages(prod.images || [prod.primaryImage])
    setShowEditModal(true)
  }

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editForm.name || !editForm.categorySlug || !editForm.basePrice) {
      toast.error('Please fill in required fields.')
      return
    }

    try {
      const payload = {
        name: editForm.name,
        brandSlug: editForm.brand.toLowerCase().replace(/\s+/g, '-'),
        categorySlug: editForm.categorySlug,
        description: editForm.description,
        shortDescription: editForm.shortDescription,
        basePrice: Number(editForm.basePrice),
        salePrice: editForm.salePrice ? Number(editForm.salePrice) : null,
        status: editForm.status,
        isFeatured: editForm.isFeatured,
        hasVariants: editForm.hasVariants,
        imageUrls: editImages.filter(img => img.trim() !== ''),
        variants: editVariants.map((v, index) => ({
          sku: v.sku || `${editForm.name.substring(0, 3).toUpperCase()}-${Date.now().toString().slice(-4)}-${index}`,
          name: v.name || 'Standard',
          price: Number(v.price || editForm.basePrice),
          salePrice: v.salePrice ? Number(v.salePrice) : null,
          attributes: { size: v.name },
          stock: Number(v.stock || '0')
        }))
      }

      await api.put(`/api/v1/products/${editForm.id}`, payload)
      toast.success('Product and variants updated successfully!')
      setShowEditModal(false)
      refetchProducts()
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to update product.')
    }
  }

  const openStockModal = (prod: Product) => {
    setSelectedProduct(prod)
    if (prod.variants && prod.variants.length > 0) {
      setSelectedVariantId(prod.variants[0].id)
    } else {
      setSelectedVariantId('')
    }
    setStockDelta('10')
    setShowStockModal(true)
  }

  const handleStockSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedVariantId) {
      toast.error('No variant selected.')
      return
    }

    try {
      await api.post(`/api/v1/products/variants/${selectedVariantId}/inventory`, {
        quantityDelta: Number(stockDelta)
      })
      toast.success('Inventory stock updated successfully!')
      setShowStockModal(false)
      refetchProducts()
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to adjust inventory.')
    }
  }

  const handleDeleteProduct = async (id: string, name: string) => {
    if (!window.confirm(`Are you sure you want to delete "${name}"? This action soft deletes the product.`)) {
      return
    }

    try {
      await api.delete(`/api/v1/products/${id}`)
      toast.success('Product deleted successfully!')
      refetchProducts()
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to delete product.')
    }
  }

  // --- Category settings handlers ---
  const resetCategoryForm = () => {
    setEditingCategory(null)
    setCategoryForm({ name: '', parentId: '', description: '', imageUrl: '', sortOrder: '0' })
  }

  const openAddCategoryModal = () => {
    resetCategoryForm()
    setShowCategoryModal(true)
  }

  const openEditCategoryModal = (cat: Category) => {
    setEditingCategory(cat)
    setCategoryForm({
      name: cat.name,
      parentId: cat.parentId || '',
      description: cat.description || '',
      imageUrl: cat.imageUrl || '',
      sortOrder: String(cat.sortOrder),
    })
    setShowCategoryModal(true)
  }

  const handleSaveCategory = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!categoryForm.name.trim()) {
      toast.error('Category name is required.')
      return
    }

    setIsSavingCategory(true)
    const payload = {
      name: categoryForm.name,
      parentId: categoryForm.parentId || null,
      description: categoryForm.description || undefined,
      imageUrl: categoryForm.imageUrl || undefined,
      sortOrder: Number(categoryForm.sortOrder || '0'),
    }

    try {
      if (editingCategory) {
        await updateCategory(editingCategory.id, payload)
        toast.success('Category updated successfully!')
      } else {
        await createCategory(payload)
        toast.success('Category created successfully!')
      }
      setShowCategoryModal(false)
      resetCategoryForm()
      refetchFlatCategories()
      // The product add/edit form's category dropdown reads the public tree query
      // (adminCategories), so it needs refreshing too whenever a category changes.
      queryClient.invalidateQueries({ queryKey: ['adminCategories'] })
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to save category.')
    } finally {
      setIsSavingCategory(false)
    }
  }

  const handleDeactivateCategory = async (cat: Category) => {
    if (!window.confirm(`Deactivate "${cat.name}"? It will be hidden from the storefront until reactivated.`)) {
      return
    }
    try {
      await deactivateCategory(cat.id)
      toast.success('Category deactivated.')
      refetchFlatCategories()
      queryClient.invalidateQueries({ queryKey: ['adminCategories'] })
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to deactivate category.')
    }
  }

  // --- Brand settings handlers ---
  const resetBrandForm = () => {
    setEditingBrand(null)
    setBrandForm({ name: '', logoUrl: '', description: '' })
  }

  const openAddBrandModal = () => {
    resetBrandForm()
    setShowBrandModal(true)
  }

  const openEditBrandModal = (brand: Brand) => {
    setEditingBrand(brand)
    setBrandForm({
      name: brand.name,
      logoUrl: brand.logoUrl || '',
      description: brand.description || '',
    })
    setShowBrandModal(true)
  }

  const handleSaveBrand = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!brandForm.name.trim()) {
      toast.error('Brand name is required.')
      return
    }

    setIsSavingBrand(true)
    const payload = {
      name: brandForm.name,
      logoUrl: brandForm.logoUrl || undefined,
      description: brandForm.description || undefined,
    }

    try {
      if (editingBrand) {
        await updateBrand(editingBrand.id, payload)
        toast.success('Brand updated successfully!')
      } else {
        await createBrand(payload)
        toast.success('Brand created successfully!')
      }
      setShowBrandModal(false)
      resetBrandForm()
      refetchBrands()
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to save brand.')
    } finally {
      setIsSavingBrand(false)
    }
  }

  const handleDeactivateBrand = async (brand: Brand) => {
    if (!window.confirm(`Deactivate "${brand.name}"?`)) {
      return
    }
    try {
      await deactivateBrand(brand.id)
      toast.success('Brand deactivated.')
      refetchBrands()
    } catch (err: any) {
      console.error(err)
      toast.error(err.response?.data?.message || 'Failed to deactivate brand.')
    }
  }

  const lowStockCount = useMemo(() => {
    return productsList.filter(p => p.variants.reduce((acc, v) => acc + (v.stock || 0), 0) <= 5).length
  }, [productsList])

  const roleName = userRoles.includes('ADMIN') ? 'System Admin' : userRoles.includes('SELLER') ? 'Authorized Seller' : 'Store Member'

  return (
    <div className="flex flex-col min-h-screen pb-16 md:pb-0 bg-slate-50/50">
      <Navbar />

      {/* Modern Dashboard Layout Container */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-8">
        
        {/* Cockpit Top Bar */}
        <div className="bg-navy rounded-3xl p-6 sm:p-8 text-white shadow-xl mb-8 flex flex-col md:flex-row justify-between items-start md:items-center gap-6 relative overflow-hidden">
          <div className="absolute right-0 top-0 opacity-10 pointer-events-none transform translate-x-12 -translate-y-12">
            <ShieldCheck size={320} className="text-saffron" />
          </div>
          <div className="z-10 space-y-2">
            <div className="flex items-center gap-2.5">
              <span className="bg-saffron text-navy text-[10px] font-black uppercase tracking-widest px-3 py-1 rounded-full shadow-sm">
                {roleName}
              </span>
              <span className="text-xs text-slate-300 font-semibold flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" /> Live Session
              </span>
            </div>
            <h1 className="font-display font-black text-2xl sm:text-4xl tracking-tight text-white flex items-center gap-2.5">
              Command Dashboard
            </h1>
            <p className="text-slate-300 text-xs sm:text-sm font-medium">
              Manage inventory, add products, adjust variant stocks, and trace order metrics.
            </p>
          </div>

          <div className="z-10 flex flex-wrap gap-3">
            <div className="bg-white/10 backdrop-blur-md rounded-2xl p-4 flex items-center gap-3 border border-white/10">
              <div className="w-10 h-10 rounded-full bg-saffron text-navy flex items-center justify-center font-bold">
                {session?.user?.firstName?.charAt(0) || 'A'}
              </div>
              <div className="text-left">
                <p className="text-xs text-slate-400 font-bold">Logged In As</p>
                <p className="text-sm font-extrabold text-white">{session?.user?.firstName || 'Admin'} {session?.user?.lastName || ''}</p>
              </div>
            </div>
          </div>
        </div>

        {/* Cockpit Workspace Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Dashboard Left Sidebar Menu */}
          <aside className="lg:col-span-3 bg-white border border-slate-100 p-4 rounded-3xl space-y-1.5 shadow-sm">
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider px-4 mb-3">Navigation</p>
            {sidebarTabs.map((tab) => {
              const Icon = tab.icon
              const selected = adminTab === tab.id
              return (
                <button
                  key={tab.id}
                  onClick={() => setAdminTab(tab.id as any)}
                  className={`w-full flex items-center justify-between px-4 py-3 rounded-2xl text-sm font-bold transition-all duration-200 ${
                    selected 
                      ? 'bg-navy text-saffron shadow-md translate-x-1' 
                      : 'text-slate-500 hover:bg-slate-50 hover:text-navy'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <Icon size={16} />
                    <span>{tab.label}</span>
                  </div>
                  {selected && <ArrowUpRight size={14} className="text-saffron" />}
                </button>
              )}
            )}

            <div className="h-[1px] bg-slate-100 my-4" />
            
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider px-4 mb-3">Quick Actions</p>
            <Button 
              variant="primary" 
              className="w-full justify-center gap-1.5 py-3 rounded-2xl text-xs font-bold shadow-md shadow-navy/10 hover:shadow-lg hover:shadow-navy/20 transition-all duration-200" 
              onClick={() => setShowAddModal(true)}
            >
              <Plus size={14} /> Add New Product
            </Button>
          </aside>

          {/* Dashboard Dynamic Panel Content */}
          <div className="lg:col-span-9 space-y-6">
            
            {adminTab === 'overview' && (
              <div className="space-y-8 animate-[fadeUp_180ms_ease-out]">
                
                {/* Metric Indicators Grid */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  {[
                    { title: 'Total Revenue', val: '₹1,58,400', color: 'text-emerald-500', trend: '+14.2%', icon: DollarSign, isUp: true },
                    { title: 'Orders Queue', val: '12', color: 'text-royal', trend: '+8.4%', icon: ShoppingBag, isUp: true },
                    { title: 'Catalog Products', val: String(productsList.length), color: 'text-navy', trend: '+4 new', icon: Layers, isUp: true },
                    { title: 'Critical Stock', val: String(lowStockCount), color: 'text-danger', trend: '-2 items', icon: AlertTriangle, isUp: false }
                  ].map((kpi, idx) => {
                    const Icon = kpi.icon
                    return (
                      <div key={idx} className="bg-white border border-slate-100 p-5 rounded-3xl flex flex-col justify-between h-36 shadow-sm hover:shadow-md transition-shadow relative overflow-hidden">
                        <div className="flex justify-between items-start">
                          <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">{kpi.title}</span>
                          <div className="p-2 bg-slate-50 text-slate rounded-xl">
                            <Icon size={16} />
                          </div>
                        </div>
                        <div>
                          <p className={`font-display font-extrabold text-2xl tracking-tight ${kpi.color}`}>{kpi.val}</p>
                          <span className={`text-[10px] font-bold flex items-center gap-0.5 mt-1 ${
                            kpi.isUp ? 'text-emerald-500' : 'text-danger'
                          }`}>
                            {kpi.trend}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>

                {/* KPI Graphs mockup */}
                <div className="bg-white border border-slate-100 rounded-3xl p-6 shadow-sm space-y-4">
                  <div className="flex justify-between items-center border-b border-slate-50 pb-4">
                    <div>
                      <h3 className="font-display font-bold text-navy text-sm sm:text-base">Store Performance Analytics</h3>
                      <p className="text-xs text-slate-400 font-medium">Real-time GMV value mapping against target thresholds.</p>
                    </div>
                    <span className="bg-emerald-50 text-emerald-600 text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1">
                      <Activity size={10} /> Active
                    </span>
                  </div>

                  <div className="h-48 w-full flex items-end gap-3 pt-4">
                    {[35, 60, 45, 90, 75, 110, 85, 130, 95, 140, 115, 160].map((val, idx) => (
                      <div key={idx} className="flex-1 flex flex-col items-center gap-2 group cursor-pointer h-full justify-end">
                        <div className="bg-slate-50 group-hover:bg-navy-light text-[9px] font-bold text-navy group-hover:text-white px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-all transform -translate-y-1">
                          ₹{val}k
                        </div>
                        <div 
                          style={{ height: `${(val / 160) * 70}%` }} 
                          className={`w-full rounded-t-lg transition-all duration-300 ${
                            idx === 11 ? 'bg-saffron' : 'bg-navy/10 group-hover:bg-navy'
                          }`}
                        />
                        <span className="text-[9px] font-bold text-slate-400 group-hover:text-navy transition-colors">
                          M{idx + 1}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Recent Activity List */}
                <div className="space-y-4">
                  <h3 className="font-display font-bold text-navy text-base px-2">Recent Order Logs</h3>
                  <div className="overflow-x-auto border border-slate-100 rounded-3xl bg-white shadow-sm">
                    <table className="w-full text-sm text-left border-collapse text-navy">
                      <thead>
                        <tr className="bg-slate-50/50 border-b border-slate-100 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                          <th className="p-4">Order ID</th>
                          <th className="p-4">Customer</th>
                          <th className="p-4">Date</th>
                          <th className="p-4">Total</th>
                          <th className="p-4 text-center">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-50">
                        {adminOrders.map((ord) => (
                          <tr key={ord.id} className="hover:bg-slate-50/20 transition-colors">
                            <td className="p-4 font-mono font-bold text-xs">{ord.id}</td>
                            <td className="p-4 font-semibold text-xs text-navy">{ord.customer}</td>
                            <td className="p-4 text-xs text-slate-400 font-medium">{ord.date}</td>
                            <td className="p-4 text-saffron font-bold text-xs">{formatPrice(ord.total)}</td>
                            <td className="p-4 text-center">
                              <span className={`inline-flex px-2.5 py-1 rounded-full text-[10px] font-extrabold ${
                                ord.status === 'DELIVERED' 
                                  ? 'bg-emerald-50 text-emerald-600' 
                                  : 'bg-saffron/15 text-saffron-dark'
                              }`}>
                                {ord.status}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )}

            {adminTab === 'products' && (
              <div className="space-y-6 animate-[fadeUp_180ms_ease-out]">
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-50 pb-4">
                  <div>
                    <h2 className="font-display font-bold text-navy text-lg">Product Catalog</h2>
                    <p className="text-xs text-slate-400 font-medium">Create products, update details, and view current variant stock metrics.</p>
                  </div>
                  <span className="bg-navy text-saffron text-[10px] font-black uppercase tracking-widest px-3 py-1 rounded-full">
                    {productsList.length} items active
                  </span>
                </div>
                
                {isProductsLoading ? (
                  <div className="space-y-4">
                    {Array.from({ length: 4 }).map((_, idx) => (
                      <Skeleton key={idx} className="h-20 w-full rounded-2xl" />
                    ))}
                  </div>
                ) : productsList.length === 0 ? (
                  <div className="bg-white border border-slate-100 rounded-3xl p-12 text-center shadow-sm">
                    <FolderOpen size={48} className="text-slate-300 mx-auto mb-4" />
                    <h3 className="font-display font-bold text-navy text-base">No active products</h3>
                    <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
                      Your store catalog is empty. Click the action button on the sidebar to add your first product.
                    </p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-4">
                    {productsList.map((prod) => {
                      const totalStock = prod.variants.reduce((acc, v) => acc + (v.stock || 0), 0)
                      const isLowStock = totalStock <= 5
                      return (
                        <div key={prod.id} className="bg-white border border-slate-100 hover:border-slate-200 p-4 rounded-3xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 shadow-sm hover:shadow-md transition-all duration-200">
                          <div className="flex items-center gap-4">
                            {prod.primaryImage ? (
                              <img src={prod.primaryImage} alt={prod.name} className="w-14 h-14 object-cover rounded-2xl bg-slate-50 border border-slate-100" />
                            ) : (
                              <div className="w-14 h-14 rounded-2xl bg-slate-100 flex items-center justify-center text-slate-400">
                                <Package size={20} />
                              </div>
                            )}
                            <div>
                              <div className="flex items-center gap-2">
                                <h4 className="font-display font-bold text-navy text-sm sm:text-base">{prod.name}</h4>
                                {prod.badge && (
                                  <span className="bg-saffron text-navy font-black text-[8px] uppercase px-2 py-0.5 rounded">
                                    {prod.badge}
                                  </span>
                                )}
                              </div>
                              <p className="text-xs text-slate-400 font-bold">{prod.brand} • <span className="text-navy">{prod.category}</span></p>
                            </div>
                          </div>

                          <div className="flex items-center justify-between sm:justify-end gap-6 w-full sm:w-auto border-t sm:border-t-0 pt-3 sm:pt-0 border-slate-50">
                            <div className="text-left sm:text-right">
                              <p className="text-xs text-slate-400 font-bold uppercase tracking-wider">Pricing</p>
                              <p className="text-saffron font-display font-extrabold text-sm sm:text-base">{formatPrice(prod.salePrice || prod.basePrice)}</p>
                            </div>
                            
                            <div className="text-left sm:text-right">
                              <p className="text-xs text-slate-400 font-bold uppercase tracking-wider">Inventory</p>
                              <span className={`inline-flex px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase ${
                                isLowStock ? 'bg-red-50 text-danger animate-pulse' : 'bg-emerald-50 text-emerald-600'
                              }`}>
                                {totalStock} units
                              </span>
                            </div>

                            <div className="flex items-center gap-2">
                              <button 
                                onClick={() => openEditModal(prod)}
                                title="Edit Product Info"
                                className="bg-slate-50 hover:bg-navy hover:text-white text-slate-500 p-2.5 rounded-2xl transition-all"
                              >
                                <Edit size={14} />
                              </button>
                              <button 
                                onClick={() => openStockModal(prod)}
                                title="Adjust Stock levels"
                                className="bg-saffron/10 hover:bg-saffron text-saffron-dark hover:text-navy p-2.5 rounded-2xl transition-all"
                              >
                                <Package size={14} />
                              </button>
                              <button 
                                onClick={() => handleDeleteProduct(prod.id, prod.name)}
                                title="Soft Delete Product"
                                className="bg-red-50 hover:bg-danger hover:text-white text-danger p-2.5 rounded-2xl transition-all"
                              >
                                <Trash2 size={14} />
                              </button>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}

            {adminTab === 'orders' && (
              <div className="space-y-6 animate-[fadeUp_180ms_ease-out]">
                <div className="border-b border-slate-50 pb-4">
                  <h2 className="font-display font-bold text-navy text-lg">Fulfillment Queue</h2>
                  <p className="text-xs text-slate-400 font-medium">Verify pending buyer orders and initiate shipping routines.</p>
                </div>
                
                <div className="overflow-x-auto border border-slate-100 rounded-3xl bg-white shadow-sm">
                  <table className="w-full text-sm text-left border-collapse text-navy">
                    <thead>
                      <tr className="bg-slate-50/50 border-b border-slate-100 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                        <th className="p-4">Order ID</th>
                        <th className="p-4">Customer</th>
                        <th className="p-4">Date</th>
                        <th className="p-4">Total</th>
                        <th className="p-4 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-50">
                      {adminOrders.map((ord) => (
                        <tr key={ord.id} className="hover:bg-slate-50/20 transition-colors">
                          <td className="p-4 font-mono font-bold text-xs">{ord.id}</td>
                          <td className="p-4 text-xs font-semibold text-navy">{ord.customer}</td>
                          <td className="p-4 text-xs text-slate-400 font-medium">{ord.date}</td>
                          <td className="p-4 text-saffron font-bold text-xs">{formatPrice(ord.total)}</td>
                          <td className="p-4 text-center">
                            <span className="bg-slate-100 text-navy text-[10px] font-black uppercase tracking-widest px-3 py-1 rounded-full">
                              {ord.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {adminTab === 'catalog' && isAdmin && (
              <div className="space-y-8 animate-[fadeUp_180ms_ease-out]">
                {/* Categories */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-50 pb-3">
                    <div>
                      <h2 className="font-display font-bold text-navy text-lg flex items-center gap-2">
                        <FolderOpen size={18} className="text-saffron" /> Categories
                      </h2>
                      <p className="text-xs text-slate-400 font-medium">Add, edit, or deactivate storefront categories.</p>
                    </div>
                    <Button variant="primary" className="gap-1.5 text-xs py-2 px-4" onClick={openAddCategoryModal}>
                      <Plus size={14} /> Add Category
                    </Button>
                  </div>

                  {isCatalogCategoriesLoading ? (
                    <div className="space-y-3">
                      {Array.from({ length: 3 }).map((_, idx) => (
                        <Skeleton key={idx} className="h-14 w-full rounded-2xl" />
                      ))}
                    </div>
                  ) : !flatCategoriesData || flatCategoriesData.length === 0 ? (
                    <div className="bg-white border border-slate-100 rounded-3xl p-8 text-center text-xs text-slate-400">
                      No categories yet.
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {flatCategoriesData.map((cat) => {
                        const parent = flatCategoriesData.find((c) => c.id === cat.parentId)
                        return (
                          <div
                            key={cat.id}
                            className="bg-white border border-slate-100 rounded-2xl p-4 flex items-center justify-between gap-4"
                          >
                            <div>
                              <div className="flex items-center gap-2">
                                <h4 className="font-bold text-navy text-sm">{cat.name}</h4>
                                {!cat.isActive && (
                                  <span className="bg-slate-100 text-slate-500 text-[9px] font-black uppercase px-2 py-0.5 rounded-full">
                                    Inactive
                                  </span>
                                )}
                              </div>
                              <p className="text-[11px] text-slate-400 font-mono mt-0.5">
                                /{cat.slug}
                                {parent ? ` -- under ${parent.name}` : ''}
                              </p>
                            </div>
                            <div className="flex items-center gap-2 flex-shrink-0">
                              <button
                                onClick={() => openEditCategoryModal(cat)}
                                className="bg-slate-50 hover:bg-navy hover:text-white text-slate-500 p-2 rounded-xl transition-all"
                              >
                                <Edit size={13} />
                              </button>
                              {cat.isActive && (
                                <button
                                  onClick={() => handleDeactivateCategory(cat)}
                                  className="bg-red-50 hover:bg-danger hover:text-white text-danger p-2 rounded-xl transition-all"
                                >
                                  <Trash2 size={13} />
                                </button>
                              )}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>

                {/* Brands */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-50 pb-3">
                    <div>
                      <h2 className="font-display font-bold text-navy text-lg flex items-center gap-2">
                        <Tag size={18} className="text-saffron" /> Brands
                      </h2>
                      <p className="text-xs text-slate-400 font-medium">Add, edit, or deactivate brands.</p>
                    </div>
                    <Button variant="primary" className="gap-1.5 text-xs py-2 px-4" onClick={openAddBrandModal}>
                      <Plus size={14} /> Add Brand
                    </Button>
                  </div>

                  {isBrandsLoading ? (
                    <div className="space-y-3">
                      {Array.from({ length: 3 }).map((_, idx) => (
                        <Skeleton key={idx} className="h-14 w-full rounded-2xl" />
                      ))}
                    </div>
                  ) : !brandsData || brandsData.length === 0 ? (
                    <div className="bg-white border border-slate-100 rounded-3xl p-8 text-center text-xs text-slate-400">
                      No brands yet.
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {brandsData.map((brand) => (
                        <div
                          key={brand.id}
                          className="bg-white border border-slate-100 rounded-2xl p-4 flex items-center justify-between gap-4"
                        >
                          <div>
                            <div className="flex items-center gap-2">
                              <h4 className="font-bold text-navy text-sm">{brand.name}</h4>
                              {!brand.isActive && (
                                <span className="bg-slate-100 text-slate-500 text-[9px] font-black uppercase px-2 py-0.5 rounded-full">
                                  Inactive
                                </span>
                              )}
                            </div>
                            <p className="text-[11px] text-slate-400 font-mono mt-0.5">/{brand.slug}</p>
                          </div>
                          <div className="flex items-center gap-2 flex-shrink-0">
                            <button
                              onClick={() => openEditBrandModal(brand)}
                              className="bg-slate-50 hover:bg-navy hover:text-white text-slate-500 p-2 rounded-xl transition-all"
                            >
                              <Edit size={13} />
                            </button>
                            {brand.isActive && (
                              <button
                                onClick={() => handleDeactivateBrand(brand)}
                                className="bg-red-50 hover:bg-danger hover:text-white text-danger p-2 rounded-xl transition-all"
                              >
                                <Trash2 size={13} />
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {adminTab === 'users' && isAdmin && (
              <div className="space-y-8 animate-[fadeUp_180ms_ease-out]">
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-50 pb-4">
                  <div>
                    <h2 className="font-display font-bold text-navy text-lg">System Members</h2>
                    <p className="text-xs text-slate-400 font-medium">Verify credentials, roles, and status of registered sellers and customers.</p>
                  </div>
                  <span className="bg-navy text-saffron text-[10px] font-black uppercase tracking-widest px-3 py-1 rounded-full">
                    {(usersList || []).length} registered
                  </span>
                </div>

                {isUsersLoading ? (
                  <div className="space-y-4">
                    {Array.from({ length: 4 }).map((_, idx) => (
                      <Skeleton key={idx} className="h-16 w-full rounded-2xl" />
                    ))}
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                    {/* Sellers Column */}
                    <div className="space-y-4">
                      <div className="flex justify-between items-center bg-slate-50/50 p-3 rounded-2xl">
                        <span className="text-xs font-black text-navy uppercase tracking-wider">Authorized Sellers</span>
                        <span className="bg-saffron text-navy text-[10px] font-black px-2 py-0.5 rounded-full">{sellers.length}</span>
                      </div>
                      
                      <div className="space-y-3 max-h-[400px] overflow-y-auto pr-1">
                        {sellers.length === 0 ? (
                          <div className="text-center py-8 text-xs text-slate-400 font-medium">No registered sellers.</div>
                        ) : (
                          sellers.map(sel => (
                            <div key={sel.id} className="bg-white border border-slate-100 p-4 rounded-2xl flex items-center justify-between gap-3 shadow-sm hover:shadow-md transition-shadow">
                              <div className="flex items-center gap-3">
                                <div className="w-9 h-9 rounded-full bg-slate-100 flex items-center justify-center text-slate font-bold uppercase text-xs">
                                  {sel.firstName.charAt(0)}
                                </div>
                                <div>
                                  <h4 className="text-xs font-bold text-navy">{sel.firstName} {sel.lastName || ''}</h4>
                                  <p className="text-[10px] text-slate-400 font-medium">{sel.email}</p>
                                </div>
                              </div>
                              <span className="bg-emerald-50 text-emerald-600 text-[9px] font-black px-2 py-0.5 rounded-full uppercase">Active</span>
                            </div>
                          ))
                        )}
                      </div>
                    </div>

                    {/* Customers Column */}
                    <div className="space-y-4">
                      <div className="flex justify-between items-center bg-slate-50/50 p-3 rounded-2xl">
                        <span className="text-xs font-black text-navy uppercase tracking-wider">Store Customers</span>
                        <span className="bg-navy text-white text-[10px] font-black px-2 py-0.5 rounded-full">{customers.length}</span>
                      </div>
                      
                      <div className="space-y-3 max-h-[400px] overflow-y-auto pr-1">
                        {customers.length === 0 ? (
                          <div className="text-center py-8 text-xs text-slate-400 font-medium">No customer accounts.</div>
                        ) : (
                          customers.map(cust => (
                            <div key={cust.id} className="bg-white border border-slate-100 p-4 rounded-2xl flex items-center justify-between gap-3 shadow-sm hover:shadow-md transition-shadow">
                              <div className="flex items-center gap-3">
                                <div className="w-9 h-9 rounded-full bg-slate-100 flex items-center justify-center text-slate font-bold uppercase text-xs">
                                  {cust.firstName.charAt(0)}
                                </div>
                                <div>
                                  <h4 className="text-xs font-bold text-navy">{cust.firstName} {cust.lastName || ''}</h4>
                                  <p className="text-[10px] text-slate-400 font-medium">{cust.email}</p>
                                </div>
                              </div>
                              <span className="bg-sky-50 text-sky-600 text-[9px] font-black px-2 py-0.5 rounded-full uppercase">Customer</span>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

          </div>

        </div>
      </main>

      {/* Add Product Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 overflow-hidden flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-navy/60 backdrop-blur-sm" onClick={() => setShowAddModal(false)} />
          <div className="relative bg-white w-full max-w-2xl rounded-3xl shadow-2xl p-6 border border-slate-100 animate-[scaleUp_200ms_ease-out] max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-4 mb-5">
              <div>
                <h2 className="font-display font-black text-navy text-xl">Create New Product</h2>
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mt-0.5">Flipkart & Amazon Catalog Add Form</p>
              </div>
              <button onClick={() => setShowAddModal(false)} className="text-slate hover:text-navy p-1.5 rounded-full hover:bg-slate-50 transition-colors">
                <X size={18} />
              </button>
            </div>
            
            <form onSubmit={handleAddSubmit} className="space-y-6">
              
              {/* Product Info Section */}
              <div className="bg-slate-50/50 p-4 rounded-2xl space-y-4">
                <h3 className="text-xs font-black text-navy uppercase tracking-wider">1. General Product Details</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Product Name *</label>
                    <input
                      type="text"
                      placeholder="e.g. Premium Leather Duffle Bag"
                      value={addForm.name}
                      onChange={(e) => setAddForm(prev => ({ ...prev, name: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Brand Name</label>
                    <input
                      type="text"
                      placeholder="e.g. Myntra Crafts"
                      value={addForm.brand}
                      onChange={(e) => setAddForm(prev => ({ ...prev, brand: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-4">
                  <div className="space-y-1.5 col-span-1">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Category *</label>
                    <select
                      value={addForm.categorySlug}
                      onChange={(e) => setAddForm(prev => ({ ...prev, categorySlug: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron cursor-pointer"
                      required
                    >
                      <option value="">Select Category</option>
                      {flatCategories.map(cat => (
                        <option key={cat.slug} value={cat.slug}>{cat.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Base Price *</label>
                    <input
                      type="number"
                      placeholder="Base price"
                      value={addForm.basePrice}
                      onChange={(e) => setAddForm(prev => ({ ...prev, basePrice: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Sale Price</label>
                    <input
                      type="number"
                      placeholder="Discount price"
                      value={addForm.salePrice}
                      onChange={(e) => setAddForm(prev => ({ ...prev, salePrice: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Short Pitch Description</label>
                  <input
                    type="text"
                    placeholder="Short description..."
                    value={addForm.shortDescription}
                    onChange={(e) => setAddForm(prev => ({ ...prev, shortDescription: e.target.value }))}
                    className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Full Catalog Description</label>
                  <textarea
                    rows={2}
                    placeholder="Complete specifications..."
                    value={addForm.description}
                    onChange={(e) => setAddForm(prev => ({ ...prev, description: e.target.value }))}
                    className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all resize-none"
                  />
                </div>
              </div>

              {/* Multiple Images Array Section */}
              <div className="bg-slate-50/50 p-4 rounded-2xl space-y-3">
                <div className="flex justify-between items-center">
                  <h3 className="text-xs font-black text-navy uppercase tracking-wider flex items-center gap-1.5"><ImageIcon size={14} /> 2. Product Images</h3>
                  <button type="button" onClick={addImageRow} className="text-[10px] font-black text-saffron bg-navy px-3 py-1 rounded-lg">
                    + Add Image
                  </button>
                </div>

                <div className="space-y-2">
                  {addImages.map((img, idx) => (
                    <div key={idx} className="flex gap-2">
                      <input
                        type="text"
                        placeholder="Paste image link URL..."
                        value={img}
                        onChange={(e) => updateAddImage(idx, e.target.value)}
                        className="bg-white border border-slate-200 rounded-xl px-4 py-2 text-xs flex-1 outline-none focus:border-saffron"
                      />
                      <button 
                        type="button" 
                        onClick={() => removeImageRow(idx)}
                        disabled={addImages.length <= 1}
                        className="text-danger p-2 bg-white border border-slate-200 hover:bg-red-50 rounded-xl disabled:opacity-30"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              {/* Multiple Variants Array Section */}
              <div className="bg-slate-50/50 p-4 rounded-2xl space-y-3">
                <div className="flex justify-between items-center">
                  <h3 className="text-xs font-black text-navy uppercase tracking-wider flex items-center gap-1.5"><Package size={14} /> 3. Multiple Variants</h3>
                  <button type="button" onClick={addVariantRow} className="text-[10px] font-black text-saffron bg-navy px-3 py-1 rounded-lg">
                    + Add Variant
                  </button>
                </div>

                <div className="space-y-3 max-h-[220px] overflow-y-auto pr-1">
                  {addVariants.map((v, idx) => (
                    <div key={idx} className="bg-white border border-slate-100 p-3 rounded-xl space-y-2 relative">
                      <div className="absolute right-2 top-2">
                        <button 
                          type="button" 
                          onClick={() => removeVariantRow(idx)}
                          disabled={addVariants.length <= 1}
                          className="text-danger hover:bg-red-50 p-1.5 rounded-lg disabled:opacity-30"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>

                      <div className="grid grid-cols-4 gap-2 pt-4 sm:pt-0">
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">SKU</label>
                          <input
                            type="text"
                            placeholder="Auto SKU"
                            value={v.sku}
                            onChange={(e) => updateAddVariant(idx, 'sku', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">Size/Label</label>
                          <input
                            type="text"
                            placeholder="e.g. Size: M"
                            value={v.name}
                            onChange={(e) => updateAddVariant(idx, 'name', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">Price (INR)</label>
                          <input
                            type="number"
                            placeholder="e.g. 2999"
                            value={v.price}
                            onChange={(e) => updateAddVariant(idx, 'price', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">Initial Stock</label>
                          <input
                            type="number"
                            placeholder="10"
                            value={v.stock}
                            onChange={(e) => updateAddVariant(idx, 'stock', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Submit Buttons */}
              <div className="flex gap-3 justify-end pt-4 border-t border-slate-100">
                <button type="button" onClick={() => setShowAddModal(false)} className="border border-slate-200 text-navy text-xs font-bold px-5 py-2.5 rounded-xl hover:bg-slate-50 transition-colors">Cancel</button>
                <button type="submit" className="bg-navy text-white text-xs font-bold px-5 py-2.5 rounded-xl hover:bg-navy-light flex items-center gap-1.5 transition-all shadow-md shadow-navy/10"><Check size={14} /> Save Product</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Product Modal */}
      {showEditModal && (
        <div className="fixed inset-0 z-50 overflow-hidden flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-navy/60 backdrop-blur-sm" onClick={() => setShowEditModal(false)} />
          <div className="relative bg-white w-full max-w-2xl rounded-3xl shadow-2xl p-6 sm:p-8 border border-slate-100 animate-[scaleUp_200ms_ease-out] max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-4 mb-5">
              <div>
                <h2 className="font-display font-black text-navy text-xl">Update Catalog Entry</h2>
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mt-0.5">Edit form with Variants & Images</p>
              </div>
              <button onClick={() => setShowEditModal(false)} className="text-slate hover:text-navy p-1.5 rounded-full hover:bg-slate-50 transition-colors">
                <X size={18} />
              </button>
            </div>
            
            <form onSubmit={handleEditSubmit} className="space-y-6">
              
              {/* Product Info */}
              <div className="bg-slate-50/50 p-4 rounded-2xl space-y-4">
                <h3 className="text-xs font-black text-navy uppercase tracking-wider">1. General Product Details</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Product Name *</label>
                    <input
                      type="text"
                      value={editForm.name}
                      onChange={(e) => setEditForm(prev => ({ ...prev, name: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Brand Name</label>
                    <input
                      type="text"
                      value={editForm.brand}
                      onChange={(e) => setEditForm(prev => ({ ...prev, brand: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-4">
                  <div className="space-y-1.5 col-span-1">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Category *</label>
                    <select
                      value={editForm.categorySlug}
                      onChange={(e) => setEditForm(prev => ({ ...prev, categorySlug: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron cursor-pointer"
                      required
                    >
                      <option value="">Select Category</option>
                      {flatCategories.map(cat => (
                        <option key={cat.slug} value={cat.slug}>{cat.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Base Price *</label>
                    <input
                      type="number"
                      value={editForm.basePrice}
                      onChange={(e) => setEditForm(prev => ({ ...prev, basePrice: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Sale Price</label>
                    <input
                      type="number"
                      value={editForm.salePrice}
                      onChange={(e) => setEditForm(prev => ({ ...prev, salePrice: e.target.value }))}
                      className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Short Description</label>
                  <input
                    type="text"
                    value={editForm.shortDescription}
                    onChange={(e) => setEditForm(prev => ({ ...prev, shortDescription: e.target.value }))}
                    className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Full Description</label>
                  <textarea
                    rows={2}
                    value={editForm.description}
                    onChange={(e) => setEditForm(prev => ({ ...prev, description: e.target.value }))}
                    className="bg-white border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all resize-none"
                  />
                </div>
              </div>

              {/* Dynamic Images */}
              <div className="bg-slate-50/50 p-4 rounded-2xl space-y-3">
                <div className="flex justify-between items-center">
                  <h3 className="text-xs font-black text-navy uppercase tracking-wider flex items-center gap-1.5"><ImageIcon size={14} /> 2. Product Images</h3>
                  <button type="button" onClick={editImageRow} className="text-[10px] font-black text-saffron bg-navy px-3 py-1 rounded-lg">
                    + Add Image
                  </button>
                </div>

                <div className="space-y-2">
                  {editImages.map((img, idx) => (
                    <div key={idx} className="flex gap-2">
                      <input
                        type="text"
                        placeholder="Image URL link..."
                        value={img}
                        onChange={(e) => updateEditImage(idx, e.target.value)}
                        className="bg-white border border-slate-200 rounded-xl px-4 py-2 text-xs flex-1 outline-none focus:border-saffron"
                      />
                      <button 
                        type="button" 
                        onClick={() => removeEditImageRow(idx)}
                        disabled={editImages.length <= 1}
                        className="text-danger p-2 bg-white border border-slate-200 hover:bg-red-50 rounded-xl disabled:opacity-30"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              {/* Dynamic Variants */}
              <div className="bg-slate-50/50 p-4 rounded-2xl space-y-3">
                <div className="flex justify-between items-center">
                  <h3 className="text-xs font-black text-navy uppercase tracking-wider flex items-center gap-1.5"><Package size={14} /> 3. Multiple Variants</h3>
                  <button type="button" onClick={editVariantRow} className="text-[10px] font-black text-saffron bg-navy px-3 py-1 rounded-lg">
                    + Add Variant
                  </button>
                </div>

                <div className="space-y-3 max-h-[220px] overflow-y-auto pr-1">
                  {editVariants.map((v, idx) => (
                    <div key={idx} className="bg-white border border-slate-100 p-3 rounded-xl space-y-2 relative">
                      <div className="absolute right-2 top-2">
                        <button 
                          type="button" 
                          onClick={() => removeEditVariantRow(idx)}
                          disabled={editVariants.length <= 1}
                          className="text-danger hover:bg-red-50 p-1.5 rounded-lg disabled:opacity-30"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>

                      <div className="grid grid-cols-4 gap-2 pt-4 sm:pt-0">
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">SKU</label>
                          <input
                            type="text"
                            placeholder="Auto SKU"
                            value={v.sku}
                            onChange={(e) => updateEditVariant(idx, 'sku', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">Size/Label</label>
                          <input
                            type="text"
                            placeholder="e.g. Size: M"
                            value={v.name}
                            onChange={(e) => updateEditVariant(idx, 'name', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">Price (INR)</label>
                          <input
                            type="number"
                            placeholder="e.g. 2999"
                            value={v.price}
                            onChange={(e) => updateEditVariant(idx, 'price', e.target.value)}
                            className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none focus:border-saffron"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[9px] font-black text-slate-400 uppercase block">Stock</label>
                          <input
                            type="number"
                            placeholder="Units"
                            value={v.stock}
                            disabled={true} // stock updates are made separately via stock adjustment modal to log ledgers!
                            className="bg-slate-100 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[11px] w-full outline-none cursor-not-allowed text-slate-400 font-bold"
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Submit Buttons */}
              <div className="flex gap-3 justify-end pt-4 border-t border-slate-100">
                <button type="button" onClick={() => setShowEditModal(false)} className="border border-slate-200 text-navy text-xs font-bold px-5 py-2.5 rounded-xl hover:bg-slate-50 transition-colors">Cancel</button>
                <button type="submit" className="bg-navy text-white text-xs font-bold px-5 py-2.5 rounded-xl hover:bg-navy-light flex items-center gap-1.5 transition-all shadow-md shadow-navy/10"><Check size={14} /> Update Product</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Stock Adjustment Modal */}
      {showStockModal && selectedProduct && (
        <div className="fixed inset-0 z-50 overflow-hidden flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-navy/60 backdrop-blur-sm" onClick={() => setShowStockModal(false)} />
          <div className="relative bg-white w-full max-w-sm rounded-3xl shadow-2xl p-6 border border-slate-100 animate-[scaleUp_200ms_ease-out]">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
              <div>
                <h2 className="font-display font-black text-navy text-base">Adjust Stock Level</h2>
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mt-0.5">{selectedProduct.name}</p>
              </div>
              <button onClick={() => setShowStockModal(false)} className="text-slate hover:text-navy p-1 rounded-full hover:bg-slate-50"><X size={18} /></button>
            </div>
            
            <form onSubmit={handleStockSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-bold text-slate uppercase tracking-wider block">Select Variant</label>
                <select
                  value={selectedVariantId}
                  onChange={(e) => setSelectedVariantId(e.target.value)}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron cursor-pointer"
                  required
                >
                  {selectedProduct.variants.map(v => (
                    <option key={v.id} value={v.id}>
                      {v.name || 'Standard'} (SKU: {v.sku}) — Current Stock: {v.stock}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-slate uppercase tracking-wider block">Quantity Delta *</label>
                <input
                  type="number"
                  placeholder="e.g. 10 to add, -5 to subtract"
                  value={stockDelta}
                  onChange={(e) => setStockDelta(e.target.value)}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron focus:ring-1 focus:ring-saffron transition-all"
                  required
                />
                <span className="text-[10px] text-slate-400 font-medium block leading-snug">
                  Input positive numbers to increase stock, or negative numbers to decrease stock.
                </span>
              </div>

              <div className="flex gap-3 justify-end pt-4 border-t border-slate-50 mt-2">
                <button type="button" onClick={() => setShowStockModal(false)} className="border border-slate-200 text-navy text-xs font-bold px-4 py-2 rounded-xl hover:bg-slate-50 transition-colors">Cancel</button>
                <button type="submit" className="bg-navy text-white text-xs font-bold px-4 py-2 rounded-xl hover:bg-navy-light flex items-center gap-1.5 transition-all shadow-md shadow-navy/10"><Check size={14} /> Apply Delta</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add/Edit Category Modal */}
      {showCategoryModal && (
        <div className="fixed inset-0 z-50 overflow-hidden flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-navy/60 backdrop-blur-sm" onClick={() => setShowCategoryModal(false)} />
          <div className="relative bg-white w-full max-w-md rounded-3xl shadow-2xl p-6 border border-slate-100 animate-[scaleUp_200ms_ease-out]">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
              <h2 className="font-display font-black text-navy text-base">
                {editingCategory ? 'Edit Category' : 'Add Category'}
              </h2>
              <button onClick={() => setShowCategoryModal(false)} className="text-slate hover:text-navy p-1 rounded-full hover:bg-slate-50">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveCategory} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Name *</label>
                <input
                  type="text"
                  value={categoryForm.name}
                  onChange={(e) => setCategoryForm((prev) => ({ ...prev, name: e.target.value }))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Parent Category</label>
                <select
                  value={categoryForm.parentId}
                  onChange={(e) => setCategoryForm((prev) => ({ ...prev, parentId: e.target.value }))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron cursor-pointer"
                >
                  <option value="">None (root category)</option>
                  {(flatCategoriesData || [])
                    .filter((c) => c.id !== editingCategory?.id)
                    .map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Description</label>
                <textarea
                  rows={2}
                  value={categoryForm.description}
                  onChange={(e) => setCategoryForm((prev) => ({ ...prev, description: e.target.value }))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Image URL</label>
                  <input
                    type="text"
                    value={categoryForm.imageUrl}
                    onChange={(e) => setCategoryForm((prev) => ({ ...prev, imageUrl: e.target.value }))}
                    className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Sort Order</label>
                  <input
                    type="number"
                    value={categoryForm.sortOrder}
                    onChange={(e) => setCategoryForm((prev) => ({ ...prev, sortOrder: e.target.value }))}
                    className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron"
                  />
                </div>
              </div>

              <div className="flex gap-3 justify-end pt-4 border-t border-slate-50">
                <button type="button" onClick={() => setShowCategoryModal(false)} className="border border-slate-200 text-navy text-xs font-bold px-4 py-2 rounded-xl hover:bg-slate-50 transition-colors">
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSavingCategory}
                  className="bg-navy text-white text-xs font-bold px-4 py-2 rounded-xl hover:bg-navy-light flex items-center gap-1.5 transition-all shadow-md shadow-navy/10 disabled:opacity-60"
                >
                  <Check size={14} /> {isSavingCategory ? 'Saving...' : 'Save Category'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add/Edit Brand Modal */}
      {showBrandModal && (
        <div className="fixed inset-0 z-50 overflow-hidden flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-navy/60 backdrop-blur-sm" onClick={() => setShowBrandModal(false)} />
          <div className="relative bg-white w-full max-w-md rounded-3xl shadow-2xl p-6 border border-slate-100 animate-[scaleUp_200ms_ease-out]">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
              <h2 className="font-display font-black text-navy text-base">
                {editingBrand ? 'Edit Brand' : 'Add Brand'}
              </h2>
              <button onClick={() => setShowBrandModal(false)} className="text-slate hover:text-navy p-1 rounded-full hover:bg-slate-50">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveBrand} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Name *</label>
                <input
                  type="text"
                  value={brandForm.name}
                  onChange={(e) => setBrandForm((prev) => ({ ...prev, name: e.target.value }))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Logo URL</label>
                <input
                  type="text"
                  value={brandForm.logoUrl}
                  onChange={(e) => setBrandForm((prev) => ({ ...prev, logoUrl: e.target.value }))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-wider block">Description</label>
                <textarea
                  rows={2}
                  value={brandForm.description}
                  onChange={(e) => setBrandForm((prev) => ({ ...prev, description: e.target.value }))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs w-full outline-none focus:border-saffron resize-none"
                />
              </div>

              <div className="flex gap-3 justify-end pt-4 border-t border-slate-50">
                <button type="button" onClick={() => setShowBrandModal(false)} className="border border-slate-200 text-navy text-xs font-bold px-4 py-2 rounded-xl hover:bg-slate-50 transition-colors">
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSavingBrand}
                  className="bg-navy text-white text-xs font-bold px-4 py-2 rounded-xl hover:bg-navy-light flex items-center gap-1.5 transition-all shadow-md shadow-navy/10 disabled:opacity-60"
                >
                  <Check size={14} /> {isSavingBrand ? 'Saving...' : 'Save Brand'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <Footer />
      <BottomNav />
    </div>
  )
}
