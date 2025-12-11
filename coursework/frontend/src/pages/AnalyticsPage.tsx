import { useEffect, useState } from 'react';
import { analyticsApi } from '@/api/client';
import { SalesSummary, TopMenuItem } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { formatCurrency, formatDate } from '@/lib/utils';

export function AnalyticsPage() {
  const [salesSummary, setSalesSummary] = useState<SalesSummary | null>(null);
  const [topItems, setTopItems] = useState<TopMenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  useEffect(() => {
    loadAnalytics();
  }, []);

  const loadAnalytics = async () => {
    try {
      const from = dateFrom || new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
      const to = dateTo || new Date().toISOString();

      const [summaryResponse, topItemsResponse] = await Promise.all([
        analyticsApi.getSalesSummary(from, to),
        analyticsApi.getTopMenuItems(from, to, 10),
      ]);

      setSalesSummary(summaryResponse.data);
      setTopItems(topItemsResponse.data);
    } catch (error) {
      console.error('Ошибка загрузки аналитики:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка аналитики...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-3xl font-bold mb-8">Аналитика</h1>

        <div className="mb-8 flex gap-4">
          <input
            type="date"
            value={dateFrom}
            onChange={(e) => setDateFrom(e.target.value)}
            className="px-4 py-2 border rounded-md"
          />
          <input
            type="date"
            value={dateTo}
            onChange={(e) => setDateTo(e.target.value)}
            className="px-4 py-2 border rounded-md"
          />
          <button
            onClick={loadAnalytics}
            className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
          >
            Обновить
          </button>
        </div>

        {salesSummary && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <Card>
              <CardHeader>
                <CardTitle>Общая выручка</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{formatCurrency(salesSummary.totalRevenue!)}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Количество заказов</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{salesSummary.totalOrders}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Средний чек</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">
                  {formatCurrency(salesSummary.averageOrderValue!)}
                </p>
              </CardContent>
            </Card>
          </div>
        )}

        <Card>
          <CardHeader>
            <CardTitle>Топ блюд</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {topItems.map((item, idx) => (
                <div key={idx} className="flex justify-between items-center p-4 border rounded-md">
                  <div>
                    <p className="font-semibold">{item.menuItemName}</p>
                    <p className="text-sm text-gray-500">
                      Заказов: {item.orderCount} | Выручка: {formatCurrency(item.revenue!)}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

