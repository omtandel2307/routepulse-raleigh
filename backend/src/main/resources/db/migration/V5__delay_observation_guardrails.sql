DELETE FROM trip_delay_observation WHERE ABS(delay_seconds) > 7200;

ALTER TABLE trip_delay_observation
    ADD CONSTRAINT chk_delay_observation_plausible
    CHECK (delay_seconds BETWEEN -7200 AND 7200);
