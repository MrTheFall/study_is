import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ordersApi, authApi } from '@/api/client';
import { Order } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';

export function OrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const { isClient } = useAuthStore();

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
                        order.status === 'completed' ? 'bg-green-100 text-green-800' :
                        order.status === 'cancelled' ? 'bg-red-100 text-red-800' :
                        'bg-yellow-100 text-yellow-800'
                      }`}>
                        {order.status}
                      </span>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    <p><strong>Тип:</strong> {order.type || 'Не указан'}</p>
                    <p><strong>Адрес:</strong> {order.deliveryAddress || 'В ресторане'}</p>
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

