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

CREATE TABLE simulation_run (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 started_at DATETIME(6) NOT NULL,
 scenario VARCHAR(32) NOT NULL,
 seed BIGINT NOT NULL
);

CREATE TABLE sim_visit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 run_id BIGINT NOT NULL,
 virtual_person_id VARCHAR(100) NOT NULL UNIQUE,
 space_id BIGINT NOT NULL,
 checked_in_at DATETIME(6) NOT NULL,
 checked_out_at DATETIME(6),
 FOREIGN KEY (run_id) REFERENCES simulation_run(id),
 FOREIGN KEY (space_id) REFERENCES study_space(id)
);
CREATE INDEX idx_visit_active ON sim_visit(run_id, space_id, checked_out_at);

CREATE TABLE space_snapshot (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 run_id BIGINT NOT NULL,
 space_id BIGINT NOT NULL,
 current_people INT NOT NULL,
 sampled_at DATETIME(6) NOT NULL,
 valid BOOLEAN NOT NULL DEFAULT TRUE,
 invalid_reason VARCHAR(500),
 FOREIGN KEY (run_id) REFERENCES simulation_run(id),
 FOREIGN KEY (space_id) REFERENCES study_space(id),
 CHECK (current_people >= 0)
);
CREATE INDEX idx_snapshot_latest ON space_snapshot(run_id, space_id, sampled_at);

CREATE TABLE noise_device (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 space_id BIGINT NOT NULL UNIQUE,
 device_code VARCHAR(64) NOT NULL UNIQUE,
 status VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
 base_db DOUBLE NOT NULL DEFAULT 45,
 fluctuation_db DOUBLE NOT NULL DEFAULT 5,
 FOREIGN KEY (space_id) REFERENCES study_space(id)
);

CREATE TABLE noise_sample (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 run_id BIGINT NOT NULL,
 device_id BIGINT NOT NULL,
 space_id BIGINT NOT NULL,
 noise_db DOUBLE NOT NULL,
 sampled_at DATETIME(6) NOT NULL,
 valid BOOLEAN NOT NULL DEFAULT TRUE,
 invalid_reason VARCHAR(500),
 FOREIGN KEY (run_id) REFERENCES simulation_run(id),
 FOREIGN KEY (device_id) REFERENCES noise_device(id),
 FOREIGN KEY (space_id) REFERENCES study_space(id),
 CHECK (noise_db BETWEEN 30 AND 90)
);
CREATE INDEX idx_noise_window ON noise_sample(run_id, space_id, sampled_at);

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
