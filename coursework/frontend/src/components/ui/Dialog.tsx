import { HTMLAttributes, ReactNode, forwardRef, useEffect, useId, useRef } from 'react';
import { cn } from '@/lib/utils';

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}

interface DialogContentProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

const openDialogIds: string[] = [];

function isFocusable(el: HTMLElement) {
  if (el.hasAttribute('disabled')) return false;
  if (el.getAttribute('aria-hidden') === 'true') return false;
  if (el instanceof HTMLInputElement && el.type === 'hidden') return false;
  if (el.tabIndex < 0) return false;
  if (el.getClientRects().length === 0) return false;
  return true;
}

function getFocusableElements(container: HTMLElement) {
  const candidates = Array.from(
    container.querySelectorAll<HTMLElement>('a[href],button,input,textarea,select,[tabindex]:not([tabindex="-1"])')
  );
  return candidates.filter(isFocusable);
}

function lockBodyScroll() {
  const body = document.body;
  const count = Number(body.dataset.dialogOpenCount || '0') + 1;
  body.dataset.dialogOpenCount = String(count);

  if (count === 1) {
    body.dataset.dialogPrevOverflow = body.style.overflow || '';
    body.style.overflow = 'hidden';
  }
}

function unlockBodyScroll() {
  const body = document.body;
  const count = Math.max(0, Number(body.dataset.dialogOpenCount || '1') - 1);

  if (count === 0) {
    body.style.overflow = body.dataset.dialogPrevOverflow || '';
    delete body.dataset.dialogPrevOverflow;
    delete body.dataset.dialogOpenCount;
    return;
  }

  body.dataset.dialogOpenCount = String(count);
}

const Dialog = ({ open, onOpenChange, children }: DialogProps) => {
  const id = useId();

  useEffect(() => {
    if (!open) return;

    openDialogIds.push(id);
    lockBodyScroll();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (openDialogIds[openDialogIds.length - 1] !== id) return;
      onOpenChange(false);
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      const idx = openDialogIds.lastIndexOf(id);
      if (idx !== -1) {
        openDialogIds.splice(idx, 1);
      }
      unlockBodyScroll();
    };
  }, [open, onOpenChange]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/50" onClick={() => onOpenChange(false)} aria-hidden="true" />
      <div className="relative z-50">{children}</div>
    </div>
  );
};

const DialogContent = forwardRef<HTMLDivElement, DialogContentProps>(
  ({ className, children, onKeyDown, ...props }, forwardedRef) => {
    const contentRef = useRef<HTMLDivElement | null>(null);
    const previousActiveElementRef = useRef<HTMLElement | null>(null);

    useEffect(() => {
      const node = contentRef.current;
      if (!node) return;

      previousActiveElementRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;

      requestAnimationFrame(() => {
        const current = contentRef.current;
        if (!current) return;

        const focusable = getFocusableElements(current);
        const target = focusable[0] ?? current;
        target.focus({ preventScroll: true });
      });

      return () => {
        const previous = previousActiveElementRef.current;
        if (previous && document.contains(previous)) {
          previous.focus({ preventScroll: true });
        }
      };
    }, []);

    const setRefs = (node: HTMLDivElement | null) => {
      contentRef.current = node;
      if (!forwardedRef) return;
      if (typeof forwardedRef === 'function') {
        forwardedRef(node);
        return;
      }
      forwardedRef.current = node;
    };

    return (
      <div
        ref={setRefs}
        tabIndex={-1}
        className={cn('bg-white rounded-lg shadow-lg p-6 w-full max-w-md mx-4', className)}
        role="dialog"
        aria-modal="true"
        onKeyDown={(event) => {
          onKeyDown?.(event);
          if (event.defaultPrevented) return;

          if (event.key !== 'Tab') return;

          const node = contentRef.current;
          if (!node) return;
          const focusable = getFocusableElements(node);
          if (focusable.length === 0) {
            event.preventDefault();
            return;
          }

          const first = focusable[0];
          const last = focusable[focusable.length - 1];
          const active = document.activeElement instanceof HTMLElement ? document.activeElement : null;

          if (event.shiftKey) {
            if (!active || active === first || !node.contains(active)) {
              event.preventDefault();
              last.focus();
            }
            return;
          }

          if (!active || active === last || !node.contains(active)) {
            event.preventDefault();
            first.focus();
          }
        }}
        {...props}
      >
        {children}
      </div>
    );
  }
);
DialogContent.displayName = 'DialogContent';

const DialogHeader = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('flex flex-col space-y-1.5 text-center sm:text-left mb-4', className)} {...props} />
));
DialogHeader.displayName = 'DialogHeader';

const DialogTitle = forwardRef<HTMLHeadingElement, HTMLAttributes<HTMLHeadingElement>>(
  ({ className, ...props }, ref) => (
    <h2 ref={ref} className={cn('text-lg font-semibold leading-none tracking-tight', className)} {...props} />
  )
);
DialogTitle.displayName = 'DialogTitle';

const DialogDescription = forwardRef<HTMLParagraphElement, HTMLAttributes<HTMLParagraphElement>>(
  ({ className, ...props }, ref) => <p ref={ref} className={cn('text-sm text-gray-500', className)} {...props} />
);
DialogDescription.displayName = 'DialogDescription';

const DialogFooter = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div
    ref={ref}
    className={cn('flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 mt-4', className)}
    {...props}
  />
));
DialogFooter.displayName = 'DialogFooter';

export { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter };
