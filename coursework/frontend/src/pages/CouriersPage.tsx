import { useEffect, useMemo, useState } from 'react';
import { couriersApi } from '@/api/client';
import { Courier } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { Select } from '@/components/ui/Select';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { getApiErrorMessage } from '@/lib/apiError';
import { useToast } from '@/components/ui/toast';

type CourierFormState = {
  name: string;
  phone: string;
  vehicleInfo: string;
  available: boolean;
};

const emptyForm: CourierFormState = {
  name: '',
  phone: '',
  vehicleInfo: '',
  available: true,
};

export function CouriersPage() {
  const toast = useToast();
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'free' | 'busy' | 'unavailable'>('all');
  const [showForm, setShowForm] = useState(false);
  const [editingCourierId, setEditingCourierId] = useState<number | null>(null);
  const [formData, setFormData] = useState<CourierFormState>(emptyForm);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingCourier, setDeletingCourier] = useState<Courier | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    loadCouriers();
  }, []);

  const loadCouriers = async () => {
    try {
      const response = await couriersApi.getAllCouriers();
      setCouriers(response.data || []);
      setError(null);
    } catch (error) {
      console.error('Ошибка загрузки курьеров:', error);
      setError(getApiErrorMessage(error, 'Не удалось загрузить курьеров'));
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setEditingCourierId(null);
    setFormData(emptyForm);
    setShowForm(false);
  };

  const submitForm = async () => {
    try {
      if (!formData.name.trim() || !formData.phone.trim()) {
        setError('Заполните имя и телефон');
        return;
      }

      if (editingCourierId) {
        await couriersApi.updateCourier(editingCourierId, formData);
      } else {
        await couriersApi.createCourier(formData);
      }

      resetForm();
      loadCouriers();
    } catch (error: any) {
      console.error('Ошибка сохранения курьера:', error);
      setError(getApiErrorMessage(error, 'Ошибка сохранения курьера'));
    }
  };

  const startEdit = (courier: Courier) => {
    setEditingCourierId(courier.id ?? null);
    setFormData({
      name: courier.name || '',
      phone: courier.phone || '',
      vehicleInfo: courier.vehicleInfo || '',
      available: courier.available !== false,
    });
    setShowForm(true);
  };

  const requestDeleteCourier = (courier: Courier) => {
    setDeletingCourier(courier);
    setDeleteDialogOpen(true);
  };

  const confirmDeleteCourier = async () => {
    if (!deletingCourier?.id) return;
    setDeleting(true);
    try {
      await couriersApi.deleteCourier(deletingCourier.id);
      toast.success('Курьер удалён');
      setDeleteDialogOpen(false);
      setDeletingCourier(null);
      await loadCouriers();
    } catch (error: any) {
      console.error('Ошибка удаления курьера:', error);
      setError(getApiErrorMessage(error, 'Ошибка удаления курьера'));
      toast.error(getApiErrorMessage(error, 'Ошибка удаления курьера'), { title: 'Ошибка' });
    } finally {
      setDeleting(false);
    }
  };

  const toggleAvailability = async (courier: Courier, available: boolean) => {
    if (!courier.id) return;
    try {
      await couriersApi.updateCourier(courier.id, {
        name: courier.name || '',
        phone: courier.phone || '',
        vehicleInfo: courier.vehicleInfo,
        available,
      });
      loadCouriers();
    } catch (error: any) {
      console.error('Ошибка обновления доступности:', error);
      setError(getApiErrorMessage(error, 'Ошибка обновления доступности'));
    }
  };

  const visibleCouriers = useMemo(() => {
    const q = query.trim().toLowerCase();

    const rank = (courier: Courier) => {
      if (courier.busy) return 1;
      if (courier.available === false) return 2;
      return 0;
    };

    return couriers
      .filter((courier) => {
        if (statusFilter === 'busy') return courier.busy === true;
        if (statusFilter === 'unavailable') return courier.available === false;
        if (statusFilter === 'free') return courier.busy !== true && courier.available !== false;
        return true;
      })
      .filter((courier) => {
        if (!q) return true;
        const hay =
          `${courier.id ?? ''} ${courier.name ?? ''} ${courier.phone ?? ''} ${courier.vehicleInfo ?? ''}`.toLowerCase();
        return hay.includes(q);
      })
      .sort((a, b) => {
        const ra = rank(a);
        const rb = rank(b);
        if (ra !== rb) return ra - rb;
        const aName = (a.name || '').toLowerCase();
        const bName = (b.name || '').toLowerCase();
        return aName.localeCompare(bName);
      });
  }, [couriers, query, statusFilter]);

  const resetFilters = () => {
    setQuery('');
    setStatusFilter('all');
  };

  if (loading) {
    return <LoadingState message="Загрузка курьеров..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Курьеры</h1>
          <p className="text-gray-600 mt-1">Список, доступность и редактирование данных курьеров.</p>
        </div>
        <Button
          onClick={() => {
            if (showForm) {
              resetForm();
            } else {
              setShowForm(true);
            }
          }}
        >
          {showForm ? 'Отмена' : 'Добавить курьера'}
        </Button>
      </div>

      {error && <RetryAlert message={error} onRetry={loadCouriers} />}

      {couriers.length > 0 && (
        <Card>
          <CardContent className="pt-6 space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1">Поиск</label>
                <Input
                  placeholder="Имя, телефон, транспорт или ID..."
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Фильтр</label>
                <Select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value as 'all' | 'free' | 'busy' | 'unavailable')}
                >
                  <option value="all">Все</option>
                  <option value="free">Свободные</option>
                  <option value="busy">Занятые</option>
                  <option value="unavailable">Недоступные</option>
                </Select>
              </div>
            </div>
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div className="text-sm text-gray-500">
                Показано: {visibleCouriers.length} из {couriers.length}
              </div>
              {query.trim() || statusFilter !== 'all' ? (
                <Button variant="outline" size="sm" onClick={resetFilters}>
                  Сбросить
                </Button>
              ) : null}
            </div>
          </CardContent>
        </Card>
      )}

      {showForm && (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle>{editingCourierId ? `Курьер #${editingCourierId}` : 'Новый курьер'}</CardTitle>
            <CardDescription>Имя, телефон и способ доставки</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Input
              placeholder="Имя"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            />
            <Input
              placeholder="Телефон"
              value={formData.phone}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
            />
            <Input
              placeholder="Транспорт (например, Bike)"
              value={formData.vehicleInfo}
              onChange={(e) => setFormData({ ...formData, vehicleInfo: e.target.value })}
            />
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={formData.available}
                onChange={(e) => setFormData({ ...formData, available: e.target.checked })}
              />
              Доступен
            </label>
            <div className="flex gap-2">
              <Button onClick={submitForm}>{editingCourierId ? 'Сохранить' : 'Создать'}</Button>
              {editingCourierId && (
                <Button variant="outline" onClick={resetForm}>
                  Отмена
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {couriers.length === 0 ? (
        <EmptyState title="Курьеров пока нет" description="Добавьте первого курьера, чтобы назначать доставку." />
      ) : visibleCouriers.length === 0 ? (
        <EmptyState
          title="Ничего не найдено"
          description="Попробуйте изменить фильтры или сбросить поиск."
          action={
            <Button variant="outline" onClick={resetFilters}>
              Сбросить фильтры
            </Button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {visibleCouriers.map((courier) => (
            <Card key={courier.id}>
              <CardHeader>
                <CardTitle>{courier.name || `Курьер #${courier.id}`}</CardTitle>
                <CardDescription>
                  {courier.busy ? 'Занят' : courier.available === false ? 'Недоступен' : 'Свободен'}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-gray-600">Телефон: {courier.phone || '—'}</p>
                <p className="text-sm text-gray-600">Транспорт: {courier.vehicleInfo || '—'}</p>
                <label className="mt-4 flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={courier.available !== false}
                    disabled={courier.busy === true}
                    onChange={(e) => toggleAvailability(courier, e.target.checked)}
                  />
                  Доступен {courier.busy ? '(нельзя изменить, занят)' : ''}
                </label>
                <div className="mt-4 flex gap-2">
                  <Button variant="outline" onClick={() => startEdit(courier)}>
                    Редактировать
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => requestDeleteCourier(courier)}
                    className="text-red-600 hover:text-red-700 hover:border-red-700"
                    disabled={courier.busy === true}
                  >
                    Удалить
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={(open) => {
          setDeleteDialogOpen(open);
          if (!open) {
            setDeletingCourier(null);
          }
        }}
        title={
          deletingCourier?.name
            ? `Удалить курьера «${deletingCourier.name}»?`
            : deletingCourier?.id
              ? `Удалить курьера #${deletingCourier.id}?`
              : 'Удалить курьера?'
        }
        description="Действие необратимо."
        confirmText="Удалить"
        confirmDisabled={deleting}
        onConfirm={() => void confirmDeleteCourier()}
      />
    </div>
  );
}
