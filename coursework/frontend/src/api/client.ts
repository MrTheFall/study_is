import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';
import { Configuration } from './generated/configuration';
import * as api from './generated';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:13228';

// Создаем axios instance с базовой конфигурацией
const axiosInstance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Интерцептор для добавления JWT токена
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Интерцептор для обработки ошибок
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Токен истек или невалиден
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Создаем конфигурацию для OpenAPI клиента
const configuration = new Configuration({
  basePath: API_BASE_URL,
  accessToken: () => localStorage.getItem('token') || '',
});

// Создаем API клиенты
// Используем axiosInstance напрямую через параметр axios
export const authApi = new api.AuthApi(configuration, API_BASE_URL, axiosInstance as any);
export const clientsApi = new api.ClientsApi(configuration, API_BASE_URL, axiosInstance as any);
export const menuApi = new api.MenuApi(configuration, API_BASE_URL, axiosInstance as any);
export const ordersApi = new api.OrdersApi(configuration, API_BASE_URL, axiosInstance as any);
export const paymentsApi = new api.PaymentsApi(configuration, API_BASE_URL, axiosInstance as any);
export const reviewsApi = new api.ReviewsApi(configuration, API_BASE_URL, axiosInstance as any);
export const inventoryApi = new api.InventoryApi(configuration, API_BASE_URL, axiosInstance as any);
export const employeesApi = new api.EmployeesApi(configuration, API_BASE_URL, axiosInstance as any);
export const shiftsApi = new api.ShiftsApi(configuration, API_BASE_URL, axiosInstance as any);
export const kitchenApi = new api.KitchenApi(configuration, API_BASE_URL, axiosInstance as any);
export const analyticsApi = new api.AnalyticsApi(configuration, API_BASE_URL, axiosInstance as any);

export { axiosInstance };
export type { AxiosRequestConfig };

