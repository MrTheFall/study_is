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
import { PaymentsPage } from './pages/PaymentsPage';
import { ShiftsPage } from './pages/ShiftsPage';
import { GetCurrentUser200ResponseUserTypeEnum } from './api/generated/api';

function App() {
  const { setAuth } = useAuthStore();

  useEffect(() => {
    const loadUserInfo = async (token: string) => {
      try {
        const response = await authApi.getCurrentUser();
        setAuth(token, response.data);
      } catch (err) {
        useAuthStore.getState().clearAuth();
      }
    };

    const storedToken = localStorage.getItem('token');
    const storedUser = localStorage.getItem('user');
    const currentUser = useAuthStore.getState().user;
    
    if (storedToken) {
      if (!currentUser) {
        if (storedUser) {
          try {
            const user = JSON.parse(storedUser);
            if (user.username && user.role !== null && user.role !== undefined) {
              setAuth(storedToken, user);
            } else {
              loadUserInfo(storedToken);
            }
          } catch {
            loadUserInfo(storedToken);
          }
        } else {
          loadUserInfo(storedToken);
        }
      } else if (currentUser && (!currentUser.role || currentUser.role === null)) {
        loadUserInfo(storedToken);
      }
    }
  }, [setAuth]);

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
            <ProtectedRoute requireManager>
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
          path="/payments"
          element={
            <ProtectedRoute
              allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
            >
              <PaymentsPage />
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
