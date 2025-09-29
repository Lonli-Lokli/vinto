'use client';

export interface TimerCallback {
  (): void;
}

export class TimerService {
  private intervals = new Map<string, NodeJS.Timeout>();

  startTimer(id: string, callback: TimerCallback, intervalMs = 1000): void {
    this.stopTimer(id); // Clear any existing timer with this ID

    const interval = setInterval(callback, intervalMs);
    this.intervals.set(id, interval);
  }

  stopTimer(id: string): boolean {
    const interval = this.intervals.get(id);
    if (interval) {
      clearInterval(interval);
      this.intervals.delete(id);
      return true;
    }
    return false;
  }

  stopAllTimers(): void {
    this.intervals.forEach((interval) => clearInterval(interval));
    this.intervals.clear();
  }

  hasTimer(id: string): boolean {
    return this.intervals.has(id);
  }
}

// Global timer service instance
export const timerService = new TimerService();