import { withAuth } from 'next-auth/middleware'
import { NextResponse } from 'next/server'

export default withAuth(
  function middleware(req) {
    const token = req.nextauth.token
    const path = req.nextUrl.pathname

    // If the refresh token has expired (real token invalidated by backend restart),
    // redirect to login so the user re-authenticates and gets a fresh token pair.
    // @ts-ignore
    if (token?.error === 'RefreshAccessTokenError') {
      const signInUrl = new URL('/login', req.url)
      signInUrl.searchParams.set('callbackUrl', req.url)
      return NextResponse.redirect(signInUrl)
    }

    // Admin authorization guard. Only the real ADMIN role counts -- this used to also treat
    // any email containing the substring "admin" as an admin (e.g. "adminfan99@gmail.com"),
    // which is exactly the kind of thing @PreAuthorize("hasRole('ADMIN')") on the backend
    // would never accept. The backend login response always includes the real roles array
    // (see AuthService/TokenResponse), so there's no need for a fallback here.
    if (path.startsWith('/admin')) {
      // @ts-expect-error token.roles isn't on next-auth's default JWT type yet
      const roles: string[] = token?.roles || []
      if (!roles.includes('ADMIN')) {
        return NextResponse.redirect(new URL('/account', req.url))
      }
    }

    return NextResponse.next()
  },
  {
    pages: {
      signIn: '/login',
    },
  }
)

export const config = {
  // /checkout is included because CartController/OrderController on the backend require
  // authentication for everything -- there's no guest-checkout support yet, so an
  // unauthenticated visitor reaching this page would just hit 401s on every API call
  // instead of a clear "please log in" redirect.
  matcher: ['/account/:path*', '/admin/:path*', '/checkout/:path*'],
}
