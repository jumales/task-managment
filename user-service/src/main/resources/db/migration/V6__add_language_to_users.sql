-- Add language preference column; defaults to English for existing and new rows
ALTER TABLE users ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'en';
