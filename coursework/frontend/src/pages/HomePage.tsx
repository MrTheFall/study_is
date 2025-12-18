import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { LoadingState } from '@/components/ui/LoadingState';

export function HomePage() {
  const { user, isClient, isEmployee, isManager, isCashier, isCook } = useAuthStore();
  const navigate = useNavigate();

  if (!user) {
    return <LoadingState message="Загрузка..." />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Главная</h1>
        <p className="text-gray-600 mt-1">Выберите действие.</p>
      </div>

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
          <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/profile')}>
            <CardHeader>
              <CardTitle>Личный кабинет</CardTitle>
              <CardDescription>Профиль и смена пароля</CardDescription>
            </CardHeader>
          </Card>
        </div>
      )}

      {isEmployee() && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {isManager() && (
            <>
              <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/menu/manage')}>
                <CardHeader>
                  <CardTitle>Меню</CardTitle>
                  <CardDescription>Управление блюдами и доступностью</CardDescription>
                </CardHeader>
              </Card>
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
              <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/reviews/manage')}>
                <CardHeader>
                  <CardTitle>Отзывы</CardTitle>
                  <CardDescription>Просмотр обратной связи</CardDescription>
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
              <Card className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => navigate('/couriers')}>
                <CardHeader>
                  <CardTitle>Курьеры</CardTitle>
                  <CardDescription>Управление курьерами и доступностью</CardDescription>
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
  );
}
