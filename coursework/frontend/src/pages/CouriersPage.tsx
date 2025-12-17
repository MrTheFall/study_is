import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { couriersApi } from '@/api/client';
import { Courier } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

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
  const navigate = useNavigate();
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingCourierId, setEditingCourierId] = useState<number | null>(null);
  const [formData, setFormData] = useState<CourierFormState>(emptyForm);

  useEffect(() => {
    loadCouriers();
  }, []);

  const loadCouriers = async () => {
    try {
      const response = await couriersApi.getAllCouriers();
      setCouriers(response.data || []);
    } catch (error) {
      console.error('Ошибка загрузки курьеров:', error);
      alert('Ошибка загрузки курьеров');
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
        alert('Заполните имя и телефон');
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
      alert(error.response?.data?.message || 'Ошибка сохранения курьера');
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

  const deleteCourier = async (courierId: number) => {
    if (!confirm('Удалить курьера?')) return;
    try {
      await couriersApi.deleteCourier(courierId);
      loadCouriers();
    } catch (error: any) {
      console.error('Ошибка удаления курьера:', error);
      alert(error.response?.data?.message || 'Ошибка удаления курьера');
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
      alert(error.response?.data?.message || 'Ошибка обновления доступности');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка курьеров...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            <Button variant="outline" onClick={() => navigate('/')}>
              ← На главную
            </Button>
            <h1 className="text-3xl font-bold">Курьеры</h1>
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
          <Card>
            <CardContent className="pt-6">
              <p className="text-center text-gray-500">Курьеров пока нет</p>
            </CardContent>
          </Card>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {couriers.map((courier) => (
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
                      onClick={() => courier.id && deleteCourier(courier.id)}
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
      </div>
    </div>
  );
}

