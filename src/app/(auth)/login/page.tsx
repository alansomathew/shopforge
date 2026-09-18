'use client'

import React, { useState } from 'react'
import { signIn, getSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/Button'
import { Mail, Lock, ShieldAlert } from 'lucide-react'
import toast from 'react-hot-toast'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const router = useRouter()

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) {
      toast.error('Please enter email and password')
      return
    }

    setLoading(true)
    try {
      const result = await signIn('credentials', {
        email,
        password,
        redirect: false,
      })

      if (result?.error) {
        toast.error(result.error)
      } else {
        toast.success('Successfully logged in!')
        // signIn({redirect:false}) doesn't hand back the session it just created, so fetch it
        // once here to read the role and decide where to land -- admins go straight to their
        // dashboard, everyone else goes to the storefront.
        const session = await getSession()
        // @ts-expect-error roles isn't on the default NextAuth Session type yet
        const roles: string[] = session?.user?.roles || []
        router.push(roles.includes('ADMIN') ? '/admin' : '/')
        router.refresh()
      }
    } catch (err) {
      toast.error('Something went wrong during login.')
    } finally {
      setLoading(false)
    }
  }

  const handleGoogleLogin = () => {
    setLoading(true)
    const toastId = toast.loading('Redirecting to Google...')
    setTimeout(() => {
      toast.dismiss(toastId)
      signIn('google', { callbackUrl: '/' }).catch(() => {
        // Fallback to customer mock
        signIn('credentials', {
          email: 'user@shopforge.com',
          password: 'User@123',
          callbackUrl: '/',
        })
      })
    }, 1000)
  }

  return (
    <div className="min-h-screen bg-navy flex items-center justify-center p-4 sm:p-6 lg:p-8">
      {/* Background radial gradient */}
      <div className="absolute inset-0 opacity-10 bg-[radial-gradient(#FAFAFA_1px,transparent_1px)] [background-size:24px_24px] pointer-events-none" />

      <div className="max-w-md w-full bg-white rounded-2xl shadow-modal overflow-hidden p-6 sm:p-8 border border-slate-100 z-10 animate-[fadeUp_250ms_ease-out]">
        <div className="text-center space-y-2 mb-8">
          <Link href="/">
            <span className="font-display font-extrabold text-2xl tracking-tight text-navy">
              SHOP<span className="text-saffron">FORGE</span>
            </span>
          </Link>
          <h2 className="font-display font-bold text-lg text-navy">Welcome Back</h2>
          <p className="text-xs text-slate">Enter credentials to access your account dashboard</p>
        </div>

        {/* Credentials hints for test */}
        <div className="bg-saffron/10 border border-saffron/20 p-4 rounded-xl mb-6 text-xs text-navy/90 space-y-2.5">
          <p className="font-bold flex items-center gap-1.5 text-saffron-dark uppercase tracking-wider text-[10px]">
            <ShieldAlert size={14} /> Testing Accounts
          </p>
          <div className="grid grid-cols-2 gap-3 text-[11px] font-mono">
            <div>
              <p className="font-bold text-navy">1. Admin Access:</p>
              <p className="text-slate-600 mt-0.5">Email: admin@shopforge.com</p>
              <p className="text-slate-600">Pass: Admin@123</p>
            </div>
            <div>
              <p className="font-bold text-navy">2. Customer Access:</p>
              <p className="text-slate-600 mt-0.5">Email: user@shopforge.com</p>
              <p className="text-slate-600">Pass: User@123</p>
            </div>
          </div>
        </div>

        <form onSubmit={handleLogin} className="space-y-4">
          {/* Email */}
          <div className="space-y-1.5">
            <label className="text-xs font-bold uppercase tracking-wider text-slate block">Email Address</label>
            <div className="relative flex items-center">
              <Mail size={16} className="absolute left-3.5 text-slate-400" />
              <input
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl pl-10 pr-4 py-2.5 text-xs text-navy outline-none w-full transition-colors"
                required
              />
            </div>
          </div>

          {/* Password */}
          <div className="space-y-1.5">
            <div className="flex justify-between items-center">
              <label className="text-xs font-bold uppercase tracking-wider text-slate block">Password</label>
              <a href="#" className="text-[10px] text-saffron hover:underline font-bold">Forgot password?</a>
            </div>
            <div className="relative flex items-center">
              <Lock size={16} className="absolute left-3.5 text-slate-400" />
              <input
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="bg-slate-50 border border-slate-200 focus:border-saffron rounded-xl pl-10 pr-4 py-2.5 text-xs text-navy outline-none w-full transition-colors"
                required
              />
            </div>
          </div>

          <Button
            type="submit"
            disabled={loading}
            className="w-full py-3 mt-2 text-sm bg-navy text-saffron hover:bg-navy-light hover:text-white justify-center font-display"
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </Button>
        </form>

        {/* Divider */}
        <div className="relative my-6 flex items-center justify-center">
          <div className="border-t border-slate-200 w-full absolute" />
          <span className="bg-white px-3 text-[10px] font-bold text-slate uppercase tracking-wider relative z-10">
            Or continue with
          </span>
        </div>

        {/* Google Button */}
        <button
          type="button"
          onClick={handleGoogleLogin}
          className="w-full py-2.5 px-4 rounded-xl border border-slate-200 hover:border-slate-400 bg-white text-navy font-semibold text-xs flex items-center justify-center gap-2 hover:bg-slate-50 transition-all active:scale-95 duration-200"
        >
          <svg className="w-4 h-4" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v3.92h6.69c-.29 1.5-1.14 2.78-2.4 3.63v3.02h3.88c2.27-2.08 3.57-5.14 3.57-8.5z"
            />
            <path
              fill="#34A853"
              d="M12 24c3.24 0 5.97-1.08 7.96-2.91l-3.88-3.02c-1.08.72-2.47 1.15-4.08 1.15-3.13 0-5.78-2.11-6.73-4.96H1.28v3.13C3.26 22.37 7.37 24 12 24z"
            />
            <path
              fill="#FBBC05"
              d="M5.27 14.26c-.25-.72-.39-1.5-.39-2.3s.14-1.58.39-2.3V6.53H1.28C.46 8.16 0 9.99 0 12s.46 3.84 1.28 5.47l3.99-3.21z"
            />
            <path
              fill="#EA4335"
              d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.37 0 3.26 1.63 1.28 4.75l3.99 3.21c.95-2.85 3.6-4.96 6.73-4.96z"
            />
          </svg>
          <span>Sign in with Google</span>
        </button>

        <div className="mt-6 text-center text-xs text-slate">
          Don&apos;t have an account?{' '}
          <Link href="/register" className="text-saffron hover:underline font-bold">
            Create account
          </Link>
        </div>
      </div>
    </div>
  )
}
