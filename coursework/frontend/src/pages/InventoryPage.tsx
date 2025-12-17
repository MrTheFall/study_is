import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { inventoryApi } from '@/api/client';
import { InventoryRecord, InventoryTransaction, LowStockItem } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function InventoryPage() {
  const navigate = useNavigate();
  const [inventory, setInventory] = useState<InventoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [transactions, setTransactions] = useState<InventoryTransaction[]>([]);
  const [reasonByIngredientId, setReasonByIngredientId] = useState<Record<number, string>>({});

  useEffect(() => {
    loadInventory();
    loadLowStock();
    loadTransactions();
  }, []);

  const loadInventory = async () => {
    try {
      const response = await inventoryApi.getInventory(false, 1);
      setInventory(response.data);
    } catch (error) {
      console.error('Ошибка загрузки инвентаря:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadLowStock = async () => {
    try {
      const response = await inventoryApi.getLowStock(1);
      setLowStock(response.data);
    } catch (error) {
      console.error('Ошибка загрузки низких остатков:', error);
    }
  };

  const loadTransactions = async () => {
    try {
      const response = await inventoryApi.getInventoryTransactions(undefined, 50, 0);
      setTransactions(response.data);
    } catch (error) {
      console.error('Ошибка загрузки журнала движений:', error);
    }
  };

  const adjustInventory = async (ingredientId: number, delta: number, reason?: string) => {
    try {
      await inventoryApi.updateInventory(ingredientId, { delta, reason });
      loadInventory();
      loadLowStock();
      loadTransactions();
    } catch (error) {
      console.error('Ошибка обновления:', error);
      alert('Ошибка обновления количества');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка инвентаря...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-4 mb-8">
          <Button variant="outline" onClick={() => navigate('/')}>
            ← На главную
          </Button>
          <h1 className="text-3xl font-bold">Инвентарь</h1>
        </div>

        {lowStock.length > 0 && (
          <Card className="mb-8 border-yellow-500">
            <CardHeader>
              <CardTitle className="text-yellow-700">Низкие остатки</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {lowStock.map((item) => (
                  <div key={item.ingredientId} className="flex justify-between">
                    <span>{item.name}</span>
                    <span className="font-semibold text-red-600">
                      {item.quantity} / {item.minThreshold}
                    </span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}

        <Card className="mb-8">
          <CardHeader>
            <CardTitle>Журнал движений склада</CardTitle>
            <CardDescription>Последние 50 операций</CardDescription>
          </CardHeader>
          <CardContent>
            {transactions.length === 0 ? (
              <div className="text-sm text-gray-600">Нет записей</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="border-b">
                      <th className="text-left py-2 pr-4">Время</th>
                      <th className="text-left py-2 pr-4">Ингредиент</th>
                      <th className="text-left py-2 pr-4">Δ</th>
                      <th className="text-left py-2 pr-4">Сотрудник</th>
                      <th className="text-left py-2 pr-4">Причина</th>
                      <th className="text-left py-2 pr-4">Источник</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transactions.map((t) => (
                      <tr key={t.id} className="border-b last:border-b-0">
                        <td className="py-2 pr-4 whitespace-nowrap">
                          {t.createdAt ? new Date(t.createdAt).toLocaleString() : '—'}
                        </td>
                        <td className="py-2 pr-4">
                          {t.ingredientName ? t.ingredientName : `#${t.ingredientId}`}
                        </td>
                        <td className={`py-2 pr-4 font-semibold ${t.delta && t.delta > 0 ? 'text-green-700' : 'text-red-700'}`}>
                          {t.delta}
                        </td>
                        <td className="py-2 pr-4">{t.employeeName ? t.employeeName : 'Система'}</td>
                        <td className="py-2 pr-4">{t.reason ? t.reason : '—'}</td>
                        <td className="py-2 pr-4">
                          {t.source ? t.source : '—'}
                          {t.orderId ? ` (#${t.orderId})` : ''}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {inventory.map((item) => (
            <Card key={item.id}>
              <CardHeader>
                <CardTitle>Ингредиент #{item.ingredientId}</CardTitle>
                <CardDescription>ID записи: {item.id}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  <div>
                    <p className="text-sm text-gray-600">Текущее количество</p>
                    <p className="text-2xl font-bold">{item.quantity}</p>
                  </div>
                  {item.lastUpdated && (
                    <div>
                      <p className="text-sm text-gray-600">Последнее обновление</p>
                      <p className="text-sm">{new Date(item.lastUpdated).toLocaleString()}</p>
                    </div>
                  )}
                  <div className="flex gap-2">
                    <Input
                      type="number"
                      step="0.001"
                      placeholder="Δ количества (Enter)"
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          const input = e.target as HTMLInputElement;
                          const delta = parseFloat(input.value);
                          if (!isNaN(delta) && item.ingredientId && item.quantity !== undefined) {
                            const reason = item.ingredientId ? reasonByIngredientId[item.ingredientId] : undefined;
                            adjustInventory(item.ingredientId, delta, reason);
                            input.value = '';
                            if (item.ingredientId) {
                              setReasonByIngredientId((prev) => ({ ...prev, [item.ingredientId as number]: '' }));
                            }
                          }
                        }
                      }}
                    />
                  </div>
                  <div>
                    <Input
                      type="text"
                      placeholder="Причина (опционально)"
                      value={item.ingredientId ? (reasonByIngredientId[item.ingredientId] ?? '') : ''}
                      onChange={(e) => {
                        if (!item.ingredientId) return;
                        const value = e.target.value;
                        setReasonByIngredientId((prev) => ({ ...prev, [item.ingredientId as number]: value }));
                      }}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
