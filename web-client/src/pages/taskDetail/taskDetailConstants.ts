import type { TaskStatus, TaskType, TimelineState } from '../../api/types';

export const STATUS_COLORS: Record<TaskStatus, string> = {
  TODO:        'default',
  IN_PROGRESS: 'blue',
  DONE:        'green',
};

export const TYPE_COLORS: Record<TaskType, string> = {
  FEATURE:        'purple',
  BUG_FIXING:     'red',
  TESTING:        'cyan',
  PLANNING:       'gold',
  TECHNICAL_DEBT: 'orange',
  DOCUMENTATION:  'geekblue',
  OTHER:          'default',
};

export const TIMELINE_STATES: TimelineState[] = ['PLANNED_START', 'PLANNED_END', 'REAL_START', 'REAL_END'];
