import { useEffect, useMemo, useState } from 'react';
import { employeesApi } from '@/api/client';
import { Employee } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { getApiErrorMessage } from '@/lib/apiError';
import { useToast } from '@/components/ui/toast';

export function EmployeesPage() {
  const toast = useToast();
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    fullName: '',
    login: '',
    password: '',
    roleId: 2,
    contactPhone: '',
    salary: '',
  });

  useEffect(() => {
    loadEmployees();
  }, []);

  const loadEmployees = async () => {
    try {
      setError(null);
      setLoading(true);
      const response = await employeesApi.getAllEmployees();
      setEmployees(response.data || []);
    } catch (e: any) {
      console.error('Ошибка загрузки сотрудников:', e);
      setError(getApiErrorMessage(e, 'Не удалось загрузить сотрудников'));
      setEmployees([]);
    } finally {
      setLoading(false);
    }
  };

  const roleLabel = (roleId?: number | null) =>
    roleId === 1 ? 'Менеджер' : roleId === 2 ? 'Кассир' : roleId === 3 ? 'Повар' : roleId ? `Роль #${roleId}` : '—';

  const filteredEmployees = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return employees;
    return employees.filter((e) => {
      const name = (e.fullName || '').toLowerCase();
      const login = (e.login || '').toLowerCase();
      return name.includes(q) || login.includes(q) || roleLabel(e.roleId).toLowerCase().includes(q);
    });
  }, [employees, search]);

  const openCreate = () => {
    setEditingEmployee(null);
    setFormError(null);
    setFormData({
      fullName: '',
      login: '',
      password: '',
      roleId: 2,
      contactPhone: '',
      salary: '',
    });
    setDialogOpen(true);
  };

  const openEdit = (employee: Employee) => {
    setEditingEmployee(employee);
    setFormError(null);
    setFormData({
      fullName: employee.fullName || '',
      login: employee.login || '',
      password: '',
      roleId: employee.roleId ?? 2,
      contactPhone: employee.contactPhone || '',
      salary: employee.salary != null ? String(employee.salary) : '',
    });
    setDialogOpen(true);
  };

  const validate = () => {
    const fullName = formData.fullName.trim();
    const login = formData.login.trim();
    const password = formData.password;

    if (!fullName) return 'Полное имя обязательно';
    if (!login) return 'Логин обязателен';
    if (!formData.roleId) return 'Роль обязательна';

    if (!editingEmployee && !password.trim()) return 'Пароль обязателен для нового сотрудника';
    if (formData.salary.trim()) {
      const salary = Number(formData.salary);
      if (!Number.isFinite(salary)) return 'Зарплата должна быть числом';
      if (salary < 0) return 'Зарплата не может быть отрицательной';
    }
    return null;
  };

  const saveEmployee = async () => {
    const message = validate();
    if (message) {
      setFormError(message);
      return;
    }

    const salaryValue = formData.salary.trim() ? Number(formData.salary) : undefined;

    setSaving(true);
    try {
      setFormError(null);
      const payload: any = {
        fullName: formData.fullName.trim(),
        login: formData.login.trim(),
        password: formData.password, // empty => keep unchanged (edit)
        roleId: formData.roleId,
        contactPhone: formData.contactPhone.trim() || undefined,
        salary: salaryValue,
      };

      if (editingEmployee?.id) {
        await employeesApi.updateEmployee(editingEmployee.id, payload);
      } else {
        await employeesApi.createEmployee(payload);
      }

      setDialogOpen(false);
      setEditingEmployee(null);
      await loadEmployees();
    } catch (e: any) {
      console.error('Ошибка сохранения сотрудника:', e);
      setFormError(e.response?.data?.message || 'Не удалось сохранить сотрудника');
    } finally {
      setSaving(false);
    }
  };

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingEmployee, setDeletingEmployee] = useState<Employee | null>(null);
  const [deleting, setDeleting] = useState(false);

  const requestDeleteEmployee = (employee: Employee) => {
    setDeletingEmployee(employee);
    setDeleteDialogOpen(true);
  };

  const confirmDeleteEmployee = async () => {
    if (!deletingEmployee?.id) return;
    setDeleting(true);
    try {
      await employeesApi.deleteEmployee(deletingEmployee.id);
      toast.success('Сотрудник удалён');
      setDeleteDialogOpen(false);
      setDeletingEmployee(null);
      await loadEmployees();
    } catch (error) {
      console.error('Ошибка удаления сотрудника:', error);
      toast.error(getApiErrorMessage(error, 'Ошибка удаления сотрудника'), { title: 'Ошибка' });
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка сотрудников..." />;
  }

  return (
    <>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
          <div>
            <h1 className="text-3xl font-bold">Сотрудники</h1>
            <p className="text-sm text-gray-500 mt-1">Роли, контакты и зарплаты</p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={loadEmployees}>
              Обновить
            </Button>
            <Button onClick={openCreate}>Добавить сотрудника</Button>
          </div>
        </div>

        {error && <RetryAlert message={error} onRetry={loadEmployees} />}

        <Card>
          <CardContent className="pt-6">
            <div className="flex flex-col md:flex-row gap-3 md:items-end">
              <div className="flex-1">
                <label className="block text-sm font-medium mb-1">Поиск</label>
                <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Имя, логин или роль" />
              </div>
              <Button variant="outline" onClick={() => setSearch('')}>
                Сбросить
              </Button>
            </div>
          </CardContent>
        </Card>

        {filteredEmployees.length === 0 ? (
          <EmptyState
            title={search.trim() ? 'Ничего не найдено' : 'Сотрудников пока нет'}
            description={
              search.trim()
                ? 'Попробуйте изменить запрос.'
                : 'Добавьте сотрудников, чтобы управлять сменами и зарплатами.'
            }
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {filteredEmployees.map((employee) => {
              const roleName = roleLabel(employee.roleId);
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
                    <div className="mt-4 flex gap-2">
                      <Button variant="outline" className="flex-1" onClick={() => openEdit(employee)}>
                        Редактировать
                      </Button>
                      <Button
                        variant="outline"
                        onClick={() => requestDeleteEmployee(employee)}
                        className="flex-1 text-red-600 hover:text-red-700 hover:border-red-700"
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

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setDialogOpen(false);
            setEditingEmployee(null);
            setFormError(null);
          } else {
            setDialogOpen(true);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingEmployee ? 'Редактировать сотрудника' : 'Новый сотрудник'}</DialogTitle>
            <DialogDescription>
              {editingEmployee
                ? 'Пароль можно не менять (оставьте пустым).'
                : 'Пароль обязателен для нового сотрудника.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
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
              placeholder={editingEmployee ? 'Новый пароль (необязательно)' : 'Пароль'}
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            />
            <div>
              <label className="block text-sm font-medium mb-1">Роль</label>
              <select
                className="w-full h-10 rounded-md border border-gray-300 px-3"
                value={formData.roleId}
                onChange={(e) => setFormData({ ...formData, roleId: parseInt(e.target.value, 10) })}
              >
                <option value={1}>Менеджер</option>
                <option value={2}>Кассир</option>
                <option value={3}>Повар</option>
              </select>
            </div>
            <Input
              placeholder="Телефон"
              value={formData.contactPhone}
              onChange={(e) => setFormData({ ...formData, contactPhone: e.target.value })}
            />
            <Input
              type="number"
              step="0.01"
              min="0"
              placeholder="Зарплата (например, 50000)"
              value={formData.salary}
              onChange={(e) => setFormData({ ...formData, salary: e.target.value })}
            />
            {formError && <p className="text-sm text-red-600">{formError}</p>}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>
              Отмена
            </Button>
            <Button onClick={saveEmployee} disabled={saving}>
              {saving ? 'Сохранение…' : 'Сохранить'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={(open) => {
          setDeleteDialogOpen(open);
          if (!open) {
            setDeletingEmployee(null);
          }
        }}
        title={
          deletingEmployee?.fullName
            ? `Удалить сотрудника «${deletingEmployee.fullName}»?`
            : deletingEmployee?.id
              ? `Удалить сотрудника #${deletingEmployee.id}?`
              : 'Удалить сотрудника?'
        }
        description="Действие необратимо."
        confirmText="Удалить"
        confirmDisabled={deleting}
        onConfirm={() => void confirmDeleteEmployee()}
      />
    </>
  );
}
