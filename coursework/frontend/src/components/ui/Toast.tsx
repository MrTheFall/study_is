import { ReactNode, useCallback, useMemo, useRef, useState } from 'react';
import { cn } from '@/lib/utils';
import { ToastApi, ToastContext, ToastInput, ToastVariant } from './toast';

type ToastItem = {
  id: string;
  variant: ToastVariant;
  title?: string;
  message: string;
  durationMs: number;
};

type ResolvedToastInput = ToastInput & { durationMs: number };

const variantClassName: Record<ToastVariant, string> = {
  info: 'border-blue-200 bg-blue-50 text-blue-900',
  success: 'border-green-200 bg-green-50 text-green-900',
  warning: 'border-yellow-200 bg-yellow-50 text-yellow-900',
  error: 'border-red-200 bg-red-50 text-red-900',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timeoutsRef = useRef<Map<string, number>>(new Map());

  const remove = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
    const handle = timeoutsRef.current.get(id);
    if (handle) {
      window.clearTimeout(handle);
      timeoutsRef.current.delete(id);
    }
  }, []);

  const show = useCallback(
    (input: ToastInput) => {
      const id = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const resolved: ResolvedToastInput = { ...input, durationMs: input.durationMs ?? 4000 };
      const next: ToastItem = {
        id,
        variant: resolved.variant,
        title: resolved.title,
        message: resolved.message,
        durationMs: resolved.durationMs,
      };

      setToasts((prev) => [...prev, next].slice(-5));

      if (resolved.durationMs > 0) {
        const handle = window.setTimeout(() => remove(id), resolved.durationMs);
        timeoutsRef.current.set(id, handle);
      }
    },
    [remove]
  );

  const api = useMemo<ToastApi>(
    () => ({
      show,
      info: (message, options) => show({ variant: 'info', message, ...options }),
      success: (message, options) => show({ variant: 'success', message, ...options }),
      warning: (message, options) => show({ variant: 'warning', message, ...options }),
      error: (message, options) => show({ variant: 'error', message, ...options }),
    }),
    [show]
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4 sm:items-end">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            aria-live="polite"
            className={cn(
              'relative w-full max-w-sm rounded-lg border px-4 py-3 shadow-lg',
              variantClassName[toast.variant]
            )}
          >
            <button
              type="button"
              onClick={() => remove(toast.id)}
              className="absolute right-2 top-2 rounded p-1 text-gray-600 hover:bg-black/5"
              aria-label="Закрыть уведомление"
            >
              ×
            </button>
            {toast.title ? <div className="mb-0.5 font-semibold">{toast.title}</div> : null}
            <div className="text-sm">{toast.message}</div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
