CREATE TABLE library_visits (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  borrower_id BIGINT NOT NULL,
  checked_in_at TIMESTAMP NOT NULL,
  purpose VARCHAR(40) NOT NULL,
  entry_method VARCHAR(20) NOT NULL,
  FOREIGN KEY (borrower_id) REFERENCES borrowers(id),
  INDEX idx_visit_borrower_time(borrower_id, checked_in_at),
  INDEX idx_visit_time(checked_in_at)
) ENGINE=InnoDB;

CREATE TABLE service_feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  visit_id BIGINT NOT NULL UNIQUE,
  rating VARCHAR(30) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (visit_id) REFERENCES library_visits(id),
  INDEX idx_feedback_created(created_at)
) ENGINE=InnoDB;
