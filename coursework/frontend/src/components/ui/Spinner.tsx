import { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

type SpinnerSize = 'sm' | 'md' | 'lg';

const sizeClassName: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-5 w-5 border-2',
  lg: 'h-8 w-8 border-[3px]',
};

export function Spinner({
  size = 'md',
  className,
  ...props
}: { size?: SpinnerSize; className?: string } & Omit<HTMLAttributes<HTMLDivElement>, 'children'>) {
  return (
    <div
      role="status"
      aria-label="Загрузка"
      className={cn('animate-spin rounded-full border-gray-300 border-t-primary-600', sizeClassName[size], className)}
      {...props}
    />
  );
}
