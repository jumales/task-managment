-- Add user_id to track who posted each comment; nullable for backward compatibility with existing rows.
ALTER TABLE task_comments ADD COLUMN user_id UUID;
