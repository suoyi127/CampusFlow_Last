ALTER TABLE space_review ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE space_review ADD CONSTRAINT chk_review_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'));
CREATE INDEX idx_review_public ON space_review(space_id,status,updated_at);
CREATE INDEX idx_review_queue ON space_review(status,updated_at);
