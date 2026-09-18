import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { immer } from 'zustand/middleware/immer'
import { getSession } from 'next-auth/react'
import {
  addCartItem,
  clearCart as clearCartApi,
  getCart as getCartApi,
  removeCartItem,
  updateCartItem,
  type CartDto,
} from '@/lib/api/cart'

export interface CartItem {
  // Present once this line has been synced to the backend (server-side CartItem id). Guest
  // additions don't have one until login triggers syncCartOnLogin() below.
  id?: string
  variantId: string
  productId: string
  slug: string
  name: string
  image: string
  price: number
  quantity: number
  sku: string
  variantName?: string
}

interface CartStore {
  items: CartItem[]
  isOpen: boolean
  addItem: (item: CartItem) => void
  removeItem: (variantId: string) => void
  updateQty: (variantId: string, qty: number) => void
  clear: () => void
  openCart: () => void
  closeCart: () => void
  total: () => number
  count: () => number
  hydrateFromServer: (cart: CartDto) => void
}

function toCartItem(dto: CartDto['items'][number]): CartItem {
  return {
    id: dto.id,
    variantId: dto.variantId,
    productId: dto.productId,
    slug: dto.productSlug,
    name: dto.productName,
    image: dto.image ?? '',
    price: dto.unitPrice,
    quantity: dto.quantity,
    sku: dto.sku,
    variantName: dto.variantName,
  }
}

/** Only registered users have a server-side cart; guests stay purely local. */
async function isLoggedIn() {
  const session = await getSession()
  // @ts-expect-error -- session isn't typed yet, see roadmap Phase 7 (NextAuth type augmentation)
  return Boolean(session?.accessToken)
}

export const useCart = create<CartStore>()(
  persist(
    immer((set, get) => ({
      items: [],
      isOpen: false,
      addItem: (item) => {
        set((state) => {
          const existing = state.items.find((i) => i.variantId === item.variantId)
          if (existing) {
            existing.quantity += item.quantity
          } else {
            state.items.push(item)
          }
          state.isOpen = true
        })
        isLoggedIn().then((loggedIn) => {
          if (!loggedIn) return
          addCartItem(item.variantId, item.quantity)
            .then((cart) => get().hydrateFromServer(cart))
            .catch((err) => console.error('Failed to sync added item with server cart', err))
        })
      },
      removeItem: (id) => {
        const removedItem = get().items.find((i) => i.variantId === id)
        set((state) => {
          state.items = state.items.filter((i) => i.variantId !== id)
        })
        if (!removedItem?.id) return
        isLoggedIn().then((loggedIn) => {
          if (!loggedIn) return
          removeCartItem(removedItem.id!)
            .then((cart) => get().hydrateFromServer(cart))
            .catch((err) => console.error('Failed to sync removed item with server cart', err))
        })
      },
      updateQty: (id, qty) => {
        const clampedQty = Math.max(1, qty)
        set((state) => {
          const item = state.items.find((i) => i.variantId === id)
          if (item) {
            item.quantity = clampedQty
          }
        })
        const updatedItem = get().items.find((i) => i.variantId === id)
        if (!updatedItem?.id) return
        isLoggedIn().then((loggedIn) => {
          if (!loggedIn) return
          updateCartItem(updatedItem.id!, clampedQty)
            .then((cart) => get().hydrateFromServer(cart))
            .catch((err) => console.error('Failed to sync updated quantity with server cart', err))
        })
      },
      clear: () => {
        set((state) => {
          state.items = []
        })
        isLoggedIn().then((loggedIn) => {
          if (!loggedIn) return
          clearCartApi().catch((err) => console.error('Failed to clear server cart', err))
        })
      },
      openCart: () =>
        set((state) => {
          state.isOpen = true
        }),
      closeCart: () =>
        set((state) => {
          state.isOpen = false
        }),
      total: () => get().items.reduce((s, i) => s + i.price * i.quantity, 0),
      count: () => get().items.reduce((s, i) => s + i.quantity, 0),
      hydrateFromServer: (cart) =>
        set((state) => {
          state.items = cart.items.map(toCartItem)
        }),
    })),
    {
      name: 'shopforge-cart',
    }
  )
)

/**
 * Runs once right after login (see components/cart/CartSync.tsx). Any items added while
 * browsing as a guest get pushed into the now-available server cart, then local state is
 * replaced with the server's authoritative version -- which also correctly merges quantities
 * for anything that was already in the server cart from a previous session.
 */
export async function syncCartOnLogin() {
  const localItems = useCart.getState().items
  try {
    for (const item of localItems) {
      if (!item.id) {
        await addCartItem(item.variantId, item.quantity)
      }
    }
    const serverCart = await getCartApi()
    useCart.getState().hydrateFromServer(serverCart)
  } catch (err) {
    console.error('Failed to sync cart on login', err)
  }
}
