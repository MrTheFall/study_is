import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useAuthStore } from './store/authStore';
import { authApi } from './api/client';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { HomePage } from './pages/HomePage';
import { MenuPage } from './pages/MenuPage';
import { OrdersPage } from './pages/OrdersPage';
import { ReviewsPage } from './pages/ReviewsPage';
import { KitchenPage } from './pages/KitchenPage';
import { InventoryPage } from './pages/InventoryPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { ShiftsPage } from './pages/ShiftsPage';
import { GetCurrentUser200ResponseUserTypeEnum } from './api/generated/api';

function App() {
  const { token, setAuth, isAuthenticated } = useAuthStore();

  useEffect(() => {
    // Проверяем токен при загрузке приложения
    const storedUser = localStorage.getItem('user');
    if (token && storedUser && !isAuthenticated) {
      try {
        const user = JSON.parse(storedUser);
        setAuth(token, user);
      } catch {
        // Если не удалось распарсить, запрашиваем заново
        authApi.getCurrentUser()
          .then((response) => {
            setAuth(token, response.data);
          })
          .catch(() => {
            useAuthStore.getState().clearAuth();
          });
      }
    }
  }, [token, isAuthenticated, setAuth]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <HomePage />
            </ProtectedRoute>
          }
        />
        <Route path="/menu" element={<MenuPage />} />
        <Route
          path="/orders"
          element={
            <ProtectedRoute>
              <OrdersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/reviews"
          element={
            <ProtectedRoute
              allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Client]}
            >
              <ReviewsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/kitchen"
          element={
            <ProtectedRoute
              allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
            >
              <KitchenPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/inventory"
          element={
            <ProtectedRoute
              allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
            >
              <InventoryPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/employees"
          element={
            <ProtectedRoute requireManager>
              <EmployeesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/analytics"
          element={
            <ProtectedRoute requireManager>
              <AnalyticsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/shifts"
          element={
            <ProtectedRoute
              allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
            >
              <ShiftsPage />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
