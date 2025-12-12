import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { kitchenApi, ordersApi } from '@/api/client';
import { KitchenQueueItem, OrderStatus } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { formatDate } from '@/lib/utils';

export function KitchenPage() {
  const navigate = useNavigate();
  const [queue, setQueue] = useState<KitchenQueueItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadQueue();
    const interval = setInterval(loadQueue, 5000); 
    return () => clearInterval(interval);
  }, []);

  const loadQueue = async () => {
    try {
      const response = await kitchenApi.getKitchenQueue();
      setQueue(response.data);
    } catch (error) {
      console.error('Ошибка загрузки очереди:', error);
    } finally {
      setLoading(false);
    }
  };

  const updateStatus = async (orderId: number, status: OrderStatus) => {
    try {
      await ordersApi.updateOrderStatus(orderId, { status });
      loadQueue();
    } catch (error) {
      console.error('Ошибка обновления статуса:', error);
      alert('Ошибка обновления статуса заказа');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка очереди...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-4 mb-8">
          <Button variant="outline" onClick={() => navigate('/')}>
            ← На главную
          </Button>
          <h1 className="text-3xl font-bold">Очередь кухни</h1>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {queue.map((item) => (
            <Card key={item.orderId} className={
              item.status?.toLowerCase() === 'preparing' ? 'border-yellow-500' :
              item.status?.toLowerCase() === 'ready' ? 'border-green-500' :
              'border-gray-300'
            }>
              <CardHeader>
                <CardTitle>Заказ #{item.orderId}</CardTitle>
                <CardDescription>
                  {formatDate(item.createdAt!)}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-2 mb-4">
                  {item.items?.map((orderItem, idx) => (
                    <div key={idx} className="flex justify-between">
                      <span>{orderItem.name || `Позиция #${orderItem.menuItemId}`}</span>
                      <span className="font-semibold">x{orderItem.quantity}</span>
                    </div>
                  ))}
                </div>
                <div className="flex gap-2">
                  {item.status?.toLowerCase() === 'confirmed' && (
                    <Button onClick={() => updateStatus(item.orderId!, OrderStatus.Preparing)}>
                      Начать готовить
                    </Button>
                  )}
                  {item.status?.toLowerCase() === 'preparing' && (
                    <Button onClick={() => updateStatus(item.orderId!, OrderStatus.Ready)}>
                      Готово
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
