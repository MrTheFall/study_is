import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { clientsApi, menuApi, ordersApi, paymentsApi } from '@/api/client';
import { MenuItem, OrderStatus, OrderType, PaymentMethod } from '@/api/generated/api';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/toast';
import { getApiErrorMessage } from '@/lib/apiError';
import { formatCurrency } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';

export function PosPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { isCashier, isManager } = useAuthStore();

  type PosDraftStage = 'payment' | 'confirm';
  type PosDraft = {
    orderId: number;
    stage: PosDraftStage;
    orderType: OrderType;
    paymentMethod: PaymentMethod;
  };

  type PosSubmitError = {
    title: string;
    message: string;
  };

  type PosSuccessInfo = {
    orderId: number;
    orderType: OrderType;
    paymentMethod: PaymentMethod;
    total: number;
    amountReceived?: number;
    change?: number;
  };

  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [cart, setCart] = useState<Map<number, number>>(new Map());
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(PaymentMethod.Cash);
  const [amountReceived, setAmountReceived] = useState('');
  const [orderType, setOrderType] = useState<OrderType>(OrderType.DineIn);

  const [customerMode, setCustomerMode] = useState<'guest' | 'clientId'>('guest');
  const [customerIdInput, setCustomerIdInput] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [isCreatingClient, setIsCreatingClient] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submittingStep, setSubmittingStep] = useState<
    'creatingOrder' | 'processingPayment' | 'confirmingOrder' | null
  >(null);
  const submitLockRef = useRef(false);
  const [draft, setDraft] = useState<PosDraft | null>(null);
  const [submitError, setSubmitError] = useState<PosSubmitError | null>(null);
  const [successInfo, setSuccessInfo] = useState<PosSuccessInfo | null>(null);
  const [successDialogOpen, setSuccessDialogOpen] = useState(false);
  const [resetDraftDialogOpen, setResetDraftDialogOpen] = useState(false);
  const [menuQuery, setMenuQuery] = useState('');
  const [availabilityFilter, setAvailabilityFilter] = useState<'all' | 'available'>('all');

  useEffect(() => {
    void loadMenu();
  }, []);

  const isStaff = isCashier() || isManager();
  const isBusy = submitting || isCreatingClient;
  const isLockedByDraft = draft !== null;
  const controlsDisabled = isBusy || isLockedByDraft;

  const loadMenu = async () => {
    try {
      setError(null);
      setLoading(true);
      const response = await menuApi.getMenu(false);
      setMenuItems(response.data || []);
    } catch (e: any) {
      console.error('Не удалось загрузить меню для кассы:', e);
      setError(getApiErrorMessage(e, 'Не удалось загрузить меню'));
      setMenuItems([]);
    } finally {
      setLoading(false);
    }
  };

  const addItem = (itemId: number) => {
    setCart((prev) => {
      const next = new Map(prev);
      next.set(itemId, (next.get(itemId) || 0) + 1);
      return next;
    });
  };

  const removeItem = (itemId: number) => {
    setCart((prev) => {
      const next = new Map(prev);
      const count = next.get(itemId) || 0;
      if (count > 1) {
        next.set(itemId, count - 1);
      } else {
        next.delete(itemId);
      }
      return next;
    });
  };

  const deleteItem = (itemId: number) => {
    setCart((prev) => {
      const next = new Map(prev);
      next.delete(itemId);
      return next;
    });
  };

  const clearCart = () => {
    setCart(new Map());
  };

  const cartLines = useMemo(() => {
    return Array.from(cart.entries())
      .map(([itemId, quantity]) => {
        const item = menuItems.find((i) => i.id === itemId);
        const unitPrice = item?.price || 0;
        return {
          itemId,
          quantity,
          name: item?.name || `Блюдо #${itemId}`,
          unitPrice,
          subtotal: unitPrice * quantity,
          canIncrement: Boolean(item?.available),
        };
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [cart, menuItems]);
  const total = useMemo(() => cartLines.reduce((sum, line) => sum + line.subtotal, 0), [cartLines]);
  const totalItems = useMemo(() => cartLines.reduce((sum, line) => sum + line.quantity, 0), [cartLines]);

  const filteredMenuItems = useMemo(() => {
    const q = menuQuery.trim().toLowerCase();
    return menuItems.filter((item) => {
      if (availabilityFilter === 'available' && item.available === false) return false;
      if (!q) return true;
      const hay = `${item.name || ''} ${item.description || ''}`.toLowerCase();
      return hay.includes(q);
    });
  }, [menuItems, menuQuery, availabilityFilter]);

  const parseMoneyInput = (value: string): number | null => {
    const normalized = value.trim().replace(/\s+/g, '').replace(',', '.');
    if (!normalized) return null;
    const parsed = Number(normalized);
    if (!Number.isFinite(parsed)) return null;
    return parsed;
  };

  const toCents = (value: number): number => Math.round(value * 100);
  const normalizeMoney = (value: number): number => toCents(value) / 100;

  const normalizedTotal = useMemo(() => normalizeMoney(total), [total]);

  const amountReceivedValue = useMemo(() => parseMoneyInput(amountReceived), [amountReceived]);
  const cashChangeValue = useMemo(() => {
    if (paymentMethod !== PaymentMethod.Cash) return null;
    if (amountReceivedValue === null) return null;
    const diffCents = toCents(amountReceivedValue) - toCents(total);
    return diffCents / 100;
  }, [amountReceivedValue, paymentMethod, total]);
  const cashShortageValue = useMemo(() => {
    if (cashChangeValue === null) return null;
    if (cashChangeValue >= 0) return 0;
    return Math.abs(cashChangeValue);
  }, [cashChangeValue]);
  const isCashEnough = paymentMethod !== PaymentMethod.Cash || (cashChangeValue !== null && cashChangeValue >= 0);
  const isCashInputValid = paymentMethod !== PaymentMethod.Cash || amountReceivedValue !== null;

  const clientIdInputValid = useMemo(() => {
    if (customerMode !== 'clientId') return true;
    const raw = customerIdInput.trim();
    if (!raw) return false;
    const parsed = Number(raw);
    return Number.isInteger(parsed) && parsed > 0;
  }, [customerIdInput, customerMode]);

  const guestPhoneValid = useMemo(() => {
    if (customerMode !== 'guest') return true;
    const digits = customerPhone.replace(/\D/g, '');
    return digits.length >= 6;
  }, [customerMode, customerPhone]);

  const clearOrderDraft = () => {
    setDraft(null);
    setSubmitError(null);
    setCart(new Map());
    setAmountReceived('');
    setCustomerIdInput('');
    setCustomerName('');
    setCustomerPhone('');
    setCustomerMode('guest');
    setPaymentMethod(PaymentMethod.Cash);
    setOrderType(OrderType.DineIn);
  };

  const toCashierError = (e: any, fallback: string): PosSubmitError => {
    const status = e?.response?.status;
    const rawMessage = getApiErrorMessage(e, fallback);

    if (status === 403) {
      return {
        title: 'Нет доступа',
        message: 'Войдите под ролью кассира или менеджера и повторите действие.',
      };
    }

    if (status === 404) {
      return {
        title: 'Не найдено',
        message: 'Заказ не найден. Откройте список заказов и проверьте, создан ли он.',
      };
    }

    if (/insufficient ingredients/i.test(rawMessage)) {
      return {
        title: 'Недостаточно ингредиентов',
        message: 'Для заказа не хватает ингредиентов. Уберите недоступные позиции или обновите меню.',
      };
    }

    if (/Payment already exists/i.test(rawMessage)) {
      return {
        title: 'Оплата уже проведена',
        message: 'Этот заказ уже оплачен. Повторно платить не нужно — отправьте заказ на кухню.',
      };
    }

    if (/Amount received is less than order total/i.test(rawMessage)) {
      return {
        title: 'Недостаточно наличных',
        message: 'Полученная сумма меньше суммы заказа. Увеличьте сумму и повторите.',
      };
    }

    if (/Orders must be paid before confirmation/i.test(rawMessage)) {
      return {
        title: 'Сначала нужна оплата',
        message: 'Заказ нельзя отправить на кухню до оплаты. Проведите оплату и повторите.',
      };
    }

    return { title: 'Ошибка', message: rawMessage };
  };

  const ensureClientId = async (): Promise<number> => {
    if (customerMode === 'clientId') {
      const raw = customerIdInput.trim();
      const parsed = parseInt(raw, 10);
      if (!raw || Number.isNaN(parsed)) {
        throw new Error('Введите корректный ID клиента');
      }
      return parsed;
    }

    const name = customerName.trim() || 'Гость';
    const phone = customerPhone.trim();
    const phoneDigits = phone.replace(/\D/g, '');
    if (!phone || phoneDigits.length < 6) {
      throw new Error('Введите телефон гостя (минимум 6 цифр)');
    }
    const email = `guest+${phoneDigits}@guest.local`;
    const password = `Guest${Date.now().toString().slice(-6)}`;

    setIsCreatingClient(true);
    try {
      const response = await clientsApi.registerClient({
        name,
        phone,
        email,
        password,
        defaultAddress: 'В ресторане',
      });
      if (!response.data.id) {
        throw new Error('Не удалось создать клиента');
      }
      return response.data.id;
    } catch (e: any) {
      if (e?.response?.status === 409) {
        try {
          const existing = await clientsApi.lookupClient(phone, undefined);
          const id = existing.data.id;
          if (typeof id === 'number') {
            toast.info(`Найден существующий клиент #${id} по телефону`, { title: 'Клиент' });
            return id;
          }
          throw new Error('Не удалось определить клиента');
        } catch (lookupErr) {
          throw new Error(
            getApiErrorMessage(lookupErr, 'Клиент с таким телефоном уже существует, но найти его не удалось')
          );
        }
      }
      throw new Error(getApiErrorMessage(e, 'Не удалось создать гостя'));
    } finally {
      setIsCreatingClient(false);
    }
  };

  const ensurePaymentProcessed = async (
    orderId: number,
    method: PaymentMethod,
    cashReceived: number | null
  ): Promise<{ amountReceived?: number; change?: number }> => {
    if (method === PaymentMethod.Cash) {
      if (cashReceived === null || !Number.isFinite(cashReceived)) {
        throw new Error('Введите корректную сумму наличными');
      }
      const normalizedCashReceived = normalizeMoney(cashReceived);
      try {
        const resp = await paymentsApi.processCashPayment({
          orderId,
          amountReceived: normalizedCashReceived,
        });
        return {
          amountReceived: resp.data.amountReceived ?? normalizedCashReceived,
          change: resp.data.change ?? undefined,
        };
      } catch (e: any) {
        const msg = getApiErrorMessage(e, 'Ошибка оплаты наличными');
        if (/Payment already exists/i.test(msg)) {
          return {};
        }
        throw e;
      }
    }

    try {
      await paymentsApi.processPayment({
        orderId,
        method,
      });
      return {};
    } catch (e: any) {
      const msg = getApiErrorMessage(e, 'Ошибка оплаты');
      if (/Payment already exists/i.test(msg)) {
        return {};
      }
      throw e;
    }
  };

  const ensureOrderConfirmed = async (orderId: number) => {
    try {
      const resp = await ordersApi.getOrderById(orderId);
      const status = resp.data.status;
      if (status && status !== OrderStatus.Pending) {
        return;
      }
    } catch {
      // If we can't read the order, still try to confirm; error will be handled by caller.
    }
    await ordersApi.updateOrderStatus(orderId, { status: OrderStatus.Confirmed });
  };

  const resumeDraft = async (current: PosDraft) => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      if (current.stage === 'payment') {
        setSubmittingStep('processingPayment');
        const cashReceived = current.paymentMethod === PaymentMethod.Cash ? amountReceivedValue : null;
        const paymentResult = await ensurePaymentProcessed(current.orderId, current.paymentMethod, cashReceived);
        setDraft((prev) => (prev ? { ...prev, stage: 'confirm' } : prev));

        setSubmittingStep('confirmingOrder');
        await ensureOrderConfirmed(current.orderId);

        setDraft(null);
        setSubmitError(null);

        const info: PosSuccessInfo = {
          orderId: current.orderId,
          orderType: current.orderType,
          paymentMethod: current.paymentMethod,
          total: normalizedTotal,
          amountReceived: paymentResult.amountReceived,
          change: paymentResult.change,
        };
        setSuccessInfo(info);
        setSuccessDialogOpen(true);
        toast.success(`Заказ #${current.orderId} принят и отправлен на кухню.`);
        setCart(new Map());
        setAmountReceived('');
        setCustomerIdInput('');
        setCustomerName('');
        setCustomerPhone('');
      } else {
        setSubmittingStep('confirmingOrder');
        await ensureOrderConfirmed(current.orderId);
        setDraft(null);
        setSubmitError(null);
        const info: PosSuccessInfo = {
          orderId: current.orderId,
          orderType: current.orderType,
          paymentMethod: current.paymentMethod,
          total: normalizedTotal,
        };
        setSuccessInfo(info);
        setSuccessDialogOpen(true);
        toast.success(`Заказ #${current.orderId} отправлен на кухню.`);
        setCart(new Map());
        setAmountReceived('');
        setCustomerIdInput('');
        setCustomerName('');
        setCustomerPhone('');
      }
    } catch (e: any) {
      console.error('Ошибка продолжения заказа на кассе:', e);
      const err = toCashierError(e, 'Не удалось продолжить заказ');
      setSubmitError(err);
      toast.error(err.message, { title: err.title });
    } finally {
      setSubmitting(false);
      setSubmittingStep(null);
    }
  };

  const submit = async () => {
    if (!isStaff) return;
    if (submitLockRef.current) return;
    submitLockRef.current = true;

    try {
      setSubmitError(null);

      if (draft) {
        if (draft.stage === 'payment' && draft.paymentMethod === PaymentMethod.Cash) {
          if (!isCashInputValid) {
            toast.warning('Введите сумму наличными числом.');
            return;
          }
          if (!isCashEnough) {
            toast.warning('Недостаточно наличных для оплаты.');
            return;
          }
        }
        await resumeDraft(draft);
        return;
      }

      if (cart.size === 0) {
        toast.warning('Выберите хотя бы одно блюдо.');
        return;
      }
      if (!clientIdInputValid) {
        toast.warning('Введите корректный ID клиента.');
        return;
      }
      if (!guestPhoneValid) {
        toast.warning('Введите телефон гостя (минимум 6 цифр).');
        return;
      }
      if (paymentMethod === PaymentMethod.Cash) {
        if (!isCashInputValid) {
          toast.warning('Введите сумму наличными числом.');
          return;
        }
        if (!isCashEnough) {
          toast.warning('Недостаточно наличных для оплаты.');
          return;
        }
      }

      setSubmitting(true);
      setSubmittingStep('creatingOrder');
      const clientId = await ensureClientId();
      const items = Array.from(cart.entries()).map(([menuItemId, quantity]) => ({ menuItemId, quantity }));

      const orderResp = await ordersApi.placeOrder({
        clientId,
        type: orderType,
        paymentMethod,
        items,
      });

      const orderId = orderResp.data.orderId;
      if (!orderId) {
        throw new Error('Не удалось получить номер заказа');
      }

      const createdDraft: PosDraft = {
        orderId,
        stage: 'payment',
        orderType,
        paymentMethod,
      };
      setDraft(createdDraft);

      setSubmittingStep('processingPayment');
      const cashReceived = paymentMethod === PaymentMethod.Cash ? amountReceivedValue : null;
      const paymentResult = await ensurePaymentProcessed(orderId, paymentMethod, cashReceived);

      setDraft((prev) => (prev ? { ...prev, stage: 'confirm' } : prev));

      setSubmittingStep('confirmingOrder');
      await ensureOrderConfirmed(orderId);

      setDraft(null);
      const info: PosSuccessInfo = {
        orderId,
        orderType,
        paymentMethod,
        total: normalizedTotal,
        amountReceived: paymentResult.amountReceived,
        change: paymentResult.change,
      };
      setSuccessInfo(info);
      setSuccessDialogOpen(true);
      toast.success(`Заказ #${orderId} принят и отправлен на кухню.`);

      setCart(new Map());
      setAmountReceived('');
      setCustomerIdInput('');
      setCustomerName('');
      setCustomerPhone('');
    } catch (e: any) {
      console.error('Ошибка оформления заказа на кассе:', e);
      const err = toCashierError(e, 'Не удалось создать заказ');
      setSubmitError(err);
      toast.error(err.message, { title: err.title });
    } finally {
      setSubmitting(false);
      setSubmittingStep(null);
      submitLockRef.current = false;
    }
  };

  if (loading) {
    return <LoadingState message="Загрузка меню..." />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Касса</h1>
          <p className="text-gray-600 mt-1">Новый заказ в зале или с собой — оплата наличными/картой.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={loadMenu} disabled={isBusy}>
            Обновить меню
          </Button>
          <Button onClick={() => navigate('/orders')} disabled={isBusy}>
            Открыть заказы
          </Button>
        </div>
      </div>

      {error && <RetryAlert message={error} onRetry={loadMenu} retryDisabled={isBusy} />}

      <Card>
        <CardHeader>
          <CardTitle>Новый заказ</CardTitle>
          <CardDescription>Выберите блюда, примите оплату и отправьте заказ на кухню.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {!isStaff ? (
            <Alert variant="warning" title="Нет доступа">
              Войдите под ролью кассира или менеджера, чтобы оформить заказ на кассе.
            </Alert>
          ) : null}

          {draft ? (
            <Alert variant="warning" title={`Незавершённый заказ #${draft.orderId}`}>
              <div className="space-y-3">
                <div>
                  {draft.stage === 'payment'
                    ? 'Заказ создан, но оплата/подтверждение не завершены.'
                    : 'Оплата проведена, осталось отправить заказ на кухню.'}
                </div>
                <div className="flex flex-col sm:flex-row gap-2">
                  <Button onClick={submit} disabled={isBusy}>
                    {draft.stage === 'payment' ? 'Продолжить оплату' : 'Отправить на кухню'}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => navigate(`/orders?orderId=${draft.orderId}`)}
                    disabled={isBusy}
                  >
                    Открыть заказ
                  </Button>
                  <Button variant="ghost" onClick={() => setResetDraftDialogOpen(true)} disabled={isBusy}>
                    Начать новый заказ
                  </Button>
                </div>
              </div>
            </Alert>
          ) : null}

          {submitError ? (
            <Alert variant="error" title={submitError.title}>
              {submitError.message}
            </Alert>
          ) : null}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Покупатель</label>
              <Select
                value={customerMode}
                onChange={(e) => {
                  const next = e.target.value as 'guest' | 'clientId';
                  setCustomerMode(next);
                  setCustomerIdInput('');
                  setCustomerName('');
                  setCustomerPhone('');
                }}
                disabled={controlsDisabled}
              >
                <option value="guest">Гость</option>
                <option value="clientId">Клиент по ID</option>
              </Select>
            </div>
            {customerMode === 'clientId' ? (
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1">ID клиента</label>
                <Input
                  placeholder="Например, 1"
                  value={customerIdInput}
                  onChange={(e) => setCustomerIdInput(e.target.value)}
                  disabled={controlsDisabled}
                />
                <p className="mt-1 text-xs text-gray-500">Если ID неизвестен, выберите “Гость”.</p>
              </div>
            ) : (
              <>
                <div>
                  <label className="block text-sm font-medium mb-1">Имя гостя</label>
                  <Input
                    placeholder="Гость"
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                    disabled={controlsDisabled}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Телефон гостя</label>
                  <Input
                    placeholder="+7..."
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(e.target.value)}
                    disabled={controlsDisabled}
                  />
                </div>
              </>
            )}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <div className="lg:col-span-2 space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
                <div className="flex-1">
                  <label className="block text-sm font-medium mb-1">Поиск по меню</label>
                  <Input
                    placeholder="Например: бургер"
                    value={menuQuery}
                    onChange={(e) => setMenuQuery(e.target.value)}
                    disabled={controlsDisabled}
                  />
                </div>
                <div className="w-full sm:w-64">
                  <label className="block text-sm font-medium mb-1">Фильтр</label>
                  <Select
                    value={availabilityFilter}
                    onChange={(e) => setAvailabilityFilter(e.target.value as 'all' | 'available')}
                    disabled={controlsDisabled}
                  >
                    <option value="all">Все блюда</option>
                    <option value="available">Только в наличии</option>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {menuItems.length === 0 ? (
                  <div className="col-span-full text-sm text-gray-500">Меню пустое.</div>
                ) : filteredMenuItems.length === 0 ? (
                  <div className="col-span-full text-sm text-gray-500">Ничего не найдено.</div>
                ) : (
                  filteredMenuItems.map((item) => (
                    <Card key={item.id}>
                      <CardHeader>
                        <CardTitle className="text-lg">{item.name}</CardTitle>
                        <CardDescription>{formatCurrency(item.price || 0)}</CardDescription>
                      </CardHeader>
                      <CardContent>
                        <div className="flex items-center gap-2">
                          <Button
                            variant="outline"
                            onClick={() => item.id && removeItem(item.id)}
                            disabled={controlsDisabled || !item.id || !cart.has(item.id)}
                          >
                            -
                          </Button>
                          <div className="min-w-[80px] text-center">
                            {item.id && cart.get(item.id) ? `${cart.get(item.id)} шт.` : '0'}
                          </div>
                          <Button
                            onClick={() => item.id && addItem(item.id)}
                            disabled={controlsDisabled || !item.id || !item.available}
                          >
                            +
                          </Button>
                        </div>
                        <p className="text-xs text-gray-500 mt-2">{item.available ? 'В наличии' : 'Нет в наличии'}</p>
                      </CardContent>
                    </Card>
                  ))
                )}
              </div>
            </div>

            <Card>
              <CardHeader>
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <CardTitle className="text-lg">Корзина</CardTitle>
                    <CardDescription>Позиций: {totalItems}</CardDescription>
                  </div>
                  <Button variant="ghost" size="sm" onClick={clearCart} disabled={controlsDisabled || cart.size === 0}>
                    Очистить
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {cartLines.length === 0 ? (
                  <p className="text-sm text-gray-500">Корзина пуста</p>
                ) : (
                  <div className="max-h-80 overflow-y-auto rounded-md border divide-y">
                    {cartLines.map((line) => (
                      <div key={line.itemId} className="flex items-center justify-between gap-3 px-3 py-2">
                        <div className="min-w-0">
                          <p className="font-medium truncate">{line.name}</p>
                          <p className="text-sm text-gray-500">
                            {formatCurrency(line.unitPrice)} × {line.quantity}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => removeItem(line.itemId)}
                            disabled={controlsDisabled}
                          >
                            -
                          </Button>
                          <span className="w-6 text-center">{line.quantity}</span>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => addItem(line.itemId)}
                            disabled={controlsDisabled || !line.canIncrement}
                          >
                            +
                          </Button>
                        </div>
                        <div className="flex flex-col items-end gap-1">
                          <p className="font-semibold">{formatCurrency(line.subtotal)}</p>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => deleteItem(line.itemId)}
                            disabled={controlsDisabled}
                          >
                            Удалить
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <div className="flex items-center justify-between">
                  <p className="font-semibold">Итого</p>
                  <p className="font-semibold">{formatCurrency(total)}</p>
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
            <div>
              <label className="block text-sm font-medium mb-1">Тип заказа</label>
              <Select
                value={orderType}
                onChange={(e) => setOrderType(e.target.value as OrderType)}
                disabled={controlsDisabled}
              >
                <option value={OrderType.DineIn}>В зале (dine_in)</option>
                <option value={OrderType.Takeout}>С собой (takeout)</option>
              </Select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Оплата</label>
              <Select
                value={paymentMethod}
                onChange={(e) => {
                  setPaymentMethod(e.target.value as PaymentMethod);
                  setAmountReceived('');
                }}
                disabled={controlsDisabled}
              >
                <option value={PaymentMethod.Cash}>Наличные</option>
                <option value={PaymentMethod.Card}>Карта</option>
              </Select>
            </div>
            {paymentMethod === PaymentMethod.Cash ? (
              <div>
                <label className="block text-sm font-medium mb-1">Получено наличными</label>
                <Input
                  type="number"
                  step="0.01"
                  value={amountReceived}
                  onChange={(e) => setAmountReceived(e.target.value)}
                  placeholder="Например, 1000"
                  disabled={isBusy || (draft !== null && draft.stage !== 'payment')}
                />
                <div className="mt-1 text-xs">
                  {amountReceived && amountReceivedValue === null ? (
                    <span className="text-red-600">Введите сумму числом.</span>
                  ) : cashChangeValue !== null && cashChangeValue < 0 ? (
                    <span className="text-red-600">Не хватает: {formatCurrency(cashShortageValue || 0)}</span>
                  ) : null}
                </div>
              </div>
            ) : (
              <div>
                <p className="text-sm text-gray-600">Оплата картой</p>
                <p className="text-xs text-gray-500">Сдача не требуется</p>
              </div>
            )}
          </div>

          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <p className="font-semibold">Итого: {formatCurrency(total)}</p>
              {isCreatingClient && <p className="text-xs text-gray-500">Создаем гостя...</p>}
              {submittingStep === 'creatingOrder' && <p className="text-xs text-gray-500">Создаем заказ...</p>}
              {submittingStep === 'processingPayment' && (
                <p className="text-xs text-gray-500">Обрабатываем оплату...</p>
              )}
              {submittingStep === 'confirmingOrder' && <p className="text-xs text-gray-500">Отправляем на кухню...</p>}
            </div>
            <Button
              onClick={submit}
              disabled={
                !isStaff ||
                isBusy ||
                (draft
                  ? draft.stage === 'payment'
                    ? draft.paymentMethod === PaymentMethod.Cash
                      ? !isCashInputValid || !isCashEnough
                      : false
                    : false
                  : cart.size === 0 ||
                    !clientIdInputValid ||
                    !guestPhoneValid ||
                    (paymentMethod === PaymentMethod.Cash ? !isCashInputValid || !isCashEnough : false))
              }
            >
              {isBusy
                ? submittingStep === 'creatingOrder'
                  ? 'Создание заказа...'
                  : submittingStep === 'processingPayment'
                    ? 'Оплата...'
                    : submittingStep === 'confirmingOrder'
                      ? 'Отправка...'
                      : 'Оформление...'
                : draft
                  ? draft.stage === 'payment'
                    ? 'Продолжить оплату и отправить на кухню'
                    : 'Отправить на кухню'
                  : 'Принять оплату и отправить на кухню'}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Dialog
        open={successDialogOpen}
        onOpenChange={(open) => {
          setSuccessDialogOpen(open);
          if (!open) {
            setSuccessInfo(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Операция успешна</DialogTitle>
            <DialogDescription>Заказ оплачен и отправлен на кухню.</DialogDescription>
          </DialogHeader>
          {successInfo ? (
            <div className="space-y-2 text-sm">
              <div>
                <strong>Заказ #{successInfo.orderId}</strong>
              </div>
              <div>Сумма: {formatCurrency(successInfo.total)}</div>
              <div>Оплата: {successInfo.paymentMethod === PaymentMethod.Cash ? 'Наличные' : 'Карта'}</div>
              {successInfo.paymentMethod === PaymentMethod.Cash ? (
                <>
                  <div>Получено: {formatCurrency(successInfo.amountReceived ?? 0)}</div>
                  <div>Сдача: {formatCurrency(successInfo.change ?? 0)}</div>
                </>
              ) : null}
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setSuccessDialogOpen(false)}>
              Новый заказ
            </Button>
            <Button
              onClick={() => {
                const orderId = successInfo?.orderId;
                setSuccessDialogOpen(false);
                if (orderId) {
                  navigate(`/orders?orderId=${orderId}`);
                }
              }}
            >
              Открыть заказ
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={resetDraftDialogOpen} onOpenChange={setResetDraftDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Начать новый заказ?</DialogTitle>
            <DialogDescription>
              Текущий заказ останется в системе. Если он уже создан — его можно найти в “Заказы”.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResetDraftDialogOpen(false)}>
              Отмена
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                setResetDraftDialogOpen(false);
                clearOrderDraft();
              }}
            >
              Начать новый
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
