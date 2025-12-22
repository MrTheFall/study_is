import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { paymentsApi } from '@/api/client';
import { Payment } from '@/api/generated/api';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { LoadingState } from '@/components/ui/LoadingState';
import { formatCurrency } from '@/lib/utils';
import { getApiErrorMessage } from '@/lib/apiError';
import { useAuthStore } from '@/store/authStore';

type PaymentResultStatus = 'success' | 'failed' | 'cancelled' | 'pending';

const mapStatusLabel = (status: PaymentResultStatus) => {
  switch (status) {
    case 'success':
      return { title: 'Платеж подтвержден', tone: 'text-green-700' };
    case 'cancelled':
      return { title: 'Платеж отменен', tone: 'text-gray-700' };
    case 'pending':
      return { title: 'Платеж в обработке', tone: 'text-amber-700' };
    default:
      return { title: 'Платеж не прошел', tone: 'text-red-700' };
  }
};

export function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const orderIdParam = searchParams.get('orderId') || '';
  const statusParam = (searchParams.get('status') || 'pending') as PaymentResultStatus;
  const messageParam = searchParams.get('message') || '';

  const orderId = useMemo(() => {
    const parsed = Number(orderIdParam);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [orderIdParam]);

  useEffect(() => {
    if (!isAuthenticated || !orderId || statusParam !== 'success') return;
    setLoading(true);
    paymentsApi
      .getPaymentByOrderId(orderId)
      .then((response) => {
        setPayment(response.data);
        setError(null);
      })
      .catch((err) => {
        setError(getApiErrorMessage(err, 'Не удалось получить данные платежа'));
        setPayment(null);
      })
      .finally(() => setLoading(false));
  }, [isAuthenticated, orderId, statusParam]);

  const label = mapStatusLabel(statusParam);

  if (loading) {
    return <LoadingState message="Проверяем платеж..." />;
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className={`text-3xl font-bold ${label.tone}`}>{label.title}</h1>
        <p className="text-gray-600 mt-2">{orderId ? `Заказ #${orderId}` : 'Не удалось определить номер заказа'}</p>
        {messageParam && <p className="text-sm text-gray-500 mt-2">{messageParam}</p>}
      </div>

      {error && (
        <Card>
          <CardContent className="pt-6 text-sm text-red-600">{error}</CardContent>
        </Card>
      )}

      {payment && (
        <Card>
          <CardHeader>
            <CardTitle>Детали платежа</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm text-gray-700">
            <div>
              <strong>Платеж #</strong> {payment.id}
            </div>
            <div>
              <strong>Сумма:</strong> {formatCurrency(payment.amount || 0)}
            </div>
            <div>
              <strong>Метод:</strong> {payment.method === 'online' ? 'Онлайн' : payment.method}
            </div>
          </CardContent>
        </Card>
      )}

      <div className="flex flex-wrap gap-3">
        <Button onClick={() => navigate(orderId ? `/orders?orderId=${orderId}` : '/orders')}>К заказам</Button>
        <Button variant="outline" onClick={() => navigate('/menu')}>
          В меню
        </Button>
      </div>
    </div>
  );
}
