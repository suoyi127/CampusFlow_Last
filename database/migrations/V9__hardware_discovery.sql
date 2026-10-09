CREATE TABLE hardware_discovery (
    device_id VARCHAR(64) PRIMARY KEY,
    first_seen DATETIME(6) NOT NULL,
    last_seen DATETIME(6) NOT NULL
);
