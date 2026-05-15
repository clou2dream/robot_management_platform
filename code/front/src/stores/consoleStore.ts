import { create } from "zustand";

interface ConsoleState {
  collapsed: boolean;
  setCollapsed: (collapsed: boolean) => void;
}

export const useConsoleStore = create<ConsoleState>((set) => ({
  collapsed: false,
  setCollapsed: (collapsed) => set({ collapsed })
}));
