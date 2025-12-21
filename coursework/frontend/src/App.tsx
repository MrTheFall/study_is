import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useAuthStore } from './store/authStore';
import { authApi } from './api/client';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AppShell } from './components/AppShell';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { HomePage } from './pages/HomePage';
import { MenuPage } from './pages/MenuPage';
import { MenuManagementPage } from './pages/MenuManagementPage';
import { OrdersPage } from './pages/OrdersPage';
import { ReviewsPage } from './pages/ReviewsPage';
import { ReviewsManagementPage } from './pages/ReviewsManagementPage';
import { KitchenPage } from './pages/KitchenPage';
import { InventoryPage } from './pages/InventoryPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { CouriersPage } from './pages/CouriersPage';
import { PaymentsPage } from './pages/PaymentsPage';
import { PosPage } from './pages/PosPage';
import { PaymentResultPage } from './pages/PaymentResultPage';
import { ShiftsPage } from './pages/ShiftsPage';
import { ProfilePage } from './pages/ProfilePage';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { GetCurrentUser200ResponseUserTypeEnum } from './api/generated/api';

function App() {
  const { setAuth } = useAuthStore();

  useEffect(() => {
    const storedToken = localStorage.getItem('token');
    if (!storedToken) return;

    const currentUser = useAuthStore.getState().user;
    const needsReload =
      !currentUser ||
      !currentUser.userType ||
      (currentUser.userType === GetCurrentUser200ResponseUserTypeEnum.Employee && !currentUser.role);

    if (!needsReload) return;

    const loadUserInfo = async () => {
      try {
        const response = await authApi.getCurrentUser();
        setAuth(storedToken, response.data);
      } catch {
        useAuthStore.getState().clearAuth();
      }
    };

    loadUserInfo();
  }, [setAuth]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<AppShell />}>
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
            path="/menu/manage"
            element={
              <ProtectedRoute requireManager>
                <MenuManagementPage />
              </ProtectedRoute>
            }
          />
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
            path="/reviews/manage"
            element={
              <ProtectedRoute requireManager>
                <ReviewsManagementPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute
                allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Client]}
              >
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/kitchen"
            element={
              <ProtectedRoute
                allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
                allowedEmployeeRoles={['Cook', 'Manager']}
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
            path="/couriers"
            element={
              <ProtectedRoute requireManager>
                <CouriersPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/payments"
            element={
              <ProtectedRoute
                allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
                allowedEmployeeRoles={['Cashier', 'Manager']}
              >
                <PaymentsPage />
              </ProtectedRoute>
            }
          />
          <Route path="/payment/result" element={<PaymentResultPage />} />
          <Route
            path="/pos"
            element={
              <ProtectedRoute
                allowedRoles={[GetCurrentUser200ResponseUserTypeEnum.Employee]}
                allowedEmployeeRoles={['Cashier', 'Manager']}
              >
                <PosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/shifts"
            element={
              <ProtectedRoute requireManager>
                <ShiftsPage />
              </ProtectedRoute>
            }
          />
          <Route path="/forbidden" element={<ForbiddenPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
