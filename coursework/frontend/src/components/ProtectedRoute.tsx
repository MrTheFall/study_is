import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { GetCurrentUser200Response, GetCurrentUser200ResponseUserTypeEnum } from '@/api/generated/api';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: GetCurrentUser200ResponseUserTypeEnum[];
  requireManager?: boolean;
}

export function ProtectedRoute({ children, allowedRoles, requireManager }: ProtectedRouteProps) {
  const { isAuthenticated, user, isManager } = useAuthStore();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (requireManager && !isManager()) {
    return <Navigate to="/" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.userType)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}

