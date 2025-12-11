import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ordersApi, authApi, paymentsApi } from '@/api/client';
import { Order, OrderStatus, PaymentMethod } from '@/api/generated/api';
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

  useEffect(() => {
    loadOrders();
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
              const canProcessPayment = isCashier() && statusStr !== 'cancelled' && statusStr !== 'delivered';
              const canUpdateStatus = (isCashier() || isManager()) && statusStr !== 'cancelled' && statusStr !== 'delivered';
              
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
                          <Button variant="outline" onClick={() => handleUpdateStatus(order.id!, OrderStatus.Delivered)}>
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

