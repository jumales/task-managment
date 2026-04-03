/**
 * Returns the display label for a phase.
 * Uses customName when set; otherwise formats the enum name (e.g. "IN_PROGRESS" → "In Progress").
 *
 * @param {import('../api/types').TaskPhaseResponse | null | undefined} phase
 * @returns {string}
 */
export function resolvePhaseLabel(phase) {
  if (!phase) return '—';
  if (phase.customName) return phase.customName;
  return phase.name
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}
