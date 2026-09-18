'use client'

import React from 'react'
import Link from 'next/link'
import { Search, ShoppingBag, User, LogOut, ShieldAlert, Heart } from 'lucide-react'
import { useCart } from '@/store/cartStore'
import { useUiStore } from '@/store/uiStore'
import { useSession, signOut } from 'next-auth/react'

export const Navbar: React.FC = () => {
  const { data: session } = useSession()
  const { count, openCart } = useCart()
  const { openSearch } = useUiStore()

  // @ts-ignore
  const user = session?.user
  // Real ADMIN role only -- see middleware.ts for why the old email-substring fallback
  // ("adminfan99@gmail.com" would have matched it) was removed rather than kept here too.
  // @ts-ignore
  const isAdmin = user?.roles?.includes('ADMIN')

  return (
    <header className="sticky top-0 z-40 bg-navy text-white shadow-nav">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 sm:h-20 flex items-center justify-between gap-4">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 flex-shrink-0">
          <span className="font-display font-extrabold text-xl sm:text-2xl tracking-tight text-white">
            SHOP<span className="text-saffron">FORGE</span>
          </span>
        </Link>

        {/* Search Centre */}
        <div
          onClick={openSearch}
          className="hidden md:flex items-center gap-2 max-w-md w-full bg-white/10 hover:bg-white/15 px-4 py-2.5 rounded-full border border-white/10 hover:border-white/20 text-slate-300 placeholder-slate-400 cursor-pointer transition-all duration-200"
        >
          <Search size={16} />
          <span className="text-sm select-none">Search products, brands... (Ctrl+K)</span>
        </div>

        {/* Icons Right */}
        <div className="flex items-center gap-2 sm:gap-4">
          {/* Mobile search trigger */}
          <button
            onClick={openSearch}
            className="md:hidden p-2 text-slate-300 hover:text-white rounded-full hover:bg-white/5 transition-colors"
          >
            <Search size={20} />
          </button>

          {/* Wishlist */}
          <Link
            href="/account/wishlist"
            className="p-2 text-slate-300 hover:text-white rounded-full hover:bg-white/5 transition-colors hidden sm:block"
          >
            <Heart size={20} />
          </Link>

          {/* Cart Icon */}
          <button
            onClick={openCart}
            className="p-2 text-slate-300 hover:text-white rounded-full hover:bg-white/5 relative transition-colors"
          >
            <ShoppingBag size={20} />
            {count() > 0 && (
              <span className="absolute top-0 right-0 bg-saffron text-navy font-bold text-[10px] w-5 h-5 flex items-center justify-center rounded-full border-2 border-navy animate-[pulseSaffron_600ms_ease-in-out_1]">
                {count()}
              </span>
            )}
          </button>

          {/* User Profile / Login */}
          {session ? (
            <div className="relative group">
              <button className="flex items-center gap-2 p-1 rounded-full hover:bg-white/5 transition-colors">
                <div className="w-8 h-8 rounded-full bg-saffron text-navy font-bold flex items-center justify-center text-sm">
                  {user?.firstName?.[0]?.toUpperCase() || user?.email?.[0]?.toUpperCase() || 'U'}
                </div>
              </button>

              {/* Dropdown Menu */}
              <div className="absolute right-0 mt-2 w-48 bg-white rounded-xl shadow-modal py-1.5 text-navy hidden group-hover:block hover:block z-50 border border-slate-100 animate-[fadeUp_150ms_ease-out]">
                <div className="px-4 py-2 border-b border-slate-50">
                  <p className="text-xs text-slate font-medium">Signed in as</p>
                  <p className="text-sm font-semibold text-navy truncate">{user?.firstName || user?.email}</p>
                </div>
                {isAdmin && (
                  <Link
                    href="/admin"
                    className="flex items-center gap-2 px-4 py-2 text-sm text-navy hover:bg-slate-50"
                  >
                    <ShieldAlert size={14} className="text-saffron" />
                    <span>Admin Dashboard</span>
                  </Link>
                )}
                <Link
                  href="/account"
                  className="flex items-center gap-2 px-4 py-2 text-sm text-navy hover:bg-slate-50"
                >
                  <User size={14} />
                  <span>My Account</span>
                </Link>
                <Link
                  href="/account/orders"
                  className="flex items-center gap-2 px-4 py-2 text-sm text-navy hover:bg-slate-50"
                >
                  <ShoppingBag size={14} />
                  <span>My Orders</span>
                </Link>
                <button
                  onClick={() => signOut({ callbackUrl: '/' })}
                  className="flex items-center gap-2 px-4 py-2 text-sm text-danger hover:bg-danger/5 w-full text-left border-t border-slate-50 mt-1.5"
                >
                  <LogOut size={14} />
                  <span>Logout</span>
                </button>
              </div>
            </div>
          ) : (
            <Link href="/login">
              <button className="bg-saffron hover:bg-saffron-dark text-navy font-display font-bold text-xs sm:text-sm px-4 py-2 rounded-full transition-all active:scale-95">
                Sign In
              </button>
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
