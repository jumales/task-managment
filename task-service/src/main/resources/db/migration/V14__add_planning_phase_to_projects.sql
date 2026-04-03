INSERT INTO task_phases (id, project_id, name, description, custom_name, deleted_at)
SELECT gen_random_uuid(), id, 'PLANNING', NULL, NULL, NULL
FROM task_projects
WHERE deleted_at IS NULL;
