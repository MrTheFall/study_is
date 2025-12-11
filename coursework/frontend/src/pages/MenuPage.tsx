import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuApi, ordersApi } from '@/api/client';
import { MenuItem, OrderType } from '@/api/generated/api';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { formatCurrency } from '@/lib/utils';

export function MenuPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [cart, setCart] = useState<Map<number, number>>(new Map());
  const [orderType, setOrderType] = useState<OrderType>(OrderType.DELIVERY);
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [isPlacingOrder, setIsPlacingOrder] = useState(false);
  const [showLoginDialog, setShowLoginDialog] = useState(false);
  const [showAddressDialog, setShowAddressDialog] = useState(false);

  useEffect(() => {
    loadMenu();
  }, []);

  const loadMenu = async () => {
    try {
      // Передаем available=false чтобы показать все блюда (включая недоступные)
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
      if (item) {
        total += item.price * quantity;
      }
    });
    return total;
  };

  const placeOrder = async () => {
    if (cart.size === 0) return;
    
    // Проверяем авторизацию перед оформлением заказа
    if (!isAuthenticated) {
      setShowLoginDialog(true);
      return;
    }
    
    if (orderType === OrderType.DELIVERY && !deliveryAddress.trim()) {
      setShowAddressDialog(true);
      return;
    }

    setIsPlacingOrder(true);
    try {
      const items = Array.from(cart.entries()).map(([menuItemId, quantity]) => ({
        menuItemId,
        quantity,
      }));

      const response = await ordersApi.placeOrder({
        items,
        orderType,
        deliveryAddress: orderType === OrderType.DELIVERY ? deliveryAddress : undefined,
      });

      setCart(new Map());
      navigate(`/orders`);
    } catch (error: any) {
      alert(error.response?.data?.message || 'Ошибка создания заказа');
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
          <h1 className="text-3xl font-bold">Меню</h1>
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
                  <span className="text-2xl font-bold">{formatCurrency(item.price)}</span>
                  <span className="text-sm text-gray-500">
                    {item.available ? 'В наличии' : 'Нет в наличии'}
                  </span>
                </div>
                <div className="flex gap-2">
                  {cart.has(item.id) && (
                    <Button
                      variant="outline"
                      onClick={() => removeFromCart(item.id!)}
                    >
                      -
                    </Button>
                  )}
                  <Button
                    onClick={() => addToCart(item.id!)}
                    disabled={!item.available}
                    className="flex-1"
                  >
                    {cart.has(item.id) ? `В корзине: ${cart.get(item.id)}` : 'Добавить'}
                  </Button>
                  {cart.has(item.id) && (
                    <Button
                      variant="outline"
                      onClick={() => addToCart(item.id!)}
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
              <div className="flex gap-4">
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={orderType === OrderType.DELIVERY}
                    onChange={() => setOrderType(OrderType.DELIVERY)}
                  />
                  Доставка
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={orderType === OrderType.TAKEOUT}
                    onChange={() => setOrderType(OrderType.TAKEOUT)}
                  />
                  На вынос
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    checked={orderType === OrderType.DINE_IN}
                    onChange={() => setOrderType(OrderType.DINE_IN)}
                  />
                  В зале
                </label>
              </div>
              {orderType === OrderType.DELIVERY && (
                <input
                  type="text"
                  placeholder="Адрес доставки"
                  value={deliveryAddress}
                  onChange={(e) => setDeliveryAddress(e.target.value)}
                  className="w-full px-4 py-2 border rounded-md"
                />
              )}
              <div className="flex justify-between items-center">
                <div>
                  <p className="font-semibold">Итого: {formatCurrency(getTotalPrice())}</p>
                  <p className="text-sm text-gray-500">
                    Товаров в корзине: {Array.from(cart.values()).reduce((a, b) => a + b, 0)}
                  </p>
                </div>
                <Button onClick={placeOrder} disabled={isPlacingOrder}>
                  {isPlacingOrder ? 'Оформление...' : 'Оформить заказ'}
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* Диалог для входа */}
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

        {/* Диалог для адреса доставки */}
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
      </div>
    </div>
  );
}

