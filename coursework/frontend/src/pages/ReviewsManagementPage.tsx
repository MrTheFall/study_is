import { useEffect, useMemo, useState } from 'react';
import { clientsApi, reviewsApi } from '@/api/client';
import { Client, Review } from '@/api/generated/api';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { Select } from '@/components/ui/Select';
import { getApiErrorMessage } from '@/lib/apiError';
import { formatDate } from '@/lib/utils';

type RatingFilter = 'all' | '1' | '2' | '3' | '4' | '5';

export function ReviewsManagementPage() {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [clientId, setClientId] = useState('');
  const [orderId, setOrderId] = useState('');
  const [ratingFilter, setRatingFilter] = useState<RatingFilter>('all');
  const [search, setSearch] = useState('');
  const [clients, setClients] = useState<Client[]>([]);
  const [clientsLoading, setClientsLoading] = useState(true);
  const [clientsError, setClientsError] = useState<string | null>(null);

  useEffect(() => {
    void loadReviews();
  }, []);

  useEffect(() => {
    void loadClients();
  }, []);

  const loadClients = async () => {
    try {
      setClientsError(null);
      setClientsLoading(true);
      const resp = await clientsApi.getAllClients();
      setClients(resp.data || []);
    } catch (e: any) {
      console.error('Ошибка загрузки клиентов:', e);
      setClients([]);
      setClientsError(getApiErrorMessage(e, 'Не удалось загрузить список клиентов'));
    } finally {
      setClientsLoading(false);
    }
  };

  const loadReviews = async () => {
    try {
      setError(null);
      setLoading(true);

      const parsedClientId = clientId.trim() ? parseInt(clientId.trim(), 10) : NaN;
      const parsedOrderId = orderId.trim() ? parseInt(orderId.trim(), 10) : NaN;

      const resp = await reviewsApi.getAllReviews(
        Number.isNaN(parsedClientId) ? undefined : parsedClientId,
        Number.isNaN(parsedOrderId) ? undefined : parsedOrderId
      );
      setReviews(resp.data || []);
    } catch (e: any) {
      console.error('Ошибка загрузки отзывов:', e);
      setError(getApiErrorMessage(e, 'Не удалось загрузить отзывы'));
      setReviews([]);
    } finally {
      setLoading(false);
    }
  };

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    let list = [...reviews];
    if (ratingFilter !== 'all') {
      const r = parseInt(ratingFilter, 10);
      list = list.filter((x) => x.rating === r);
    }
    if (q) {
      list = list.filter((x) => {
        const comment = (x.comment || '').toLowerCase();
        return (
          comment.includes(q) ||
          String(x.orderId || '').includes(q) ||
          String(x.clientId || '').includes(q)
        );
      });
    }
    list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    return list;
  }, [reviews, ratingFilter, search]);

  const stats = useMemo(() => {
    if (filtered.length === 0) return null;
    const sum = filtered.reduce((acc, r) => acc + (r.rating || 0), 0);
    const avg = sum / filtered.length;
    return { count: filtered.length, avg };
  }, [filtered]);

  const orderOptions = useMemo(() => {
    const set = new Set<number>();
    for (const r of reviews) {
      if (typeof r.orderId === 'number') set.add(r.orderId);
    }
    return Array.from(set).sort((a, b) => b - a);
  }, [reviews]);

  if (loading) {
    return <LoadingState message="Загрузка отзывов..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Отзывы</h1>
          <p className="text-sm text-gray-500 mt-1">Просмотр обратной связи от клиентов</p>
        </div>
        <Button variant="outline" onClick={loadReviews}>
          Обновить
        </Button>
      </div>

      {error && (
        <RetryAlert message={error} onRetry={loadReviews} />
      )}

      <Card>
          <CardContent className="pt-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
              <div>
                <label className="block text-sm font-medium mb-1">Клиент</label>
                <Select
                  value={clientId}
                  onChange={(e) => setClientId(e.target.value)}
                  disabled={clientsLoading || Boolean(clientsError)}
                >
                  <option value="">Все клиенты</option>
                  {clients
                    .filter((c): c is Client & { id: number } => typeof c.id === 'number')
                    .sort((a, b) => (b.id || 0) - (a.id || 0))
                    .map((client) => (
                      <option key={client.id} value={String(client.id)}>
                        {client.name || `Клиент #${client.id}`}
                        {client.phone ? ` • ${client.phone}` : ''}
                        {client.email ? ` • ${client.email}` : ''}
                      </option>
                    ))}
                </Select>
                {clientsError ? <p className="mt-1 text-xs text-red-600">{clientsError}</p> : null}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Заказ</label>
                <Select value={orderId} onChange={(e) => setOrderId(e.target.value)} disabled={orderOptions.length === 0}>
                  <option value="">Все заказы</option>
                  {orderOptions.map((id) => (
                    <option key={id} value={String(id)}>
                      #{id}
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Оценка</label>
                <Select
                  value={ratingFilter}
                  onChange={(e) => setRatingFilter(e.target.value as RatingFilter)}
                >
                  <option value="all">Все</option>
                  <option value="5">5</option>
                  <option value="4">4</option>
                  <option value="3">3</option>
                  <option value="2">2</option>
                  <option value="1">1</option>
                </Select>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={loadClients} disabled={clientsLoading}>
                  Обновить клиентов
                </Button>
                <Button onClick={loadReviews}>Применить</Button>
              </div>
            </div>
            <div className="mt-3">
              <label className="block text-sm font-medium mb-1">Поиск по комментарию/ID</label>
              <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="например, delivery" />
            </div>
            {stats && (
              <p className="mt-3 text-sm text-gray-600">
                Найдено: <span className="font-medium">{stats.count}</span> • Средняя оценка:{' '}
                <span className="font-medium">{stats.avg.toFixed(2)}</span>
              </p>
            )}
          </CardContent>
        </Card>

        {filtered.length === 0 ? (
          <EmptyState title="Отзывов нет" description="Попробуйте изменить фильтры или период." />
        ) : (
          <div className="space-y-4">
            {filtered.map((review) => (
              <Card key={review.id}>
                <CardHeader>
                  <div className="flex justify-between items-start gap-4">
                    <div>
                      <CardTitle className="text-xl">Заказ #{review.orderId}</CardTitle>
                      <CardDescription>
                        Клиент #{review.clientId} • {review.createdAt ? formatDate(review.createdAt) : '—'}
                      </CardDescription>
                    </div>
                    <div className="text-2xl">{'⭐'.repeat(review.rating || 0)}</div>
                  </div>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-gray-800 whitespace-pre-wrap">{review.comment || '—'}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
    </div>
  );
}
