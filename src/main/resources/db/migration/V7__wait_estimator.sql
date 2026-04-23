ALTER TABLE restaurant_tables
    ADD COLUMN occupied_at         TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cleaning_started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE restaurant_configs
    ADD COLUMN cleaning_duration_minutes INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN wait_buffer_minutes       INTEGER NOT NULL DEFAULT 3;

ALTER TABLE restaurant_configs
    ADD CONSTRAINT chk_cleaning_duration_nonneg CHECK (cleaning_duration_minutes >= 0),
    ADD CONSTRAINT chk_wait_buffer_nonneg       CHECK (wait_buffer_minutes >= 0);

UPDATE restaurant_tables
SET occupied_at = now()
WHERE status = 'OCCUPIED' AND occupied_at IS NULL;

UPDATE restaurant_tables
SET cleaning_started_at = now()
WHERE status = 'CLEANING' AND cleaning_started_at IS NULL;
