import { useEffect, useState } from 'react';
import { employeesApi } from '@/api/client';
import { Employee } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function EmployeesPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    login: '',
    password: '',
    role: 'WAITER' as Employee.RoleEnum,
    phone: '',
    email: '',
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
        name: '',
        login: '',
        password: '',
        role: 'WAITER',
        phone: '',
        email: '',
      });
      loadEmployees();
    } catch (error) {
      console.error('Ошибка создания сотрудника:', error);
    }
  };

  if (loading) {
    return <div className="p-8 text-center">Загрузка сотрудников...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold">Сотрудники</h1>
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
                placeholder="Имя"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
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
                value={formData.role}
                onChange={(e) => setFormData({ ...formData, role: e.target.value as Employee.RoleEnum })}
              >
                <option value="WAITER">Официант</option>
                <option value="COOK">Повар</option>
                <option value="MANAGER">Управляющий</option>
              </select>
              <Input
                placeholder="Телефон"
                value={formData.phone}
                onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              />
              <Input
                type="email"
                placeholder="Email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              />
              <Button onClick={createEmployee}>Создать</Button>
            </CardContent>
          </Card>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {employees.map((employee) => (
            <Card key={employee.id}>
              <CardHeader>
                <CardTitle>{employee.name}</CardTitle>
                <CardDescription>{employee.role}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-gray-600">Логин: {employee.login}</p>
                <p className="text-sm text-gray-600">Телефон: {employee.phone}</p>
                <p className="text-sm text-gray-600">Email: {employee.email}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}

