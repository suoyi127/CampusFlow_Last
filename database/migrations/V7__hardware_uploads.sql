-- 真实设备记录独立于 simulation_run，模拟重置不影响硬件历史与去重。
CREATE TABLE hardware_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 device_id VARCHAR(64) NOT NULL,
 space_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 event_id VARCHAR(20),
 event_sequence DECIMAL(20,0),
 event_type VARCHAR(16),
 headcount BIGINT NOT NULL,
 decibel DOUBLE,
 sound_valid BOOLEAN,
 sampled_at DATETIME(6) NOT NULL,
 received_at DATETIME(6) NOT NULL,
 UNIQUE (device_id,event_id),
 FOREIGN KEY (space_id) REFERENCES study_space(id),
 CHECK (kind IN ('EVENT','TELEMETRY')),
 CHECK (headcount BETWEEN 0 AND 4294967295)
);
CREATE INDEX idx_hardware_current ON hardware_record(space_id,sampled_at,id);
CREATE INDEX idx_hardware_received ON hardware_record(space_id,received_at);

-- 永久性协议错误先保存再确认 MQTT；数据库故障不确认，让 broker 重投。
CREATE TABLE hardware_rejection (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 topic VARCHAR(200) NOT NULL,
 payload VARCHAR(8192) NOT NULL,
 error_code VARCHAR(64) NOT NULL,
 received_at DATETIME(6) NOT NULL
);
