import { useEffect, useMemo, useState } from 'react';
import { authApi, ordersApi, reviewsApi } from '@/api/client';
import { Order, PaymentMethod, Review } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Textarea';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { formatDate } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';
import { getApiErrorMessage } from '@/lib/apiError';
import { useToast } from '@/components/ui/toast';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';

export function ReviewsPage() {
  const toast = useToast();
  const { setAuth } = useAuthStore();
  const [reviews, setReviews] = useState<Review[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [reviewDialogOpen, setReviewDialogOpen] = useState(false);
  const [reviewOrderId, setReviewOrderId] = useState<number | null>(null);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void loadData();
  }, []);

  const ensureClientId = async (): Promise<number> => {
    let user = useAuthStore.getState().user;
    if (!user?.userId) {
      const me = await authApi.getCurrentUser();
      const token = useAuthStore.getState().token || localStorage.getItem('token') || '';
      setAuth(token, me.data);
      user = me.data;
    }
    if (!user?.userId) {
      throw new Error('Не удалось определить пользователя. Войдите заново.');
    }
    return user.userId;
  };

  const loadData = async () => {
    try {
      setLoadError(null);
      setLoading(true);

      const clientId = await ensureClientId();
      const [reviewsResp, ordersResp] = await Promise.all([
        reviewsApi.getAllReviews(clientId),
        ordersApi.getAllOrders(undefined, clientId),
      ]);
      setReviews(reviewsResp.data || []);
      setOrders(ordersResp.data || []);
    } catch (error) {
      console.error('Ошибка загрузки отзывов:', error);
      setLoadError(getApiErrorMessage(error, 'Не удалось загрузить данные'));
    } finally {
      setLoading(false);
    }
  };

  const reviewsByOrderId = useMemo(() => {
    const map: Record<number, Review> = {};
    for (const review of reviews) {
      if (typeof review.orderId === 'number') {
        map[review.orderId] = review;
      }
    }
    return map;
  }, [reviews]);

  const reviewableOrders = useMemo(() => {
    const list = (orders || []).filter((order) => {
      if (typeof order.id !== 'number') return false;
      if (reviewsByOrderId[order.id]) return false;

      const statusValue = typeof order.status === 'string' ? order.status : (order.status as any)?.value || 'pending';
      const statusStr = String(statusValue);

      if (order.paymentMethod === PaymentMethod.Online) {
        return statusStr === 'delivered' || statusStr === 'completed';
      }
      return statusStr === 'completed';
    });

    list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    return list;
  }, [orders, reviewsByOrderId]);

  const openReviewDialog = (orderId: number) => {
    setReviewOrderId(orderId);
    setRating(5);
    setComment('');
    setFormError(null);
    setReviewDialogOpen(true);
  };

  const submitReview = async () => {
    if (!reviewOrderId) return;
    setSubmitting(true);
    try {
      setFormError(null);
      const clientId = await ensureClientId();
      await reviewsApi.createReview({
        clientId,
        orderId: reviewOrderId,
        rating: rating,
        comment: comment.trim() || undefined,
      });
      setReviewDialogOpen(false);
      setReviewOrderId(null);
      setComment('');
      toast.success('Отзыв отправлен. Спасибо!');
      await loadData();
    } catch (error) {
      console.error('Ошибка создания отзыва:', error);
      const message = getApiErrorMessage(
        error,
        'Не удалось создать отзыв. Проверьте, что заказ завершён и отзыв ещё не оставлен.'
      );
      setFormError(message);
      toast.error(message, { title: 'Ошибка' });
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка отзывов..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Отзывы</h1>
          <p className="text-gray-600 mt-1">Оставляйте обратную связь по завершённым заказам — без ввода ID.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={loadData}>
            Обновить
          </Button>
        </div>
      </div>

      {loadError && <RetryAlert message={loadError} onRetry={loadData} />}

      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Заказы, ожидающие отзыв</CardTitle>
          <CardDescription>Выберите заказ из списка — вводить ID вручную не нужно.</CardDescription>
        </CardHeader>
        <CardContent>
          {reviewableOrders.length === 0 ? (
            <EmptyState title="Нет заказов для отзыва" description="Отзывы доступны после завершения заказа." />
          ) : (
            <div className="space-y-3">
              {reviewableOrders.map((order) => (
                <div
                  key={order.id}
                  className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 rounded-md border border-gray-200 p-3"
                >
                  <div>
                    <div className="font-medium">Заказ #{order.id}</div>
                    <div className="text-sm text-gray-600">{order.createdAt ? formatDate(order.createdAt) : '—'}</div>
                  </div>
                  <Button variant="outline" onClick={() => openReviewDialog(order.id!)}>
                    Оставить отзыв
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <div className="space-y-4">
        {reviews.length === 0 ? (
          <EmptyState title="Отзывов пока нет" description="Оставьте первый отзыв по завершённому заказу." />
        ) : (
          reviews.map((review) => (
            <Card key={review.id}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <div>
                    <CardTitle>Заказ #{review.orderId}</CardTitle>
                    <CardDescription>{formatDate(review.createdAt!)}</CardDescription>
                  </div>
                  <div className="text-2xl">{'⭐'.repeat(review.rating!)}</div>
                </div>
              </CardHeader>
              <CardContent>
                <p>{review.comment}</p>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      <Dialog
        open={reviewDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setReviewDialogOpen(false);
            setReviewOrderId(null);
            setComment('');
            setFormError(null);
          } else {
            setReviewDialogOpen(true);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Оставить отзыв</DialogTitle>
            <DialogDescription>Отзыв доступен после завершения заказа</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <p>
              <strong>Заказ #{reviewOrderId ?? '—'}</strong>
            </p>
            <div>
              <label className="block text-sm font-medium mb-1">Оценка</label>
              <div className="flex gap-2">
                {[1, 2, 3, 4, 5].map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setRating(r)}
                    className={`w-10 h-10 rounded-full ${r <= rating ? 'bg-yellow-400' : 'bg-gray-200'}`}
                    aria-label={`Оценка ${r}`}
                  >
                    ⭐
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Комментарий</label>
              <Textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Что понравилось? Что можно улучшить? (необязательно)"
              />
            </div>
            {formError && <p className="text-sm text-red-600">{formError}</p>}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setReviewDialogOpen(false)} disabled={submitting}>
              Отмена
            </Button>
            <Button onClick={submitReview} disabled={submitting || !reviewOrderId}>
              {submitting ? 'Отправка…' : 'Отправить'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
