CREATE TABLE class_distributions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  teacher_id BIGINT NOT NULL,
  grade_section VARCHAR(120) NOT NULL,
  school_year VARCHAR(20) NOT NULL,
  distributed_at TIMESTAMP NOT NULL,
  expected_return_date DATE NOT NULL,
  status VARCHAR(30) NOT NULL,
  notes VARCHAR(1000),
  created_by BIGINT NOT NULL,
  FOREIGN KEY (teacher_id) REFERENCES borrowers(id),
  FOREIGN KEY (created_by) REFERENCES users(id),
  INDEX idx_distribution_teacher(teacher_id),
  INDEX idx_distribution_status(status),
  INDEX idx_distribution_school_year(school_year)
) ENGINE=InnoDB;
ALTER TABLE loans ADD COLUMN distribution_id BIGINT NULL;
ALTER TABLE loans ADD CONSTRAINT fk_loan_distribution FOREIGN KEY (distribution_id) REFERENCES class_distributions(id);
CREATE INDEX idx_loan_distribution ON loans(distribution_id);
