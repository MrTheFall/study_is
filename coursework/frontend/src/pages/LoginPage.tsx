import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { authApi } from '@/api/client';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';

const clientLoginSchema = z.object({
  email: z.string().email('Неверный формат email'),
  password: z.string().min(1, 'Пароль обязателен'),
});

const employeeLoginSchema = z.object({
  login: z.string().min(1, 'Логин обязателен'),
  password: z.string().min(1, 'Пароль обязателен'),
});

type ClientLoginForm = z.infer<typeof clientLoginSchema>;
type EmployeeLoginForm = z.infer<typeof employeeLoginSchema>;

export function LoginPage() {
  const [isClient, setIsClient] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const clientForm = useForm<ClientLoginForm>({
    resolver: zodResolver(clientLoginSchema),
  });

  const employeeForm = useForm<EmployeeLoginForm>({
    resolver: zodResolver(employeeLoginSchema),
  });

  const onClientSubmit = async (data: ClientLoginForm) => {
    try {
      setError(null);
      const response = await authApi.loginClient({ email: data.email, password: data.password });
      const token = response.data.token!;
      
      if (!token) {
        setError('Токен не получен');
        return;
      }
      
      localStorage.setItem('token', token);
      
      const tempUser = {
        userId: undefined,
        username: data.email,
        userType: 'CLIENT' as any,
        role: null,
      };
      
      setAuth(token, tempUser);
      navigate('/', { replace: true });
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка входа');
      localStorage.removeItem('token');
    }
  };

  const onEmployeeSubmit = async (data: EmployeeLoginForm) => {
    try {
      setError(null);
      const response = await authApi.loginEmployee({ login: data.login, password: data.password });
      const token = response.data.token!;
      
      if (!token) {
        setError('Токен не получен');
        return;
      }
      
      localStorage.setItem('token', token);
      
      try {
        const userResponse = await authApi.getCurrentUser();
        setAuth(token, userResponse.data);
      } catch (err) {
        console.error('Failed to load user info:', err);
        const tempUser = {
          userId: undefined,
          username: data.login,
          userType: 'EMPLOYEE' as any,
          role: null,
        };
        setAuth(token, tempUser);
      }
      
      setTimeout(() => {
        navigate('/', { replace: true });
      }, 100);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка входа');
      localStorage.removeItem('token');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Вход в систему</CardTitle>
          <CardDescription>
            Войдите как {isClient ? 'клиент' : 'сотрудник'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex gap-2 mb-6">
            <Button
              variant={isClient ? 'default' : 'outline'}
              onClick={() => setIsClient(true)}
              className="flex-1"
            >
              Клиент
            </Button>
            <Button
              variant={!isClient ? 'default' : 'outline'}
              onClick={() => setIsClient(false)}
              className="flex-1"
            >
              Сотрудник
            </Button>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-md">
              {error}
            </div>
          )}

          {isClient ? (
            <form onSubmit={clientForm.handleSubmit(onClientSubmit)} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Email</label>
                <Input
                  type="email"
                  {...clientForm.register('email')}
                  placeholder="email@example.com"
                />
                {clientForm.formState.errors.email && (
                  <p className="text-red-500 text-sm mt-1">
                    {clientForm.formState.errors.email.message}
                  </p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Пароль</label>
                <Input
                  type="password"
                  {...clientForm.register('password')}
                  placeholder="••••••••"
                />
                {clientForm.formState.errors.password && (
                  <p className="text-red-500 text-sm mt-1">
                    {clientForm.formState.errors.password.message}
                  </p>
                )}
              </div>
              <Button type="submit" className="w-full">
                Войти
              </Button>
            </form>
          ) : (
            <form onSubmit={employeeForm.handleSubmit(onEmployeeSubmit)} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Логин</label>
                <Input
                  {...employeeForm.register('login')}
                  placeholder="Введите логин"
                />
                {employeeForm.formState.errors.login && (
                  <p className="text-red-500 text-sm mt-1">
                    {employeeForm.formState.errors.login.message}
                  </p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Пароль</label>
                <Input
                  type="password"
                  {...employeeForm.register('password')}
                  placeholder="••••••••"
                />
                {employeeForm.formState.errors.password && (
                  <p className="text-red-500 text-sm mt-1">
                    {employeeForm.formState.errors.password.message}
                  </p>
                )}
              </div>
              <Button type="submit" className="w-full">
                Войти
              </Button>
            </form>
          )}

          <div className="mt-4 space-y-2">
            <div className="text-center">
              <a href="/register" className="text-sm text-primary-600 hover:underline">
                Нет аккаунта? Зарегистрироваться
              </a>
            </div>
            <div className="text-center">
              <Button
                variant="outline"
                onClick={() => navigate('/menu')}
                className="w-full"
              >
                Посмотреть меню без входа
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

