ALTER TABLE system_config ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
INSERT INTO system_config(config_key,config_value,updated_at,version) VALUES
('RUNTIME','{"simulationSeconds":5,"validitySeconds":30,"noiseWindowSeconds":60,"distanceWeight":0.30,"quietWeight":0.30,"freeWeight":0.25,"facilityWeight":0.15}',CURRENT_TIMESTAMP,1);
