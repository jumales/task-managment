-- Reference to the file-service record for the user's profile picture.
-- Nullable: existing users have no avatar; the client shows a default placeholder.
ALTER TABLE users ADD COLUMN avatar_file_id UUID;
