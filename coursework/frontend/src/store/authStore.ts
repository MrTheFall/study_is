import { create } from 'zustand';
import { GetCurrentUser200Response, GetCurrentUser200ResponseUserTypeEnum } from '@/api/generated/api';

interface AuthState {
  token: string | null;
  user: GetCurrentUser200Response | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: GetCurrentUser200Response) => void;
  clearAuth: () => void;
  isClient: () => boolean;
  isEmployee: () => boolean;
  isManager: () => boolean;
  isCashier: () => boolean;
  isCook: () => boolean;
}

const getStoredUser = () => {
  try {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  } catch {
    return null;
  }
};

export const useAuthStore = create<AuthState>((set, get) => ({
  token: localStorage.getItem('token'),
  user: getStoredUser(),
  isAuthenticated: !!localStorage.getItem('token'),
  setAuth: (token: string, user: GetCurrentUser200Response) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    set({ token, user, isAuthenticated: true });
  },
  clearAuth: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ token: null, user: null, isAuthenticated: false });
  },
      isClient: () => {
        const user = get().user;
        return user?.userType === GetCurrentUser200ResponseUserTypeEnum.Client;
      },
      isEmployee: () => {
        const user = get().user;
        return user?.userType === GetCurrentUser200ResponseUserTypeEnum.Employee;
      },
  isManager: () => {
    const user = get().user;
    return user?.role === 'Manager' || user?.role === 'MANAGER';
  },
  isCashier: () => {
    const user = get().user;
    return user?.role === 'Cashier' || user?.role === 'CASHIER';
  },
  isCook: () => {
    const user = get().user;
    return user?.role === 'Cook' || user?.role === 'COOK';
  },
}));

