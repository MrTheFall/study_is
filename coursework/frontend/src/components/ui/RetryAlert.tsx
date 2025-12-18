import { ReactNode } from 'react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

export function RetryAlert({
  message,
  title = 'Ошибка',
  onRetry,
  retryLabel = 'Повторить',
  retryDisabled,
  className,
}: {
  message: ReactNode;
  title?: string;
  onRetry?: () => void;
  retryLabel?: string;
  retryDisabled?: boolean;
  className?: string;
}) {
  return (
    <Alert variant="error" title={title} className={cn(className)}>
      {onRetry ? (
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <span>{message}</span>
          <Button variant="outline" onClick={onRetry} disabled={retryDisabled}>
            {retryLabel}
          </Button>
        </div>
      ) : (
        message
      )}
    </Alert>
  );
}
