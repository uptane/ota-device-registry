ALTER TABLE Device ADD COLUMN hibernated BOOLEAN NOT NULL DEFAULT FALSE
;

ALTER TABLE Device ALTER COLUMN hibernated DROP DEFAULT
;
