// components/di-provider.tsx
'use client';

import React, { createContext, useContext, useMemo } from 'react';
import { setupDIContainer, getInstance, isDIConfigured } from '../di/setup';
import { CardAnimationStore } from '../stores';
import { UIStore } from '../stores';
import { BugReportStore } from '../stores/bug-report-store';

/**
 * Store context for accessing DI-managed stores
 */
interface StoreContextValue {
  cardAnimationStore: CardAnimationStore;
  uiStore: UIStore;
  bugReportStore: BugReportStore;
}

const StoreContext = createContext<StoreContextValue | null>(null);

/**
 * DI Provider - Sets up dependency injection and provides stores to React tree
 */
export function DIProvider({ children }: { children: React.ReactNode }) {
  // Get store instances from DI container
  const stores = useMemo<StoreContextValue>(() => {
    // Only setup DI in browser environment

    if (!isDIConfigured()) {
      setupDIContainer();
    }

    return {
      cardAnimationStore: getInstance<CardAnimationStore>(CardAnimationStore),
      uiStore: getInstance<UIStore>(UIStore),
      bugReportStore: getInstance<BugReportStore>(BugReportStore),
    };
  }, []);

  return (
    <StoreContext.Provider value={stores}>{children}</StoreContext.Provider>
  );
}

/**
 * Hook to access stores from DI container
 */
export function useStores(): StoreContextValue {
  const context = useContext(StoreContext);

  if (!context) {
    throw new Error('useStores must be used within a DIProvider');
  }

  return context;
}

/**
 * Hook to access cardAnimationStore
 */
export function useCardAnimationStore(): CardAnimationStore {
  return useStores().cardAnimationStore;
}

/**
 * Hook to access uiStore
 */
export function useUIStore(): UIStore {
  return useStores().uiStore;
}

/**
 * Hook to access bugReportStore
 */
export function useBugReportStore(): BugReportStore {
  return useStores().bugReportStore;
}
