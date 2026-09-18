'use client'

import { useEffect, useRef } from 'react'
import { useSession } from 'next-auth/react'
import { syncCartOnLogin } from '@/store/cartStore'

/**
 * Renders nothing -- it exists only to run syncCartOnLogin() exactly once whenever a session
 * becomes authenticated, merging any guest-cart items into the user's server-side cart. Mounted
 * once at the app root (see Providers.tsx) so it applies everywhere, not per-page.
 */
export function CartSync() {
  const { status } = useSession()
  const hasSynced = useRef(false)

  useEffect(() => {
    if (status !== 'authenticated' || hasSynced.current) return
    hasSynced.current = true
    syncCartOnLogin()
  }, [status])

  return null
}
