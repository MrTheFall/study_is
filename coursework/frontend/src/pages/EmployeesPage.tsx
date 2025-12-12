import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { employeesApi } from '@/api/client';
import { Employee } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function EmployeesPage() {
  const navigate = useNavigate();
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    fullName: '',
    login: '',
    password: '',
    roleId: 2,
    contactPhone: '',
    salary: 0,
  });

  useEffect(() => {
    loadEmployees();
  }, []);

  const loadEmployees = async () => {
    try {
      const response = await employeesApi.getAllEmployees();
      setEmployees(response.data);
    } catch (error) {
      console.error('Ошибка загрузки сотрудников:', error);
    } finally {
      setLoading(false);
    }
  };

  const createEmployee = async () => {
    try {
      await employeesApi.createEmployee(formData);
      setShowForm(false);
      setFormData({
        fullName: '',
        login: '',
        password: '',
        roleId: 2,
        contactPhone: '',
        salary: 0,
      });
      loadEmployees();
    } catch (error) {
      console.error('Ошибка создания сотрудника:', error);
      alert('Ошибка создания сотрудника');
    }
  };

  const deleteEmployee = async (employeeId: number) => {
    if (!confirm('Вы уверены, что хотите удалить этого сотрудника?')) {
      return;
    }
    try {
      await employeesApi.deleteEmployee(employeeId);
      loadEmployees();
    } catch (error) {
      console.error('Ошибка удаления сотрудника:', error);
      alert('Ошибка удаления сотрудника');
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка сотрудников...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            <Button variant="outline" onClick={() => navigate('/')}>
              ← На главную
            </Button>
            <h1 className="text-3xl font-bold">Сотрудники</h1>
          </div>
          <Button onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Отмена' : 'Добавить сотрудника'}
          </Button>
        </div>

        {showForm && (
          <Card className="mb-8">
            <CardHeader>
              <CardTitle>Новый сотрудник</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                placeholder="Полное имя"
                value={formData.fullName}
                onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
              />
              <Input
                placeholder="Логин"
                value={formData.login}
                onChange={(e) => setFormData({ ...formData, login: e.target.value })}
              />
              <Input
                type="password"
                placeholder="Пароль"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              />
              <select
                className="w-full h-10 rounded-md border border-gray-300 px-3"
                value={formData.roleId}
                onChange={(e) => setFormData({ ...formData, roleId: parseInt(e.target.value) })}
              >
                <option value={1}>Менеджер</option>
                <option value={2}>Кассир</option>
                <option value={3}>Повар</option>
              </select>
              <Input
                placeholder="Телефон"
                value={formData.contactPhone}
                onChange={(e) => setFormData({ ...formData, contactPhone: e.target.value })}
              />
              <Input
                type="number"
                placeholder="Зарплата"
                value={formData.salary}
                onChange={(e) => setFormData({ ...formData, salary: parseFloat(e.target.value) || 0 })}
              />
              <Button onClick={createEmployee}>Создать</Button>
            </CardContent>
          </Card>
        )}

        {employees.length === 0 ? (
          <Card>
            <CardContent className="pt-6">
              <p className="text-center text-gray-500">Сотрудников пока нет</p>
            </CardContent>
          </Card>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {employees.map((employee) => {
              const roleName = employee.roleId === 1 ? 'Менеджер' : employee.roleId === 2 ? 'Кассир' : employee.roleId === 3 ? 'Повар' : `Роль #${employee.roleId}`;
              return (
                <Card key={employee.id}>
                  <CardHeader>
                    <CardTitle>{employee.fullName}</CardTitle>
                    <CardDescription>{roleName}</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm text-gray-600">Логин: {employee.login}</p>
                    <p className="text-sm text-gray-600">Телефон: {employee.contactPhone}</p>
                    {employee.salary && (
                      <p className="text-sm text-gray-600">Зарплата: ${employee.salary.toFixed(2)}</p>
                    )}
                    {employee.hiredAt && (
                      <p className="text-sm text-gray-600">Принят: {new Date(employee.hiredAt).toLocaleDateString()}</p>
                    )}
                    <div className="mt-4">
                      <Button
                        variant="outline"
                        onClick={() => employee.id && deleteEmployee(employee.id)}
                        className="w-full text-red-600 hover:text-red-700 hover:border-red-700"
                      >
                        Удалить
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

