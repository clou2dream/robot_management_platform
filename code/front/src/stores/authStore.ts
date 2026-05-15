import { create } from "zustand";
import type { CurrentUser } from "../types/auth";

interface AuthState {
  accessToken: string | null;
  currentUser: CurrentUser | null;
  setAccessToken: (token: string | null) => void;
  setCurrentUser: (user: CurrentUser | null) => void;
  setSession: (token: string, user: CurrentUser) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  currentUser: null,
  setAccessToken: (accessToken) => set({ accessToken }),
  setCurrentUser: (currentUser) => set({ currentUser }),
  setSession: (accessToken, currentUser) => set({ accessToken, currentUser }),
  clearAuth: () => set({ accessToken: null, currentUser: null })
}));

export const getAccessToken = () => useAuthStore.getState().accessToken;
