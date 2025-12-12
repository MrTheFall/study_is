import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { paymentsApi, ordersApi } from '@/api/client';
import { Payment, PaymentMethod } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { formatCurrency, formatDate } from '@/lib/utils';

export function PaymentsPage() {
  const navigate = useNavigate();
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    orderId: '',
    method: 'card' as PaymentMethod,
    amountReceived: '',
  });

  useEffect(() => {
    loadPayments();
  }, []);

  const loadPayments = async () => {
    try {
      const response = await ordersApi.getAllOrders();
      const orders = response.data;
      const paymentPromises = orders
        .filter(order => order.id)
        .map(order => 
          paymentsApi.getPaymentByOrderId(order.id!)
            .then(res => res.data)
            .catch(() => null)
        );
      const paymentResults = await Promise.all(paymentPromises);
      setPayments(paymentResults.filter((p): p is Payment => p !== null));
    } catch (error) {
      console.error('Ошибка загрузки платежей:', error);
    } finally {
      setLoading(false);
    }
  };

  const processPayment = async () => {
    try {
      if (!formData.orderId) {
        alert('Введите ID заказа');
        return;
      }
      await paymentsApi.processPayment({
        orderId: parseInt(formData.orderId),
        method: formData.method,
      });
      setShowForm(false);
      setFormData({
        orderId: '',
        method: 'card',
        amountReceived: '',
      });
      loadPayments();
    } catch (error: any) {
      console.error('Ошибка обработки платежа:', error);
      alert(error.response?.data?.message || 'Ошибка обработки платежа');
    }
  };

  const processCashPayment = async () => {
    try {
      if (!formData.orderId || !formData.amountReceived) {
        alert('Введите ID заказа и полученную сумму');
        return;
      }
      const response = await paymentsApi.processCashPayment({
        orderId: parseInt(formData.orderId),
        amountReceived: parseFloat(formData.amountReceived),
      });
      alert(`Сдача: ${formatCurrency(response.data.change!)}`);
      setShowForm(false);
      setFormData({
        orderId: '',
        method: 'cash',
        amountReceived: '',
      });
      loadPayments();
    } catch (error: any) {
      console.error('Ошибка обработки наличного платежа:', error);
      alert(error.response?.data?.message || 'Ошибка обработки платежа');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка платежей...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            <Button variant="outline" onClick={() => navigate('/')}>
              ← На главную
            </Button>
            <h1 className="text-3xl font-bold">Платежи</h1>
          </div>
          <Button onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Отмена' : 'Обработать платеж'}
          </Button>
        </div>

        {showForm && (
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Обработка платежа</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                type="number"
                placeholder="ID заказа"
                value={formData.orderId}
                onChange={(e) => setFormData({ ...formData, orderId: e.target.value })}
              />
              <select
                className="w-full h-10 rounded-md border border-gray-300 px-3"
                value={formData.method}
                onChange={(e) => setFormData({ ...formData, method: e.target.value as PaymentMethod })}
              >
                <option value="card">Карта</option>
                <option value="cash">Наличные</option>
                <option value="online">Онлайн</option>
              </select>
              {formData.method === 'cash' && (
                <Input
                  type="number"
                  step="0.01"
                  placeholder="Полученная сумма"
                  value={formData.amountReceived}
                  onChange={(e) => setFormData({ ...formData, amountReceived: e.target.value })}
                />
              )}
              {formData.method === 'cash' ? (
                <Button onClick={processCashPayment}>Обработать наличный платеж</Button>
              ) : (
                <Button onClick={processPayment}>Обработать платеж</Button>
              )}
            </CardContent>
          </Card>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {payments.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <p className="text-center text-gray-500">Платежей пока нет</p>
              </CardContent>
            </Card>
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
                  <p className="text-sm text-gray-600">
                    Статус: {payment.success ? 'Успешно' : 'Ошибка'}
                  </p>
                  {payment.paidAt && (
                    <p className="text-sm text-gray-600">
                      Оплачен: {formatDate(payment.paidAt)}
                    </p>
                  )}
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

