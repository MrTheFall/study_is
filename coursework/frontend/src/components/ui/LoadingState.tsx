import { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { Spinner } from '@/components/ui/Spinner';

export function LoadingState({
  message = 'Загрузка…',
  className,
  children,
}: {
  message?: string;
  className?: string;
  children?: ReactNode;
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center py-12 text-center', className)}>
      <Spinner className="mb-3" />
      <div className="text-sm text-gray-600">{message}</div>
      {children ? <div className="mt-4">{children}</div> : null}
    </div>
  );
}
