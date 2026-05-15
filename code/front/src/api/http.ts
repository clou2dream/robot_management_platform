import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig
} from "axios";
import { useAuthStore, getAccessToken } from "../stores/authStore";
import type { LoginResponse } from "../types/auth";

const http: AxiosInstance = axios.create({
  baseURL: "/api",
  timeout: 15000,
  withCredentials: true
});

let refreshPromise: Promise<string> | null = null;

export const refreshAccessToken = async () => {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<LoginResponse>("/api/auth/refresh", undefined, {
        withCredentials: true
      })
      .then((response) => {
        const { accessToken, currentUser } = response.data;
        useAuthStore.getState().setSession(accessToken, currentUser);
        return accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
};

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const response = error.response;
    const originalRequest = error.config as
      | (InternalAxiosRequestConfig & { _retry?: boolean })
      | undefined;

    if (response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const token = await refreshAccessToken();
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return http(originalRequest);
      } catch (refreshError) {
        useAuthStore.getState().clearAuth();
        window.location.assign("/login");
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export { http };
