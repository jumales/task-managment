import type { TaskPhaseResponse, TaskPhaseName } from '../api/types';

/**
 * Formats a TaskPhaseName enum value into a human-readable string.
 * e.g. "IN_PROGRESS" → "In Progress"
 */
export function formatPhaseEnum(name: string): string {
  return name
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Returns the display label for a phase.
 * Uses customName when set; otherwise falls back to the formatted enum name.
 */
export function resolvePhaseLabel(phase: TaskPhaseResponse | null | undefined): string {
  if (!phase) return '—';
  if (phase.customName) return phase.customName;
  return formatPhaseEnum(phase.name);
}

/**
 * Returns true when the task is fully finished (RELEASED or REJECTED phase).
 * All modifications are blocked for finished tasks.
 */
export function isTaskFinished(phaseName: TaskPhaseName): boolean {
  return phaseName === 'RELEASED' || phaseName === 'REJECTED';
}

/**
 * Returns true when task fields (title, description, status, etc.) are locked.
 * Includes DONE (dev-finished) in addition to RELEASED and REJECTED.
 */
export function isTaskFieldsLocked(phaseName: TaskPhaseName): boolean {
  return phaseName === 'DONE' || isTaskFinished(phaseName);
}
