import { useEffect, useState } from 'react';
import { inventoryApi } from '@/api/client';
import { InventoryRecord } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function InventoryPage() {
  const [inventory, setInventory] = useState<InventoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [lowStock, setLowStock] = useState<InventoryRecord[]>([]);

  useEffect(() => {
    loadInventory();
    loadLowStock();
  }, []);

  const loadInventory = async () => {
    try {
      const response = await inventoryApi.getAllInventory();
      setInventory(response.data);
    } catch (error) {
      console.error('Ошибка загрузки инвентаря:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadLowStock = async () => {
    try {
      const response = await inventoryApi.getLowStockItems();
      setLowStock(response.data);
    } catch (error) {
      console.error('Ошибка загрузки низких остатков:', error);
    }
  };

  const updateQuantity = async (id: number, quantity: number) => {
    try {
      await inventoryApi.updateInventory(id, { quantity });
      loadInventory();
    } catch (error) {
      console.error('Ошибка обновления:', error);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка инвентаря...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-3xl font-bold mb-8">Инвентарь</h1>

        {lowStock.length > 0 && (
          <Card className="mb-8 border-yellow-500">
            <CardHeader>
              <CardTitle className="text-yellow-700">Низкие остатки</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {lowStock.map((item) => (
                  <div key={item.id} className="flex justify-between">
                    <span>{item.ingredientName}</span>
                    <span className="font-semibold text-red-600">
                      {item.currentQuantity} / {item.minimumQuantity}
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
                <CardTitle>{item.ingredientName}</CardTitle>
                <CardDescription>Единица: {item.unit}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  <div>
                    <p className="text-sm text-gray-600">Текущее количество</p>
                    <p className="text-2xl font-bold">{item.currentQuantity}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Минимум</p>
                    <p className="text-lg">{item.minimumQuantity}</p>
                  </div>
                  <div className="flex gap-2">
                    <Input
                      type="number"
                      placeholder="Новое количество"
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          const input = e.target as HTMLInputElement;
                          updateQuantity(item.id!, parseFloat(input.value));
                          input.value = '';
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

