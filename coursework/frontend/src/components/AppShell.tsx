import { useMemo } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { GetCurrentUser200ResponseUserTypeEnum } from '@/api/generated/api';
import { useAuthStore } from '@/store/authStore';
import { cn } from '@/lib/utils';

type NavItem = {
  to: string;
  label: string;
  visible: boolean;
};

function roleLabel(role?: string | null) {
  if (!role) return 'Сотрудник';
  switch (role) {
    case 'Manager':
    case 'MANAGER':
      return 'Менеджер';
    case 'Cashier':
    case 'CASHIER':
      return 'Кассир';
    case 'Cook':
    case 'COOK':
      return 'Повар';
    default:
      return role;
  }
}

export function AppShell() {
  const navigate = useNavigate();
  const { user, isAuthenticated, clearAuth, isClient, isEmployee, isManager, isCashier, isCook } = useAuthStore();

  const items = useMemo<NavItem[]>(() => {
    const authed = isAuthenticated && !!user;
    const client = authed && isClient();
    const employee = authed && isEmployee();
    const manager = authed && isManager();
    const cashier = authed && isCashier();
    const cook = authed && isCook();

    return [
      { to: '/menu', label: 'Меню', visible: !employee },
      { to: '/', label: 'Главная', visible: authed },

      // Client
      { to: '/orders', label: 'Мои заказы', visible: client },
      { to: '/reviews', label: 'Отзывы', visible: client },
      { to: '/profile', label: 'Профиль', visible: client },

      // Cashier
      { to: '/pos', label: 'Касса', visible: cashier },
      { to: '/orders', label: 'Заказы', visible: cashier },
      { to: '/payments', label: 'История оплат', visible: cashier },

      // Cook
      { to: '/kitchen', label: 'Кухня', visible: cook },
      { to: '/orders', label: 'Заказы', visible: cook },

      // Manager
      { to: '/menu/manage', label: 'Управление меню', visible: manager },
      { to: '/employees', label: 'Сотрудники', visible: manager },
      { to: '/shifts', label: 'Смены', visible: manager },
      { to: '/inventory', label: 'Инвентарь', visible: manager },
      { to: '/couriers', label: 'Курьеры', visible: manager },
      { to: '/analytics', label: 'Аналитика', visible: manager },
      { to: '/reviews/manage', label: 'Отзывы (менеджер)', visible: manager },
      { to: '/pos', label: 'Касса', visible: manager },
      { to: '/orders', label: 'Заказы', visible: manager },
      { to: '/payments', label: 'История оплат', visible: manager },

      // Employee shared (fallback)
      { to: '/orders', label: 'Заказы', visible: employee && !manager && !cashier && !cook },
    ];
  }, [isAuthenticated, user, isClient, isEmployee, isManager, isCashier, isCook]);

  const userCaption = useMemo(() => {
    if (!user) return null;
    if (user.userType === GetCurrentUser200ResponseUserTypeEnum.Client) {
      return `${user.username} (Клиент)`;
    }
    return `${user.username} (${roleLabel(user.role)})`;
  }, [user]);

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="sticky top-0 z-40 border-b bg-white/95 backdrop-blur">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between gap-4">
            <NavLink
              to={isAuthenticated ? '/' : '/menu'}
              className="text-xl font-bold text-primary-600"
            >
              Красти Крабс
            </NavLink>

            <div className="flex items-center gap-3">
              {userCaption ? (
                <span className="hidden sm:inline text-sm text-gray-600">{userCaption}</span>
              ) : null}

              {isAuthenticated ? (
                <Button variant="outline" size="sm" onClick={handleLogout}>
                  Выйти
                </Button>
              ) : (
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" onClick={() => navigate('/login')}>
                    Войти
                  </Button>
                  <Button size="sm" onClick={() => navigate('/register')}>
                    Регистрация
                  </Button>
                </div>
              )}
            </div>
          </div>

          <nav className="pb-3 -mb-3">
            <div className="flex gap-2 overflow-x-auto">
              {items
                .filter((item) => item.visible)
                .map((item) => (
                  <NavLink
                    key={`${item.to}:${item.label}`}
                    to={item.to}
                    end={item.to === '/'}
                    className={({ isActive }) =>
                      cn(
                        'whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors',
                        isActive
                          ? 'bg-primary-50 text-primary-700'
                          : 'text-gray-700 hover:bg-gray-100 hover:text-gray-900'
                      )
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
            </div>
          </nav>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>
    </div>
  );
}
