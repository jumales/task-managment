/**
 * Formats a TaskPhaseName enum value into a human-readable string.
 * e.g. "IN_PROGRESS" → "In Progress"
 */
export function formatPhaseEnum(name) {
    return name
        .split('_')
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
        .join(' ');
}
/**
 * Returns the display label for a phase.
 * Uses customName when set; otherwise falls back to the formatted enum name.
 */
export function resolvePhaseLabel(phase) {
    if (!phase)
        return '—';
    if (phase.customName)
        return phase.customName;
    return formatPhaseEnum(phase.name);
}
