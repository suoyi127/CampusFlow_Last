ALTER TABLE sys_user ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
CREATE TABLE account_management_lock (id INT PRIMARY KEY);
INSERT INTO account_management_lock(id) VALUES (1);
