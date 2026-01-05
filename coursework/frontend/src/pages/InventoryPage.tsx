import { useEffect, useMemo, useState } from 'react';
import { inventoryApi } from '@/api/client';
import { InventoryRecord, InventoryTransaction, LowStockItem } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { Select } from '@/components/ui/Select';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { getApiErrorMessage } from '@/lib/apiError';

export function InventoryPage() {
  const [inventory, setInventory] = useState<InventoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [ingredientNameById, setIngredientNameById] = useState<Record<number, string>>({});
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [transactions, setTransactions] = useState<InventoryTransaction[]>([]);
  const [transactionsLoading, setTransactionsLoading] = useState(false);
  const [transactionsIngredientId, setTransactionsIngredientId] = useState<number | null>(null);
  const [transactionsLimit, setTransactionsLimit] = useState(50);
  const [transactionsOffset, setTransactionsOffset] = useState(0);
  const [reasonByIngredientId, setReasonByIngredientId] = useState<Record<number, string>>({});
  const [deltaByIngredientId, setDeltaByIngredientId] = useState<Record<number, string>>({});
  const [inventoryQuery, setInventoryQuery] = useState('');
  const [adjustingByIngredientId, setAdjustingByIngredientId] = useState<Record<number, boolean>>({});

  useEffect(() => {
    loadInventory();
    loadLowStock();
    void loadTransactions();
  }, []);

  useEffect(() => {
    if (loading) return;
    void loadTransactions();
  }, [transactionsIngredientId, transactionsLimit, transactionsOffset]);

  const loadInventory = async () => {
    try {
      const response = await inventoryApi.getInventory(false, 1);
      setInventory(response.data || []);
      setError(null);
    } catch (error) {
      console.error('Ошибка загрузки инвентаря:', error);
      setError(getApiErrorMessage(error, 'Не удалось загрузить инвентарь'));
    } finally {
      setLoading(false);
    }
  };

  const loadLowStock = async () => {
    try {
      const response = await inventoryApi.getLowStock(1);
      const data = response.data || [];
      setLowStock(data);
      setIngredientNameById((prev) => {
        const next = { ...prev };
        for (const item of data) {
          if (typeof item.ingredientId === 'number' && item.name) {
            next[item.ingredientId] = item.name;
          }
        }
        return next;
      });
    } catch (error) {
      console.error('Ошибка загрузки низких остатков:', error);
      setError((prev) => prev ?? getApiErrorMessage(error, 'Не удалось загрузить низкие остатки'));
    }
  };

  const loadTransactions = async (options?: { ingredientId?: number | null; limit?: number; offset?: number }) => {
    setTransactionsLoading(true);
    try {
      const ingredientId = options?.ingredientId ?? transactionsIngredientId;
      const limit = options?.limit ?? transactionsLimit;
      const offset = options?.offset ?? transactionsOffset;
      const response = await inventoryApi.getInventoryTransactions(ingredientId ?? undefined, limit, offset);
      const data = response.data || [];
      setTransactions(data);
      setIngredientNameById((prev) => {
        const next = { ...prev };
        for (const t of data) {
          if (typeof t.ingredientId === 'number' && t.ingredientName) {
            next[t.ingredientId] = t.ingredientName;
          }
        }
        return next;
      });
    } catch (error) {
      console.error('Ошибка загрузки журнала движений:', error);
      setError((prev) => prev ?? getApiErrorMessage(error, 'Не удалось загрузить журнал движений'));
    } finally {
      setTransactionsLoading(false);
    }
  };

  const adjustInventory = async (ingredientId: number, delta: number, reason?: string) => {
    try {
      setAdjustingByIngredientId((prev) => ({ ...prev, [ingredientId]: true }));
      await inventoryApi.updateInventory(ingredientId, { delta, reason });
      await loadInventory();
      await loadLowStock();
      await loadTransactions();
      setDeltaByIngredientId((prev) => {
        const next = { ...prev };
        delete next[ingredientId];
        return next;
      });
      setReasonByIngredientId((prev) => {
        const next = { ...prev };
        delete next[ingredientId];
        return next;
      });
    } catch (error) {
      console.error('Ошибка обновления:', error);
      setError(getApiErrorMessage(error, 'Не удалось обновить количество'));
    } finally {
      setAdjustingByIngredientId((prev) => {
        const next = { ...prev };
        delete next[ingredientId];
        return next;
      });
    }
  };

  const reloadAll = () => {
    setLoading(true);
    setError(null);
    setTransactionsOffset(0);
    void loadInventory();
    void loadLowStock();
    void loadTransactions({ offset: 0 });
  };

  const lowStockById = useMemo(() => {
    const map: Record<number, LowStockItem> = {};
    for (const item of lowStock) {
      if (typeof item.ingredientId === 'number') {
        map[item.ingredientId] = item;
      }
    }
    return map;
  }, [lowStock]);

  const inventorySorted = useMemo(() => {
    const list = [...inventory];
    list.sort((a, b) => (a.ingredientId || 0) - (b.ingredientId || 0));
    return list;
  }, [inventory]);

  const filteredInventory = useMemo(() => {
    const q = inventoryQuery.trim().toLowerCase();
    if (!q) return inventorySorted;
    return inventorySorted.filter((item) => {
      const id = item.ingredientId;
      const idStr = typeof id === 'number' ? String(id) : '';
      const name = typeof id === 'number' ? ingredientNameById[id] || '' : '';
      return `${name} ${idStr}`.toLowerCase().includes(q);
    });
  }, [ingredientNameById, inventoryQuery, inventorySorted]);

  if (loading) {
    return <LoadingState message="Загрузка инвентаря..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Инвентарь</h1>
          <p className="text-gray-600 mt-1">Остатки, низкие пороги и журнал движений.</p>
        </div>
        <Button variant="outline" onClick={reloadAll}>
          Обновить
        </Button>
      </div>

      {error && <RetryAlert message={error} onRetry={reloadAll} />}

      {lowStock.length > 0 && (
        <Card className="border-yellow-500">
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

      <Card>
        <CardHeader>
          <CardTitle>Журнал движений склада</CardTitle>
          <CardDescription>Приход/расход, причина, сотрудник, время</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-3 mb-4">
            <div className="w-full md:w-80">
              <label className="block text-sm font-medium mb-1">Ингредиент</label>
              <Select
                value={transactionsIngredientId == null ? 'all' : String(transactionsIngredientId)}
                onChange={(e) => {
                  const value = e.target.value;
                  setTransactionsIngredientId(value === 'all' ? null : parseInt(value, 10));
                  setTransactionsOffset(0);
                }}
              >
                <option value="all">Все ингредиенты</option>
                {inventorySorted
                  .map((inv) => inv.ingredientId)
                  .filter((id): id is number => typeof id === 'number')
                  .map((id) => (
                    <option key={id} value={id}>
                      {ingredientNameById[id] ? ingredientNameById[id] : `Ингредиент #${id}`}
                    </option>
                  ))}
              </Select>
            </div>
            <div className="w-full md:w-44">
              <label className="block text-sm font-medium mb-1">Показать</label>
              <Select
                value={String(transactionsLimit)}
                onChange={(e) => {
                  setTransactionsLimit(parseInt(e.target.value, 10));
                  setTransactionsOffset(0);
                }}
              >
                <option value="25">25</option>
                <option value="50">50</option>
                <option value="100">100</option>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setTransactionsOffset((prev) => Math.max(0, prev - transactionsLimit))}
                disabled={transactionsLoading || transactionsOffset === 0}
              >
                Назад
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setTransactionsOffset((prev) => prev + transactionsLimit)}
                disabled={transactionsLoading || transactions.length < transactionsLimit}
              >
                Вперед
              </Button>
            </div>
          </div>

          {transactions.length === 0 ? (
            <div className="text-sm text-gray-600">{transactionsLoading ? 'Загрузка...' : 'Нет записей'}</div>
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
                      <td className="py-2 pr-4">{t.ingredientName ? t.ingredientName : `#${t.ingredientId}`}</td>
                      <td
                        className={`py-2 pr-4 font-semibold ${t.delta && t.delta > 0 ? 'text-green-700' : 'text-red-700'}`}
                      >
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
              <div className="mt-3 text-xs text-gray-500">
                Смещение: {transactionsOffset} • Показано: {transactions.length}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div className="w-full sm:w-96">
          <label className="block text-sm font-medium mb-1">Поиск по ингредиентам</label>
          <Input
            placeholder="Название или ID ингредиента..."
            value={inventoryQuery}
            onChange={(e) => setInventoryQuery(e.target.value)}
          />
        </div>
        <div className="text-sm text-gray-500">
          {inventory.length > 0 ? `Показано: ${filteredInventory.length} из ${inventory.length}` : 'Нет данных'}
        </div>
      </div>

      {inventory.length === 0 ? (
        <EmptyState
          title="Нет данных об остатках"
          description="Попробуйте обновить страницу или проверить подключение к серверу."
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredInventory.map((item) => {
            const ingredientId = item.ingredientId;
            const ingredientName = typeof ingredientId === 'number' ? ingredientNameById[ingredientId] : undefined;
            const low = typeof ingredientId === 'number' ? lowStockById[ingredientId] : undefined;
            const deltaRaw = typeof ingredientId === 'number' ? (deltaByIngredientId[ingredientId] ?? '') : '';
            const deltaValue = deltaRaw.trim() ? Number(deltaRaw.replace(',', '.')) : null;
            const deltaValid = deltaValue != null && Number.isFinite(deltaValue) && deltaValue !== 0;
            const reason = typeof ingredientId === 'number' ? reasonByIngredientId[ingredientId] : undefined;
            const isAdjusting =
              typeof ingredientId === 'number' ? Boolean(adjustingByIngredientId[ingredientId]) : false;

            return (
              <Card key={item.id} className={low ? 'border-yellow-500' : undefined}>
                <CardHeader>
                  <CardTitle>{ingredientName ? ingredientName : `Ингредиент #${ingredientId}`}</CardTitle>
                  <CardDescription>
                    {typeof ingredientId === 'number' ? `ID ингредиента: ${ingredientId}` : 'ID ингредиента: —'} • ID
                    записи: {item.id}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div>
                      <p className="text-sm text-gray-600">Текущее количество</p>
                      <p className="text-2xl font-bold">{item.quantity}</p>
                    </div>
                    {low ? (
                      <div className="text-sm text-yellow-800 bg-yellow-50 border border-yellow-200 rounded-md px-3 py-2">
                        Низкий остаток: {low.quantity} / {low.minThreshold}
                      </div>
                    ) : null}
                    {item.lastUpdated && (
                      <div>
                        <p className="text-sm text-gray-600">Последнее обновление</p>
                        <p className="text-sm">{new Date(item.lastUpdated).toLocaleString()}</p>
                      </div>
                    )}
                    <div className="space-y-2">
                      <label className="block text-sm font-medium">Изменение количества (Δ)</label>
                      <div className="flex gap-2">
                        <Input
                          type="number"
                          step="0.001"
                          placeholder="Например, -2 или 5"
                          value={deltaRaw}
                          onChange={(e) => {
                            if (typeof ingredientId !== 'number') return;
                            setDeltaByIngredientId((prev) => ({ ...prev, [ingredientId]: e.target.value }));
                          }}
                          disabled={isAdjusting || typeof ingredientId !== 'number'}
                        />
                        <Button
                          onClick={() => {
                            if (typeof ingredientId !== 'number') return;
                            if (!deltaValid || deltaValue == null) return;
                            void adjustInventory(ingredientId, deltaValue, reason?.trim() || undefined);
                          }}
                          disabled={isAdjusting || typeof ingredientId !== 'number' || !deltaValid}
                        >
                          {isAdjusting ? 'Применение...' : 'Применить'}
                        </Button>
                      </div>
                      <p className="text-xs text-gray-500">Положительное значение — приход, отрицательное — расход.</p>
                    </div>
                    <div>
                      <Input
                        type="text"
                        placeholder="Причина (опционально)"
                        value={typeof ingredientId === 'number' ? (reasonByIngredientId[ingredientId] ?? '') : ''}
                        onChange={(e) => {
                          if (typeof ingredientId !== 'number') return;
                          const value = e.target.value;
                          setReasonByIngredientId((prev) => ({ ...prev, [ingredientId]: value }));
                        }}
                        disabled={isAdjusting || typeof ingredientId !== 'number'}
                      />
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
