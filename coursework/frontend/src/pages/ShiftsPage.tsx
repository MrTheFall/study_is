import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { shiftsApi } from '@/api/client';
import { Shift } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { formatDate } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';

export function ShiftsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { isManager } = useAuthStore();
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    shiftDate: '',
    startTime: '',
    endTime: '',
    note: '',
  });
  const [filterDate, setFilterDate] = useState(searchParams.get('date') || '');

  useEffect(() => {
    loadShifts();
  }, [filterDate]);

  const loadShifts = async () => {
    try {
      setError(null);
      setLoading(true);
      const dateParam = filterDate || undefined;
      const response = await shiftsApi.getAllShifts(dateParam);
      setShifts(response.data || []);
    } catch (error: any) {
      console.error('Ошибка загрузки смен:', error);
      setError(error.response?.data?.message || 'Ошибка загрузки смен');
      setShifts([]);
    } finally {
      setLoading(false);
    }
  };

  const createShift = async () => {
    try {
      if (!formData.shiftDate || !formData.startTime || !formData.endTime) {
        alert('Заполните все обязательные поля');
        return;
      }
      await shiftsApi.createShift({
        shiftDate: formData.shiftDate,
        startTime: formData.startTime,
        endTime: formData.endTime,
        note: formData.note || undefined,
      });
      setShowForm(false);
      setFormData({ shiftDate: '', startTime: '', endTime: '', note: '' });
      loadShifts();
    } catch (error: any) {
      console.error('Ошибка создания смены:', error);
      alert(error.response?.data?.message || 'Ошибка создания смены');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка смен...</div>;
  }

  if (error && !loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="flex items-center gap-4 mb-8">
            <Button variant="outline" onClick={() => navigate('/')}>
              ← На главную
            </Button>
            <h1 className="text-3xl font-bold">Смены</h1>
          </div>
          <Card>
            <CardContent className="pt-6">
              <p className="text-center text-red-500">{error}</p>
              <Button onClick={loadShifts} className="mt-4">Попробовать снова</Button>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            <Button variant="outline" onClick={() => navigate('/')}>
              ← На главную
            </Button>
            <h1 className="text-3xl font-bold">Смены</h1>
          </div>
          {isManager() && (
            <Button onClick={() => setShowForm(!showForm)}>
              {showForm ? 'Отмена' : 'Создать смену'}
            </Button>
          )}
        </div>

        <div className="mb-4 flex gap-4 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium mb-1">Фильтр по дате</label>
            <Input
              type="date"
              value={filterDate}
              onChange={(e) => {
                setFilterDate(e.target.value);
                if (e.target.value) {
                  navigate(`/shifts?date=${e.target.value}`, { replace: true });
                } else {
                  navigate('/shifts', { replace: true });
                }
              }}
            />
          </div>
          {filterDate && (
            <Button variant="outline" onClick={() => {
              setFilterDate('');
              navigate('/shifts', { replace: true });
            }}>
              Сбросить фильтр
            </Button>
          )}
        </div>

        {showForm && isManager() && (
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Новая смена</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                type="date"
                value={formData.shiftDate}
                onChange={(e) => setFormData({ ...formData, shiftDate: e.target.value })}
                placeholder="Дата смены"
                required
              />
              <Input
                type="time"
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
                placeholder="Начало смены"
                required
              />
              <Input
                type="time"
                value={formData.endTime}
                onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
                placeholder="Конец смены"
                required
              />
              <Input
                value={formData.note}
                onChange={(e) => setFormData({ ...formData, note: e.target.value })}
                placeholder="Примечание (необязательно)"
              />
              <Button onClick={createShift}>Создать</Button>
            </CardContent>
          </Card>
        )}

        <div className="space-y-4">
          {shifts.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                <p className="text-center text-gray-500">Смен пока нет</p>
              </CardContent>
            </Card>
          ) : (
            shifts.map((shift) => (
              <Card key={shift.id}>
                <CardHeader>
                  <CardTitle>Смена #{shift.id}</CardTitle>
                  <CardDescription>
                    {shift.shiftDate ? formatDate(shift.shiftDate) : 'Дата не указана'}
                    {shift.startTime && shift.endTime && (
                      <> | {shift.startTime} - {shift.endTime}</>
                    )}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  {shift.note && (
                    <p className="text-sm text-gray-600 mb-2">
                      Примечание: {shift.note}
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

