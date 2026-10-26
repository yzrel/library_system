CREATE TABLE class_sections (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  grade_level VARCHAR(80) NOT NULL,
  section_name VARCHAR(80) NOT NULL,
  school_year VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_class_section (school_year, grade_level, section_name),
  INDEX idx_class_school_year (school_year)
) ENGINE=InnoDB;

CREATE TABLE class_adviser_assignments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_section_id BIGINT NOT NULL UNIQUE,
  teacher_id BIGINT NOT NULL UNIQUE,
  CONSTRAINT fk_class_adviser_section FOREIGN KEY (class_section_id) REFERENCES class_sections(id),
  CONSTRAINT fk_class_adviser_teacher FOREIGN KEY (teacher_id) REFERENCES borrowers(id)
) ENGINE=InnoDB;

ALTER TABLE borrowers ADD COLUMN class_section_id BIGINT NULL;
ALTER TABLE borrowers ADD CONSTRAINT fk_borrower_class_section FOREIGN KEY (class_section_id) REFERENCES class_sections(id);
CREATE INDEX idx_borrower_class_section ON borrowers(class_section_id);

INSERT INTO class_sections (grade_level, section_name, school_year, active)
SELECT DISTINCT TRIM(b.grade_section), '',
  CONCAT(
    IF(MONTH(CURRENT_DATE) >= 6, YEAR(CURRENT_DATE), YEAR(CURRENT_DATE) - 1),
    '-',
    IF(MONTH(CURRENT_DATE) >= 6, YEAR(CURRENT_DATE) + 1, YEAR(CURRENT_DATE))
  ),
  TRUE
FROM borrowers b JOIN users u ON u.id = b.user_id
WHERE u.role = 'STUDENT' AND b.grade_section IS NOT NULL AND TRIM(b.grade_section) <> '';

UPDATE borrowers b
JOIN users u ON u.id = b.user_id AND u.role = 'STUDENT'
JOIN class_sections cs ON cs.grade_level = TRIM(b.grade_section) AND cs.section_name = ''
SET b.class_section_id = cs.id
WHERE b.grade_section IS NOT NULL AND TRIM(b.grade_section) <> '';
