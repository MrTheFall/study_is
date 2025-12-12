import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';
import { Configuration } from './generated/configuration';
import * as api from './generated';

const resolveApiBaseUrl = () => {
  if (typeof window !== 'undefined') {
    const { protocol, hostname } = window.location;
    const port = import.meta.env.VITE_API_PORT || '13228';
    const portSegment = port ? `:${port}` : '';
    return `${protocol}//${hostname}${portSegment}`;
  }

  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }

  return 'http://localhost:13228';
};

const API_BASE_URL = resolveApiBaseUrl();

const axiosInstance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

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

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

const configuration = new Configuration({
  basePath: API_BASE_URL,
  accessToken: () => localStorage.getItem('token') || '',
});
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
