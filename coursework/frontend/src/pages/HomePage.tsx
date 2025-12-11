import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { GetCurrentUser200ResponseUserTypeEnum } from '@/api/generated/api';
import { Button } from '@/components/ui/Button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';

export function HomePage() {
  const { user, isClient, isEmployee, isManager, isCashier, isCook, clearAuth } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  if (!user) {
    return <div>Загрузка...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center">
              <h1 className="text-2xl font-bold text-primary-600">Красти Крабс</h1>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {user.username} {user.userType === GetCurrentUser200ResponseUserTypeEnum.Client 
                  ? '(Клиент)' 
                  : `(Сотрудник: ${user.role === 'Manager' ? 'Менеджер' : user.role === 'Cashier' ? 'Кассир' : user.role === 'Cook' ? 'Повар' : user.role || 'Сотрудник'})`}
              </span>
              <Button variant="outline" onClick={handleLogout}>
                Выйти
              </Button>
            </div>
          </div>
        </div>
      </nav>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {isClient() && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/menu')}>
              <CardHeader>
                <CardTitle>Меню</CardTitle>
                <CardDescription>Просмотр меню и создание заказов</CardDescription>
              </CardHeader>
            </Card>
            <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/orders')}>
              <CardHeader>
                <CardTitle>Мои заказы</CardTitle>
                <CardDescription>История и статус заказов</CardDescription>
              </CardHeader>
            </Card>
            <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/reviews')}>
              <CardHeader>
                <CardTitle>Отзывы</CardTitle>
                <CardDescription>Оставить отзыв о заказе</CardDescription>
              </CardHeader>
            </Card>
          </div>
        )}

        {isEmployee() && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {isManager() && (
              <>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/employees')}>
                  <CardHeader>
                    <CardTitle>Сотрудники</CardTitle>
                    <CardDescription>Управление персоналом</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/analytics')}>
                  <CardHeader>
                    <CardTitle>Аналитика</CardTitle>
                    <CardDescription>Отчеты и статистика</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/payments')}>
                  <CardHeader>
                    <CardTitle>Платежи</CardTitle>
                    <CardDescription>Обработка платежей</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/inventory')}>
                  <CardHeader>
                    <CardTitle>Инвентарь</CardTitle>
                    <CardDescription>Управление складом</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/orders')}>
                  <CardHeader>
                    <CardTitle>Заказы</CardTitle>
                    <CardDescription>Управление заказами</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/shifts')}>
                  <CardHeader>
                    <CardTitle>Смены</CardTitle>
                    <CardDescription>Управление рабочими сменами</CardDescription>
                  </CardHeader>
                </Card>
              </>
            )}
            {isCashier() && (
              <>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/orders')}>
                  <CardHeader>
                    <CardTitle>Заказы</CardTitle>
                    <CardDescription>Просмотр и управление заказами</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/payments')}>
                  <CardHeader>
                    <CardTitle>Платежи</CardTitle>
                    <CardDescription>Обработка платежей</CardDescription>
                  </CardHeader>
                </Card>
              </>
            )}
            {isCook() && (
              <>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/kitchen')}>
                  <CardHeader>
                    <CardTitle>Кухня</CardTitle>
                    <CardDescription>Очередь заказов для кухни</CardDescription>
                  </CardHeader>
                </Card>
                <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/orders')}>
                  <CardHeader>
                    <CardTitle>Заказы</CardTitle>
                    <CardDescription>Просмотр заказов</CardDescription>
                  </CardHeader>
                </Card>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

