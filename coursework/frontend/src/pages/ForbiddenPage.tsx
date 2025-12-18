import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { useAuthStore } from '@/store/authStore';

export function ForbiddenPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isAuthenticated, clearAuth } = useAuthStore();

  const { returnTo } = useMemo(() => {
    const search = new URLSearchParams(location.search);
    const raw = search.get('returnTo') || '';
    const safe = raw.startsWith('/') ? raw : '/';
    return { returnTo: safe };
  }, [location.search]);

  const goHome = () => {
    navigate(isAuthenticated ? '/' : '/menu', { replace: true });
  };

  const loginAsDifferent = () => {
    if (isAuthenticated) {
      clearAuth();
    }
    const params = new URLSearchParams();
    params.set('returnTo', returnTo);
    navigate(`/login?${params.toString()}`, { replace: true });
  };

  return (
    <div className="max-w-xl mx-auto">
      <Card>
        <CardHeader>
          <CardTitle>Нет доступа</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Alert variant="warning" title="Недостаточно прав">
            <div className="space-y-2">
              <div>У вас нет доступа к запрошенной странице.</div>
              {returnTo !== '/' ? (
                <div className="text-xs text-yellow-900/80">
                  Запрошено: <span className="font-mono">{returnTo}</span>
                </div>
              ) : null}
            </div>
          </Alert>

          <div className="flex flex-col sm:flex-row gap-2">
            <Button onClick={goHome}>На главную</Button>
            <Button variant="outline" onClick={loginAsDifferent}>
              Войти другим аккаунтом
            </Button>
            <Button variant="ghost" onClick={() => navigate('/menu')}>
              В меню
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

