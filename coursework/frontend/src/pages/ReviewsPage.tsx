import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { reviewsApi } from '@/api/client';
import { Review } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { formatDate } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';

export function ReviewsPage() {
  const navigate = useNavigate();
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [orderId, setOrderId] = useState('');

  useEffect(() => {
    loadReviews();
  }, []);

  const loadReviews = async () => {
    try {
      const response = await reviewsApi.getAllReviews();
      setReviews(response.data);
    } catch (error) {
      console.error('Ошибка загрузки отзывов:', error);
    } finally {
      setLoading(false);
    }
  };

  const submitReview = async () => {
    try {
      const user = useAuthStore.getState().user;
      if (!user?.userId) {
        alert('Пользователь не найден');
        return;
      }
      await reviewsApi.createReview({
        clientId: user.userId,
        orderId: parseInt(orderId),
        rating: rating,
        comment: comment,
      });
      setShowForm(false);
      setComment('');
      setOrderId('');
      loadReviews();
    } catch (error) {
      console.error('Ошибка создания отзыва:', error);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка отзывов...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            <Button variant="outline" onClick={() => navigate('/')}>
              ← На главную
            </Button>
            <h1 className="text-3xl font-bold">Отзывы</h1>
          </div>
          <Button onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Отмена' : 'Оставить отзыв'}
          </Button>
        </div>

        {showForm && (
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Новый отзыв</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">ID заказа</label>
                <Input
                  type="number"
                  value={orderId}
                  onChange={(e) => setOrderId(e.target.value)}
                  placeholder="Введите ID заказа"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Оценка</label>
                <div className="flex gap-2">
                  {[1, 2, 3, 4, 5].map((r) => (
                    <button
                      key={r}
                      onClick={() => setRating(r)}
                      className={`w-10 h-10 rounded-full ${
                        r <= rating ? 'bg-yellow-400' : 'bg-gray-200'
                      }`}
                    >
                      ⭐
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Комментарий</label>
                <Input
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Ваш отзыв"
                />
              </div>
              <Button onClick={submitReview}>Отправить</Button>
            </CardContent>
          </Card>
        )}

        <div className="space-y-4">
          {reviews.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <p className="text-center text-gray-500">Отзывов пока нет</p>
              </CardContent>
            </Card>
          ) : (
            reviews.map((review) => (
              <Card key={review.id}>
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <div>
                      <CardTitle>Заказ #{review.orderId}</CardTitle>
                      <CardDescription>{formatDate(review.createdAt!)}</CardDescription>
                    </div>
                    <div className="text-2xl">
                      {'⭐'.repeat(review.rating!)}
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <p>{review.comment}</p>
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

