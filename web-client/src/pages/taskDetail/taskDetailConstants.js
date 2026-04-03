export const STATUS_COLORS = {
    TODO: 'default',
    IN_PROGRESS: 'blue',
    DONE: 'green',
};
export const TYPE_COLORS = {
    FEATURE: 'purple',
    BUG_FIXING: 'red',
    TESTING: 'cyan',
    PLANNING: 'gold',
    TECHNICAL_DEBT: 'orange',
    DOCUMENTATION: 'geekblue',
    OTHER: 'default',
};
export const TIMELINE_STATES = ['PLANNED_START', 'PLANNED_END', 'REAL_START', 'REAL_END'];
/** Returns a translated label map for all TaskType values. Intended for use inside useMemo. */
export function getTypeLabels(t) {
    return {
        FEATURE: t('tasks.types.FEATURE'),
        BUG_FIXING: t('tasks.types.BUG_FIXING'),
        TESTING: t('tasks.types.TESTING'),
        PLANNING: t('tasks.types.PLANNING'),
        TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
        DOCUMENTATION: t('tasks.types.DOCUMENTATION'),
        OTHER: t('tasks.types.OTHER'),
    };
}
/** Returns a translated label map for all WorkType values. Intended for use inside useMemo. */
export function getWorkTypeLabels(t) {
    return {
        DEVELOPMENT: t('tasks.workTypes.DEVELOPMENT'),
        TESTING: t('tasks.workTypes.TESTING'),
        CODE_REVIEW: t('tasks.workTypes.CODE_REVIEW'),
        DESIGN: t('tasks.workTypes.DESIGN'),
        PLANNING: t('tasks.workTypes.PLANNING'),
        DOCUMENTATION: t('tasks.workTypes.DOCUMENTATION'),
        DEPLOYMENT: t('tasks.workTypes.DEPLOYMENT'),
        MEETING: t('tasks.workTypes.MEETING'),
        OTHER: t('tasks.workTypes.OTHER'),
    };
}
