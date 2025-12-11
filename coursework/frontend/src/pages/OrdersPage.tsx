import { useEffect, useState } from 'react';
import { ordersApi } from '@/api/client';
import { Order } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';

export function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const { isClient } = useAuthStore();

  useEffect(() => {
    loadOrders();
  }, []);

  const loadOrders = async () => {
    try {
      if (isClient()) {
        // Для клиента - получить его заказы
        const user = useAuthStore.getState().user;
        if (user?.userId) {
          const response = await ordersApi.getClientOrders(user.userId);
          setOrders(response.data);
        }
      } else {
        // Для сотрудника - все заказы
        const response = await ordersApi.getAllOrders();
        setOrders(response.data);
      }
    } catch (error) {
      console.error('Ошибка загрузки заказов:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка заказов...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-3xl font-bold mb-8">Заказы</h1>
        <div className="space-y-4">
          {orders.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <p className="text-center text-gray-500">Заказов пока нет</p>
              </CardContent>
            </Card>
          ) : (
            orders.map((order) => (
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
                        order.status === 'COMPLETED' ? 'bg-green-100 text-green-800' :
                        order.status === 'CANCELLED' ? 'bg-red-100 text-red-800' :
                        'bg-yellow-100 text-yellow-800'
                      }`}>
                        {order.status}
                      </span>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    <p><strong>Тип:</strong> {order.orderType}</p>
                    <p><strong>Адрес:</strong> {order.deliveryAddress || 'В ресторане'}</p>
                    {order.items && order.items.length > 0 && (
                      <div>
                        <strong>Блюда:</strong>
                        <ul className="list-disc list-inside ml-4">
                          {order.items.map((item, idx) => (
                            <li key={idx}>
                              {item.menuItemName} x{item.quantity} - {formatCurrency(item.price! * item.quantity!)}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

