import { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('rounded-lg border border-dashed border-gray-300 bg-white px-6 py-10 text-center', className)}>
      <div className="text-lg font-semibold text-gray-900">{title}</div>
      {description ? <div className="mt-1 text-sm text-gray-600">{description}</div> : null}
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
    </div>
  );
}
