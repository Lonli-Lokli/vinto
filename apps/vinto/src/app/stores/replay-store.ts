'use client';

import { makeAutoObservable } from 'mobx';
import { injectable } from 'tsyringe';
import { CommandData } from '../commands/command';

@injectable()
export class ReplayStore {
  isReplayMode = false;
  commands: CommandData[] = [];
  currentCommandIndex = -1; // -1 means initial state before any commands
  isPaused = true; // Always start paused in replay mode

  constructor() {
    makeAutoObservable(this);
  }

  // Start replay mode with a list of commands
  startReplay(commands: CommandData[]) {
    this.isReplayMode = true;
    this.commands = commands;
    this.currentCommandIndex = -1;
    this.isPaused = true;
  }

  // Exit replay mode
  exitReplay() {
    this.isReplayMode = false;
    this.commands = [];
    this.currentCommandIndex = -1;
    this.isPaused = true;
  }

  // Move to next command
  nextCommand(): CommandData | null {
    if (this.currentCommandIndex < this.commands.length - 1) {
      this.currentCommandIndex++;
      return this.commands[this.currentCommandIndex];
    }
    return null;
  }

  // Move to previous command (for undo functionality)
  previousCommand(): number {
    if (this.currentCommandIndex > -1) {
      this.currentCommandIndex--;
    }
    return this.currentCommandIndex;
  }

  // Jump to specific command index
  jumpToCommand(index: number) {
    if (index >= -1 && index < this.commands.length) {
      this.currentCommandIndex = index;
    }
  }

  // Getters
  get hasNextCommand(): boolean {
    return this.currentCommandIndex < this.commands.length - 1;
  }

  get hasPreviousCommand(): boolean {
    return this.currentCommandIndex > -1;
  }

  get currentCommand(): CommandData | null {
    if (this.currentCommandIndex >= 0 && this.currentCommandIndex < this.commands.length) {
      return this.commands[this.currentCommandIndex];
    }
    return null;
  }

  get progress(): { current: number; total: number } {
    return {
      current: this.currentCommandIndex + 1, // +1 because index starts at -1
      total: this.commands.length,
    };
  }

  reset() {
    this.isReplayMode = false;
    this.commands = [];
    this.currentCommandIndex = -1;
    this.isPaused = true;
  }
}
