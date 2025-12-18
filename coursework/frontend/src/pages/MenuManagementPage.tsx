import { useEffect, useMemo, useState } from 'react';
import { menuApi } from '@/api/client';
import { MenuItem } from '@/api/generated/api';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { formatCurrency } from '@/lib/utils';
import { getApiErrorMessage } from '@/lib/apiError';
import { useToast } from '@/components/ui/toast';

type MenuFilter = 'all' | 'available' | 'unavailable';

type MenuItemFormState = {
  name: string;
  description: string;
  price: string;
  prepTimeMinutes: string;
  available: boolean;
};

const toFormState = (item?: MenuItem | null): MenuItemFormState => ({
  name: item?.name || '',
  description: item?.description || '',
  price: item?.price != null ? String(item.price) : '',
  prepTimeMinutes: item?.prepTimeMinutes != null ? String(item.prepTimeMinutes) : '0',
  available: item?.available ?? true,
});

export function MenuManagementPage() {
  const toast = useToast();
  const [items, setItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<MenuFilter>('all');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<MenuItem | null>(null);
  const [form, setForm] = useState<MenuItemFormState>(() => toFormState(null));
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void loadMenu();
  }, []);

  const loadMenu = async () => {
    try {
      setError(null);
      setLoading(true);
      const response = await menuApi.getMenu(false, search.trim() || undefined);
      setItems(response.data || []);
    } catch (e: any) {
      console.error('Ошибка загрузки меню:', e);
      setError(getApiErrorMessage(e, 'Не удалось загрузить меню'));
      setItems([]);
    } finally {
      setLoading(false);
    }
  };

  const filteredItems = useMemo(() => {
    const trimmed = search.trim().toLowerCase();
    let list = items;
    if (trimmed) {
      list = list.filter((i) => (i.name || '').toLowerCase().includes(trimmed));
    }
    if (filter === 'available') {
      list = list.filter((i) => i.available === true);
    }
    if (filter === 'unavailable') {
      list = list.filter((i) => i.available === false);
    }
    return list;
  }, [items, search, filter]);

  const openCreate = () => {
    setEditingItem(null);
    setForm(toFormState(null));
    setFormError(null);
    setDialogOpen(true);
  };

  const openEdit = (item: MenuItem) => {
    setEditingItem(item);
    setForm(toFormState(item));
    setFormError(null);
    setDialogOpen(true);
  };

  const validateForm = (state: MenuItemFormState): { ok: true; payload: any } | { ok: false; message: string } => {
    const name = state.name.trim();
    if (!name) return { ok: false, message: 'Название обязательно' };

    const price = Number(state.price);
    if (!Number.isFinite(price)) return { ok: false, message: 'Цена должна быть числом' };
    if (price < 0) return { ok: false, message: 'Цена не может быть отрицательной' };

    const prep = state.prepTimeMinutes.trim() ? Number(state.prepTimeMinutes) : 0;
    if (!Number.isFinite(prep) || !Number.isInteger(prep)) return { ok: false, message: 'Время приготовления должно быть целым числом' };
    if (prep < 0) return { ok: false, message: 'Время приготовления не может быть отрицательным' };

    return {
      ok: true,
      payload: {
        name,
        description: state.description.trim() || undefined,
        price,
        prepTimeMinutes: prep,
        available: state.available,
      },
    };
  };

  const saveItem = async () => {
    const validation = validateForm(form);
    if (!validation.ok) {
      setFormError(validation.message);
      return;
    }

    setSaving(true);
    try {
      setFormError(null);
      if (editingItem?.id) {
        await menuApi.updateMenuItem(editingItem.id, validation.payload);
      } else {
        await menuApi.createMenuItem(validation.payload);
      }
      setDialogOpen(false);
      setEditingItem(null);
      await loadMenu();
    } catch (e: any) {
      console.error('Ошибка сохранения блюда:', e);
      setFormError(e.response?.data?.message || 'Не удалось сохранить блюдо');
    } finally {
      setSaving(false);
    }
  };

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingItem, setDeletingItem] = useState<MenuItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  const requestDeleteItem = (item: MenuItem) => {
    setDeletingItem(item);
    setDeleteDialogOpen(true);
  };

  const confirmDeleteItem = async () => {
    if (!deletingItem?.id) return;
    setDeleting(true);
    try {
      await menuApi.deleteMenuItem(deletingItem.id);
      toast.success('Блюдо удалено');
      setDeleteDialogOpen(false);
      setDeletingItem(null);
      await loadMenu();
    } catch (e: any) {
      console.error('Ошибка удаления блюда:', e);
      toast.error(getApiErrorMessage(e, 'Не удалось удалить блюдо'), { title: 'Ошибка' });
    } finally {
      setDeleting(false);
    }
  };

  const toggleAvailability = async (item: MenuItem) => {
    if (!item.id) return;
    if (!item.name || item.price == null) {
      toast.warning('Не удалось изменить доступность: некорректные данные блюда');
      return;
    }
    try {
      await menuApi.updateMenuItem(item.id, {
        name: item.name,
        description: item.description || undefined,
        price: item.price,
        prepTimeMinutes: item.prepTimeMinutes ?? 0,
        available: !item.available,
      });
      await loadMenu();
    } catch (e: any) {
      console.error('Ошибка изменения доступности:', e);
      toast.error(getApiErrorMessage(e, 'Не удалось изменить доступность'), { title: 'Ошибка' });
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка меню..." />;
  }

  return (
    <>
      <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Управление меню</h1>
          <p className="text-sm text-gray-500 mt-1">Создавайте блюда, меняйте цену и доступность</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={loadMenu}>
            Обновить
          </Button>
          <Button onClick={openCreate}>Добавить блюдо</Button>
        </div>
      </div>

      {error && (
        <RetryAlert message={error} onRetry={loadMenu} />
      )}

      <Card>
          <CardContent className="pt-6">
            <div className="flex flex-col md:flex-row gap-3 md:items-end">
              <div className="flex-1">
                <label className="block text-sm font-medium mb-1">Поиск</label>
                <Input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Например: Patty"
                />
              </div>
              <div className="w-full md:w-60">
                <label className="block text-sm font-medium mb-1">Фильтр</label>
                <select
                  className="w-full h-10 rounded-md border border-gray-300 px-3"
                  value={filter}
                  onChange={(e) => setFilter(e.target.value as MenuFilter)}
                >
                  <option value="all">Все</option>
                  <option value="available">Только доступные</option>
                  <option value="unavailable">Только недоступные</option>
                </select>
              </div>
              <Button variant="outline" onClick={loadMenu}>
                Применить
              </Button>
            </div>
          </CardContent>
      </Card>

        {filteredItems.length === 0 ? (
          <EmptyState
            title={search.trim() ? 'Ничего не найдено' : 'Блюд пока нет'}
            description={search.trim() ? 'Попробуйте изменить запрос или фильтр.' : 'Добавьте первое блюдо, чтобы оно появилось в меню.'}
            action={<Button onClick={openCreate}>Добавить блюдо</Button>}
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {filteredItems.map((item) => (
              <Card key={item.id}>
                <CardHeader>
                  <CardTitle className="text-xl">{item.name || `Блюдо #${item.id}`}</CardTitle>
                  <CardDescription>
                    {item.available ? 'Доступно' : 'Недоступно'} • {formatCurrency(item.price || 0)}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  {item.description ? (
                    <p className="text-sm text-gray-700 mb-4 line-clamp-3">{item.description}</p>
                  ) : (
                    <p className="text-sm text-gray-500 mb-4">Без описания</p>
                  )}
                  <div className="text-sm text-gray-600 mb-4">
                    Время приготовления: {item.prepTimeMinutes ?? 0} мин
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" className="flex-1" onClick={() => openEdit(item)}>
                      Редактировать
                    </Button>
                    <Button
                      variant="outline"
                      className="flex-1"
                      onClick={() => toggleAvailability(item)}
                      title={item.available ? 'Скрыть из меню' : 'Сделать доступным'}
                    >
                      {item.available ? 'Скрыть' : 'Показать'}
                    </Button>
                  </div>
                  <div className="mt-2">
                    <Button
                      variant="destructive"
                      className="w-full"
                      onClick={() => requestDeleteItem(item)}
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

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setDialogOpen(false);
            setEditingItem(null);
            setForm(toFormState(null));
            setFormError(null);
          } else {
            setDialogOpen(true);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingItem ? 'Редактировать блюдо' : 'Новое блюдо'}</DialogTitle>
            <DialogDescription>
              Цена и название обязательны. Доступность можно переключать в любой момент.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Название</label>
              <Input
                value={form.name}
                onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                placeholder="Krabby Patty"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Описание</label>
              <Input
                value={form.description}
                onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
                placeholder="Краткое описание (необязательно)"
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium mb-1">Цена</label>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form.price}
                  onChange={(e) => setForm((p) => ({ ...p, price: e.target.value }))}
                  placeholder="5.99"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Время (мин)</label>
                <Input
                  type="number"
                  step="1"
                  min="0"
                  value={form.prepTimeMinutes}
                  onChange={(e) => setForm((p) => ({ ...p, prepTimeMinutes: e.target.value }))}
                  placeholder="7"
                />
              </div>
            </div>
            <div className="flex items-center justify-between rounded-md border border-gray-200 p-3">
              <div>
                <div className="text-sm font-medium">Доступность</div>
                <div className="text-xs text-gray-500">Если ингредиентов нет на складе — блюдо автоматически станет недоступным</div>
              </div>
              <button
                type="button"
                onClick={() => setForm((p) => ({ ...p, available: !p.available }))}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                  form.available ? 'bg-primary-600' : 'bg-gray-300'
                }`}
                aria-pressed={form.available}
              >
                <span
                  className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform ${
                    form.available ? 'translate-x-5' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>
            {formError && (
              <p className="text-sm text-red-600">{formError}</p>
            )}
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDialogOpen(false)}
              disabled={saving}
            >
              Отмена
            </Button>
            <Button onClick={saveItem} disabled={saving}>
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
            setDeletingItem(null);
          }
        }}
        title={
          deletingItem?.name
            ? `Удалить блюдо «${deletingItem.name}»?`
            : deletingItem?.id
              ? `Удалить блюдо #${deletingItem.id}?`
              : 'Удалить блюдо?'
        }
        description="Действие необратимо."
        confirmText="Удалить"
        confirmDisabled={deleting}
        onConfirm={() => void confirmDeleteItem()}
      />
    </>
  );
}
