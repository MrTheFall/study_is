import axios from 'axios';

type ApiErrorShape = {
  code?: string;
  message?: string;
};

export function getApiErrorMessage(error: unknown, fallback = 'Произошла ошибка'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorShape | undefined;
    return data?.message || error.message || fallback;
  }

  if (error instanceof Error) {
    return error.message || fallback;
  }

  return fallback;
}
