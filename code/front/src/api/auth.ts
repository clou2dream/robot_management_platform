import { http } from "./http";
import type { CurrentUser, LoginRequest, LoginResponse } from "../types/auth";

export const login = async (payload: LoginRequest) => {
  const response = await http.post<LoginResponse>("/auth/login", payload);
  return response.data;
};

export const logout = async () => {
  await http.post("/auth/logout");
};

export const getCurrentUser = async () => {
  const response = await http.get<CurrentUser>("/auth/me");
  return response.data;
};
