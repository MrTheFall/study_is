import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { GetCurrentUser200ResponseUserTypeEnum } from '@/api/generated/api';
import { LoadingState } from '@/components/ui/LoadingState';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: GetCurrentUser200ResponseUserTypeEnum[];
  allowedEmployeeRoles?: string[];
  requireManager?: boolean;
}

export function ProtectedRoute({ children, allowedRoles, allowedEmployeeRoles, requireManager }: ProtectedRouteProps) {
  const location = useLocation();
  const { isAuthenticated, user, isManager } = useAuthStore();
  const hasToken = localStorage.getItem('token');

  const returnTo = `${location.pathname}${location.search}`;
  const loginUrl = `/login?returnTo=${encodeURIComponent(returnTo)}`;
  const forbiddenUrl = `/forbidden?returnTo=${encodeURIComponent(returnTo)}`;

  if (!hasToken) {
    return <Navigate to={loginUrl} replace />;
  }

  if (!isAuthenticated) {
    return <Navigate to={loginUrl} replace />;
  }

  if (!user || !user.userType) {
    return <LoadingState message="Загрузка профиля..." />;
  }

  if (requireManager && !isManager()) {
    return <Navigate to={forbiddenUrl} replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.userType)) {
    return <Navigate to={forbiddenUrl} replace />;
  }

  if (allowedEmployeeRoles) {
    if (user.userType !== GetCurrentUser200ResponseUserTypeEnum.Employee) {
      return <Navigate to={forbiddenUrl} replace />;
    }
    if (!user.role || !allowedEmployeeRoles.includes(user.role)) {
      return <Navigate to={forbiddenUrl} replace />;
    }
  }

  return <>{children}</>;
}
