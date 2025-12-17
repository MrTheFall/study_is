import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ordersApi, authApi, paymentsApi, menuApi, clientsApi, couriersApi } from '@/api/client';
import { Courier, MenuItem, Order, OrderItem, OrderStatus, OrderType, PaymentMethod } from '@/api/generated/api';
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
  const [error, setError] = useState<string | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(PaymentMethod.Card);
  const [amountReceived, setAmountReceived] = useState('');
  const { isClient, isCashier, isManager } = useAuthStore();
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [cashierCart, setCashierCart] = useState<Map<number, number>>(new Map());
  const [cashierPaymentMethod, setCashierPaymentMethod] = useState<PaymentMethod>(PaymentMethod.Cash);
  const [cashierAmountReceived, setCashierAmountReceived] = useState('');
  const [cashierOrderType, setCashierOrderType] = useState<OrderType>(OrderType.DineIn);
  const [customerIdInput, setCustomerIdInput] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [isCreatingWalkin, setIsCreatingWalkin] = useState(false);
  const [orderItemsByOrderId, setOrderItemsByOrderId] = useState<Record<number, OrderItem[]>>({});

  useEffect(() => {
    loadOrders();
    if (isCashier()) {
      loadMenu();
    }
    if (isManager()) {
      loadCouriers();
    }
  }, []);

  const loadOrders = async () => {
    try {
      setError(null);
      if (isClient()) {
        let user = useAuthStore.getState().user;
        if (!user?.userId) {
          try {
            const userResponse = await authApi.getCurrentUser();
            useAuthStore.getState().setAuth(useAuthStore.getState().token || '', userResponse.data);
            user = userResponse.data;
          } catch (err) {
            console.error('Failed to load user info:', err);
            setError('Не удалось загрузить информацию о пользователе');
            return;
          }
        }
        
        if (user?.userId) {
          const response = await ordersApi.getAllOrders(undefined, user.userId);
          const list = response.data || [];
          setOrders(list);
          void preloadOrderItems(list);
        }
      } else {
        const response = await ordersApi.getAllOrders();
        const list = response.data || [];
        setOrders(list);
        void preloadOrderItems(list);
      }
    } catch (error: any) {
      console.error('Ошибка загрузки заказов:', error);
      console.error('Error details:', error.response?.data);
      setError(error.response?.data?.message || 'Ошибка загрузки заказов');
    } finally {
      setLoading(false);
    }
  };

  const loadCouriers = async () => {
    try {
      const response = await couriersApi.getAllCouriers();
      setCouriers(response.data || []);
    } catch (error) {
      console.error('Не удалось загрузить курьеров:', error);
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

  const preloadOrderItems = async (ordersList: Order[]) => {
    const orderIds = ordersList
      .map((order) => order.id)
      .filter((id): id is number => typeof id === 'number');

    if (orderIds.length === 0) return;

    const results = await Promise.all(
      orderIds.map(async (orderId) => {
        try {
          const resp = await ordersApi.getOrderItems(orderId);
          return { orderId, items: resp.data || [] };
        } catch (error) {
          console.error(`Не удалось загрузить позиции заказа #${orderId}:`, error);
          return { orderId, items: [] as OrderItem[] };
        }
      })
    );

    setOrderItemsByOrderId((prev) => {
      const next: Record<number, OrderItem[]> = { ...prev };
      for (const result of results) {
        next[result.orderId] = result.items;
      }
      return next;
    });
  };

  const handleProcessPayment = async () => {
    if (!selectedOrder?.id) return;
    
    try {
      if (paymentMethod === PaymentMethod.Cash) {
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

      const statusValue =
        typeof selectedOrder.status === 'string'
          ? selectedOrder.status
          : (selectedOrder.status as any)?.value || 'pending';
      const statusStr = String(statusValue);
      if ((isCashier() || isManager()) && statusStr === 'pending') {
        await ordersApi.updateOrderStatus(selectedOrder.id, { status: OrderStatus.Confirmed });
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

    if (cashierPaymentMethod === PaymentMethod.Cash && !cashierAmountReceived) {
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
        paymentMethod: cashierPaymentMethod,
        items,
      });
      const orderId = orderResp.data.orderId;
      if (!orderId) {
        throw new Error('Не удалось получить номер заказа');
      }

      if (cashierPaymentMethod === PaymentMethod.Cash) {
        const resp = await paymentsApi.processCashPayment({
          orderId,
          amountReceived: parseFloat(cashierAmountReceived),
        });
        alert(`Платеж обработан. Сдача: ${formatCurrency(resp.data.change!)}`);
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

        {error && (
          <Card className="mb-8">
            <CardContent className="pt-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <p className="text-red-600">{error}</p>
              <Button
                variant="outline"
                onClick={() => {
                  setLoading(true);
                  loadOrders();
                }}
              >
                Повторить
              </Button>
            </CardContent>
          </Card>
        )}

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
                  <label className="block text-sm font-medium mb-1">Тип заказа</label>
                  <select
                    className="w-full h-10 rounded-md border border-gray-300 px-3"
                    value={cashierOrderType}
                    onChange={(e) => setCashierOrderType(e.target.value as OrderType)}
                  >
                    <option value={OrderType.DineIn}>В зале (dine_in)</option>
                    <option value={OrderType.Takeout}>С собой (takeout)</option>
                  </select>
                </div>
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
                    <option value={PaymentMethod.Cash}>Наличные</option>
                    <option value={PaymentMethod.Card}>Карта</option>
                  </select>
                </div>
                {cashierPaymentMethod === PaymentMethod.Cash && (
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
                    <p className="text-sm text-gray-500">Тип: {cashierOrderType}</p>
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
              const isOnlinePayment = order.paymentMethod === PaymentMethod.Online;
              const isDeliveryOrder = order.type === OrderType.Delivery;
              const assignedCourierId = order.courierId ?? order.courier?.id ?? null;
              const hasCourier = assignedCourierId !== null && assignedCourierId !== undefined;
              const canAssignCourier = isManager()
                && isDeliveryOrder
                && statusStr !== 'cancelled'
                && statusStr !== 'completed'
                && statusStr !== 'delivered';
              const canEmployeeProcessPayment =
                (isCashier() || isManager())
                && !isOnlinePayment
                && (isDeliveryOrder ? statusStr === 'delivered' : statusStr === 'pending');
              const canClientPayOnline = isClient() && statusStr === 'pending' && isOnlinePayment;
              const canEmployeeConfirm = (isCashier() || isManager()) && statusStr === 'pending' && isDeliveryOrder && !isOnlinePayment;
              const canEmployeeCancel = (isCashier() || isManager()) && (statusStr === 'pending' || statusStr === 'confirmed');
              const canClientCancel = isClient() && statusStr === 'pending';
              const canUpdateStatus = (isCashier() || isManager()) && statusStr !== 'cancelled' && statusStr !== 'completed';
              const items = order.id ? orderItemsByOrderId[order.id] : undefined;
              
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
                      {isDeliveryOrder && (
                        <p>
                          <strong>Курьер:</strong>{' '}
                          {order.courier
                            ? `${order.courier.name || `Курьер #${order.courier.id}`}${order.courier.phone ? ` (${order.courier.phone})` : ''}`
                            : hasCourier
                              ? `Курьер #${assignedCourierId}`
                              : '—'}
                        </p>
                      )}
                      {canAssignCourier && (
                        <div className="max-w-md">
                          <label className="block text-sm font-medium mb-1">Назначить курьера</label>
                          <select
                            className="w-full h-10 rounded-md border border-gray-300 px-3"
                            value={assignedCourierId ?? ''}
                            onChange={async (e) => {
                              const value = e.target.value;
                              const courierId = value ? parseInt(value, 10) : null;
                              try {
                                await ordersApi.assignCourierToOrder(order.id!, { courierId });
                                await loadOrders();
                                await loadCouriers();
                              } catch (error: any) {
                                console.error('Ошибка назначения курьера:', error);
                                alert(error.response?.data?.message || 'Ошибка назначения курьера');
                              }
                            }}
                          >
                            <option value="">— не назначен —</option>
                            {couriers.map((courier) => {
                              const id = courier.id;
                              if (!id) return null;
                              const disabled =
                                (courier.available === false || courier.busy === true) && id !== assignedCourierId;
                              const statusLabel = courier.busy
                                ? 'занят'
                                : courier.available === false
                                  ? 'недоступен'
                                  : 'свободен';
                              return (
                                <option key={id} value={id} disabled={disabled}>
                                  {courier.name || `Курьер #${id}`} ({statusLabel})
                                </option>
                              );
                            })}
                          </select>
                          <p className="mt-1 text-xs text-gray-500">
                            Для передачи в доставку курьер должен быть назначен.
                          </p>
                        </div>
                      )}
                      <p>
                        <strong>Оплата:</strong>{' '}
                        {order.paymentMethod === PaymentMethod.Online
                          ? 'онлайн'
                          : order.paymentMethod === PaymentMethod.Cash
                            ? 'при получении (наличные)'
                            : order.paymentMethod === PaymentMethod.Card
                              ? 'при получении (карта)'
                              : '—'}
                      </p>
                      {order.preparingAt && (
                        <p><strong>Начало приготовления:</strong> {formatDate(order.preparingAt)}</p>
                      )}
                      {order.readyAt && (
                        <p><strong>Готово:</strong> {formatDate(order.readyAt)}</p>
                      )}
                      {order.deliveredAt && (
                        <p><strong>Доставлено:</strong> {formatDate(order.deliveredAt)}</p>
                      )}
                      <div>
                        <p className="font-semibold">Позиции:</p>
                        {items ? (
                          items.length > 0 ? (
                            <ul className="text-sm text-gray-700 space-y-1">
                              {items.map((item) => (
                                <li key={item.id ?? `${item.menuItemId}-${item.unitPrice}`}>
                                  {item.name || `Блюдо #${item.menuItemId}`}{' '}
                                  — {item.quantity} × {formatCurrency(item.unitPrice || 0)}
                                  {item.note ? ` (${item.note})` : ''}
                                </li>
                              ))}
                            </ul>
                          ) : (
                            <p className="text-sm text-gray-500">—</p>
                          )
                        ) : (
                          <p className="text-sm text-gray-500">загрузка...</p>
                        )}
                      </div>
                      {(isCashier() || isManager()) && (
                        <>
                          <p>
                            <strong>Создатель:</strong>{' '}
                            {order.createdByEmployeeId ? `сотрудник #${order.createdByEmployeeId}` : 'клиент'}
                          </p>
                          <p>
                            <strong>Принял:</strong>{' '}
                            {order.acceptedByEmployeeId ? `сотрудник #${order.acceptedByEmployeeId}` : '—'}
                          </p>
                        </>
                      )}
                    </div>
                    {(canEmployeeProcessPayment || canClientPayOnline || canEmployeeConfirm || canEmployeeCancel || canClientCancel || canUpdateStatus) && (
                      <div className="mt-4 flex gap-2">
                        {canEmployeeProcessPayment && (
                          <Button onClick={() => {
                            setSelectedOrder(order);
                            setPaymentMethod(order.paymentMethod === PaymentMethod.Cash || order.paymentMethod === PaymentMethod.Card
                              ? order.paymentMethod
                              : PaymentMethod.Card);
                            setShowPaymentDialog(true);
                          }}>
                            Обработать платеж
                          </Button>
                        )}
                        {canClientPayOnline && (
                          <Button onClick={async () => {
                            try {
                              await paymentsApi.processPayment({ orderId: order.id!, method: PaymentMethod.Online });
                              alert('Оплата прошла успешно. Заказ отправлен на кухню.');
                              loadOrders();
                            } catch (error: any) {
                              console.error('Ошибка онлайн-оплаты:', error);
                              alert(error.response?.data?.message || 'Ошибка онлайн-оплаты');
                            }
                          }}>
                            Оплатить онлайн
                          </Button>
                        )}
                        {canEmployeeConfirm && (
                          <Button
                            variant="outline"
                            onClick={() => handleUpdateStatus(order.id!, OrderStatus.Confirmed)}
                          >
                            Подтвердить
                          </Button>
                        )}
                        {canEmployeeCancel && (
                          <Button
                            variant="destructive"
                            onClick={() => handleUpdateStatus(order.id!, OrderStatus.Cancelled)}
                          >
                            Отменить
                          </Button>
                        )}
                        {canClientCancel && (
                          <Button
                            variant="destructive"
                            onClick={() => handleUpdateStatus(order.id!, OrderStatus.Cancelled)}
                          >
                            Отменить
                          </Button>
                        )}
                        {canUpdateStatus && statusStr === 'ready' && (
                          <Button
                            variant="outline"
                            disabled={isDeliveryOrder && !hasCourier}
                            title={isDeliveryOrder && !hasCourier ? 'Назначьте курьера перед передачей в доставку' : undefined}
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
                        {canUpdateStatus && isDeliveryOrder && statusStr === 'delivered' && (
                          <Button
                            variant="outline"
                            onClick={() => handleUpdateStatus(order.id!, OrderStatus.Completed)}
                          >
                            Закрыть заказ
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
                  {isClient() ? (
                    <option value={PaymentMethod.Online}>Онлайн</option>
                  ) : selectedOrder.paymentMethod === PaymentMethod.Cash ? (
                    <option value={PaymentMethod.Cash}>Наличные</option>
                  ) : selectedOrder.paymentMethod === PaymentMethod.Card ? (
                    <option value={PaymentMethod.Card}>Карта</option>
                  ) : (
                    <>
                      <option value={PaymentMethod.Card}>Карта</option>
                      <option value={PaymentMethod.Cash}>Наличные</option>
                    </>
                  )}
                </select>
              </div>
              {paymentMethod === PaymentMethod.Cash && (
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
