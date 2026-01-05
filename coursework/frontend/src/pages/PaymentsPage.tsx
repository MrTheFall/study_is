import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { paymentsApi } from '@/api/client';
import { Payment } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { Select } from '@/components/ui/Select';
import { formatCurrency, formatDate } from '@/lib/utils';
import { getApiErrorMessage } from '@/lib/apiError';

export function PaymentsPage() {
  const navigate = useNavigate();
  const [payments, setPayments] = useState<Payment[]>([]);
  const [failedPayments, setFailedPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [pageSize, setPageSize] = useState(30);
  const [page, setPage] = useState(1);

  useEffect(() => {
    void loadPayments();
  }, [page, pageSize]);

  const loadPayments = async () => {
    try {
      setLoadError(null);
      setLoading(true);
      const offset = Math.max(0, (page - 1) * pageSize);
      const [listResp, failedResp] = await Promise.all([
        paymentsApi.getPayments(undefined, undefined, undefined, pageSize, offset),
        paymentsApi.getPayments(false, undefined, undefined, 10, 0),
      ]);

      const paymentsList = (listResp.data || []).slice().sort((a, b) => {
        const aTs = a.paidAt ? Date.parse(a.paidAt) : 0;
        const bTs = b.paidAt ? Date.parse(b.paidAt) : 0;
        if (aTs !== bTs) return bTs - aTs;
        return (b.id || 0) - (a.id || 0);
      });
      setPayments(paymentsList);

      const failedList = (failedResp.data || []).slice().sort((a, b) => {
        const aTs = a.paidAt ? Date.parse(a.paidAt) : 0;
        const bTs = b.paidAt ? Date.parse(b.paidAt) : 0;
        if (aTs !== bTs) return bTs - aTs;
        return (b.id || 0) - (a.id || 0);
      });
      setFailedPayments(failedList);
    } catch (error) {
      console.error('Ошибка загрузки платежей:', error);
      setLoadError(getApiErrorMessage(error, 'Не удалось загрузить платежи'));
    } finally {
      setLoading(false);
    }
  };

  const hasNextPage = useMemo(() => payments.length === pageSize, [payments.length, pageSize]);
  const canPrevPage = page > 1;

  if (loading) {
    return <LoadingState message="Загрузка платежей..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">История оплат</h1>
          <p className="text-gray-600 mt-1">
            Здесь — история и ошибки. Обработка оплаты выполняется в карточке заказа.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={loadPayments}>
            Обновить
          </Button>
          <Button onClick={() => navigate('/orders')}>Открыть заказы</Button>
        </div>
      </div>

      {loadError && <RetryAlert message={loadError} onRetry={loadPayments} />}

      {failedPayments.length > 0 && (
        <Card className="border-red-300">
          <CardHeader>
            <CardTitle className="text-xl text-red-700">Проблемные платежи</CardTitle>
            <CardDescription>Платежи со статусом “Ошибка”</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {failedPayments.slice(0, 10).map((p) => (
                <div
                  key={p.id}
                  className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 rounded-md border border-red-200 bg-red-50 p-3"
                >
                  <div className="text-sm">
                    <div className="font-medium">
                      Платеж #{p.id} • Заказ #{p.orderId}
                    </div>
                    <div className="text-gray-700">Сумма: {formatCurrency(p.amount || 0)}</div>
                  </div>
                  {p.orderId ? (
                    <Button variant="outline" onClick={() => navigate(`/orders?orderId=${p.orderId}`)}>
                      Открыть заказ
                    </Button>
                  ) : null}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold">Платежи</h2>
          <p className="text-sm text-gray-500">Успешные и неуспешные операции оплаты</p>
        </div>
        <div className="flex flex-col sm:flex-row gap-2 sm:items-end">
          <div className="w-full sm:w-40">
            <label className="block text-sm font-medium mb-1">На странице</label>
            <Select
              value={String(pageSize)}
              onChange={(e) => {
                setPageSize(parseInt(e.target.value, 10));
                setPage(1);
              }}
            >
              <option value="10">10</option>
              <option value="30">30</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </Select>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={!canPrevPage}>
              Назад
            </Button>
            <Button variant="outline" onClick={() => setPage((p) => p + 1)} disabled={!hasNextPage}>
              Вперёд
            </Button>
          </div>
        </div>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {payments.length === 0 ? (
          <EmptyState
            title={page === 1 ? 'Платежей пока нет' : 'На этой странице платежей нет'}
            description={
              page === 1 ? 'Оплаты появятся здесь после обработки заказов.' : 'Перейдите на предыдущую страницу.'
            }
          />
        ) : (
          payments.map((payment) => (
            <Card key={payment.id} className={payment.success ? 'border-green-500' : 'border-red-500'}>
              <CardHeader>
                <CardTitle>Платеж #{payment.id}</CardTitle>
                <CardDescription>Заказ #{payment.orderId}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-gray-600">
                  Метод: {payment.method === 'card' ? 'Карта' : payment.method === 'cash' ? 'Наличные' : 'Онлайн'}
                </p>
                <p className="text-sm text-gray-600">Сумма: {formatCurrency(payment.amount!)}</p>
                <p className="text-sm text-gray-600">Статус: {payment.success ? 'Успешно' : 'Ошибка'}</p>
                {payment.paidAt && <p className="text-sm text-gray-600">Оплачен: {formatDate(payment.paidAt)}</p>}
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
