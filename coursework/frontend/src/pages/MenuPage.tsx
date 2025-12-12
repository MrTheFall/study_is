import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuApi, ordersApi, authApi, paymentsApi } from '@/api/client';
import { GetCurrentUser200ResponseUserTypeEnum, MenuItem, OrderType, PaymentMethod, OrderStatus } from '@/api/generated/api';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { formatCurrency } from '@/lib/utils';

export function MenuPage() {
  const navigate = useNavigate();
  const { isAuthenticated, isClient } = useAuthStore();
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [cart, setCart] = useState<Map<number, number>>(new Map());
  const [orderType, setOrderType] = useState<OrderType>(OrderType.Delivery);
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [isPlacingOrder, setIsPlacingOrder] = useState(false);
  const [showLoginDialog, setShowLoginDialog] = useState(false);
  const [showAddressDialog, setShowAddressDialog] = useState(false);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [cardData, setCardData] = useState({ number: '', expiry: '', cvv: '' });
  const [paymentError, setPaymentError] = useState<string | null>(null);

  useEffect(() => {
    loadMenu();
  }, []);

  const loadMenu = async () => {
    try {
      const response = await menuApi.getMenu(false);
      setMenuItems(response.data);
    } catch (error) {
      console.error('Ошибка загрузки меню:', error);
    } finally {
      setLoading(false);
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

  const getTotalPrice = () => {
    let total = 0;
    cart.forEach((quantity, itemId) => {
      const item = menuItems.find((i) => i.id === itemId);
      if (item && item.price) {
        total += item.price * quantity;
      }
    });
    return total;
  };

  const startCheckout = () => {
    if (cart.size === 0) return;

    if (!isAuthenticated) {
      setShowLoginDialog(true);
      return;
    }

    if (!isClient()) {
      alert('Только клиенты могут оформлять заказы. Войдите в систему как клиент.');
      useAuthStore.getState().clearAuth();
      setShowLoginDialog(true);
      return;
    }

    if (orderType === OrderType.Delivery && !deliveryAddress.trim()) {
      setShowAddressDialog(true);
      return;
    }

    setPaymentError(null);
    setShowPaymentDialog(true);
  };

  const placeOrder = async () => {
    if (cart.size === 0) return;
    if (!isAuthenticated || !isClient()) {
      setShowPaymentDialog(false);
      setShowLoginDialog(true);
      return;
    }
    if (orderType !== OrderType.Delivery) {
      setOrderType(OrderType.Delivery);
    }
    if (!deliveryAddress.trim()) {
      setShowPaymentDialog(false);
      setShowAddressDialog(true);
      return;
    }

    const sanitizedCardNumber = cardData.number.replace(/\s+/g, '');
    const sanitizedExpiry = cardData.expiry.trim();
    const sanitizedCvv = cardData.cvv.trim();

    if (!/^[0-9]{16}$/.test(sanitizedCardNumber)) {
      setPaymentError('Введите корректный номер карты (16 цифр)');
      return;
    }
    if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(sanitizedExpiry)) {
      setPaymentError('Срок действия в формате ММ/ГГ');
      return;
    }
    if (!/^[0-9]{3,4}$/.test(sanitizedCvv)) {
      setPaymentError('CVV должен содержать 3-4 цифры');
      return;
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
        } catch (err: any) {
          useAuthStore.getState().clearAuth();
          throw new Error('Не удалось загрузить информацию о пользователе. Пожалуйста, войдите заново.');
        }
      }

      if (user.userType !== GetCurrentUser200ResponseUserTypeEnum.Client) {
        useAuthStore.getState().clearAuth();
        throw new Error('Только клиенты могут оформлять заказы. Войдите как клиент.');
      }

      const items = Array.from(cart.entries()).map(([menuItemId, quantity]) => ({
        menuItemId,
        quantity,
      }));

      const requestData: any = {
        clientId: user.userId,
        type: OrderType.Delivery,
        items,
        deliveryAddress,
      };

      const orderResponse = await ordersApi.placeOrder(requestData);
      const orderId = orderResponse.data.orderId;
      if (!orderId) {
        throw new Error('Заказ создан, но не удалось получить его номер.');
      }

      await paymentsApi.processPayment({
        orderId,
        method: PaymentMethod.Card,
      });

      await ordersApi.updateOrderStatus(orderId, { status: OrderStatus.Confirmed });

      setCart(new Map());
      setShowPaymentDialog(false);
      alert('Оплата прошла успешно. Заказ отправлен на кухню.');
      navigate('/orders');
    } catch (error: any) {
      console.error('Order placement error:', error);
      const errorMessage = error.response?.data?.message || error.message || 'Ошибка оформления заказа';
      setPaymentError(errorMessage);
      alert(errorMessage);
    } finally {
      setIsPlacingOrder(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка меню...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            {isAuthenticated && (
              <Button variant="outline" onClick={() => navigate('/')}>
                ← На главную
              </Button>
            )}
            <h1 className="text-3xl font-bold">Меню</h1>
          </div>
          {!isAuthenticated && (
            <Button variant="outline" onClick={() => navigate('/login')}>
              Войти
            </Button>
          )}
        </div>
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
                  <span className="text-sm text-gray-500">
                    {item.available ? 'В наличии' : 'Нет в наличии'}
                  </span>
                </div>
                <div className="flex gap-2">
                  {item.id && cart.has(item.id) && (
                    <Button
                      variant="outline"
                      onClick={() => item.id && removeFromCart(item.id)}
                    >
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
                    <Button
                      variant="outline"
                      onClick={() => item.id && addToCart(item.id)}
                    >
                      +
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        {cart.size > 0 && (
          <div className="fixed bottom-0 left-0 right-0 bg-white border-t shadow-lg p-4">
            <div className="max-w-7xl mx-auto space-y-4">
              <input
                type="text"
                placeholder="Адрес доставки"
                value={deliveryAddress}
                onChange={(e) => setDeliveryAddress(e.target.value)}
                className="w-full px-4 py-2 border rounded-md"
              />
              <div className="flex justify-between items-center">
                <div>
                  <p className="font-semibold">Итого: {formatCurrency(getTotalPrice())}</p>
                  <p className="text-sm text-gray-500">
                    Товаров в корзине: {Array.from(cart.values()).reduce((a, b) => a + b, 0)}
                  </p>
                  <p className="text-sm text-gray-500">
                    Заказы с сайта — только доставка и оплата картой онлайн.
                  </p>
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
              <DialogTitle>Требуется вход</DialogTitle>
              <DialogDescription>
                Для оформления заказа необходимо войти в систему.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowLoginDialog(false)}>
                Отмена
              </Button>
              <Button onClick={() => {
                setShowLoginDialog(false);
                navigate('/login');
              }}>
                Войти
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        <Dialog open={showAddressDialog} onOpenChange={setShowAddressDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Укажите адрес доставки</DialogTitle>
              <DialogDescription>
                Для заказа с доставкой необходимо указать адрес.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowAddressDialog(false)}>
                Закрыть
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        <Dialog
          open={showPaymentDialog}
          onOpenChange={(open) => {
            setShowPaymentDialog(open);
            if (!open) {
              setPaymentError(null);
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Оплата картой</DialogTitle>
              <DialogDescription>
                Оплата проходит в тестовом режиме. После подтверждения заказ сразу уйдет на кухню.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <Input
                placeholder="Номер карты"
                value={cardData.number}
                onChange={(e) => setCardData({ ...cardData, number: e.target.value })}
              />
              <div className="flex gap-2">
                <Input
                  placeholder="MM/YY"
                  value={cardData.expiry}
                  onChange={(e) => setCardData({ ...cardData, expiry: e.target.value })}
                />
                <Input
                  placeholder="CVV"
                  value={cardData.cvv}
                  onChange={(e) => setCardData({ ...cardData, cvv: e.target.value })}
                />
              </div>
              {paymentError && (
                <p className="text-red-600 text-sm">{paymentError}</p>
              )}
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowPaymentDialog(false)}>
                Отмена
              </Button>
              <Button onClick={placeOrder} disabled={isPlacingOrder}>
                {isPlacingOrder ? 'Оплата...' : 'Оплатить и отправить'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  );
}
