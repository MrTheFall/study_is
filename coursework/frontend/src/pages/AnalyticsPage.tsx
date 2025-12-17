import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { analyticsApi } from '@/api/client';
import { FinancialSummary, ReportView, SalesByEmployeeItem, SalesByTimeOfDayItem, SalesSummary, TopMenuItem } from '@/api/generated/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { formatCurrency } from '@/lib/utils';

export function AnalyticsPage() {
  const navigate = useNavigate();
  const [salesSummary, setSalesSummary] = useState<SalesSummary | null>(null);
  const [topItems, setTopItems] = useState<TopMenuItem[]>([]);
  const [salesByEmployee, setSalesByEmployee] = useState<SalesByEmployeeItem[]>([]);
  const [salesByTimeOfDay, setSalesByTimeOfDay] = useState<SalesByTimeOfDayItem[]>([]);
  const [financialSummary, setFinancialSummary] = useState<FinancialSummary | null>(null);
  const [reportViews, setReportViews] = useState<ReportView[]>([]);
  const [loading, setLoading] = useState(true);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  useEffect(() => {
    loadAnalytics();
  }, []);

  const toIsoStartOfDay = (date: string) => new Date(date).toISOString();

  const toIsoEndOfDay = (date: string) => {
    const d = new Date(date);
    d.setUTCDate(d.getUTCDate() + 1);
    return d.toISOString();
  };

  const loadAnalytics = async () => {
    try {
      const from = dateFrom
        ? toIsoStartOfDay(dateFrom)
        : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
      const to = dateTo ? toIsoEndOfDay(dateTo) : new Date().toISOString();

      const [summaryResponse, topItemsResponse, byEmployeeResponse, byTimeOfDayResponse, financialResponse] = await Promise.all([
        analyticsApi.getSalesSummary(from, to),
        analyticsApi.getTopMenuItems(from, to, 10),
        analyticsApi.getSalesByEmployee(from, to),
        analyticsApi.getSalesByTimeOfDay(from, to),
        analyticsApi.getFinancialSummary(from, to),
      ]);

      setSalesSummary(summaryResponse.data);
      setTopItems(topItemsResponse.data);
      setSalesByEmployee(byEmployeeResponse.data);
      setSalesByTimeOfDay(byTimeOfDayResponse.data);
      setFinancialSummary(financialResponse.data);

      const viewsResponse = await analyticsApi.getReportViews(50, 0);
      setReportViews(viewsResponse.data);
    } catch (error) {
      console.error('Ошибка загрузки аналитики:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка аналитики...</div>;
  }

  const formatTimeOfDayBucket = (bucket?: string) => {
    switch (bucket) {
      case 'morning':
        return 'Утро (06–12)';
      case 'day':
        return 'День (12–18)';
      case 'evening':
        return 'Вечер (18–24)';
      case 'night':
        return 'Ночь (00–06)';
      default:
        return bucket || '—';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-4 mb-8">
          <Button variant="outline" onClick={() => navigate('/')}>
            ← На главную
          </Button>
          <h1 className="text-3xl font-bold">Аналитика</h1>
        </div>

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
          salesSummary.hasData === false ? (
            <Card className="mb-8">
              <CardHeader>
                <CardTitle>Продажи</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-center text-gray-500">Нет данных за выбранный период</p>
              </CardContent>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
              <Card>
                <CardHeader>
                  <CardTitle>Общая выручка</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">{formatCurrency(salesSummary.revenue!)}</p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Количество заказов</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">{salesSummary.ordersCnt || 0}</p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Средний чек</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">
                    {formatCurrency(salesSummary.avgTicket!)}
                  </p>
                </CardContent>
              </Card>
            </div>
          )
        )}

        {financialSummary && (
          financialSummary.hasData === false ? (
            <Card className="mb-8">
              <CardHeader>
                <CardTitle>Финансы</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-center text-gray-500">Нет данных за выбранный период</p>
              </CardContent>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
              <Card>
                <CardHeader>
                  <CardTitle>Выручка</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">{formatCurrency(financialSummary.revenue || 0)}</p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Расходы (ингредиенты)</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">{formatCurrency(financialSummary.ingredientExpenses || 0)}</p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Расходы (зарплата)</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">{formatCurrency(financialSummary.salaryExpenses || 0)}</p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Прибыль</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-3xl font-bold">{formatCurrency(financialSummary.profit || 0)}</p>
                </CardContent>
              </Card>
            </div>
          )
        )}

        <Card>
          <CardHeader>
            <CardTitle>Топ блюд</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {topItems.length === 0 ? (
                <p className="text-center text-gray-500">Нет данных</p>
              ) : (
                topItems.map((item, idx) => (
                  <div key={idx} className="flex justify-between items-center p-4 border rounded-md">
                    <div>
                      <p className="font-semibold">{item.name || `Блюдо #${item.menuItemId}`}</p>
                      <p className="text-sm text-gray-500">
                        Количество: {item.quantity || 0} | Выручка: {formatCurrency(item.revenue!)}
                      </p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-8">
          <Card>
            <CardHeader>
              <CardTitle>Продажи по сотрудникам</CardTitle>
            </CardHeader>
            <CardContent>
              {salesByEmployee.length === 0 ? (
                <p className="text-center text-gray-500">Нет данных</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead>
                      <tr className="border-b">
                        <th className="text-left py-2 pr-4">Сотрудник</th>
                        <th className="text-right py-2 pr-4">Заказы</th>
                        <th className="text-right py-2 pr-4">Выручка</th>
                        <th className="text-right py-2 pr-4">Средний чек</th>
                      </tr>
                    </thead>
                    <tbody>
                      {salesByEmployee.map((row) => (
                        <tr key={row.employeeId} className="border-b last:border-b-0">
                          <td className="py-2 pr-4">{row.employeeName || `Сотрудник #${row.employeeId}`}</td>
                          <td className="py-2 pr-4 text-right">{row.ordersCnt || 0}</td>
                          <td className="py-2 pr-4 text-right">{formatCurrency(row.revenue || 0)}</td>
                          <td className="py-2 pr-4 text-right">{formatCurrency(row.avgTicket || 0)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Продажи по времени суток</CardTitle>
            </CardHeader>
            <CardContent>
              {salesByTimeOfDay.length === 0 ? (
                <p className="text-center text-gray-500">Нет данных</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead>
                      <tr className="border-b">
                        <th className="text-left py-2 pr-4">Время суток</th>
                        <th className="text-right py-2 pr-4">Заказы</th>
                        <th className="text-right py-2 pr-4">Выручка</th>
                        <th className="text-right py-2 pr-4">Средний чек</th>
                      </tr>
                    </thead>
                    <tbody>
                      {salesByTimeOfDay.map((row, idx) => (
                        <tr key={`${row.bucket}-${idx}`} className="border-b last:border-b-0">
                          <td className="py-2 pr-4">{formatTimeOfDayBucket(row.bucket)}</td>
                          <td className="py-2 pr-4 text-right">{row.ordersCnt || 0}</td>
                          <td className="py-2 pr-4 text-right">{formatCurrency(row.revenue || 0)}</td>
                          <td className="py-2 pr-4 text-right">{formatCurrency(row.avgTicket || 0)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <Card className="mt-8">
          <CardHeader>
            <CardTitle>История просмотров отчётов</CardTitle>
          </CardHeader>
          <CardContent>
            {reportViews.length === 0 ? (
              <p className="text-center text-gray-500">Нет данных</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="border-b">
                      <th className="text-left py-2 pr-4">Время</th>
                      <th className="text-left py-2 pr-4">Отчёт</th>
                      <th className="text-left py-2 pr-4">Период</th>
                      <th className="text-left py-2 pr-4">Сотрудник</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reportViews.map((v) => (
                      <tr key={v.id} className="border-b last:border-b-0">
                        <td className="py-2 pr-4 whitespace-nowrap">
                          {v.viewedAt ? new Date(v.viewedAt).toLocaleString() : '—'}
                        </td>
                        <td className="py-2 pr-4">
                          {v.report === 'sales_summary'
                            ? 'Сводка продаж'
                            : v.report === 'top_menu_items'
                              ? 'Топ блюд'
                              : v.report === 'sales_by_employee'
                                ? 'Продажи по сотрудникам'
                                : v.report === 'sales_by_time_of_day'
                                  ? 'Продажи по времени суток'
                                  : v.report === 'financial_summary'
                                    ? 'Финансовый отчёт'
                              : v.report || '—'}
                        </td>
                        <td className="py-2 pr-4">
                          {v.from ? new Date(v.from).toLocaleDateString() : '—'} –{' '}
                          {v.to ? new Date(v.to).toLocaleDateString() : '—'}
                        </td>
                        <td className="py-2 pr-4">{v.employeeName ? v.employeeName : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
