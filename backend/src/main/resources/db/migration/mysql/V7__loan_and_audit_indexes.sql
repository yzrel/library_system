CREATE INDEX idx_loan_returned_at ON loans(returned_at);
CREATE INDEX idx_loan_due_at ON loans(due_at);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
