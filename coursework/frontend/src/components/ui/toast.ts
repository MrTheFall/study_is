import { createContext, useContext } from 'react';

export type ToastVariant = 'info' | 'success' | 'warning' | 'error';

export type ToastInput = {
  variant: ToastVariant;
  title?: string;
  message: string;
  durationMs?: number;
};

export type ToastApi = {
  show: (toast: ToastInput) => void;
  info: (message: string, options?: Omit<ToastInput, 'variant' | 'message'>) => void;
  success: (message: string, options?: Omit<ToastInput, 'variant' | 'message'>) => void;
  warning: (message: string, options?: Omit<ToastInput, 'variant' | 'message'>) => void;
  error: (message: string, options?: Omit<ToastInput, 'variant' | 'message'>) => void;
};

export const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return ctx;
}

