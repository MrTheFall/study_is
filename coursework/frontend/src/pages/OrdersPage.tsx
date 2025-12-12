import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ordersApi, authApi, paymentsApi, menuApi, clientsApi } from '@/api/client';
import { MenuItem, Order, OrderStatus, OrderType, PaymentMethod } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';

export function OrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('card');
  const [amountReceived, setAmountReceived] = useState('');
  const { isClient, isCashier, isManager } = useAuthStore();
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [cashierCart, setCashierCart] = useState<Map<number, number>>(new Map());
  const [cashierPaymentMethod, setCashierPaymentMethod] = useState<PaymentMethod>('cash');
  const [cashierAmountReceived, setCashierAmountReceived] = useState('');
  const cashierOrderType: OrderType = OrderType.DineIn;
  const [customerIdInput, setCustomerIdInput] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [isCreatingWalkin, setIsCreatingWalkin] = useState(false);

  useEffect(() => {
    loadOrders();
    if (isCashier()) {
      loadMenu();
    }
  }, []);

  const loadOrders = async () => {
    try {
      if (isClient()) {
        let user = useAuthStore.getState().user;
        if (!user?.userId) {
          try {
            const userResponse = await authApi.getCurrentUser();
            useAuthStore.getState().setAuth(useAuthStore.getState().token || '', userResponse.data);
            user = userResponse.data;
          } catch (err) {
            console.error('Failed to load user info:', err);
            return;
          }
        }
        
        if (user?.userId) {
          const response = await ordersApi.getAllOrders(undefined, user.userId);
          setOrders(response.data || []);
        }
      } else {
        const response = await ordersApi.getAllOrders();
        setOrders(response.data || []);
      }
    } catch (error: any) {
      console.error('Ошибка загрузки заказов:', error);
      console.error('Error details:', error.response?.data);
    } finally {
      setLoading(false);
    }
  };

  const loadMenu = async () => {
    try {
      const response = await menuApi.getMenu(false);
      setMenuItems(response.data || []);
    } catch (error) {
      console.error('Не удалось загрузить меню для кассира:', error);
    }
  };

  const handleProcessPayment = async () => {
    if (!selectedOrder?.id) return;
    
    try {
      if (paymentMethod === 'cash') {
        if (!amountReceived) {
          alert('Введите полученную сумму');
          return;
        }
        const response = await paymentsApi.processCashPayment({
          orderId: selectedOrder.id,
          amountReceived: parseFloat(amountReceived),
        });
        alert(`Платеж обработан. Сдача: ${formatCurrency(response.data.change!)}`);
      } else {
        await paymentsApi.processPayment({
          orderId: selectedOrder.id,
          method: paymentMethod,
        });
        alert('Платеж обработан успешно');
      }
      setShowPaymentDialog(false);
      setSelectedOrder(null);
      setAmountReceived('');
      loadOrders();
    } catch (error: any) {
      console.error('Ошибка обработки платежа:', error);
      alert(error.response?.data?.message || 'Ошибка обработки платежа');
    }
  };

  const addCashierItem = (itemId: number) => {
    setCashierCart((prev) => {
      const next = new Map(prev);
      next.set(itemId, (next.get(itemId) || 0) + 1);
      return next;
    });
  };

  const removeCashierItem = (itemId: number) => {
    setCashierCart((prev) => {
      const next = new Map(prev);
      const count = next.get(itemId) || 0;
      if (count > 1) {
        next.set(itemId, count - 1);
      } else {
        next.delete(itemId);
      }
      return next;
    });
  };

  const getCashierTotal = () => {
    let total = 0;
    cashierCart.forEach((qty, id) => {
      const item = menuItems.find((i) => i.id === id);
      if (item?.price) {
        total += item.price * qty;
      }
    });
    return total;
  };

  const ensureClientId = async (): Promise<number> => {
    if (customerIdInput.trim()) {
      const parsed = parseInt(customerIdInput.trim(), 10);
      if (!Number.isNaN(parsed)) {
        return parsed;
      }
    }

    const name = customerName.trim() || 'Гость';
    const phone = customerPhone.trim() || `guest-${Date.now()}`;
    const email = `guest+${Date.now()}@guest.local`;
    const password = `Guest${Date.now().toString().slice(-6)}`;

    setIsCreatingWalkin(true);
    try {
      const response = await clientsApi.registerClient({
        name,
        phone,
        email,
        password,
        defaultAddress: 'В ресторане',
      });
      return response.data.id!;
    } finally {
      setIsCreatingWalkin(false);
    }
  };

  const createCashierOrder = async () => {
    if (!isCashier()) return;
    if (cashierCart.size === 0) {
      alert('Выберите хотя бы одно блюдо.');
      return;
    }

    if (cashierPaymentMethod === 'cash' && !cashierAmountReceived) {
      alert('Введите сумму, полученную наличными.');
      return;
    }

    const clientId = await ensureClientId();

    const items = Array.from(cashierCart.entries()).map(([menuItemId, quantity]) => ({
      menuItemId,
      quantity,
    }));

    try {
      const orderResp = await ordersApi.placeOrder({
        clientId,
        type: cashierOrderType,
        items,
      });
      const orderId = orderResp.data.orderId;
      if (!orderId) {
        throw new Error('Не удалось получить номер заказа');
      }

      if (cashierPaymentMethod === 'cash') {
        await paymentsApi.processCashPayment({
          orderId,
          amountReceived: parseFloat(cashierAmountReceived),
        });
      } else {
        await paymentsApi.processPayment({
          orderId,
          method: cashierPaymentMethod,
        });
      }

      await ordersApi.updateOrderStatus(orderId, { status: OrderStatus.Confirmed });
      setCashierCart(new Map());
      setCashierAmountReceived('');
      setCustomerIdInput('');
      setCustomerName('');
      setCustomerPhone('');
      alert('Заказ принят, оплачен и отправлен на кухню.');
      loadOrders();
    } catch (error: any) {
      console.error('Ошибка создания заказа на кассе:', error);
      alert(error.response?.data?.message || error.message || 'Не удалось создать заказ');
    }
  };

  const handleUpdateStatus = async (orderId: number, status: OrderStatus) => {
    try {
      await ordersApi.updateOrderStatus(orderId, { status });
      loadOrders();
    } catch (error: any) {
      console.error('Ошибка обновления статуса:', error);
      alert(error.response?.data?.message || 'Ошибка обновления статуса');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка заказов...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-4 mb-8">
          <Button variant="outline" onClick={() => navigate('/')}>
            ← На главную
          </Button>
          <h1 className="text-3xl font-bold">Заказы</h1>
        </div>

        {isCashier() && (
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Новый заказ в зале</CardTitle>
              <CardDescription>Выберите блюда, примите оплату (наличные/карта) и отправьте заказ на кухню.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1">ID клиента (если известен)</label>
                  <Input
                    placeholder="Например, 1"
                    value={customerIdInput}
                    onChange={(e) => setCustomerIdInput(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Имя гостя</label>
                  <Input
                    placeholder="Гость"
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Телефон гостя</label>
                  <Input
                    placeholder="+7..."
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(e.target.value)}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {menuItems.length === 0 ? (
                  <div className="col-span-full text-sm text-gray-500">Меню загружается...</div>
                ) : (
                  menuItems.map((item) => (
                    <Card key={item.id}>
                      <CardHeader>
                        <CardTitle className="text-lg">{item.name}</CardTitle>
                        <CardDescription>{formatCurrency(item.price || 0)}</CardDescription>
                      </CardHeader>
                      <CardContent>
                        <div className="flex items-center gap-2">
                          <Button
                            variant="outline"
                            onClick={() => item.id && removeCashierItem(item.id)}
                            disabled={!item.id || !cashierCart.has(item.id)}
                          >
                            -
                          </Button>
                          <div className="min-w-[80px] text-center">
                            {item.id && cashierCart.get(item.id) ? `${cashierCart.get(item.id)} шт.` : '0'}
                          </div>
                          <Button
                            onClick={() => item.id && addCashierItem(item.id)}
                            disabled={!item.id || !item.available}
                          >
                            +
                          </Button>
                        </div>
                        <p className="text-xs text-gray-500 mt-2">
                          {item.available ? 'В наличии' : 'Нет в наличии'}
                        </p>
                      </CardContent>
                    </Card>
                  ))
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                <div>
                  <label className="block text-sm font-medium mb-1">Оплата</label>
                  <select
                    className="w-full h-10 rounded-md border border-gray-300 px-3"
                    value={cashierPaymentMethod}
                    onChange={(e) => {
                      setCashierPaymentMethod(e.target.value as PaymentMethod);
                      setCashierAmountReceived('');
                    }}
                  >
                    <option value="cash">Наличные</option>
                    <option value="card">Карта</option>
                  </select>
                </div>
                {cashierPaymentMethod === 'cash' && (
                  <div>
                    <label className="block text-sm font-medium mb-1">Получено наличными</label>
                    <Input
                      type="number"
                      step="0.01"
                      placeholder="Например, 500"
                      value={cashierAmountReceived}
                      onChange={(e) => setCashierAmountReceived(e.target.value)}
                    />
                  </div>
                )}
                <div className="flex flex-col items-start md:items-end gap-2">
                  <div>
                    <p className="font-semibold">Итого: {formatCurrency(getCashierTotal())}</p>
                    <p className="text-sm text-gray-500">Тип: в зале (dine_in)</p>
                    {isCreatingWalkin && <p className="text-xs text-gray-500">Создаем гостя...</p>}
                  </div>
                  <Button onClick={createCashierOrder} disabled={isCreatingWalkin}>
                    Принять оплату и отправить на кухню
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        <div className="space-y-4">
          {orders.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <p className="text-center text-gray-500">Заказов пока нет</p>
              </CardContent>
            </Card>
          ) : (
            orders.map((order) => {
              const statusValue = typeof order.status === 'string' 
                ? order.status 
                : (order.status as any)?.value || 'pending';
              const statusStr = String(statusValue);
              const canProcessPayment = isCashier() && statusStr === 'pending';
              const canUpdateStatus = (isCashier() || isManager()) && statusStr !== 'cancelled' && statusStr !== 'delivered' && statusStr !== 'completed';
              const isDeliveryOrder = order.type === OrderType.Delivery;
              
              return (
                <Card key={order.id}>
                  <CardHeader>
                    <div className="flex justify-between items-start">
                      <div>
                        <CardTitle>Заказ #{order.id}</CardTitle>
                        <CardDescription>
                          {formatDate(order.createdAt!)}
                        </CardDescription>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl font-bold">{formatCurrency(order.totalAmount!)}</div>
                        <span className={`inline-block px-2 py-1 rounded text-sm ${
                          statusStr === 'delivered' || statusStr === 'completed' ? 'bg-green-100 text-green-800' :
                          statusStr === 'cancelled' ? 'bg-red-100 text-red-800' :
                          'bg-yellow-100 text-yellow-800'
                        }`}>
                          {statusStr}
                        </span>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-2">
                      <p><strong>Тип:</strong> {order.type || 'Не указан'}</p>
                      <p><strong>Адрес:</strong> {order.deliveryAddress || 'В ресторане'}</p>
                    </div>
                    {(canProcessPayment || canUpdateStatus) && (
                      <div className="mt-4 flex gap-2">
                        {canProcessPayment && (
                          <Button onClick={() => {
                            setSelectedOrder(order);
                            setShowPaymentDialog(true);
                          }}>
                            Обработать платеж
                          </Button>
                        )}
                        {canUpdateStatus && statusStr === 'ready' && (
                          <Button
                            variant="outline"
                            onClick={() =>
                              handleUpdateStatus(
                                order.id!,
                                isDeliveryOrder ? OrderStatus.Delivering : OrderStatus.Completed
                              )
                            }
                          >
                            {isDeliveryOrder ? 'Передать в доставку' : 'Отметить как выдан'}
                          </Button>
                        )}
                        {canUpdateStatus && isDeliveryOrder && statusStr === 'delivering' && (
                          <Button
                            variant="outline"
                            onClick={() => handleUpdateStatus(order.id!, OrderStatus.Delivered)}
                          >
                            Отметить как доставлен
                          </Button>
                        )}
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })
          )}
        </div>
      </div>

      {showPaymentDialog && selectedOrder && (
        <Dialog
          open={showPaymentDialog}
          onOpenChange={(open) => {
            if (!open) {
              setShowPaymentDialog(false);
              setSelectedOrder(null);
              setAmountReceived('');
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Обработка платежа</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <p><strong>Заказ #{selectedOrder.id}</strong></p>
              <p>Сумма: {formatCurrency(selectedOrder.totalAmount!)}</p>
              <div>
                <label className="block text-sm font-medium mb-1">Способ оплаты</label>
                <select
                  className="w-full h-10 rounded-md border border-gray-300 px-3"
                  value={paymentMethod}
                  onChange={(e) => {
                    setPaymentMethod(e.target.value as PaymentMethod);
                    setAmountReceived('');
                  }}
                >
                  <option value="card">Карта</option>
                  <option value="cash">Наличные</option>
                  <option value="online">Онлайн</option>
                </select>
              </div>
              {paymentMethod === 'cash' && (
                <div>
                  <label className="block text-sm font-medium mb-1">Полученная сумма</label>
                  <Input
                    type="number"
                    step="0.01"
                    value={amountReceived}
                    onChange={(e) => setAmountReceived(e.target.value)}
                    placeholder="Введите сумму"
                  />
                </div>
              )}
              <div className="flex gap-2 justify-end">
                <Button variant="outline" onClick={() => {
                  setShowPaymentDialog(false);
                  setSelectedOrder(null);
                  setAmountReceived('');
                }}>
                  Отмена
                </Button>
                <Button onClick={handleProcessPayment}>
                  Обработать
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
