import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { clientsApi } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';

const phonePattern = /^[+\d\s()-]+$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const countPhoneDigits = (value: string) => value.replace(/\D/g, '').length;
const sanitizePhoneInput = (value: string) => value.replace(/[^\d+()\s-]/g, '');
const sanitizeEmailInput = (value: string) => value.replace(/\s+/g, '');

const registerSchema = z.object({
  name: z.string().min(1, 'Имя обязательно'),
  phone: z
    .string()
    .min(1, 'Телефон обязателен')
    .refine((value) => phonePattern.test(value), 'Телефон должен содержать только цифры и символы +()-')
    .refine((value) => countPhoneDigits(value) >= 6, 'Телефон должен содержать минимум 6 цифр'),
  email: z
    .string()
    .trim()
    .min(1, 'Email обязателен')
    .email('Неверный формат email')
    .refine((value) => emailPattern.test(value), 'Email должен быть вида name@example.com'),
  password: z.string().min(6, 'Пароль должен быть не менее 6 символов'),
  defaultAddress: z.string().min(1, 'Адрес обязателен'),
});

type RegisterForm = z.infer<typeof registerSchema>;

export function RegisterPage() {
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const returnToParam = new URLSearchParams(location.search).get('returnTo');
  const returnTo = returnToParam && returnToParam.startsWith('/') ? returnToParam : '/';

  const form = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
  });
  const phoneRegister = form.register('phone', {
    onChange: (event) => {
      const sanitized = sanitizePhoneInput(event.target.value);
      if (sanitized !== event.target.value) {
        event.target.value = sanitized;
      }
    },
  });
  const emailRegister = form.register('email', {
    onChange: (event) => {
      const sanitized = sanitizeEmailInput(event.target.value);
      if (sanitized !== event.target.value) {
        event.target.value = sanitized;
      }
    },
  });

  const onSubmit = async (data: RegisterForm) => {
    try {
      setError(null);
      await clientsApi.registerClient({
        name: data.name,
        phone: data.phone,
        email: data.email,
        password: data.password,
        defaultAddress: data.defaultAddress,
      });
      setSuccess(true);
      setTimeout(() => {
        navigate(returnTo !== '/' ? `/login?returnTo=${encodeURIComponent(returnTo)}` : '/login');
      }, 2000);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка регистрации');
    }
  };

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <Card className="w-full max-w-md">
          <CardContent className="pt-6">
            <div className="text-center">
              <div className="text-green-600 text-5xl mb-4">✓</div>
              <h2 className="text-2xl font-bold mb-2">Регистрация успешна!</h2>
              <p className="text-gray-600">Перенаправление на страницу входа...</p>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4 py-12">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Регистрация</CardTitle>
          <CardDescription>Создайте аккаунт клиента</CardDescription>
        </CardHeader>
        <CardContent>
          {error && (
            <Alert variant="error" title="Ошибка" className="mb-4">
              {error}
            </Alert>
          )}

          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Имя</label>
              <Input {...form.register('name')} placeholder="Введите имя" />
              {form.formState.errors.name && (
                <p className="text-red-500 text-sm mt-1">{form.formState.errors.name.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Телефон</label>
              <Input {...phoneRegister} placeholder="+7 (999) 123-45-67" inputMode="tel" autoComplete="tel" />
              {form.formState.errors.phone && (
                <p className="text-red-500 text-sm mt-1">{form.formState.errors.phone.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Email</label>
              <Input
                type="email"
                {...emailRegister}
                placeholder="email@example.com"
                inputMode="email"
                autoComplete="email"
              />
              {form.formState.errors.email && (
                <p className="text-red-500 text-sm mt-1">{form.formState.errors.email.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Пароль</label>
              <Input type="password" {...form.register('password')} placeholder="••••••••" />
              {form.formState.errors.password && (
                <p className="text-red-500 text-sm mt-1">{form.formState.errors.password.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Адрес доставки</label>
              <Input {...form.register('defaultAddress')} placeholder="Введите адрес" />
              {form.formState.errors.defaultAddress && (
                <p className="text-red-500 text-sm mt-1">{form.formState.errors.defaultAddress.message}</p>
              )}
            </div>

            <Button type="submit" className="w-full">
              Зарегистрироваться
            </Button>
          </form>

          <div className="mt-4 text-center">
            <Link
              to={returnTo !== '/' ? `/login?returnTo=${encodeURIComponent(returnTo)}` : '/login'}
              className="text-sm text-primary-600 hover:underline"
            >
              Уже есть аккаунт? Войти
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
