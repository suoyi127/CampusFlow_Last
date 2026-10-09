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

-- 仅恢复历史中唯一的一对一关系，避免升级后原实测空间退回模拟。
INSERT INTO hardware_device(device_id,name,space_id,enabled,created_at,updated_at)
SELECT d.device_id,d.device_id,d.space_id,TRUE,d.created_at,d.updated_at
FROM (SELECT device_id,MIN(space_id) AS space_id,MIN(received_at) AS created_at,MAX(received_at) AS updated_at
      FROM hardware_record GROUP BY device_id HAVING COUNT(DISTINCT space_id)=1) d
JOIN (SELECT space_id FROM hardware_record GROUP BY space_id HAVING COUNT(DISTINCT device_id)=1) s
ON s.space_id=d.space_id;
