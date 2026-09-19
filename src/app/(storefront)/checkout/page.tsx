'use client'

import React, { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { useCart } from '@/store/cartStore'
import { formatPrice } from '@/lib/utils'
import { Navbar } from '@/components/layout/Navbar'
import { Footer } from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { Button } from '@/components/ui/Button'
import { getAddresses } from '@/lib/api/addresses'
import { checkout, getOrder, OrderDto } from '@/lib/api/orders'
import { initiatePayment, verifyPayment } from '@/lib/api/payments'
import { loadRazorpayScript } from '@/lib/razorpay'
import { MapPin, Truck, ClipboardCheck, ChevronRight, CheckCircle2, Wallet, CreditCard } from 'lucide-react'
import Link from 'next/link'
import toast from 'react-hot-toast'

// Mirrors CheckoutService's FREE_SHIPPING_THRESHOLD / STANDARD_SHIPPING_FEE constants on the
// backend -- this is only a preview shown before the order is placed. The authoritative fee is
// always whatever the server actually charges in the created order, computed the same way.
const FREE_SHIPPING_THRESHOLD = 499
const STANDARD_SHIPPING_FEE = 99

export default function CheckoutPage() {
  const { items, total, clear } = useCart()
  const [step, setStep] = useState<1 | 2 | 3>(1)
  const [placedOrder, setPlacedOrder] = useState<OrderDto | null>(null)
  const [isPlacingOrder, setIsPlacingOrder] = useState(false)
  // Set once checkout() succeeds, so a retry after a cancelled/failed payment re-initiates
  // payment for the SAME order instead of calling checkout() again -- the cart is already
  // cleared by then, so a second checkout() call would just fail with "cart is empty".
  const [createdOrder, setCreatedOrder] = useState<OrderDto | null>(null)

  const [selectedAddressId, setSelectedAddressId] = useState<string | null>(null)
  const [shippingMethod, setShippingMethod] = useState<'STANDARD' | 'EXPRESS'>('STANDARD')
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'RAZORPAY'>('COD')

  const { data: addresses, isLoading: isAddressesLoading } = useQuery({
    queryKey: ['myAddresses'],
    queryFn: getAddresses,
  })

  const calculatedTotal = useMemo(() => {
    const subtotal = total()
    const shipping = subtotal === 0 || subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : STANDARD_SHIPPING_FEE
    return { subtotal, shipping, final: subtotal + shipping }
  }, [total])

  const handlePlaceOrder = async () => {
    if (!selectedAddressId) {
      toast.error('Please select a shipping address')
      setStep(1)
      return
    }

    setIsPlacingOrder(true)
    try {
      // Reuse the already-created order on a retry (see createdOrder's declaration) instead of
      // calling checkout() again, which would fail: the cart was already cleared on the first
      // successful call, regardless of whether payment itself went on to succeed.
      const order = createdOrder ?? (await checkout({ addressId: selectedAddressId, shippingMethod }))
      if (!createdOrder) setCreatedOrder(order)

      const payment = await initiatePayment({ orderId: order.id, method: paymentMethod })

      if (payment.gateway === 'cod') {
        const finalOrder = await getOrder(order.id)
        setPlacedOrder(finalOrder)
        clear()
        toast.success('Order placed successfully!')
        setIsPlacingOrder(false)
        return
      }

      // Razorpay path: the modal is event-driven (opens and returns immediately), so
      // isPlacingOrder is deliberately left true and only cleared inside the callbacks below --
      // clearing it right after rzp.open() would let "Place Order" be clicked again while the
      // modal is still open.
      const loaded = await loadRazorpayScript()
      if (!loaded || !window.Razorpay) {
        toast.error('Could not load the payment gateway. Please try again.')
        setIsPlacingOrder(false)
        return
      }

      const rzp = new window.Razorpay({
        key: payment.razorpayKeyId!,
        amount: Math.round(payment.amount * 100),
        currency: payment.currency,
        name: 'ShopForge',
        description: `Order ${order.orderNumber}`,
        order_id: payment.razorpayOrderId!,
        handler: (response) => {
          verifyPayment({
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          })
            .then((finalOrder) => {
              setPlacedOrder(finalOrder)
              clear()
              toast.success('Payment successful!')
            })
            .catch(() => toast.error('Payment verification failed. You can retry below.'))
            .finally(() => setIsPlacingOrder(false))
        },
        modal: {
          ondismiss: () => {
            toast.error('Payment cancelled. You can retry below.')
            setIsPlacingOrder(false)
          },
        },
        theme: { color: '#f4a825' },
      })
      rzp.open()
    } catch (err) {
      const message = axios.isAxiosError(err) ? err.response?.data?.message : undefined
      toast.error(message || 'Failed to place order')
      setIsPlacingOrder(false)
    }
  }

  if (placedOrder) {
    return (
      <div className="flex flex-col min-h-screen bg-surface">
        <Navbar />
        <main className="flex-1 max-w-lg mx-auto w-full px-4 py-16 text-center space-y-6">
          <div className="w-20 h-20 bg-emerald-50 text-emerald-500 rounded-full flex items-center justify-center mx-auto border border-emerald-100 animate-bounce">
            <CheckCircle2 size={48} />
          </div>
          <div className="space-y-2">
            <h1 className="font-display font-black text-2xl sm:text-3xl text-navy">Order Placed Successfully!</h1>
            <p className="text-slate text-sm">
              Thank you for shopping on ShopForge. A confirmation email is on its way.
            </p>
          </div>

          <div className="bg-white border border-slate-100 p-6 rounded-2xl text-left space-y-4">
            <div className="flex justify-between border-b border-slate-50 pb-3 text-sm">
              <span className="text-slate font-medium">Order Number</span>
              <span className="font-mono font-bold text-navy">{placedOrder.orderNumber}</span>
            </div>
            <div className="flex justify-between border-b border-slate-50 pb-3 text-sm">
              <span className="text-slate font-medium">Status</span>
              <span className="font-bold text-navy">{placedOrder.status}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate font-medium">Total</span>
              <span className="font-bold text-navy">{formatPrice(placedOrder.totalAmount)}</span>
            </div>
          </div>

          <div className="pt-4 flex flex-col sm:flex-row gap-3">
            <Link href="/account" className="flex-1">
              <Button variant="secondary" className="w-full justify-center">Track Order</Button>
            </Link>
            <Link href="/" className="flex-1">
              <Button variant="primary" className="w-full justify-center">Continue Shopping</Button>
            </Link>
          </div>
        </main>
        <Footer />
      </div>
    )
  }

  return (
    <div className="flex flex-col min-h-screen bg-surface">
      <Navbar />

      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="font-display font-black text-2xl sm:text-3xl text-navy mb-8">Checkout</h1>

        {items.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-slate mb-4">You have no items in your cart to checkout.</p>
            <Link href="/"><Button variant="primary">Shop Now</Button></Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Steps Form - Left 60% */}
            <div className="lg:col-span-8 space-y-6">
              {/* Steps Progress Indicator */}
              <div className="bg-white border border-slate-100 rounded-2xl p-6 flex justify-around items-center">
                {[
                  { num: 1, label: 'Address', icon: MapPin },
                  { num: 2, label: 'Shipping', icon: Truck },
                  { num: 3, label: 'Review', icon: ClipboardCheck },
                ].map((s) => {
                  const active = step >= s.num
                  const isCurrent = step === s.num
                  return (
                    <div key={s.num} className="flex items-center gap-2">
                      <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border transition-colors ${
                        isCurrent
                          ? 'bg-saffron text-navy border-saffron'
                          : active
                          ? 'bg-navy text-white border-navy'
                          : 'bg-slate-50 text-slate-400 border-slate-200'
                      }`}>
                        {s.num}
                      </div>
                      <span className={`text-xs sm:text-sm font-semibold transition-colors ${active ? 'text-navy' : 'text-slate-400'}`}>
                        {s.label}
                      </span>
                      {s.num < 3 && <ChevronRight size={14} className="text-slate-300 hidden sm:block" />}
                    </div>
                  )
                })}
              </div>

              {/* Step 1: Address */}
              {step === 1 && (
                <div className="bg-white border border-slate-100 rounded-2xl p-6 space-y-6 animate-[fadeUp_200ms_ease-out]">
                  <h2 className="font-display font-bold text-navy text-lg flex items-center gap-2">
                    <MapPin size={20} className="text-saffron" /> Select Shipping Address
                  </h2>

                  {isAddressesLoading ? (
                    <div className="flex items-center justify-center py-8">
                      <div className="w-6 h-6 border-2 border-navy border-t-saffron rounded-full animate-spin" />
                    </div>
                  ) : !addresses || addresses.length === 0 ? (
                    <div className="text-center py-8 space-y-3">
                      <p className="text-sm text-slate">You don&apos;t have any saved addresses yet.</p>
                      <Link href="/account" className="inline-block">
                        <Button variant="primary">Add an address</Button>
                      </Link>
                    </div>
                  ) : (
                    <>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {addresses.map((addr) => (
                          <div
                            key={addr.id}
                            onClick={() => setSelectedAddressId(addr.id)}
                            className={`p-4 rounded-xl border-2 cursor-pointer transition-all ${
                              selectedAddressId === addr.id
                                ? 'border-saffron bg-saffron/5'
                                : 'border-slate-200 hover:border-slate-300'
                            }`}
                          >
                            <div className="flex justify-between items-start">
                              <span className="font-bold text-navy text-sm">
                                {addr.firstName} {addr.lastName}
                              </span>
                              <input
                                type="radio"
                                checked={selectedAddressId === addr.id}
                                onChange={() => setSelectedAddressId(addr.id)}
                                className="text-saffron focus:ring-saffron border-slate-300 w-4 h-4 cursor-pointer"
                              />
                            </div>
                            <p className="text-xs text-slate-600 mt-2">
                              {addr.addressLine1}
                              {addr.addressLine2 ? `, ${addr.addressLine2}` : ''}
                            </p>
                            <p className="text-xs text-slate-600">
                              {addr.city}, {addr.state} - {addr.postalCode}
                            </p>
                            <p className="text-xs text-slate-400 font-mono mt-2">{addr.phone}</p>
                          </div>
                        ))}
                      </div>

                      <Link
                        href="/account"
                        className="text-xs font-bold text-saffron hover:text-saffron-dark hover:underline inline-block"
                      >
                        + Manage addresses
                      </Link>
                    </>
                  )}

                  <div className="flex justify-end pt-4 border-t border-slate-50">
                    <Button
                      variant="primary"
                      onClick={() => (selectedAddressId ? setStep(2) : toast.error('Please select an address'))}
                    >
                      Proceed to Shipping
                    </Button>
                  </div>
                </div>
              )}

              {/* Step 2: Shipping */}
              {step === 2 && (
                <div className="bg-white border border-slate-100 rounded-2xl p-6 space-y-6 animate-[fadeUp_200ms_ease-out]">
                  <h2 className="font-display font-bold text-navy text-lg flex items-center gap-2">
                    <Truck size={20} className="text-saffron" /> Select Shipping Method
                  </h2>

                  <div className="space-y-3">
                    <div
                      onClick={() => setShippingMethod('STANDARD')}
                      className={`p-4 rounded-xl border-2 cursor-pointer flex justify-between items-center transition-all ${
                        shippingMethod === 'STANDARD' ? 'border-saffron bg-saffron/5' : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <input type="radio" checked={shippingMethod === 'STANDARD'} onChange={() => setShippingMethod('STANDARD')} className="text-saffron focus:ring-saffron" />
                        <div>
                          <span className="font-bold text-navy text-sm block">Standard Shipping</span>
                          <span className="text-xs text-slate-500">Delivery in 5 - 7 Business Days</span>
                        </div>
                      </div>
                      <span className="font-bold text-navy text-sm">
                        {calculatedTotal.shipping === 0 ? 'FREE' : formatPrice(calculatedTotal.shipping)}
                      </span>
                    </div>

                    <div
                      onClick={() => setShippingMethod('EXPRESS')}
                      className={`p-4 rounded-xl border-2 cursor-pointer flex justify-between items-center transition-all ${
                        shippingMethod === 'EXPRESS' ? 'border-saffron bg-saffron/5' : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <input type="radio" checked={shippingMethod === 'EXPRESS'} onChange={() => setShippingMethod('EXPRESS')} className="text-saffron focus:ring-saffron" />
                        <div>
                          <span className="font-bold text-navy text-sm block">Express Courier Delivery</span>
                          <span className="text-xs text-slate-500">Delivery in 2 - 3 Business Days</span>
                        </div>
                      </div>
                      <span className="font-bold text-navy text-sm">
                        {calculatedTotal.shipping === 0 ? 'FREE' : formatPrice(calculatedTotal.shipping)}
                      </span>
                    </div>

                    <p className="text-[10px] text-slate-400 leading-relaxed px-1">
                      Delivery speed is a request only for now -- the shipping fee is always based on your order value, not which option you pick.
                    </p>
                  </div>

                  <div className="flex justify-between pt-4 border-t border-slate-50">
                    <Button variant="secondary" onClick={() => setStep(1)}>
                      Back
                    </Button>
                    <Button variant="primary" onClick={() => setStep(3)}>
                      Review Order
                    </Button>
                  </div>
                </div>
              )}

              {/* Step 3: Review & Place Order */}
              {step === 3 && (
                <div className="bg-white border border-slate-100 rounded-2xl p-6 space-y-6 animate-[fadeUp_200ms_ease-out]">
                  <h2 className="font-display font-bold text-navy text-lg flex items-center gap-2">
                    <ClipboardCheck size={20} className="text-saffron" /> Payment Method
                  </h2>

                  <div className="space-y-3">
                    <div
                      onClick={() => setPaymentMethod('COD')}
                      className={`p-4 rounded-xl border-2 cursor-pointer flex items-center gap-3 transition-all ${
                        paymentMethod === 'COD' ? 'border-saffron bg-saffron/5' : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <input type="radio" checked={paymentMethod === 'COD'} onChange={() => setPaymentMethod('COD')} className="text-saffron focus:ring-saffron" />
                      <Wallet size={18} className="text-slate-500" />
                      <div>
                        <span className="font-bold text-navy text-sm block">Cash on Delivery</span>
                        <span className="text-xs text-slate-500">Pay when your order arrives</span>
                      </div>
                    </div>

                    <div
                      onClick={() => setPaymentMethod('RAZORPAY')}
                      className={`p-4 rounded-xl border-2 cursor-pointer flex items-center gap-3 transition-all ${
                        paymentMethod === 'RAZORPAY' ? 'border-saffron bg-saffron/5' : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <input type="radio" checked={paymentMethod === 'RAZORPAY'} onChange={() => setPaymentMethod('RAZORPAY')} className="text-saffron focus:ring-saffron" />
                      <CreditCard size={18} className="text-slate-500" />
                      <div>
                        <span className="font-bold text-navy text-sm block">Pay Online</span>
                        <span className="text-xs text-slate-500">UPI, Card, or Netbanking via Razorpay</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex justify-between pt-4 border-t border-slate-50">
                    <Button variant="secondary" onClick={() => setStep(2)}>
                      Back
                    </Button>
                    <Button
                      variant="primary"
                      onClick={handlePlaceOrder}
                      disabled={isPlacingOrder}
                      className="py-3 text-sm bg-saffron text-navy hover:bg-saffron-dark font-display font-bold disabled:opacity-60"
                    >
                      {isPlacingOrder ? 'Placing order...' : `Place Order (${formatPrice(calculatedTotal.final)})`}
                    </Button>
                  </div>
                </div>
              )}
            </div>

            {/* Sidebar Summary - Right 40% */}
            <div className="lg:col-span-4 bg-white border border-slate-100 rounded-2xl p-6 space-y-6 lg:sticky lg:top-28">
              <h2 className="font-display font-bold text-navy text-lg border-b border-slate-50 pb-3">Order Details</h2>
              <div className="divide-y divide-slate-50 max-h-[220px] overflow-y-auto pr-1 no-scrollbar">
                {items.map((item) => (
                  <div key={item.variantId} className="flex gap-3 py-3 first:pt-0 last:pb-0">
                    <img src={item.image} alt={item.name} className="w-12 h-12 object-cover rounded-lg bg-slate-50 flex-shrink-0" />
                    <div className="flex-1 text-xs">
                      <p className="font-bold text-navy line-clamp-1">{item.name}</p>
                      <p className="text-slate mt-0.5">Qty: {item.quantity}</p>
                      <p className="text-saffron font-bold mt-0.5">{formatPrice(item.price * item.quantity)}</p>
                    </div>
                  </div>
                ))}
              </div>

              <div className="border-t border-slate-50 pt-4 space-y-3.5 text-xs sm:text-sm">
                <div className="flex justify-between">
                  <span className="text-slate">Subtotal</span>
                  <span className="font-semibold text-navy">{formatPrice(calculatedTotal.subtotal)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate">Shipping fee</span>
                  <span className="font-semibold text-navy">
                    {calculatedTotal.shipping === 0 ? 'FREE' : formatPrice(calculatedTotal.shipping)}
                  </span>
                </div>
                <div className="border-t border-slate-50 pt-3 flex justify-between items-baseline">
                  <span className="font-display font-bold text-navy">Payable Total</span>
                  <span className="font-display font-black text-navy text-base sm:text-lg">
                    {formatPrice(calculatedTotal.final)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      <Footer />
      <BottomNav />
    </div>
  )
}
