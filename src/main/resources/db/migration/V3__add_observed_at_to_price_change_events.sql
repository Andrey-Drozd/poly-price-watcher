ALTER TABLE price_change_events
    ADD COLUMN observed_at TIMESTAMP WITH TIME ZONE;

UPDATE price_change_events
SET observed_at = created_at
WHERE observed_at IS NULL;

ALTER TABLE price_change_events
    ALTER COLUMN observed_at SET NOT NULL;
