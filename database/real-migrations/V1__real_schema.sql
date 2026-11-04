-- 全新真实数据库，仅创建结构与运行配置，不插入账号、空间或传感器样例。

CREATE TABLE sys_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(64) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(24) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL,
 CHECK (role IN ('USER', 'DATA_ADMIN', 'SERVER_ADMIN'))
);

CREATE TABLE study_space (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(100) NOT NULL,
 type VARCHAR(32) NOT NULL,
 address VARCHAR(200) NOT NULL,
 latitude DOUBLE NOT NULL,
 longitude DOUBLE NOT NULL,
 capacity INT NOT NULL,
 open_time VARCHAR(5) NOT NULL,
 close_time VARCHAR(5) NOT NULL,
 open_days VARCHAR(20) NOT NULL,
 all_day BOOLEAN NOT NULL DEFAULT FALSE,
 facilities VARCHAR(100) NOT NULL,
 description VARCHAR(1000) NOT NULL DEFAULT '',
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 CHECK (capacity > 0),
 CHECK (latitude BETWEEN -90 AND 90),
 CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE space_review (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 space_id BIGINT NOT NULL,
 environment_score INT NOT NULL,
 facility_score INT NOT NULL,
 content VARCHAR(500) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL,
 reviewed_by BIGINT,
 reviewed_at DATETIME(6),
 review_reason VARCHAR(500),
 updated_at DATETIME(6) NOT NULL,
 UNIQUE (user_id, space_id),
 FOREIGN KEY (user_id) REFERENCES sys_user(id),
 FOREIGN KEY (space_id) REFERENCES study_space(id),
 FOREIGN KEY (reviewed_by) REFERENCES sys_user(id),
 CHECK (environment_score BETWEEN 1 AND 5),
 CHECK (facility_score BETWEEN 1 AND 5)
);

CREATE TABLE system_config (
 config_key VARCHAR(64) PRIMARY KEY,
 config_value VARCHAR(1000) NOT NULL,
 updated_at DATETIME(6) NOT NULL
);

CREATE TABLE audit_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 actor VARCHAR(64) NOT NULL,
 action VARCHAR(64) NOT NULL,
 target VARCHAR(100) NOT NULL,
 result VARCHAR(16) NOT NULL,
 reason VARCHAR(500),
 occurred_at DATETIME(6) NOT NULL
);

ALTER TABLE space_review ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE space_review ADD CONSTRAINT chk_review_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'));
CREATE INDEX idx_review_public ON space_review(space_id,status,updated_at);
CREATE INDEX idx_review_queue ON space_review(status,updated_at);


ALTER TABLE system_config ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
INSERT INTO system_config(config_key,config_value,updated_at,version) VALUES
('RUNTIME','{"validitySeconds":30,"noiseWindowSeconds":60,"distanceWeight":0.30,"quietWeight":0.30,"freeWeight":0.25,"facilityWeight":0.15}',CURRENT_TIMESTAMP,1);


ALTER TABLE sys_user ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
CREATE TABLE account_management_lock (id INT PRIMARY KEY);
INSERT INTO account_management_lock(id) VALUES (1);


-- 保存设备原始事实，事件重投按物理设备与事件编号去重。
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


ALTER TABLE study_space ADD COLUMN coordinate_system VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

CREATE TABLE hardware_device (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 device_id VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL,
 space_id BIGINT NULL UNIQUE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 1,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_hardware_device_space FOREIGN KEY (space_id) REFERENCES study_space(id)
);



CREATE TABLE hardware_discovery (
    device_id VARCHAR(64) PRIMARY KEY,
    first_seen DATETIME(6) NOT NULL,
    last_seen DATETIME(6) NOT NULL
);


ALTER TABLE study_space ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'REAL';
ALTER TABLE sys_user ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'REAL';
ALTER TABLE hardware_record ADD COLUMN people_valid BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE hardware_record ADD COLUMN people_invalid_reason VARCHAR(500);
ALTER TABLE hardware_record ADD COLUMN noise_valid BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE hardware_record ADD COLUMN noise_invalid_reason VARCHAR(500);

