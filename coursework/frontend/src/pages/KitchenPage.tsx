import { useEffect, useMemo, useRef, useState } from 'react';
import { kitchenApi, ordersApi } from '@/api/client';
import { KitchenQueueItem, OrderStatus } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { getApiErrorMessage } from '@/lib/apiError';
import { formatDate } from '@/lib/utils';

export function KitchenPage() {
  const [queue, setQueue] = useState<KitchenQueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const refreshInFlightRef = useRef(false);
  const [updatingOrderIds, setUpdatingOrderIds] = useState<Record<number, boolean>>({});

  useEffect(() => {
    loadQueue();
    const interval = setInterval(loadQueue, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const loadQueue = async () => {
    if (refreshInFlightRef.current) return;
    refreshInFlightRef.current = true;
    setRefreshing(true);
    try {
      const response = await kitchenApi.getKitchenQueue();
      setQueue(response.data || []);
      setError(null);
      setLastUpdatedAt(Date.now());
    } catch (error) {
      console.error('Ошибка загрузки очереди:', error);
      setError(getApiErrorMessage(error, 'Не удалось загрузить очередь кухни'));
    } finally {
      setLoading(false);
      setRefreshing(false);
      refreshInFlightRef.current = false;
    }
  };

  const updateStatus = async (orderId: number, status: OrderStatus) => {
    try {
      setUpdatingOrderIds((prev) => ({ ...prev, [orderId]: true }));
      await ordersApi.updateOrderStatus(orderId, { status });
      await loadQueue();
    } catch (error) {
      console.error('Ошибка обновления статуса:', error);
      setError(getApiErrorMessage(error, 'Не удалось обновить статус заказа'));
    } finally {
      setUpdatingOrderIds((prev) => {
        const next = { ...prev };
        delete next[orderId];
        return next;
      });
    }
  };

  const formatDuration = (totalSeconds: number) => {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
  };

  const secondsSinceNow = (iso: string | null | undefined) => {
    if (!iso) return null;
    const from = new Date(iso).getTime();
    if (Number.isNaN(from)) return null;
    return Math.max(0, Math.floor((now - from) / 1000));
  };

  const getSlaTone = (elapsedSeconds: number | null, warnSeconds: number, badSeconds: number) => {
    if (elapsedSeconds == null) return 'muted';
    if (elapsedSeconds >= badSeconds) return 'bad';
    if (elapsedSeconds >= warnSeconds) return 'warn';
    return 'ok';
  };

  const SLA = {
    confirmedWarn: 3 * 60,
    confirmedBad: 6 * 60,
    preparingWarn: 15 * 60,
    preparingBad: 25 * 60,
    readyWarn: 5 * 60,
    readyBad: 10 * 60,
  };

  const groups = useMemo(() => {
    const confirmed: KitchenQueueItem[] = [];
    const preparing: KitchenQueueItem[] = [];
    const ready: KitchenQueueItem[] = [];
    const other: KitchenQueueItem[] = [];

    for (const item of queue) {
      const status = item.status?.toLowerCase();
      if (status === 'confirmed') confirmed.push(item);
      else if (status === 'preparing') preparing.push(item);
      else if (status === 'ready') ready.push(item);
      else other.push(item);
    }

    const byTsAsc = (getTs: (item: KitchenQueueItem) => number) => (a: KitchenQueueItem, b: KitchenQueueItem) =>
      getTs(a) - getTs(b);

    const createdTs = (item: KitchenQueueItem) => (item.createdAt ? Date.parse(item.createdAt) : 0) || 0;
    const preparingTs = (item: KitchenQueueItem) =>
      (item.preparingAt ? Date.parse(item.preparingAt) : createdTs(item)) || 0;
    const readyTs = (item: KitchenQueueItem) => (item.readyAt ? Date.parse(item.readyAt) : createdTs(item)) || 0;

    confirmed.sort(byTsAsc(createdTs));
    preparing.sort(byTsAsc(preparingTs));
    ready.sort(byTsAsc(readyTs));
    other.sort(byTsAsc(createdTs));

    return { confirmed, preparing, ready, other };
  }, [queue]);

  const updatedSecondsAgo = lastUpdatedAt != null ? Math.max(0, Math.floor((now - lastUpdatedAt) / 1000)) : null;

  if (loading) {
    return <LoadingState message="Загрузка очереди..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Очередь кухни</h1>
          <p className="text-gray-600 mt-1">
            {updatedSecondsAgo != null
              ? `Обновлено ${updatedSecondsAgo} сек назад. Автообновление: каждые 5 секунд.`
              : 'Автообновление: каждые 5 секунд.'}
            {refreshing ? ' Обновление…' : ''}
          </p>
        </div>
        <Button variant="outline" onClick={loadQueue} disabled={refreshing}>
          {refreshing ? 'Обновление...' : 'Обновить'}
        </Button>
      </div>

      {error && <RetryAlert message={error} onRetry={loadQueue} />}

      {queue.length === 0 ? (
        <EmptyState
          title="Нет заказов в очереди"
          description="Как только появятся подтверждённые заказы, они отобразятся здесь."
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {(
            [
              {
                key: 'confirmed' as const,
                title: 'Новые',
                description: 'Confirmed — ждут начала приготовления',
                items: groups.confirmed,
              },
              {
                key: 'preparing' as const,
                title: 'В работе',
                description: 'Preparing — готовятся сейчас',
                items: groups.preparing,
              },
              {
                key: 'ready' as const,
                title: 'Готовые',
                description: 'Ready — ждут выдачи/доставки',
                items: groups.ready,
              },
            ] as const
          ).map((col) => (
            <div key={col.key} className="space-y-3">
              <div className="flex items-end justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold">{col.title}</h2>
                  <p className="text-sm text-gray-500">{col.description}</p>
                </div>
                <div className="text-sm text-gray-500">({col.items.length})</div>
              </div>

              {col.items.length === 0 ? (
                <Card>
                  <CardContent className="pt-6">
                    <p className="text-sm text-gray-500">Пусто</p>
                  </CardContent>
                </Card>
              ) : (
                <div className="space-y-4">
                  {col.items.map((item, idx) => {
                    const status = item.status?.toLowerCase();
                    const cookingDurationSeconds =
                      typeof item.cookingDurationSeconds === 'number' ? item.cookingDurationSeconds : null;
                    const cookingSecondsFallback =
                      item.readyAt && item.preparingAt
                        ? Math.max(
                            0,
                            Math.floor((new Date(item.readyAt).getTime() - new Date(item.preparingAt).getTime()) / 1000)
                          )
                        : null;
                    const cookingSeconds = cookingDurationSeconds ?? cookingSecondsFallback;

                    const elapsedSinceCreatedSeconds = secondsSinceNow(item.createdAt);
                    const elapsedPreparingSeconds = secondsSinceNow(item.preparingAt);
                    const elapsedReadySeconds = secondsSinceNow(item.readyAt);

                    const tone =
                      status === 'confirmed'
                        ? getSlaTone(elapsedSinceCreatedSeconds, SLA.confirmedWarn, SLA.confirmedBad)
                        : status === 'preparing'
                          ? getSlaTone(elapsedPreparingSeconds, SLA.preparingWarn, SLA.preparingBad)
                          : status === 'ready'
                            ? getSlaTone(elapsedReadySeconds, SLA.readyWarn, SLA.readyBad)
                            : 'muted';

                    const toneClass =
                      tone === 'bad'
                        ? 'border-red-400 bg-red-50'
                        : tone === 'warn'
                          ? 'border-yellow-400 bg-yellow-50'
                          : '';

                    const statusBorderClass =
                      status === 'confirmed'
                        ? 'border-blue-300'
                        : status === 'preparing'
                          ? 'border-yellow-500'
                          : status === 'ready'
                            ? 'border-green-500'
                            : 'border-gray-300';

                    const isOldest = idx === 0 && col.items.length > 1;

                    return (
                      <Card
                        key={item.orderId}
                        className={`${statusBorderClass} ${toneClass} ${isOldest ? 'ring-2 ring-primary-500 ring-offset-2' : ''}`}
                      >
                        <CardHeader>
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <CardTitle>Заказ #{item.orderId}</CardTitle>
                              <CardDescription>{item.createdAt ? formatDate(item.createdAt) : '—'}</CardDescription>
                            </div>
                            {tone !== 'muted' ? (
                              <span
                                className={`inline-flex items-center rounded px-2 py-1 text-xs font-medium ${
                                  tone === 'bad'
                                    ? 'bg-red-100 text-red-800'
                                    : tone === 'warn'
                                      ? 'bg-yellow-100 text-yellow-800'
                                      : 'bg-green-100 text-green-800'
                                }`}
                              >
                                {tone === 'bad' ? 'Очень долго' : tone === 'warn' ? 'Долго' : 'В норме'}
                              </span>
                            ) : null}
                          </div>
                        </CardHeader>
                        <CardContent>
                          <div className="space-y-2 mb-4">
                            {item.items?.map((orderItem, itemIdx) => (
                              <div key={itemIdx} className="flex justify-between gap-3">
                                <span className="min-w-0 truncate">
                                  {orderItem.name || `Позиция #${orderItem.menuItemId}`}
                                </span>
                                <span className="font-semibold whitespace-nowrap">x{orderItem.quantity}</span>
                              </div>
                            ))}
                          </div>

                          <div className="text-sm text-gray-600 mb-4 space-y-1">
                            {status === 'confirmed' && (
                              <p>
                                <strong>Ожидает старта:</strong>{' '}
                                {elapsedSinceCreatedSeconds != null ? formatDuration(elapsedSinceCreatedSeconds) : '—'}
                              </p>
                            )}
                            {status === 'preparing' && (
                              <p>
                                <strong>Готовится:</strong>{' '}
                                {elapsedPreparingSeconds != null ? formatDuration(elapsedPreparingSeconds) : '—'}
                              </p>
                            )}
                            {status === 'ready' && (
                              <>
                                <p>
                                  <strong>Готов:</strong>{' '}
                                  {elapsedReadySeconds != null ? formatDuration(elapsedReadySeconds) : '—'}
                                </p>
                                <p>
                                  <strong>Время приготовления:</strong>{' '}
                                  {cookingSeconds != null ? formatDuration(cookingSeconds) : '—'}
                                </p>
                              </>
                            )}
                          </div>

                          <div className="flex gap-2">
                            {status === 'confirmed' && (
                              <Button
                                onClick={() => updateStatus(item.orderId!, OrderStatus.Preparing)}
                                disabled={!item.orderId || Boolean(item.orderId && updatingOrderIds[item.orderId])}
                              >
                                {item.orderId && updatingOrderIds[item.orderId] ? '...' : 'Начать готовить'}
                              </Button>
                            )}
                            {status === 'preparing' && (
                              <Button
                                onClick={() => updateStatus(item.orderId!, OrderStatus.Ready)}
                                disabled={!item.orderId || Boolean(item.orderId && updatingOrderIds[item.orderId])}
                              >
                                {item.orderId && updatingOrderIds[item.orderId] ? '...' : 'Готово'}
                              </Button>
                            )}
                          </div>
                        </CardContent>
                      </Card>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {groups.other.length > 0 ? (
        <div className="space-y-3">
          <h2 className="text-lg font-semibold">Другие статусы</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {groups.other.map((item) => (
              <Card key={item.orderId} className="border-gray-300">
                <CardHeader>
                  <CardTitle>Заказ #{item.orderId}</CardTitle>
                  <CardDescription>{item.status}</CardDescription>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-gray-600">{item.createdAt ? formatDate(item.createdAt) : '—'}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}
