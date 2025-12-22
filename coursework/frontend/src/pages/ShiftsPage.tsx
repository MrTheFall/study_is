import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { employeesApi, shiftsApi } from '@/api/client';
import { Employee, Shift } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
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
import { useAuthStore } from '@/store/authStore';
import { getApiErrorMessage } from '@/lib/apiError';
import { useToast } from '@/components/ui/toast';

type ShiftAssignment = {
  employee: Employee;
  employeeShiftId: number;
};

export function ShiftsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { isManager } = useAuthStore();
  const toast = useToast();
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [assignmentsByShiftId, setAssignmentsByShiftId] = useState<Record<number, ShiftAssignment[]>>({});
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
  const todayStr = useMemo(() => new Date().toISOString().slice(0, 10), []);

  useEffect(() => {
    loadShifts();
  }, [filterDate]);

  useEffect(() => {
    if (isManager()) {
      void loadEmployees();
    }
  }, []);

  useEffect(() => {
    if (!isManager()) return;
    if (employees.length === 0) {
      setAssignmentsByShiftId({});
      return;
    }
    void loadAssignments();
  }, [employees, shifts, filterDate]);

  const loadShifts = async () => {
    try {
      setError(null);
      setLoading(true);
      const dateParam = filterDate || undefined;
      const response = await shiftsApi.getAllShifts(dateParam);
      setShifts(response.data || []);
    } catch (error: any) {
      console.error('Ошибка загрузки смен:', error);
      setError(getApiErrorMessage(error, 'Ошибка загрузки смен'));
      setShifts([]);
    } finally {
      setLoading(false);
    }
  };

  const loadEmployees = async () => {
    try {
      const response = await employeesApi.getAllEmployees();
      setEmployees(response.data || []);
    } catch (e) {
      console.error('Ошибка загрузки сотрудников:', e);
      setEmployees([]);
    }
  };

  const loadAssignments = async () => {
    try {
      const dateParam = filterDate || undefined;
      const resp = await shiftsApi.getShiftAssignments(dateParam);
      const assignments = resp.data || [];

      const employeeById = new Map<number, Employee>();
      for (const e of employees) {
        if (e.id != null) employeeById.set(e.id, e);
      }

      const next: Record<number, ShiftAssignment[]> = {};
      for (const assignment of assignments) {
        const shiftId = assignment.shiftId;
        const employeeShiftId = assignment.id;
        const employeeId = assignment.employeeId;
        if (typeof shiftId !== 'number') continue;
        if (typeof employeeShiftId !== 'number') continue;
        if (typeof employeeId !== 'number') continue;
        const employee = employeeById.get(employeeId);
        if (!employee) continue;
        if (!next[shiftId]) next[shiftId] = [];
        next[shiftId].push({ employee, employeeShiftId });
      }
      setAssignmentsByShiftId(next);
    } catch (e) {
      console.error('Ошибка загрузки назначений смен:', e);
      setAssignmentsByShiftId({});
    }
  };

  const createShift = async () => {
    try {
      if (!formData.shiftDate || !formData.startTime || !formData.endTime) {
        toast.warning('Заполните все обязательные поля');
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
      toast.error(getApiErrorMessage(error, 'Ошибка создания смены'), { title: 'Ошибка' });
    }
  };

  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editingShift, setEditingShift] = useState<Shift | null>(null);
  const [editForm, setEditForm] = useState({ shiftDate: '', startTime: '', endTime: '', note: '' });
  const [editError, setEditError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const openEdit = (shift: Shift) => {
    setEditingShift(shift);
    setEditError(null);
    setEditForm({
      shiftDate: shift.shiftDate || '',
      startTime: shift.startTime || '',
      endTime: shift.endTime || '',
      note: shift.note || '',
    });
    setEditDialogOpen(true);
  };

  const saveEdit = async () => {
    if (!editingShift?.id) return;
    if (!editForm.shiftDate || !editForm.startTime || !editForm.endTime) {
      setEditError('Заполните дату и время');
      return;
    }
    setSaving(true);
    try {
      setEditError(null);
      await shiftsApi.updateShift(editingShift.id, {
        shiftDate: editForm.shiftDate,
        startTime: editForm.startTime,
        endTime: editForm.endTime,
        note: editForm.note.trim() || undefined,
      });
      setEditDialogOpen(false);
      setEditingShift(null);
      await loadShifts();
    } catch (e: any) {
      console.error('Ошибка обновления смены:', e);
      setEditError(e.response?.data?.message || 'Не удалось обновить смену');
    } finally {
      setSaving(false);
    }
  };

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingShift, setDeletingShift] = useState<Shift | null>(null);
  const [deleting, setDeleting] = useState(false);

  const requestDeleteShift = (shift: Shift) => {
    setDeletingShift(shift);
    setDeleteDialogOpen(true);
  };

  const confirmDeleteShift = async () => {
    if (!deletingShift?.id) return;
    setDeleting(true);
    try {
      await shiftsApi.deleteShift(deletingShift.id);
      toast.success(`Смена #${deletingShift.id} удалена`);
      setDeleteDialogOpen(false);
      setDeletingShift(null);
      await loadShifts();
    } catch (e: any) {
      console.error('Ошибка удаления смены:', e);
      toast.error(getApiErrorMessage(e, 'Не удалось удалить смену'), { title: 'Ошибка' });
    } finally {
      setDeleting(false);
    }
  };

  const [assignSelection, setAssignSelection] = useState<Record<number, string>>({});
  const [assigningShiftId, setAssigningShiftId] = useState<number | null>(null);

  const assignEmployee = async (shiftId: number) => {
    const selected = assignSelection[shiftId];
    if (!selected) {
      toast.warning('Выберите сотрудника');
      return;
    }
    const employeeId = parseInt(selected, 10);
    if (Number.isNaN(employeeId)) return;

    setAssigningShiftId(shiftId);
    try {
      await shiftsApi.assignEmployeeToShift(shiftId, { employeeId });
      setAssignSelection((prev) => ({ ...prev, [shiftId]: '' }));
      await loadAssignments();
    } catch (e: any) {
      console.error('Ошибка назначения сотрудника:', e);
      toast.error(getApiErrorMessage(e, 'Не удалось назначить сотрудника'), { title: 'Ошибка' });
    } finally {
      setAssigningShiftId(null);
    }
  };

  const formatShiftDate = (date: string) =>
    new Intl.DateTimeFormat('ru-RU', { year: 'numeric', month: 'long', day: 'numeric' }).format(
      new Date(`${date}T00:00:00`)
    );

  const [removeDialogOpen, setRemoveDialogOpen] = useState(false);
  const [removingAssignment, setRemovingAssignment] = useState<{
    employeeShiftId: number;
    shiftId: number;
    employeeLabel: string;
  } | null>(null);
  const [removing, setRemoving] = useState(false);

  const requestRemoveAssignment = (shiftId: number, assignment: ShiftAssignment) => {
    setRemovingAssignment({
      employeeShiftId: assignment.employeeShiftId,
      shiftId,
      employeeLabel:
        assignment.employee.fullName || assignment.employee.login || `Сотрудник #${assignment.employee.id}`,
    });
    setRemoveDialogOpen(true);
  };

  const confirmRemoveAssignment = async () => {
    if (!removingAssignment) return;
    setRemoving(true);
    try {
      await shiftsApi.removeEmployeeFromShift(removingAssignment.employeeShiftId);
      toast.success(`Сотрудник снят со смены #${removingAssignment.shiftId}`);
      setRemoveDialogOpen(false);
      setRemovingAssignment(null);
      await loadAssignments();
    } catch (e: any) {
      console.error('Ошибка снятия сотрудника со смены:', e);
      toast.error(getApiErrorMessage(e, 'Не удалось снять сотрудника со смены'), { title: 'Ошибка' });
    } finally {
      setRemoving(false);
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка смен..." />;
  }

  return (
    <>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
          <div>
            <h1 className="text-3xl font-bold">Смены</h1>
            <p className="text-gray-600 mt-1">Создание смен и назначение сотрудников.</p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={loadShifts}>
              Обновить
            </Button>
            {isManager() && (
              <Button onClick={() => setShowForm(!showForm)}>{showForm ? 'Отмена' : 'Создать смену'}</Button>
            )}
          </div>
        </div>

        {error && <RetryAlert message={error} onRetry={loadShifts} />}

        <div className="flex flex-col sm:flex-row gap-4 items-end">
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
          <Button
            variant="outline"
            onClick={() => {
              setFilterDate(todayStr);
              navigate(`/shifts?date=${todayStr}`, { replace: true });
            }}
            disabled={filterDate === todayStr}
          >
            Сегодня
          </Button>
          {filterDate && (
            <Button
              variant="outline"
              onClick={() => {
                setFilterDate('');
                navigate('/shifts', { replace: true });
              }}
            >
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
                    {shift.shiftDate ? formatShiftDate(shift.shiftDate) : 'Дата не указана'}
                    {shift.startTime && shift.endTime && (
                      <>
                        {' '}
                        | {shift.startTime} - {shift.endTime}
                      </>
                    )}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  {shift.note && <p className="text-sm text-gray-600 mb-2">Примечание: {shift.note}</p>}

                  {isManager() && shift.id && (
                    <div className="mt-4 space-y-3">
                      <div>
                        <div className="text-sm font-medium mb-1">Назначены:</div>
                        {(assignmentsByShiftId[shift.id] || []).length === 0 ? (
                          <p className="text-sm text-gray-500">Пока никто не назначен</p>
                        ) : (
                          <div className="flex flex-wrap gap-2">
                            {(assignmentsByShiftId[shift.id] || []).map((a) => (
                              <span
                                key={a.employeeShiftId}
                                className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-700"
                                title={a.employee.login || undefined}
                              >
                                {a.employee.fullName || `Сотрудник #${a.employee.id}`}
                                <button
                                  type="button"
                                  className="ml-1 text-gray-500 hover:text-red-600"
                                  onClick={() => requestRemoveAssignment(shift.id!, a)}
                                  aria-label="Снять со смены"
                                >
                                  ×
                                </button>
                              </span>
                            ))}
                          </div>
                        )}
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-3 gap-2 items-end">
                        <div className="md:col-span-2">
                          <label className="block text-sm font-medium mb-1">Назначить сотрудника</label>
                          <Select
                            value={assignSelection[shift.id] ?? ''}
                            onChange={(e) => setAssignSelection((prev) => ({ ...prev, [shift.id!]: e.target.value }))}
                          >
                            <option value="">— выберите —</option>
                            {employees
                              .filter((e) => {
                                const id = e.id;
                                if (!id) return false;
                                const assigned = (assignmentsByShiftId[shift.id!] || []).some(
                                  (a) => a.employee.id === id
                                );
                                return !assigned;
                              })
                              .map((e) => (
                                <option key={e.id} value={String(e.id)}>
                                  {e.fullName || `Сотрудник #${e.id}`} ({e.login || 'логин не указан'})
                                </option>
                              ))}
                          </Select>
                        </div>
                        <Button onClick={() => assignEmployee(shift.id!)} disabled={assigningShiftId === shift.id}>
                          {assigningShiftId === shift.id ? 'Назначаем…' : 'Назначить'}
                        </Button>
                      </div>

                      <div className="flex gap-2">
                        <Button variant="outline" className="flex-1" onClick={() => openEdit(shift)}>
                          Редактировать
                        </Button>
                        <Button
                          variant="outline"
                          className="flex-1 text-red-600 hover:text-red-700 hover:border-red-700"
                          onClick={() => requestDeleteShift(shift)}
                        >
                          Удалить
                        </Button>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>

      <Dialog
        open={editDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setEditDialogOpen(false);
            setEditingShift(null);
            setEditError(null);
          } else {
            setEditDialogOpen(true);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Редактировать смену</DialogTitle>
            <DialogDescription>Дата и время обязательны</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <Input
              type="date"
              value={editForm.shiftDate}
              onChange={(e) => setEditForm((p) => ({ ...p, shiftDate: e.target.value }))}
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                type="time"
                value={editForm.startTime}
                onChange={(e) => setEditForm((p) => ({ ...p, startTime: e.target.value }))}
              />
              <Input
                type="time"
                value={editForm.endTime}
                onChange={(e) => setEditForm((p) => ({ ...p, endTime: e.target.value }))}
              />
            </div>
            <Input
              value={editForm.note}
              onChange={(e) => setEditForm((p) => ({ ...p, note: e.target.value }))}
              placeholder="Примечание (необязательно)"
            />
            {editError && <p className="text-sm text-red-600">{editError}</p>}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDialogOpen(false)} disabled={saving}>
              Отмена
            </Button>
            <Button onClick={saveEdit} disabled={saving}>
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
            setDeletingShift(null);
          }
        }}
        title={deletingShift?.id ? `Удалить смену #${deletingShift.id}?` : 'Удалить смену?'}
        description="Действие необратимо."
        confirmText="Удалить"
        confirmDisabled={deleting}
        onConfirm={() => void confirmDeleteShift()}
      />

      <ConfirmDialog
        open={removeDialogOpen}
        onOpenChange={(open) => {
          if (!open && removing) return;
          setRemoveDialogOpen(open);
          if (!open) {
            setRemovingAssignment(null);
          }
        }}
        title={`Снять сотрудника со смены #${removingAssignment?.shiftId ?? '—'}?`}
        description={removingAssignment ? `Сотрудник: ${removingAssignment.employeeLabel}` : undefined}
        confirmText={removing ? 'Снимаем…' : 'Снять'}
        cancelText="Отмена"
        confirmVariant="destructive"
        confirmDisabled={!removingAssignment || removing}
        onConfirm={confirmRemoveAssignment}
      />
    </>
  );
}
