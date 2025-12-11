import { useEffect, useState } from 'react';
import { shiftsApi } from '@/api/client';
import { Shift } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { formatDate } from '@/lib/utils';

export function ShiftsPage() {
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    startTime: '',
    endTime: '',
    employeeId: '',
  });

  useEffect(() => {
    loadShifts();
  }, []);

  const loadShifts = async () => {
    try {
      const response = await shiftsApi.getAllShifts();
      setShifts(response.data);
    } catch (error) {
      console.error('Ошибка загрузки смен:', error);
    } finally {
      setLoading(false);
    }
  };

  const createShift = async () => {
    try {
      await shiftsApi.createShift({
        startTime: formData.startTime,
        endTime: formData.endTime,
      });
      setShowForm(false);
      setFormData({ startTime: '', endTime: '', employeeId: '' });
      loadShifts();
    } catch (error) {
      console.error('Ошибка создания смены:', error);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка смен...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold">Смены</h1>
          <Button onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Отмена' : 'Создать смену'}
          </Button>
        </div>

        {showForm && (
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Новая смена</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                type="datetime-local"
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
                placeholder="Начало смены"
              />
              <Input
                type="datetime-local"
                value={formData.endTime}
                onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
                placeholder="Конец смены"
              />
              <Button onClick={createShift}>Создать</Button>
            </CardContent>
          </Card>
        )}

        <div className="space-y-4">
          {shifts.map((shift) => (
            <Card key={shift.id}>
              <CardHeader>
                <CardTitle>Смена #{shift.id}</CardTitle>
                <CardDescription>
                  {formatDate(shift.startTime!)} - {formatDate(shift.endTime!)}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-gray-600">
                  Статус: {shift.status}
                </p>
                {shift.employees && shift.employees.length > 0 && (
                  <div className="mt-4">
                    <p className="font-semibold mb-2">Сотрудники:</p>
                    <ul className="list-disc list-inside">
                      {shift.employees.map((emp) => (
                        <li key={emp.employeeId}>{emp.employeeName}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}

