import { useEffect, useMemo, useState } from 'react';
import { auditApi } from '@/api/client';
import { AuditLogEntry } from '@/api/generated/api';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { Select } from '@/components/ui/Select';
import { getApiErrorMessage } from '@/lib/apiError';
import { formatDate } from '@/lib/utils';

const ACTION_LABELS: Record<string, string> = {
  employee_create: 'Создание сотрудника',
  employee_update: 'Обновление сотрудника',
  employee_delete: 'Удаление сотрудника',
  menu_item_create: 'Добавление блюда',
  menu_item_update: 'Изменение блюда',
  menu_item_delete: 'Удаление блюда',
  inventory_adjust: 'Корректировка склада',
  courier_create: 'Добавление курьера',
  courier_update: 'Изменение курьера',
  courier_delete: 'Удаление курьера',
  shift_create: 'Создание смены',
  shift_update: 'Обновление смены',
  shift_delete: 'Удаление смены',
  shift_assign: 'Назначение на смену',
  shift_unassign: 'Снятие со смены',
  order_create: 'Создание заказа',
  order_status_change: 'Смена статуса заказа',
  order_payment_method_change: 'Смена способа оплаты',
  order_courier_assign: 'Назначение курьера',
  payment_process: 'Оплата заказа',
  salary_payment_create: 'Выплата зарплаты',
};

const ENTITY_LABELS: Record<string, string> = {
  order: 'Заказ',
  employee: 'Сотрудник',
  menu_item: 'Блюдо',
  inventory: 'Склад',
  courier: 'Курьер',
  shift: 'Смена',
  employee_shift: 'Назначение на смену',
  salary_payment: 'Выплата',
};

const toIsoStartOfDay = (date: string) => new Date(date).toISOString();

const toIsoEndOfDay = (date: string) => {
  const d = new Date(date);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString();
};

const parseOptionalInt = (value: string) => {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
};

export function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [employeeId, setEmployeeId] = useState('');
  const [orderId, setOrderId] = useState('');
  const [action, setAction] = useState('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [limit, setLimit] = useState(50);
  const [offset, setOffset] = useState(0);

  useEffect(() => {
    void loadLogs();
  }, []);

  useEffect(() => {
    if (loading) return;
    void loadLogs();
  }, [limit, offset]);

  const loadLogs = async (options?: { offset?: number }) => {
    setLoading(true);
    setError(null);
    try {
      const employeeIdValue = parseOptionalInt(employeeId);
      const orderIdValue = parseOptionalInt(orderId);
      const from = fromDate ? toIsoStartOfDay(fromDate) : undefined;
      const to = toDate ? toIsoEndOfDay(toDate) : undefined;
      const response = await auditApi.getAuditLogs(
        employeeIdValue,
        orderIdValue,
        action === 'all' ? undefined : action,
        from,
        to,
        limit,
        options?.offset ?? offset
      );
      setLogs(response.data || []);
    } catch (error) {
      console.error('Ошибка загрузки журнала:', error);
      setError(getApiErrorMessage(error, 'Не удалось загрузить журнал действий'));
    } finally {
      setLoading(false);
    }
  };

  const applyFilters = () => {
    setOffset(0);
    void loadLogs({ offset: 0 });
  };

  const resetFilters = () => {
    setEmployeeId('');
    setOrderId('');
    setAction('all');
    setFromDate('');
    setToDate('');
    setOffset(0);
    void loadLogs({ offset: 0 });
  };

  const actionOptions = useMemo(() => Object.entries(ACTION_LABELS), []);

  const renderActionLabel = (value?: string | null) => {
    if (!value) return '—';
    return ACTION_LABELS[value] || value;
  };

  const renderTarget = (entry: AuditLogEntry) => {
    if (entry.orderId) {
      return `Заказ #${entry.orderId}`;
    }
    if (entry.entityType) {
      const label = ENTITY_LABELS[entry.entityType] || entry.entityType;
      return entry.entityId ? `${label} #${entry.entityId}` : label;
    }
    return entry.entityId ? `#${entry.entityId}` : '—';
  };

  const renderDetails = (entry: AuditLogEntry) => {
    const parts: string[] = [];
    if (entry.fromValue || entry.toValue) {
      parts.push(`${entry.fromValue ?? '—'} → ${entry.toValue ?? '—'}`);
    }
    if (entry.details) {
      parts.push(entry.details);
    }
    return parts.length ? parts.join(' • ') : '—';
  };

  if (loading && logs.length === 0) {
    return <LoadingState message="Загрузка журнала действий..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Журнал действий</h1>
          <p className="text-gray-600 mt-1">Действия сотрудников и изменения заказов.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={applyFilters} disabled={loading}>
            Обновить
          </Button>
          <Button variant="outline" onClick={resetFilters} disabled={loading}>
            Сбросить
          </Button>
        </div>
      </div>

      {error && (
        <RetryAlert
          title="Не удалось загрузить журнал"
          message={error}
          onRetry={applyFilters}
          retryDisabled={loading}
        />
      )}

      <Card>
        <CardHeader>
          <CardTitle>Фильтры</CardTitle>
          <CardDescription>Используйте фильтры, чтобы найти нужные действия.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">Сотрудник (ID)</label>
              <Input
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value)}
                placeholder="Например, 2"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Заказ (ID)</label>
              <Input value={orderId} onChange={(e) => setOrderId(e.target.value)} placeholder="Например, 101" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Тип действия</label>
              <Select value={action} onChange={(e) => setAction(e.target.value)}>
                <option value="all">Все действия</option>
                {actionOptions.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Дата с</label>
              <Input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Дата по</label>
              <Input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Лимит</label>
              <Select value={String(limit)} onChange={(e) => setLimit(parseInt(e.target.value, 10))}>
                <option value="20">20</option>
                <option value="50">50</option>
                <option value="100">100</option>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Записи журнала</CardTitle>
          <CardDescription>Последние действия с учётом выбранных фильтров.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between gap-2 mb-4">
            <div className="text-sm text-gray-500">
              Смещение: {offset} • Показано: {logs.length}
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setOffset((prev) => Math.max(0, prev - limit))}
                disabled={loading || offset === 0}
              >
                Назад
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setOffset((prev) => prev + limit)}
                disabled={loading || logs.length < limit}
              >
                Вперёд
              </Button>
            </div>
          </div>

          {loading ? (
            <LoadingState message="Загрузка журнала..." className="py-10" />
          ) : logs.length === 0 ? (
            <EmptyState title="Нет записей" description="Попробуйте изменить фильтры или обновить страницу." />
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-2 pr-4">Время</th>
                    <th className="text-left py-2 pr-4">Сотрудник</th>
                    <th className="text-left py-2 pr-4">Действие</th>
                    <th className="text-left py-2 pr-4">Объект</th>
                    <th className="text-left py-2 pr-4">Детали</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((entry) => (
                    <tr key={entry.id} className="border-b last:border-b-0">
                      <td className="py-2 pr-4 whitespace-nowrap">
                        {entry.createdAt ? formatDate(entry.createdAt) : '—'}
                      </td>
                      <td className="py-2 pr-4">
                        {entry.employeeName ? entry.employeeName : entry.employeeId ? `#${entry.employeeId}` : '—'}
                      </td>
                      <td className="py-2 pr-4">{renderActionLabel(entry.action)}</td>
                      <td className="py-2 pr-4">{renderTarget(entry)}</td>
                      <td className="py-2 pr-4">{renderDetails(entry)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
