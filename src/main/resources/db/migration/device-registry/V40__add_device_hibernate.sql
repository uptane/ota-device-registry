ALTER TABLE Device ADD COLUMN hibernated BOOLEAN NOT NULL DEFAULT FALSE
;

ALTER TABLE Device ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT '1970-01-01 00:00:00' ON UPDATE current_timestamp(3) AFTER created_at
;

ALTER TABLE Device
ALTER COLUMN hibernated DROP DEFAULT,
ALTER COLUMN updated_at SET DEFAULT current_timestamp(3)
;

CREATE TABLE DeviceHibernationStatus (
    device_uuid         CHAR(36)     NOT NULL COLLATE utf8_bin,
    previous_status      BOOLEAN NOT NULL,
    new_status           BOOLEAN NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT current_timestamp(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),

    INDEX (device_uuid, created_at)
);

CREATE TRIGGER device_hibernate_status_update AFTER UPDATE ON Device
       FOR EACH ROW
       INSERT INTO DeviceHibernationStatus (device_uuid, previous_status, new_status) VALUES (NEW.uuid, OLD.hibernated, NEW.hibernated)
;