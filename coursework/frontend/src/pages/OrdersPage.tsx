import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ordersApi, authApi, paymentsApi, couriersApi, reviewsApi } from '@/api/client';
import { Courier, Order, OrderItem, OrderStatus, OrderType, Payment, PaymentMethod, Review } from '@/api/generated/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { LoadingState } from '@/components/ui/LoadingState';
import { Textarea } from '@/components/ui/Textarea';
import { RetryAlert } from '@/components/ui/RetryAlert';
import { formatCurrency, formatDate } from '@/lib/utils';
import { getApiErrorMessage } from '@/lib/apiError';
import {
  formatCardNumberInput,
  formatExpiryInput,
  isValidLuhn,
  sanitizeCardNumber,
  validateExpiry,
} from '@/lib/cardUtils';
import { useAuthStore } from '@/store/authStore';
import { useToast } from '@/components/ui/toast';

type ClientStatusFilter =
  | 'active'
  | 'all'
  | 'pending'
  | 'confirmed'
  | 'preparing'
  | 'ready'
  | 'delivering'
  | 'delivered'
  | 'completed'
  | 'cancelled';

type EmployeeStatusFilter = ClientStatusFilter;

type EmployeeSort = 'newest' | 'oldest' | 'amountDesc' | 'amountAsc';

function getOrderStatusString(order: Order): string {
  const statusValue = typeof order.status === 'string' ? order.status : (order.status as any)?.value || 'pending';
  return String(statusValue);
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  pending: 'Новый',
  confirmed: 'Подтвержден',
  preparing: 'Готовится',
  ready: 'Готов',
  delivering: 'В пути',
  delivered: 'Доставлен',
  completed: 'Завершен',
  cancelled: 'Отменен',
};

function getOrderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function getOrderTypeLabel(type: OrderType | null | undefined): string {
  if (!type) return '—';
  switch (type) {
    case OrderType.Delivery:
      return 'Доставка';
    case OrderType.DineIn:
      return 'В зале';
    case OrderType.Takeout:
      return 'С собой';
    default:
      return String(type);
  }
}

function getPaymentMethodLabel(method: PaymentMethod | null | undefined): string {
  if (!method) return '—';
  switch (method) {
    case PaymentMethod.Online:
      return 'Онлайн';
    case PaymentMethod.Cash:
      return 'При получении (наличные)';
    case PaymentMethod.Card:
      return 'При получении (карта)';
    default:
      return String(method);
  }
}

export function OrdersPage() {
  const toast = useToast();
  const [searchParams] = useSearchParams();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(PaymentMethod.Card);
  const [amountReceived, setAmountReceived] = useState('');
  const { isClient, isCashier, isManager } = useAuthStore();
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [orderItemsByOrderId, setOrderItemsByOrderId] = useState<Record<number, OrderItem[]>>({});
  const [paymentsByOrderId, setPaymentsByOrderId] = useState<Record<number, Payment | null>>({});
  const [reviewsByOrderId, setReviewsByOrderId] = useState<Record<number, Review>>({});
  const [reviewDialogOpen, setReviewDialogOpen] = useState(false);
  const [reviewOrderId, setReviewOrderId] = useState<number | null>(null);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [clientStatusFilter, setClientStatusFilter] = useState<ClientStatusFilter>('active');
  const [employeeQuery, setEmployeeQuery] = useState('');
  const [employeeStatusFilter, setEmployeeStatusFilter] = useState<EmployeeStatusFilter>('active');
  const [employeeTypeFilter, setEmployeeTypeFilter] = useState<'all' | OrderType>('all');
  const [employeePaymentFilter, setEmployeePaymentFilter] = useState<'all' | PaymentMethod>('all');
  const [employeeFrom, setEmployeeFrom] = useState('');
  const [employeeTo, setEmployeeTo] = useState('');
  const [employeeSort, setEmployeeSort] = useState<EmployeeSort>('newest');
  const [employeePageSize, setEmployeePageSize] = useState(10);
  const [employeePage, setEmployeePage] = useState(1);
  const [employeeCancelDialogOpen, setEmployeeCancelDialogOpen] = useState(false);
  const [employeeCancelOrder, setEmployeeCancelOrder] = useState<Order | null>(null);
  const [employeeCancelSubmitting, setEmployeeCancelSubmitting] = useState(false);
  const [clientPayDialogOpen, setClientPayDialogOpen] = useState(false);
  const [clientPayOrder, setClientPayOrder] = useState<Order | null>(null);
  const [clientPayMethod, setClientPayMethod] = useState<PaymentMethod>(PaymentMethod.Online);
  const [clientCardData, setClientCardData] = useState({ number: '', expiry: '', cvv: '' });
  const [clientPayError, setClientPayError] = useState<string | null>(null);
  const [clientPaySubmitting, setClientPaySubmitting] = useState(false);
  const loadOrdersRef = useRef<() => Promise<void>>(() => Promise.resolve());

  const focusedOrderId = useMemo(() => {
    const raw = searchParams.get('orderId');
    if (!raw) return null;
    const parsed = parseInt(raw, 10);
    return Number.isFinite(parsed) ? parsed : null;
  }, [searchParams]);

  const isClientUser = isClient();
  const employeeFiltersDirty =
    employeeQuery.trim().length > 0 ||
    employeeStatusFilter !== 'active' ||
    employeeTypeFilter !== 'all' ||
    employeePaymentFilter !== 'all' ||
    Boolean(employeeFrom) ||
    Boolean(employeeTo) ||
    employeeSort !== 'newest' ||
    employeePageSize !== 10;

  const resetEmployeeFilters = () => {
    setEmployeeQuery('');
    setEmployeeStatusFilter('active');
    setEmployeeTypeFilter('all');
    setEmployeePaymentFilter('all');
    setEmployeeFrom('');
    setEmployeeTo('');
    setEmployeeSort('newest');
    setEmployeePageSize(10);
    setEmployeePage(1);
  };

  const clientVisibleOrders = useMemo(() => {
    if (!isClientUser) return [];
    const sorted = [...orders].sort((a, b) => {
      const aTs = a.createdAt ? Date.parse(a.createdAt) : 0;
      const bTs = b.createdAt ? Date.parse(b.createdAt) : 0;
      if (aTs !== bTs) return bTs - aTs;
      return (b.id || 0) - (a.id || 0);
    });

    return sorted.filter((order) => {
      const statusStr = getOrderStatusString(order);
      if (clientStatusFilter === 'all') return true;
      if (clientStatusFilter === 'active') return statusStr !== 'cancelled' && statusStr !== 'completed';
      return statusStr === clientStatusFilter;
    });
  }, [orders, isClientUser, clientStatusFilter]);

  const employeeFilteredOrders = useMemo(() => {
    if (isClientUser) return [];

    const q = employeeQuery.trim().toLowerCase();
    const qDigits = q.replace(/\D/g, '');
    const fromTs = employeeFrom ? Date.parse(`${employeeFrom}T00:00:00`) : null;
    const toTs = employeeTo ? Date.parse(`${employeeTo}T23:59:59.999`) : null;

    const filtered = orders.filter((order) => {
      const statusStr = getOrderStatusString(order);
      if (employeeStatusFilter !== 'all') {
        if (employeeStatusFilter === 'active') {
          if (statusStr === 'cancelled' || statusStr === 'completed') return false;
        } else if (statusStr !== employeeStatusFilter) {
          return false;
        }
      }

      if (employeeTypeFilter !== 'all' && order.type !== employeeTypeFilter) {
        return false;
      }

      if (employeePaymentFilter !== 'all' && order.paymentMethod !== employeePaymentFilter) {
        return false;
      }

      if (fromTs !== null || toTs !== null) {
        if (!order.createdAt) return false;
        const createdTs = Date.parse(order.createdAt);
        if (!Number.isFinite(createdTs)) return false;
        if (fromTs !== null && createdTs < fromTs) return false;
        if (toTs !== null && createdTs > toTs) return false;
      }

      if (!q) return true;

      const orderIdStr = order.id != null ? String(order.id) : '';
      const clientIdStr = order.clientId != null ? String(order.clientId) : '';
      const address = (order.deliveryAddress || '').toLowerCase();
      const courierName = (order.courier?.name || '').toLowerCase();
      const courierPhone = (order.courier?.phone || '').toLowerCase();
      const statusLabel = getOrderStatusLabel(statusStr).toLowerCase();

      const items = order.id ? orderItemsByOrderId[order.id] : undefined;
      const itemsHay =
        items && items.length > 0
          ? items
              .map((it) => it.name || `#${it.menuItemId}`)
              .join(' ')
              .toLowerCase()
          : '';

      if (qDigits) {
        if (orderIdStr.includes(qDigits)) return true;
        if (clientIdStr.includes(qDigits)) return true;
        if (courierPhone.replace(/\D/g, '').includes(qDigits)) return true;
      }

      const hay = `${orderIdStr} ${clientIdStr} ${address} ${courierName} ${courierPhone} ${statusLabel} ${itemsHay}`;
      return hay.includes(q);
    });

    const sorted = [...filtered].sort((a, b) => {
      if (employeeSort === 'amountAsc' || employeeSort === 'amountDesc') {
        const aAmount = a.totalAmount ?? 0;
        const bAmount = b.totalAmount ?? 0;
        if (aAmount !== bAmount) {
          return employeeSort === 'amountAsc' ? aAmount - bAmount : bAmount - aAmount;
        }
      }

      const aTs = a.createdAt ? Date.parse(a.createdAt) : 0;
      const bTs = b.createdAt ? Date.parse(b.createdAt) : 0;
      if (aTs !== bTs) return employeeSort === 'oldest' ? aTs - bTs : bTs - aTs;
      return employeeSort === 'oldest' ? (a.id || 0) - (b.id || 0) : (b.id || 0) - (a.id || 0);
    });

    return sorted;
  }, [
    isClientUser,
    orders,
    employeeQuery,
    employeeStatusFilter,
    employeeTypeFilter,
    employeePaymentFilter,
    employeeFrom,
    employeeTo,
    employeeSort,
    orderItemsByOrderId,
  ]);

  const employeeTotalPages = useMemo(() => {
    if (isClientUser) return 1;
    const size = Math.max(1, employeePageSize);
    return Math.max(1, Math.ceil(employeeFilteredOrders.length / size));
  }, [isClientUser, employeeFilteredOrders.length, employeePageSize]);

  const employeePageSafe = useMemo(() => {
    if (isClientUser) return 1;
    return Math.min(Math.max(employeePage, 1), employeeTotalPages);
  }, [isClientUser, employeePage, employeeTotalPages]);

  useEffect(() => {
    if (isClientUser) return;
    if (employeePage !== employeePageSafe) {
      setEmployeePage(employeePageSafe);
    }
  }, [isClientUser, employeePage, employeePageSafe]);

  const employeeVisibleOrders = useMemo(() => {
    if (isClientUser) return [];
    const start = (employeePageSafe - 1) * employeePageSize;
    return employeeFilteredOrders.slice(start, start + employeePageSize);
  }, [isClientUser, employeeFilteredOrders, employeePageSafe, employeePageSize]);

  const visibleOrders = isClientUser ? clientVisibleOrders : employeeVisibleOrders;

  useEffect(() => {
    loadOrders();
    if (isManager() || isCashier()) {
      loadCouriers();
    }
  }, []);

  useEffect(() => {
    if (!isClientUser) return;
    const intervalId = window.setInterval(() => {
      void loadOrdersRef.current();
    }, 5000);
    return () => window.clearInterval(intervalId);
  }, [isClientUser]);

  useEffect(() => {
    if (isClientUser) return;
    void preloadOrderPayments(visibleOrders);
  }, [isClientUser, visibleOrders]);

  useEffect(() => {
    if (loading) return;
    if (!focusedOrderId) return;
    const el = document.getElementById(`order-${focusedOrderId}`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [loading, focusedOrderId, visibleOrders.length]);

  useEffect(() => {
    if (isClientUser) return;
    if (!focusedOrderId) return;
    setEmployeeQuery(String(focusedOrderId));
    setEmployeeStatusFilter('all');
    setEmployeePage(1);
  }, [focusedOrderId, isClientUser]);

  const loadOrders = async () => {
    try {
      setError(null);
      if (isClient()) {
        let user = useAuthStore.getState().user;
        if (!user?.userId) {
          try {
            const userResponse = await authApi.getCurrentUser();
            useAuthStore.getState().setAuth(useAuthStore.getState().token || '', userResponse.data);
            user = userResponse.data;
          } catch (err) {
            console.error('Failed to load user info:', err);
            setError('Не удалось загрузить информацию о пользователе');
            return;
          }
        }

        if (user?.userId) {
          const response = await ordersApi.getAllOrders(undefined, user.userId);
          const list = response.data || [];
          setOrders(list);
          void preloadOrderItems(list);
          void loadClientReviews(user.userId);
        }
      } else {
        const response = await ordersApi.getAllOrders();
        const list = response.data || [];
        setOrders(list);
        void preloadOrderItems(list);
        setReviewsByOrderId({});
      }
    } catch (error: any) {
      console.error('Ошибка загрузки заказов:', error);
      console.error('Error details:', error.response?.data);
      setError(error.response?.data?.message || 'Ошибка загрузки заказов');
    } finally {
      setLoading(false);
    }
  };
  loadOrdersRef.current = loadOrders;

  const loadClientReviews = async (clientId: number) => {
    try {
      const response = await reviewsApi.getAllReviews(clientId);
      const map: Record<number, Review> = {};
      for (const review of response.data || []) {
        if (typeof review.orderId === 'number') {
          map[review.orderId] = review;
        }
      }
      setReviewsByOrderId(map);
    } catch (e) {
      console.error('Не удалось загрузить отзывы клиента:', e);
      setReviewsByOrderId({});
    }
  };

  const openReviewDialog = (orderId: number) => {
    setReviewOrderId(orderId);
    setReviewRating(5);
    setReviewComment('');
    setReviewError(null);
    setReviewDialogOpen(true);
  };

  const submitReview = async () => {
    if (!reviewOrderId) return;
    const user = useAuthStore.getState().user;
    if (!user?.userId) {
      setReviewError('Пользователь не найден. Войдите заново.');
      return;
    }

    setReviewSubmitting(true);
    try {
      setReviewError(null);
      await reviewsApi.createReview({
        clientId: user.userId,
        orderId: reviewOrderId,
        rating: reviewRating,
        comment: reviewComment.trim() || undefined,
      });
      setReviewDialogOpen(false);
      setReviewOrderId(null);
      setReviewComment('');
      await loadClientReviews(user.userId);
      toast.success('Спасибо за отзыв!');
    } catch (e: any) {
      console.error('Ошибка отправки отзыва:', e);
      setReviewError(e.response?.data?.message || 'Не удалось отправить отзыв');
    } finally {
      setReviewSubmitting(false);
    }
  };

  const loadCouriers = async () => {
    try {
      const response = await couriersApi.getAllCouriers();
      setCouriers(response.data || []);
    } catch (error) {
      console.error('Не удалось загрузить курьеров:', error);
    }
  };

  const preloadOrderPayments = async (ordersList: Order[]) => {
    if (!(isCashier() || isManager())) return;

    const eligibleOrderIds = ordersList
      .filter((order) => {
        const statusStr = getOrderStatusString(order);
        return (
          order.type === OrderType.Delivery &&
          statusStr === 'delivered' &&
          (order.paymentMethod === PaymentMethod.Cash || order.paymentMethod === PaymentMethod.Card)
        );
      })
      .map((order) => order.id)
      .filter((id): id is number => typeof id === 'number');

    if (eligibleOrderIds.length === 0) return;

    const missing = eligibleOrderIds.filter((id) => paymentsByOrderId[id] === undefined);
    if (missing.length === 0) return;

    const results = await Promise.allSettled(missing.map((id) => paymentsApi.getPaymentByOrderId(id)));
    setPaymentsByOrderId((prev) => {
      const next: Record<number, Payment | null> = { ...prev };
      for (let i = 0; i < missing.length; i++) {
        const orderId = missing[i];
        const result = results[i];
        if (result.status === 'fulfilled') {
          next[orderId] = result.value.data;
          continue;
        }
        const httpStatus = (result.reason as any)?.response?.status;
        if (httpStatus === 404) {
          next[orderId] = null;
          continue;
        }
        console.error(`Не удалось загрузить платёж для заказа #${orderId}:`, result.reason);
      }
      return next;
    });
  };

  const preloadOrderItems = async (ordersList: Order[]) => {
    const orderIds = ordersList.map((order) => order.id).filter((id): id is number => typeof id === 'number');

    if (orderIds.length === 0) return;

    const missing = orderIds.filter((id) => orderItemsByOrderId[id] === undefined);
    if (missing.length === 0) return;

    const chunks: number[][] = [];
    for (let i = 0; i < missing.length; i += 200) {
      chunks.push(missing.slice(i, i + 200));
    }

    for (const chunk of chunks) {
      try {
        const resp = await ordersApi.getOrderItemsBatch({ orderIds: chunk });
        const items = resp.data || [];

        setOrderItemsByOrderId((prev) => {
          const next: Record<number, OrderItem[]> = { ...prev };
          for (const orderId of chunk) {
            if (next[orderId] === undefined) next[orderId] = [];
          }
          for (const item of items) {
            const oid = item.orderId;
            if (typeof oid !== 'number') continue;
            if (!next[oid]) next[oid] = [];
            next[oid] = [...next[oid], item];
          }
          return next;
        });
      } catch (error) {
        console.error(`Не удалось загрузить позиции заказов:`, error);
        setOrderItemsByOrderId((prev) => {
          const next: Record<number, OrderItem[]> = { ...prev };
          for (const orderId of chunk) {
            if (next[orderId] === undefined) next[orderId] = [];
          }
          return next;
        });
      }
    }
  };

  const handleProcessPayment = async () => {
    if (!selectedOrder?.id) return;

    try {
      if (paymentMethod === PaymentMethod.Cash) {
        if (!amountReceived) {
          toast.warning('Введите полученную сумму');
          return;
        }
        const response = await paymentsApi.processCashPayment({
          orderId: selectedOrder.id,
          amountReceived: parseFloat(amountReceived),
        });
        toast.success(`Платеж обработан. Сдача: ${formatCurrency(response.data.change!)}`);
        try {
          const payment = await paymentsApi.getPaymentByOrderId(selectedOrder.id);
          setPaymentsByOrderId((prev) => ({ ...prev, [selectedOrder.id!]: payment.data }));
        } catch (e) {
          console.error('Не удалось загрузить платёж после обработки наличными:', e);
        }
      } else {
        const payment = await paymentsApi.processPayment({
          orderId: selectedOrder.id,
          method: paymentMethod,
        });
        setPaymentsByOrderId((prev) => ({ ...prev, [selectedOrder.id!]: payment.data }));
        toast.success('Платеж обработан успешно');
      }

      const statusValue =
        typeof selectedOrder.status === 'string'
          ? selectedOrder.status
          : (selectedOrder.status as any)?.value || 'pending';
      const statusStr = String(statusValue);
      if ((isCashier() || isManager()) && statusStr === 'pending') {
        await ordersApi.updateOrderStatus(selectedOrder.id, { status: OrderStatus.Confirmed });
      }

      setShowPaymentDialog(false);
      setSelectedOrder(null);
      setAmountReceived('');
      loadOrders();
    } catch (error: any) {
      console.error('Ошибка обработки платежа:', error);
      toast.error(getApiErrorMessage(error, 'Ошибка обработки платежа'), { title: 'Ошибка' });
    }
  };

  const handleUpdateStatus = async (orderId: number, status: OrderStatus): Promise<boolean> => {
    try {
      await ordersApi.updateOrderStatus(orderId, { status });
      await loadOrders();
      return true;
    } catch (error: any) {
      console.error('Ошибка обновления статуса:', error);
      toast.error(getApiErrorMessage(error, 'Ошибка обновления статуса'), { title: 'Ошибка' });
      return false;
    }
  };

  const openEmployeeCancel = (order: Order) => {
    setEmployeeCancelOrder(order);
    setEmployeeCancelDialogOpen(true);
  };

  const confirmEmployeeCancel = async () => {
    if (!employeeCancelOrder?.id) return;
    setEmployeeCancelSubmitting(true);
    try {
      const ok = await handleUpdateStatus(employeeCancelOrder.id, OrderStatus.Cancelled);
      if (!ok) return;
      toast.info(`Заказ #${employeeCancelOrder.id} отменён`);
      setEmployeeCancelDialogOpen(false);
      setEmployeeCancelOrder(null);
    } finally {
      setEmployeeCancelSubmitting(false);
    }
  };

  const openClientPaymentDialog = (order: Order) => {
    setClientPayOrder(order);
    setClientPayMethod(PaymentMethod.Online);
    setClientCardData({ number: '', expiry: '', cvv: '' });
    setClientPayError(null);
    setClientPayDialogOpen(true);
  };

  const closeClientPaymentDialog = () => {
    setClientPayDialogOpen(false);
    setClientPayOrder(null);
    setClientPayError(null);
    setClientPaySubmitting(false);
    setClientPayMethod(PaymentMethod.Online);
    setClientCardData({ number: '', expiry: '', cvv: '' });
  };

  const submitClientPaymentOrSwitch = async () => {
    if (!clientPayOrder?.id) return;
    if (!isClient()) return;

    setClientPaySubmitting(true);
    try {
      setClientPayError(null);

      if (clientPayMethod !== PaymentMethod.Online) {
        await ordersApi.updateOrderPaymentMethod(clientPayOrder.id, { paymentMethod: clientPayMethod });
        toast.info('Способ оплаты изменён. Оплата — при получении.');
        closeClientPaymentDialog();
        loadOrders();
        return;
      }

      const sanitizedCardNumber = sanitizeCardNumber(clientCardData.number);
      const sanitizedExpiry = clientCardData.expiry.trim();
      const sanitizedCvv = clientCardData.cvv.trim();

      if (!/^[0-9]{16}$/.test(sanitizedCardNumber) || !isValidLuhn(sanitizedCardNumber)) {
        setClientPayError('Введите корректный номер карты');
        return;
      }
      const expiryCheck = validateExpiry(sanitizedExpiry);
      if (!expiryCheck.valid) {
        setClientPayError(
          expiryCheck.reason === 'expired' ? 'Срок действия карты истек' : 'Срок действия в формате ММ/ГГ'
        );
        return;
      }
      if (!/^[0-9]{3,4}$/.test(sanitizedCvv)) {
        setClientPayError('CVV должен содержать 3-4 цифры');
        return;
      }

      const shouldSimulateFailure = sanitizedCardNumber === '0000000000000000';
      const response = await paymentsApi.startOnlinePayment({
        orderId: clientPayOrder.id,
        cardNumber: sanitizedCardNumber,
        cardExpiry: sanitizedExpiry,
        cardCvv: sanitizedCvv,
        shouldSimulateFailure,
      });
      const redirectUrl = response.data.redirectUrl;
      if (!redirectUrl) {
        throw new Error('Не удалось получить ссылку для оплаты в банке.');
      }

      closeClientPaymentDialog();
      window.location.href = redirectUrl;
    } catch (error: any) {
      console.error('Ошибка онлайн-оплаты:', error);
      const message = getApiErrorMessage(error, 'Ошибка онлайн-оплаты');
      setClientPayError(message);
      toast.error(message, { title: 'Ошибка' });
    } finally {
      setClientPaySubmitting(false);
    }
  };

  const cancelClientPayOrder = async () => {
    if (!clientPayOrder?.id) return;
    if (!isClient()) return;

    setClientPaySubmitting(true);
    try {
      await ordersApi.updateOrderStatus(clientPayOrder.id, { status: OrderStatus.Cancelled });
      toast.info('Заказ отменён');
      closeClientPaymentDialog();
      loadOrders();
    } catch (error: any) {
      console.error('Ошибка отмены заказа:', error);
      const message = getApiErrorMessage(error, 'Не удалось отменить заказ');
      setClientPayError(message);
      toast.error(message, { title: 'Ошибка' });
    } finally {
      setClientPaySubmitting(false);
    }
  };

  const reload = () => {
    setLoading(true);
    void loadOrders();
  };

  if (loading) {
    return <LoadingState message="Загрузка заказов..." />;
  }

  return (
    <>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
          <div>
            <h1 className="text-3xl font-bold">Заказы</h1>
            <p className="text-gray-600 mt-1">
              {isClient() ? 'История и статус ваших заказов.' : 'Управление заказами.'}
            </p>
          </div>
          <Button variant="outline" onClick={reload}>
            Обновить
          </Button>
        </div>

        {error && <RetryAlert message={error} onRetry={reload} />}

        <div className="space-y-4">
          {isClientUser && (
            <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
              <div className="text-sm text-gray-500">
                {orders.length > 0 ? `Показано: ${visibleOrders.length} из ${orders.length}` : 'Заказов пока нет'}
              </div>
              <div className="w-full sm:w-72">
                <label className="block text-sm font-medium mb-1">Фильтр по статусу</label>
                <Select
                  value={clientStatusFilter}
                  onChange={(e) => setClientStatusFilter(e.target.value as ClientStatusFilter)}
                >
                  <option value="active">Активные</option>
                  <option value="all">Все</option>
                  <option value="pending">{getOrderStatusLabel('pending')}</option>
                  <option value="confirmed">{getOrderStatusLabel('confirmed')}</option>
                  <option value="preparing">{getOrderStatusLabel('preparing')}</option>
                  <option value="ready">{getOrderStatusLabel('ready')}</option>
                  <option value="delivering">{getOrderStatusLabel('delivering')}</option>
                  <option value="delivered">{getOrderStatusLabel('delivered')}</option>
                  <option value="completed">{getOrderStatusLabel('completed')}</option>
                  <option value="cancelled">{getOrderStatusLabel('cancelled')}</option>
                </Select>
              </div>
            </div>
          )}

          {!isClientUser && (
            <Card>
              <CardContent className="pt-6 space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <div className="md:col-span-2">
                    <label className="block text-sm font-medium mb-1">Поиск</label>
                    <Input
                      placeholder="Заказ #, клиент #, адрес, курьер, позиция..."
                      value={employeeQuery}
                      onChange={(e) => {
                        setEmployeeQuery(e.target.value);
                        setEmployeePage(1);
                      }}
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Статус</label>
                    <Select
                      value={employeeStatusFilter}
                      onChange={(e) => {
                        setEmployeeStatusFilter(e.target.value as EmployeeStatusFilter);
                        setEmployeePage(1);
                      }}
                    >
                      <option value="active">Активные</option>
                      <option value="all">Все</option>
                      <option value="pending">{getOrderStatusLabel('pending')}</option>
                      <option value="confirmed">{getOrderStatusLabel('confirmed')}</option>
                      <option value="preparing">{getOrderStatusLabel('preparing')}</option>
                      <option value="ready">{getOrderStatusLabel('ready')}</option>
                      <option value="delivering">{getOrderStatusLabel('delivering')}</option>
                      <option value="delivered">{getOrderStatusLabel('delivered')}</option>
                      <option value="completed">{getOrderStatusLabel('completed')}</option>
                      <option value="cancelled">{getOrderStatusLabel('cancelled')}</option>
                    </Select>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <div>
                    <label className="block text-sm font-medium mb-1">Тип</label>
                    <Select
                      value={employeeTypeFilter}
                      onChange={(e) => {
                        setEmployeeTypeFilter(e.target.value as 'all' | OrderType);
                        setEmployeePage(1);
                      }}
                    >
                      <option value="all">Все</option>
                      <option value={OrderType.DineIn}>В зале</option>
                      <option value={OrderType.Takeout}>С собой</option>
                      <option value={OrderType.Delivery}>Доставка</option>
                    </Select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Оплата</label>
                    <Select
                      value={employeePaymentFilter}
                      onChange={(e) => {
                        setEmployeePaymentFilter(e.target.value as 'all' | PaymentMethod);
                        setEmployeePage(1);
                      }}
                    >
                      <option value="all">Все</option>
                      <option value={PaymentMethod.Cash}>Наличные</option>
                      <option value={PaymentMethod.Card}>Карта</option>
                      <option value={PaymentMethod.Online}>Онлайн</option>
                    </Select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Сортировка</label>
                    <Select
                      value={employeeSort}
                      onChange={(e) => {
                        setEmployeeSort(e.target.value as EmployeeSort);
                        setEmployeePage(1);
                      }}
                    >
                      <option value="newest">Сначала новые</option>
                      <option value="oldest">Сначала старые</option>
                      <option value="amountDesc">Сумма: по убыванию</option>
                      <option value="amountAsc">Сумма: по возрастанию</option>
                    </Select>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                  <div>
                    <label className="block text-sm font-medium mb-1">Период: с</label>
                    <Input
                      type="date"
                      value={employeeFrom}
                      onChange={(e) => {
                        setEmployeeFrom(e.target.value);
                        setEmployeePage(1);
                      }}
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Период: по</label>
                    <Input
                      type="date"
                      value={employeeTo}
                      onChange={(e) => {
                        setEmployeeTo(e.target.value);
                        setEmployeePage(1);
                      }}
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">На странице</label>
                    <Select
                      value={String(employeePageSize)}
                      onChange={(e) => {
                        setEmployeePageSize(parseInt(e.target.value, 10));
                        setEmployeePage(1);
                      }}
                    >
                      <option value="10">10</option>
                      <option value="25">25</option>
                      <option value="50">50</option>
                    </Select>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                  <div className="text-sm text-gray-500">
                    {orders.length > 0
                      ? `Показано: ${visibleOrders.length} из ${employeeFilteredOrders.length} (всего ${orders.length})`
                      : 'Заказов пока нет'}
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setEmployeePage((p) => Math.max(1, p - 1))}
                      disabled={employeePageSafe <= 1}
                    >
                      Назад
                    </Button>
                    <span className="text-sm text-gray-600">
                      Стр. {employeePageSafe} / {employeeTotalPages}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setEmployeePage((p) => Math.min(employeeTotalPages, p + 1))}
                      disabled={employeePageSafe >= employeeTotalPages}
                    >
                      Вперед
                    </Button>
                    {employeeFiltersDirty ? (
                      <Button variant="ghost" size="sm" onClick={resetEmployeeFilters}>
                        Сбросить
                      </Button>
                    ) : null}
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {visibleOrders.length === 0 ? (
            <Card>
              <CardContent className="pt-6">
                {orders.length === 0 ? (
                  <p className="text-center text-gray-500">Заказов пока нет</p>
                ) : isClientUser ? (
                  clientStatusFilter !== 'all' ? (
                    <div className="flex flex-col items-center gap-3">
                      <p className="text-center text-gray-500">По выбранному фильтру заказов нет</p>
                      <Button variant="outline" onClick={() => setClientStatusFilter('all')}>
                        Показать все
                      </Button>
                    </div>
                  ) : (
                    <p className="text-center text-gray-500">Заказов пока нет</p>
                  )
                ) : employeeStatusFilter === 'active' && !employeeFiltersDirty ? (
                  <div className="flex flex-col items-center gap-3">
                    <p className="text-center text-gray-500">Активных заказов нет</p>
                    <Button
                      variant="outline"
                      onClick={() => {
                        setEmployeeStatusFilter('all');
                        setEmployeePage(1);
                      }}
                    >
                      Показать все
                    </Button>
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-3">
                    <p className="text-center text-gray-500">По выбранным фильтрам заказов нет</p>
                    <Button variant="outline" onClick={resetEmployeeFilters}>
                      Сбросить фильтры
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>
          ) : (
            visibleOrders.map((order) => {
              const statusStr = getOrderStatusString(order);
              const statusLabel = getOrderStatusLabel(statusStr);
              const isOnlinePayment = order.paymentMethod === PaymentMethod.Online;
              const isDeliveryOrder = order.type === OrderType.Delivery;
              const payment = typeof order.id === 'number' ? paymentsByOrderId[order.id] : undefined;
              const paymentLoaded = typeof order.id === 'number' && payment !== undefined;
              const isPaid = payment != null;
              const payOnReceiptDelivery =
                isDeliveryOrder &&
                !isOnlinePayment &&
                (order.paymentMethod === PaymentMethod.Cash || order.paymentMethod === PaymentMethod.Card);
              const paymentCheckInProgress = payOnReceiptDelivery && statusStr === 'delivered' && !paymentLoaded;
              const assignedCourierId = order.courierId ?? order.courier?.id ?? null;
              const hasCourier = assignedCourierId !== null && assignedCourierId !== undefined;
              const canAssignCourier =
                (isManager() || isCashier()) &&
                isDeliveryOrder &&
                statusStr !== 'cancelled' &&
                statusStr !== 'completed' &&
                statusStr !== 'delivered';
              const courierRequiredNow = isDeliveryOrder && statusStr === 'ready';
              const canEmployeeProcessPayment =
                (isCashier() || isManager()) &&
                !isOnlinePayment &&
                (isDeliveryOrder ? statusStr === 'delivered' : statusStr === 'pending') &&
                !isPaid &&
                !paymentCheckInProgress;
              const canClientPayOnline = isClient() && statusStr === 'pending' && isOnlinePayment;
              const canEmployeeConfirm =
                (isCashier() || isManager()) && statusStr === 'pending' && isDeliveryOrder && !isOnlinePayment;
              const canEmployeeCancel =
                (isCashier() || isManager()) && (statusStr === 'pending' || statusStr === 'confirmed');
              const canClientCancel = isClient() && statusStr === 'pending';
              const canUpdateStatus =
                (isCashier() || isManager()) && statusStr !== 'cancelled' && statusStr !== 'completed';
              const canCloseDeliveryOrder =
                canUpdateStatus && isDeliveryOrder && statusStr === 'delivered' && (!payOnReceiptDelivery || isPaid);
              const nextStepHint = (() => {
                if (paymentCheckInProgress) return 'Проверяем оплату';
                if (canEmployeeProcessPayment) return 'Принять оплату';
                if (canClientPayOnline) return 'Оплатить онлайн';
                if (canEmployeeConfirm) return 'Подтвердить заказ';
                if (canUpdateStatus && statusStr === 'ready') {
                  return isDeliveryOrder ? 'Передать в доставку' : 'Отметить как выдан';
                }
                if (canUpdateStatus && isDeliveryOrder && statusStr === 'delivering') return 'Отметить как доставлен';
                if (canCloseDeliveryOrder) return 'Закрыть заказ';
                return null;
              })();
              const items = order.id ? orderItemsByOrderId[order.id] : undefined;
              const review = order.id ? reviewsByOrderId[order.id] : undefined;
              const canLeaveReview =
                isClient() &&
                typeof order.id === 'number' &&
                !review &&
                (order.paymentMethod === PaymentMethod.Online
                  ? statusStr === 'delivered' || statusStr === 'completed'
                  : statusStr === 'completed');

              return (
                <Card
                  key={order.id}
                  id={order.id ? `order-${order.id}` : undefined}
                  className={order.id === focusedOrderId ? 'ring-2 ring-primary-500 ring-offset-2' : undefined}
                >
                  <CardHeader>
                    <div className="flex justify-between items-start">
                      <div>
                        <CardTitle>Заказ #{order.id}</CardTitle>
                        <CardDescription>{formatDate(order.createdAt!)}</CardDescription>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl font-bold">{formatCurrency(order.totalAmount!)}</div>
                        <span
                          className={`inline-block px-2 py-1 rounded text-sm ${
                            statusStr === 'delivered' || statusStr === 'completed'
                              ? 'bg-green-100 text-green-800'
                              : statusStr === 'cancelled'
                                ? 'bg-red-100 text-red-800'
                                : 'bg-yellow-100 text-yellow-800'
                          }`}
                        >
                          {statusLabel}
                        </span>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-2">
                      <p>
                        <strong>Тип:</strong> {getOrderTypeLabel(order.type)}
                      </p>
                      <p>
                        <strong>Адрес:</strong> {order.deliveryAddress || 'В ресторане'}
                      </p>
                      {isDeliveryOrder && (
                        <p>
                          <strong>Курьер:</strong>{' '}
                          {order.courier
                            ? `${order.courier.name || `Курьер #${order.courier.id}`}${order.courier.phone ? ` (${order.courier.phone})` : ''}`
                            : hasCourier
                              ? `Курьер #${assignedCourierId}`
                              : '—'}
                        </p>
                      )}
                      {canAssignCourier && (
                        <div className="max-w-md">
                          <label className="block text-sm font-medium mb-1">Назначить курьера</label>
                          <Select
                            value={assignedCourierId ?? ''}
                            className={courierRequiredNow && !hasCourier ? 'border-red-400' : undefined}
                            onChange={async (e) => {
                              const value = e.target.value;
                              const courierId = value ? parseInt(value, 10) : null;
                              try {
                                await ordersApi.assignCourierToOrder(order.id!, { courierId });
                                await loadOrders();
                                await loadCouriers();
                              } catch (error: any) {
                                console.error('Ошибка назначения курьера:', error);
                                toast.error(getApiErrorMessage(error, 'Ошибка назначения курьера'), {
                                  title: 'Ошибка',
                                });
                              }
                            }}
                          >
                            <option value="">— не назначен —</option>
                            {couriers.map((courier) => {
                              const id = courier.id;
                              if (!id) return null;
                              const disabled =
                                (courier.available === false || courier.busy === true) && id !== assignedCourierId;
                              const statusLabel = courier.busy
                                ? 'занят'
                                : courier.available === false
                                  ? 'недоступен'
                                  : 'свободен';
                              return (
                                <option key={id} value={id} disabled={disabled}>
                                  {courier.name || `Курьер #${id}`} ({statusLabel})
                                </option>
                              );
                            })}
                          </Select>
                          <p
                            className={`mt-1 text-xs ${courierRequiredNow && !hasCourier ? 'text-red-600' : 'text-gray-500'}`}
                          >
                            {courierRequiredNow && !hasCourier
                              ? 'Назначьте курьера, чтобы передать заказ в доставку.'
                              : 'Для передачи в доставку курьер должен быть назначен.'}
                          </p>
                        </div>
                      )}
                      <p>
                        <strong>Оплата:</strong> {getPaymentMethodLabel(order.paymentMethod)}
                      </p>
                      {isClientUser && (
                        <div className="rounded-md bg-gray-50 p-3">
                          <p className="text-sm font-medium mb-2">Прогресс</p>
                          {statusStr === 'cancelled' ? (
                            <p className="text-sm text-red-700">Заказ отменён</p>
                          ) : (
                            <ol className="space-y-1">
                              {(isDeliveryOrder
                                ? ['pending', 'confirmed', 'preparing', 'ready', 'delivering', 'delivered', 'completed']
                                : ['pending', 'confirmed', 'preparing', 'ready', 'completed']
                              ).map((step, idx, arr) => {
                                const currentIndex = Math.max(0, arr.indexOf(statusStr));
                                const done = idx < currentIndex;
                                const current = idx === currentIndex;
                                const dotClass = done ? 'bg-green-500' : current ? 'bg-primary-600' : 'bg-gray-300';
                                const textClass = idx <= currentIndex ? 'text-gray-900' : 'text-gray-500';
                                const ts =
                                  step === 'preparing'
                                    ? order.preparingAt
                                    : step === 'ready'
                                      ? order.readyAt
                                      : step === 'delivered'
                                        ? order.deliveredAt
                                        : null;
                                return (
                                  <li key={step} className="flex items-center justify-between gap-3">
                                    <div className="flex items-center gap-2 min-w-0">
                                      <span className={`h-2.5 w-2.5 rounded-full ${dotClass}`} />
                                      <span className={`text-sm truncate ${textClass}`}>
                                        {getOrderStatusLabel(step)}
                                      </span>
                                    </div>
                                    {ts ? (
                                      <span className="text-xs text-gray-500 whitespace-nowrap">{formatDate(ts)}</span>
                                    ) : null}
                                  </li>
                                );
                              })}
                            </ol>
                          )}
                        </div>
                      )}
                      {!isClientUser && order.preparingAt && (
                        <p>
                          <strong>Начало приготовления:</strong> {formatDate(order.preparingAt)}
                        </p>
                      )}
                      {!isClientUser && order.readyAt && (
                        <p>
                          <strong>Готово:</strong> {formatDate(order.readyAt)}
                        </p>
                      )}
                      {!isClientUser && order.deliveredAt && (
                        <p>
                          <strong>Доставлено:</strong> {formatDate(order.deliveredAt)}
                        </p>
                      )}
                      {isClient() && review && (
                        <p>
                          <strong>Отзыв:</strong> {'⭐'.repeat(review.rating || 0)}{' '}
                          <span className="text-sm text-gray-500">
                            {review.createdAt ? `(${formatDate(review.createdAt)})` : ''}
                          </span>
                        </p>
                      )}
                      <div>
                        <p className="font-semibold">Позиции:</p>
                        {items ? (
                          items.length > 0 ? (
                            <ul className="text-sm text-gray-700 space-y-1">
                              {items.map((item) => (
                                <li key={item.id ?? `${item.menuItemId}-${item.unitPrice}`}>
                                  {item.name || `Блюдо #${item.menuItemId}`} — {item.quantity} ×{' '}
                                  {formatCurrency(item.unitPrice || 0)}
                                  {item.note ? ` (${item.note})` : ''}
                                </li>
                              ))}
                            </ul>
                          ) : (
                            <p className="text-sm text-gray-500">—</p>
                          )
                        ) : (
                          <p className="text-sm text-gray-500">загрузка...</p>
                        )}
                      </div>
                      {(isCashier() || isManager()) && (
                        <>
                          <p>
                            <strong>Создатель:</strong>{' '}
                            {order.createdByEmployeeId ? `сотрудник #${order.createdByEmployeeId}` : 'клиент'}
                          </p>
                        </>
                      )}
                    </div>
                    {(canEmployeeProcessPayment ||
                      canClientPayOnline ||
                      canEmployeeConfirm ||
                      canEmployeeCancel ||
                      canClientCancel ||
                      canLeaveReview ||
                      canUpdateStatus) && (
                      <div className="mt-4 space-y-2">
                        {nextStepHint ? (
                          <p className="text-sm text-gray-600">
                            Следующий шаг: <span className="font-medium">{nextStepHint}</span>
                          </p>
                        ) : null}
                        {canUpdateStatus && courierRequiredNow && !hasCourier ? (
                          <p className="text-sm text-red-600">Передать в доставку нельзя: назначьте курьера.</p>
                        ) : null}
                        <div className="flex flex-wrap gap-2">
                          {paymentCheckInProgress && (
                            <Button disabled variant="outline">
                              Проверяем оплату...
                            </Button>
                          )}
                          {canEmployeeProcessPayment && (
                            <Button
                              onClick={() => {
                                setSelectedOrder(order);
                                setPaymentMethod(
                                  order.paymentMethod === PaymentMethod.Cash ||
                                    order.paymentMethod === PaymentMethod.Card
                                    ? order.paymentMethod
                                    : PaymentMethod.Card
                                );
                                setShowPaymentDialog(true);
                              }}
                            >
                              Обработать платеж
                            </Button>
                          )}
                          {canClientPayOnline && (
                            <Button onClick={() => openClientPaymentDialog(order)}>Оплатить онлайн</Button>
                          )}
                          {canEmployeeConfirm && (
                            <Button
                              variant="outline"
                              onClick={() => handleUpdateStatus(order.id!, OrderStatus.Confirmed)}
                            >
                              Подтвердить
                            </Button>
                          )}
                          {canEmployeeCancel && (
                            <Button variant="destructive" onClick={() => openEmployeeCancel(order)}>
                              Отменить
                            </Button>
                          )}
                          {canClientCancel && (
                            <Button
                              variant="destructive"
                              onClick={() => handleUpdateStatus(order.id!, OrderStatus.Cancelled)}
                            >
                              Отменить
                            </Button>
                          )}
                          {canLeaveReview && (
                            <Button variant="outline" onClick={() => openReviewDialog(order.id!)}>
                              Оставить отзыв
                            </Button>
                          )}
                          {canUpdateStatus && statusStr === 'ready' && (
                            <Button
                              variant="outline"
                              disabled={isDeliveryOrder && !hasCourier}
                              title={
                                isDeliveryOrder && !hasCourier
                                  ? 'Назначьте курьера перед передачей в доставку'
                                  : undefined
                              }
                              onClick={() =>
                                handleUpdateStatus(
                                  order.id!,
                                  isDeliveryOrder ? OrderStatus.Delivering : OrderStatus.Completed
                                )
                              }
                            >
                              {isDeliveryOrder ? 'Передать в доставку' : 'Отметить как выдан'}
                            </Button>
                          )}
                          {canUpdateStatus && isDeliveryOrder && statusStr === 'delivering' && (
                            <Button
                              variant="outline"
                              onClick={() => handleUpdateStatus(order.id!, OrderStatus.Delivered)}
                            >
                              Отметить как доставлен
                            </Button>
                          )}
                          {canCloseDeliveryOrder && (
                            <Button
                              variant="outline"
                              onClick={() => handleUpdateStatus(order.id!, OrderStatus.Completed)}
                            >
                              Закрыть заказ
                            </Button>
                          )}
                        </div>
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })
          )}
        </div>
      </div>

      {showPaymentDialog && selectedOrder && (
        <Dialog
          open={showPaymentDialog}
          onOpenChange={(open) => {
            if (!open) {
              setShowPaymentDialog(false);
              setSelectedOrder(null);
              setAmountReceived('');
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Обработка платежа</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <p>
                <strong>Заказ #{selectedOrder.id}</strong>
              </p>
              <p>Сумма: {formatCurrency(selectedOrder.totalAmount!)}</p>
              <div>
                <label className="block text-sm font-medium mb-1">Способ оплаты</label>
                <select
                  className="w-full h-10 rounded-md border border-gray-300 px-3"
                  value={paymentMethod}
                  onChange={(e) => {
                    setPaymentMethod(e.target.value as PaymentMethod);
                    setAmountReceived('');
                  }}
                >
                  {isClient() ? (
                    <option value={PaymentMethod.Online}>Онлайн</option>
                  ) : selectedOrder.paymentMethod === PaymentMethod.Cash ? (
                    <option value={PaymentMethod.Cash}>Наличные</option>
                  ) : selectedOrder.paymentMethod === PaymentMethod.Card ? (
                    <option value={PaymentMethod.Card}>Карта</option>
                  ) : (
                    <>
                      <option value={PaymentMethod.Card}>Карта</option>
                      <option value={PaymentMethod.Cash}>Наличные</option>
                    </>
                  )}
                </select>
              </div>
              {paymentMethod === PaymentMethod.Cash && (
                <div>
                  <label className="block text-sm font-medium mb-1">Полученная сумма</label>
                  <Input
                    type="number"
                    step="0.01"
                    value={amountReceived}
                    onChange={(e) => setAmountReceived(e.target.value)}
                    placeholder="Введите сумму"
                  />
                </div>
              )}
              <div className="flex gap-2 justify-end">
                <Button
                  variant="outline"
                  onClick={() => {
                    setShowPaymentDialog(false);
                    setSelectedOrder(null);
                    setAmountReceived('');
                  }}
                >
                  Отмена
                </Button>
                <Button onClick={handleProcessPayment}>Обработать</Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}

      <ConfirmDialog
        open={employeeCancelDialogOpen}
        onOpenChange={(open) => {
          if (!open && employeeCancelSubmitting) return;
          setEmployeeCancelDialogOpen(open);
          if (!open) {
            setEmployeeCancelOrder(null);
          }
        }}
        title={`Отменить заказ #${employeeCancelOrder?.id ?? '—'}?`}
        description={
          employeeCancelOrder
            ? `Заказ будет отменён. Действие нельзя отменить. Сумма: ${formatCurrency(employeeCancelOrder.totalAmount || 0)}.`
            : 'Заказ будет отменён. Действие нельзя отменить.'
        }
        confirmText={employeeCancelSubmitting ? 'Отмена…' : 'Отменить'}
        cancelText="Назад"
        confirmVariant="destructive"
        confirmDisabled={employeeCancelSubmitting || !employeeCancelOrder?.id}
        onConfirm={confirmEmployeeCancel}
      />

      <Dialog
        open={clientPayDialogOpen}
        onOpenChange={(open) => {
          if (!open && !clientPaySubmitting) {
            closeClientPaymentDialog();
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Оплата заказа #{clientPayOrder?.id ?? '—'}</DialogTitle>
            <DialogDescription>
              Для онлайн-оплаты вы будете перенаправлены на страницу банка (3-D Secure). Если оплата не проходит —
              попробуйте ещё раз, выберите оплату при получении или отмените заказ.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <p>
              <strong>Сумма:</strong> {formatCurrency(clientPayOrder?.totalAmount || 0)}
            </p>

            <div>
              <label className="block text-sm font-medium mb-1">Способ оплаты</label>
              <Select
                value={clientPayMethod}
                onChange={(e) => {
                  setClientPayMethod(e.target.value as PaymentMethod);
                  setClientPayError(null);
                }}
                disabled={clientPaySubmitting}
              >
                <option value={PaymentMethod.Online}>Онлайн</option>
                <option value={PaymentMethod.Cash}>При получении (наличные)</option>
                <option value={PaymentMethod.Card}>При получении (карта)</option>
              </Select>
              {clientPayMethod !== PaymentMethod.Online && (
                <p className="mt-1 text-xs text-gray-500">
                  Оплата при получении: оплатите курьеру/кассиру, когда заказ будет доставлен/выдан.
                </p>
              )}
            </div>

            {clientPayMethod === PaymentMethod.Online && (
              <div className="space-y-2">
                <Input
                  placeholder="Номер карты"
                  value={clientCardData.number}
                  onChange={(e) =>
                    setClientCardData((prev) => ({ ...prev, number: formatCardNumberInput(e.target.value) }))
                  }
                  disabled={clientPaySubmitting}
                  inputMode="numeric"
                  autoComplete="cc-number"
                  maxLength={19}
                />
                <div className="flex gap-2">
                  <Input
                    placeholder="MM/YY"
                    value={clientCardData.expiry}
                    onChange={(e) =>
                      setClientCardData((prev) => ({ ...prev, expiry: formatExpiryInput(e.target.value) }))
                    }
                    disabled={clientPaySubmitting}
                    inputMode="numeric"
                    autoComplete="cc-exp"
                    maxLength={5}
                  />
                  <Input
                    placeholder="CVV"
                    value={clientCardData.cvv}
                    onChange={(e) =>
                      setClientCardData((prev) => ({ ...prev, cvv: e.target.value.replace(/\D/g, '').slice(0, 4) }))
                    }
                    disabled={clientPaySubmitting}
                    inputMode="numeric"
                    autoComplete="cc-csc"
                    maxLength={4}
                  />
                </div>
                <p className="text-xs text-gray-500">
                  Для симуляции отказа платежа введите номер карты 0000 0000 0000 0000 или неверный код подтверждения.
                  Тестовый код 3-D Secure: 123456.
                </p>
              </div>
            )}

            {clientPayError && <p className="text-sm text-red-600">{clientPayError}</p>}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeClientPaymentDialog} disabled={clientPaySubmitting}>
              Закрыть
            </Button>
            <Button
              variant="destructive"
              onClick={cancelClientPayOrder}
              disabled={clientPaySubmitting || !clientPayOrder?.id}
            >
              Отменить заказ
            </Button>
            <Button onClick={submitClientPaymentOrSwitch} disabled={clientPaySubmitting || !clientPayOrder?.id}>
              {clientPaySubmitting
                ? clientPayMethod === PaymentMethod.Online
                  ? 'Оплата...'
                  : 'Сохранение...'
                : clientPayMethod === PaymentMethod.Online
                  ? 'Оплатить'
                  : 'Сохранить'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={reviewDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            setReviewDialogOpen(false);
            setReviewOrderId(null);
            setReviewComment('');
            setReviewError(null);
          } else {
            setReviewDialogOpen(true);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Оставить отзыв</DialogTitle>
            <DialogDescription>Отзыв доступен после завершения заказа</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <p>
              <strong>Заказ #{reviewOrderId ?? '—'}</strong>
            </p>
            <div>
              <label className="block text-sm font-medium mb-1">Оценка</label>
              <div className="flex gap-2">
                {[1, 2, 3, 4, 5].map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setReviewRating(r)}
                    className={`w-10 h-10 rounded-full ${r <= reviewRating ? 'bg-yellow-400' : 'bg-gray-200'}`}
                    aria-label={`Оценка ${r}`}
                  >
                    ⭐
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Комментарий</label>
              <Textarea
                value={reviewComment}
                onChange={(e) => setReviewComment(e.target.value)}
                placeholder="Что понравилось? Что можно улучшить? (необязательно)"
              />
            </div>
            {reviewError && <p className="text-sm text-red-600">{reviewError}</p>}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setReviewDialogOpen(false)} disabled={reviewSubmitting}>
              Отмена
            </Button>
            <Button onClick={submitReview} disabled={reviewSubmitting || !reviewOrderId}>
              {reviewSubmitting ? 'Отправка…' : 'Отправить'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
