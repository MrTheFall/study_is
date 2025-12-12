import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { inventoryApi } from '@/api/client';
import { InventoryRecord, LowStockItem } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function InventoryPage() {
  const navigate = useNavigate();
  const [inventory, setInventory] = useState<InventoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);

  useEffect(() => {
    loadInventory();
    loadLowStock();
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

  const updateQuantity = async (ingredientId: number, newQuantity: number, currentQuantity: number) => {
    try {
      const delta = newQuantity - currentQuantity;
      await inventoryApi.updateInventory(ingredientId, { delta });
      loadInventory();
      loadLowStock();
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
                      step="0.01"
                      placeholder="Изменение количества"
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          const input = e.target as HTMLInputElement;
                          const delta = parseFloat(input.value);
                          if (!isNaN(delta) && item.ingredientId && item.quantity !== undefined) {
                            updateQuantity(item.ingredientId, item.quantity + delta, item.quantity);
                            input.value = '';
                          }
                        }
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

