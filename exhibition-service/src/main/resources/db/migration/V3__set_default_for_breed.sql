ALTER TABLE pets
    ALTER COLUMN breed SET DEFAULT 'unknown';

UPDATE pets
    SET breed = 'unknown'
    WHERE breed IS NULL;

ALTER TABLE pets
    ALTER COLUMN breed SET NOT NULL;