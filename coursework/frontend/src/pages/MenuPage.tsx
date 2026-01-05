import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuApi, ordersApi, authApi, paymentsApi, clientsApi } from '@/api/client';
import {
  GetCurrentUser200ResponseUserTypeEnum,
  MenuItem,
  OrderType,
  PaymentMethod,
  OrderStatus,
} from '@/api/generated/api';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { formatCurrency } from '@/lib/utils';
import { getApiErrorMessage } from '@/lib/apiError';
import {
  formatCardNumberInput,
  formatExpiryInput,
  isValidLuhn,
  sanitizeCardNumber,
  validateExpiry,
} from '@/lib/cardUtils';
import { useToast } from '@/components/ui/toast';

type LoginDialogMode = 'loginRequired' | 'clientOnly';

const CHECKOUT_DRAFT_KEY = 'checkoutDraft.v1';

export function MenuPage() {
  const navigate = useNavigate();
  const { isAuthenticated, isClient } = useAuthStore();
  const toast = useToast();
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [menuError, setMenuError] = useState<string | null>(null);
  const [cart, setCart] = useState<Map<number, number>>(new Map());
  const [orderType, setOrderType] = useState<OrderType>(OrderType.Delivery);
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [addressError, setAddressError] = useState<string | null>(null);
  const [profileDefaultAddress, setProfileDefaultAddress] = useState('');
  const [profileDefaultAddressLoaded, setProfileDefaultAddressLoaded] = useState(false);
  const [profileDefaultAddressLoading, setProfileDefaultAddressLoading] = useState(false);
  const [isPlacingOrder, setIsPlacingOrder] = useState(false);
  const [showLoginDialog, setShowLoginDialog] = useState(false);
  const [loginDialogMode, setLoginDialogMode] = useState<LoginDialogMode>('loginRequired');
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(PaymentMethod.Online);
  const [cardData, setCardData] = useState({ number: '', expiry: '', cvv: '' });
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [createdOrderId, setCreatedOrderId] = useState<number | null>(null);
  const [resumeCheckout, setResumeCheckout] = useState(false);
  const addressInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    restoreCheckoutDraft();
    void loadMenu();
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    if (!isClient()) return;
    if (profileDefaultAddressLoaded || profileDefaultAddressLoading) return;
    void loadProfileDefaultAddress();
  }, [isAuthenticated, isClient, profileDefaultAddressLoaded, profileDefaultAddressLoading]);

  useEffect(() => {
    if (!resumeCheckout) return;
    if (!isAuthenticated) return;
    if (!isClient()) return;
    if (cart.size === 0) return;
    if (!deliveryAddress.trim()) {
      if (!profileDefaultAddressLoaded) return;
      if (profileDefaultAddress.trim()) {
        setDeliveryAddress(profileDefaultAddress);
        return;
      }
      setAddressError('Введите адрес доставки');
      toast.warning('Введите адрес доставки');
      addressInputRef.current?.focus();
      setResumeCheckout(false);
      return;
    }
    setResumeCheckout(false);
    setPaymentError(null);
    setShowPaymentDialog(true);
  }, [resumeCheckout, isAuthenticated, isClient, cart.size, deliveryAddress, toast]);

  const persistCheckoutDraft = () => {
    try {
      const payload = {
        cart: Array.from(cart.entries()),
        deliveryAddress,
        createdAt: new Date().toISOString(),
      };
      sessionStorage.setItem(CHECKOUT_DRAFT_KEY, JSON.stringify(payload));
    } catch {
      // ignore
    }
  };

  const restoreCheckoutDraft = () => {
    try {
      const raw = sessionStorage.getItem(CHECKOUT_DRAFT_KEY);
      if (!raw) return;
      sessionStorage.removeItem(CHECKOUT_DRAFT_KEY);

      const draft = JSON.parse(raw) as any;
      const hasCart = draft?.cart && Array.isArray(draft.cart) && draft.cart.length > 0;
      if (hasCart) {
        setCart(new Map(draft.cart));
      }
      if (typeof draft?.deliveryAddress === 'string') {
        setDeliveryAddress(draft.deliveryAddress);
      }
      if (hasCart) {
        setResumeCheckout(true);
      }
    } catch {
      // ignore
    }
  };

  const loadMenu = async () => {
    try {
      const response = await menuApi.getMenu(false);
      setMenuItems(response.data);
      setMenuError(null);
    } catch (error) {
      console.error('Ошибка загрузки меню:', error);
      setMenuError(getApiErrorMessage(error, 'Ошибка загрузки меню'));
    } finally {
      setLoading(false);
    }
  };

  const loadProfileDefaultAddress = async () => {
    if (!isAuthenticated) return;
    if (!isClient()) return;
    if (profileDefaultAddressLoading) return;

    setProfileDefaultAddressLoading(true);
    try {
      let user = useAuthStore.getState().user;
      if (!user?.userId) {
        const me = await authApi.getCurrentUser();
        useAuthStore.getState().setAuth(useAuthStore.getState().token || '', me.data);
        user = me.data;
      }

      if (!user?.userId) return;
      if (user.userType !== GetCurrentUser200ResponseUserTypeEnum.Client) return;

      const response = await clientsApi.getClientById(user.userId);
      const defaultAddress = response.data.defaultAddress?.trim() || '';
      setProfileDefaultAddress(defaultAddress);
      if (defaultAddress) {
        setDeliveryAddress((prev) => (prev.trim() ? prev : defaultAddress));
      }
    } catch (error) {
      console.error('Ошибка загрузки адреса из профиля:', error);
      setProfileDefaultAddress('');
    } finally {
      setProfileDefaultAddressLoading(false);
      setProfileDefaultAddressLoaded(true);
    }
  };

  const addToCart = (itemId: number) => {
    setCart((prev) => {
      const newCart = new Map(prev);
      newCart.set(itemId, (newCart.get(itemId) || 0) + 1);
      return newCart;
    });
  };

  const removeFromCart = (itemId: number) => {
    setCart((prev) => {
      const newCart = new Map(prev);
      const count = newCart.get(itemId) || 0;
      if (count > 1) {
        newCart.set(itemId, count - 1);
      } else {
        newCart.delete(itemId);
      }
      return newCart;
    });
  };

  const deleteFromCart = (itemId: number) => {
    setCart((prev) => {
      const newCart = new Map(prev);
      newCart.delete(itemId);
      return newCart;
    });
  };

  const clearCart = () => {
    setCart(new Map());
  };

  const startCheckout = () => {
    if (cart.size === 0) return;

    if (!isAuthenticated) {
      setLoginDialogMode('loginRequired');
      setShowLoginDialog(true);
      return;
    }

    if (!isClient()) {
      setLoginDialogMode('clientOnly');
      setShowLoginDialog(true);
      return;
    }

    if (orderType === OrderType.Delivery && !deliveryAddress.trim()) {
      setAddressError('Введите адрес доставки');
      toast.warning('Введите адрес доставки');
      addressInputRef.current?.focus();
      return;
    }

    setAddressError(null);
    setPaymentError(null);
    setShowPaymentDialog(true);
  };

  const submitOrder = async () => {
    if (cart.size === 0 && !createdOrderId) return;
    if (!isAuthenticated) {
      setShowPaymentDialog(false);
      setLoginDialogMode('loginRequired');
      setShowLoginDialog(true);
      return;
    }
    if (!isClient()) {
      setShowPaymentDialog(false);
      setLoginDialogMode('clientOnly');
      setShowLoginDialog(true);
      return;
    }
    if (orderType !== OrderType.Delivery) {
      setOrderType(OrderType.Delivery);
    }
    if (!deliveryAddress.trim()) {
      setShowPaymentDialog(false);
      setAddressError('Введите адрес доставки');
      toast.warning('Введите адрес доставки');
      addressInputRef.current?.focus();
      return;
    }

    const isOnlinePayment = paymentMethod === PaymentMethod.Online;
    const sanitizedCardNumber = sanitizeCardNumber(cardData.number);
    const sanitizedExpiry = cardData.expiry.trim();
    const sanitizedCvv = cardData.cvv.trim();

    if (isOnlinePayment) {
      if (!/^[0-9]{16}$/.test(sanitizedCardNumber) || !isValidLuhn(sanitizedCardNumber)) {
        setPaymentError('Введите корректный номер карты');
        return;
      }
      const expiryCheck = validateExpiry(sanitizedExpiry);
      if (!expiryCheck.valid) {
        setPaymentError(
          expiryCheck.reason === 'expired' ? 'Срок действия карты истек' : 'Срок действия в формате ММ/ГГ'
        );
        return;
      }
      if (!/^[0-9]{3,4}$/.test(sanitizedCvv)) {
        setPaymentError('CVV должен содержать 3-4 цифры');
        return;
      }
    }

    setIsPlacingOrder(true);
    try {
      let user = useAuthStore.getState().user;

      if (!user) {
        throw new Error('Пользователь не найден. Пожалуйста, войдите заново.');
      }

      if (!user.userId) {
        try {
          const userResponse = await authApi.getCurrentUser();
          useAuthStore.getState().setAuth(useAuthStore.getState().token || '', userResponse.data);
          user = userResponse.data;
          if (!user.userId) {
            throw new Error('ID пользователя не найден. Пожалуйста, войдите заново.');
          }
          if (user.userType !== GetCurrentUser200ResponseUserTypeEnum.Client) {
            throw new Error('Только клиенты могут оформлять заказы. Войдите как клиент.');
          }
        } catch {
          useAuthStore.getState().clearAuth();
          throw new Error('Не удалось загрузить информацию о пользователе. Пожалуйста, войдите заново.');
        }
      }

      if (user.userType !== GetCurrentUser200ResponseUserTypeEnum.Client) {
        throw new Error('Только клиенты могут оформлять заказы. Войдите как клиент.');
      }

      let orderId = createdOrderId;

      if (orderId == null) {
        const items = Array.from(cart.entries()).map(([menuItemId, quantity]) => ({
          menuItemId,
          quantity,
        }));

        const requestData: any = {
          clientId: user.userId,
          type: OrderType.Delivery,
          paymentMethod,
          items,
          deliveryAddress,
        };

        const orderResponse = await ordersApi.placeOrder(requestData);
        const newOrderId = orderResponse.data.orderId;
        if (newOrderId == null) {
          throw new Error('Заказ создан, но не удалось получить его номер.');
        }

        orderId = newOrderId;
        setCreatedOrderId(newOrderId);
        setCart(new Map());
      }

      if (createdOrderId != null) {
        await ordersApi.updateOrderPaymentMethod(createdOrderId, { paymentMethod });
      }

      if (paymentMethod === PaymentMethod.Online) {
        const shouldSimulateFailure = sanitizedCardNumber === '0000000000000000';
        const response = await paymentsApi.startOnlinePayment({
          orderId,
          cardNumber: sanitizedCardNumber,
          cardExpiry: sanitizedExpiry,
          cardCvv: sanitizedCvv,
          shouldSimulateFailure,
        });

        const redirectUrl = response.data.redirectUrl;
        if (!redirectUrl) {
          throw new Error('Не удалось получить ссылку для оплаты в банке.');
        }

        setCreatedOrderId(null);
        setShowPaymentDialog(false);
        window.location.href = redirectUrl;
        return;
      }

      setCreatedOrderId(null);
      setShowPaymentDialog(false);
      toast.info('Заказ создан. Оплата при получении. Ожидайте подтверждения.');
      navigate('/orders');
    } catch (error: any) {
      console.error('Order placement error:', error);
      const errorMessage = getApiErrorMessage(error, 'Ошибка оформления заказа');
      setPaymentError(errorMessage);
      toast.error(errorMessage, { title: 'Ошибка' });
    } finally {
      setIsPlacingOrder(false);
    }
  };

  const cancelCreatedOrder = async () => {
    if (!createdOrderId) return;
    try {
      await ordersApi.updateOrderStatus(createdOrderId, { status: OrderStatus.Cancelled });
      setCreatedOrderId(null);
      setShowPaymentDialog(false);
      setPaymentError(null);
      toast.info('Заказ отменён');
    } catch (error: any) {
      console.error('Order cancellation error:', error);
      toast.error(getApiErrorMessage(error, 'Не удалось отменить заказ'), { title: 'Ошибка' });
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка меню..." />;
  }

  const cartLines = Array.from(cart.entries()).map(([itemId, quantity]) => {
    const item = menuItems.find((i) => i.id === itemId);
    const unitPrice = item?.price || 0;
    return {
      itemId,
      quantity,
      name: item?.name || `Блюдо #${itemId}`,
      unitPrice,
      subtotal: unitPrice * quantity,
      canIncrement: Boolean(item?.available),
    };
  });
  const totalItemsInCart = cartLines.reduce((sum, line) => sum + line.quantity, 0);
  const totalPrice = cartLines.reduce((sum, line) => sum + line.subtotal, 0);

  return (
    <div className={cart.size > 0 ? 'pb-80' : undefined}>
      <div className="flex items-end justify-between gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold">Меню</h1>
          <p className="text-gray-600 mt-1">Доступность блюд учитывает остатки на складе.</p>
        </div>
      </div>
      {menuError && (
        <RetryAlert
          className="mb-6"
          message={menuError}
          onRetry={() => {
            setLoading(true);
            void loadMenu();
          }}
        />
      )}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {menuItems.map((item) => (
          <Card key={item.id}>
            <CardHeader>
              <CardTitle>{item.name}</CardTitle>
              <CardDescription>{item.description}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex justify-between items-center mb-4">
                <span className="text-2xl font-bold">{formatCurrency(item.price || 0)}</span>
                <span className="text-sm text-gray-500">{item.available ? 'В наличии' : 'Нет в наличии'}</span>
              </div>
              <div className="flex gap-2">
                {item.id && cart.has(item.id) && (
                  <Button variant="outline" onClick={() => item.id && removeFromCart(item.id)}>
                    -
                  </Button>
                )}
                <Button
                  onClick={() => item.id && addToCart(item.id)}
                  disabled={!item.available || !item.id}
                  className="flex-1"
                >
                  {item.id && cart.has(item.id) ? `В корзине: ${cart.get(item.id)}` : 'Добавить'}
                </Button>
                {item.id && cart.has(item.id) && (
                  <Button variant="outline" onClick={() => item.id && addToCart(item.id)}>
                    +
                  </Button>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {cart.size > 0 && (
        <div className="fixed bottom-0 left-0 right-0 bg-white border-t shadow-lg">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 space-y-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="font-semibold">Корзина</p>
                <p className="text-sm text-gray-500">Товаров: {totalItemsInCart}</p>
              </div>
              <Button variant="ghost" size="sm" onClick={clearCart}>
                Очистить
              </Button>
            </div>

            <div className="max-h-40 overflow-y-auto rounded-md border divide-y">
              {cartLines.map((line) => (
                <div key={line.itemId} className="flex items-center justify-between gap-3 px-3 py-2">
                  <div className="min-w-0">
                    <p className="font-medium truncate">{line.name}</p>
                    <p className="text-sm text-gray-500">
                      {formatCurrency(line.unitPrice)} × {line.quantity}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button variant="outline" size="sm" onClick={() => removeFromCart(line.itemId)}>
                      -
                    </Button>
                    <span className="w-6 text-center">{line.quantity}</span>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => addToCart(line.itemId)}
                      disabled={!line.canIncrement}
                    >
                      +
                    </Button>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <p className="font-semibold">{formatCurrency(line.subtotal)}</p>
                    <Button variant="ghost" size="sm" onClick={() => deleteFromCart(line.itemId)}>
                      Удалить
                    </Button>
                  </div>
                </div>
              ))}
            </div>

            <div className="space-y-1">
              <div className="flex flex-col sm:flex-row sm:items-center gap-2">
                <Input
                  type="text"
                  placeholder="Адрес доставки"
                  value={deliveryAddress}
                  onChange={(e) => {
                    setDeliveryAddress(e.target.value);
                    setAddressError(null);
                  }}
                  className={addressError ? 'border-red-500 focus-visible:ring-red-500' : undefined}
                  ref={addressInputRef}
                />
                {profileDefaultAddress.trim() && (
                  <Button
                    variant="outline"
                    size="md"
                    className="px-6 min-w-[140px] whitespace-nowrap"
                    onClick={() => {
                      setDeliveryAddress(profileDefaultAddress);
                      setAddressError(null);
                      addressInputRef.current?.focus();
                    }}
                  >
                    Из профиля
                  </Button>
                )}
              </div>
              {addressError ? (
                <p className="text-sm text-red-600">{addressError}</p>
              ) : (
                <p className="text-sm text-gray-500">Адрес нужен для доставки. Можно сохранить в профиле.</p>
              )}
            </div>
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <p className="font-semibold">Итого: {formatCurrency(totalPrice)}</p>
              </div>
              <Button onClick={startCheckout} disabled={isPlacingOrder}>
                {isPlacingOrder ? 'Оформление...' : 'Перейти к оплате'}
              </Button>
            </div>
          </div>
        </div>
      )}

      <Dialog open={showLoginDialog} onOpenChange={setShowLoginDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {loginDialogMode === 'loginRequired' ? 'Требуется вход' : 'Оформление недоступно'}
            </DialogTitle>
            <DialogDescription>
              {loginDialogMode === 'loginRequired'
                ? 'Для оформления заказа необходимо войти в систему.'
                : 'Оформлять заказы с сайта может только клиент. Войдите как клиент.'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowLoginDialog(false)}>
              Отмена
            </Button>
            {loginDialogMode === 'loginRequired' ? (
              <Button
                onClick={() => {
                  persistCheckoutDraft();
                  setShowLoginDialog(false);
                  navigate('/login?returnTo=/menu');
                }}
              >
                Войти
              </Button>
            ) : (
              <Button
                onClick={() => {
                  persistCheckoutDraft();
                  useAuthStore.getState().clearAuth();
                  setShowLoginDialog(false);
                  navigate('/login?returnTo=/menu');
                }}
              >
                Выйти и войти как клиент
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={showPaymentDialog}
        onOpenChange={(open) => {
          setShowPaymentDialog(open);
          if (!open) {
            setPaymentError(null);
            setCreatedOrderId(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Оформление заказа</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium mb-1">Способ оплаты</label>
              <Select
                value={paymentMethod}
                onChange={(e) => {
                  setPaymentMethod(e.target.value as PaymentMethod);
                  setPaymentError(null);
                }}
              >
                <option value={PaymentMethod.Online}>Онлайн</option>
                <option value={PaymentMethod.Cash}>При получении (наличные)</option>
                <option value={PaymentMethod.Card}>При получении (карта)</option>
              </Select>
            </div>
            {paymentMethod === PaymentMethod.Online && (
              <>
                <Input
                  placeholder="Номер карты"
                  value={cardData.number}
                  onChange={(e) => setCardData({ ...cardData, number: formatCardNumberInput(e.target.value) })}
                  inputMode="numeric"
                  autoComplete="cc-number"
                  maxLength={19}
                />
                <div className="flex gap-2">
                  <Input
                    placeholder="MM/YY"
                    value={cardData.expiry}
                    onChange={(e) => setCardData({ ...cardData, expiry: formatExpiryInput(e.target.value) })}
                    inputMode="numeric"
                    autoComplete="cc-exp"
                    maxLength={5}
                  />
                  <Input
                    placeholder="CVV"
                    value={cardData.cvv}
                    onChange={(e) => setCardData({ ...cardData, cvv: e.target.value.replace(/\D/g, '').slice(0, 4) })}
                    inputMode="numeric"
                    autoComplete="cc-csc"
                    maxLength={4}
                  />
                </div>
              </>
            )}
            {paymentError && <p className="text-red-600 text-sm">{paymentError}</p>}
          </div>
          <DialogFooter>
            {paymentError && createdOrderId && (
              <Button variant="destructive" onClick={cancelCreatedOrder} disabled={isPlacingOrder}>
                Отменить заказ
              </Button>
            )}
            <Button variant="outline" onClick={() => setShowPaymentDialog(false)}>
              Отмена
            </Button>
            <Button onClick={submitOrder} disabled={isPlacingOrder}>
              {isPlacingOrder
                ? paymentMethod === PaymentMethod.Online
                  ? 'Оплата...'
                  : 'Оформление...'
                : paymentMethod === PaymentMethod.Online
                  ? 'Оплатить'
                  : 'Создать заказ'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
