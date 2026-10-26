package ph.edu.vfgnhs.library;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

enum Role { STUDENT, TEACHER, ADMIN }
enum AccountStatus { ACTIVE, SUSPENDED, DISABLED }
enum BorrowerStatus { ACTIVE, BLOCKED, INACTIVE }
enum CopyStatus { AVAILABLE, ON_LOAN, RESERVED, LOST, DAMAGED, WEEDED, ARCHIVED }
enum RequestStatus { PENDING, APPROVED, REJECTED, CANCELLED, FULFILLED }
enum VisitPurpose { READING, ATTEND_CLASS, REMEDIAL, MEETING, REVIEW, STUDY, PLAY_BOARD_GAMES, RESEARCH, USE_DIGITAL_SERVICES }
enum EntryMethod { QR, MANUAL }
enum FeedbackRating { SATISFIED, NEUTRAL, NOT_SATISFIED }
enum DistributionStatus { ACTIVE, PARTIALLY_RETURNED, RETURNED }

@Entity
@Table(name = "users")
class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true) String email;
    @Column(name = "password_hash", nullable = false) String passwordHash;
    @Column(name = "first_name", nullable = false) String firstName;
    @Column(name = "last_name", nullable = false) String lastName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) AccountStatus status;
    @Column(name = "school_id", nullable = false, unique = true) String schoolId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
}

@Entity
@Table(name = "borrowers")
class Borrower {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") User user;
    @Column(name = "card_number", nullable = false, unique = true) String cardNumber;
    @Column(name = "grade_section") String gradeSection;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "class_section_id") ClassSection classSection;
    String department;
    String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) BorrowerStatus status;
    String notes;
}

@Entity
@Table(name = "class_sections")
class ClassSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "grade_level", nullable = false) String gradeLevel;
    @Column(name = "section_name", nullable = false) String sectionName;
    @Column(name = "school_year", nullable = false) String schoolYear;
    @Column(nullable = false) boolean active;
}

@Entity
@Table(name = "class_adviser_assignments")
class ClassAdviserAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "class_section_id", unique = true) ClassSection classSection;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "teacher_id", unique = true) Borrower teacher;
}

@Entity
@Table(name = "books")
class Book {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false) String title;
    String subtitle;
    String author;
    String isbn;
    String publisher;
    @Column(name = "publication_year") Integer publicationYear;
    String edition;
    String subject;
    String description;
    @Column(name = "cover_url") String coverUrl;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
}

@Entity
@Table(name = "book_copies")
class BookCopy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "book_id") Book book;
    @Column(name = "accession_number", nullable = false, unique = true) String accessionNumber;
    @Column(name = "call_number") String callNumber;
    @Column(name = "volume_copy") String volumeCopy;
    String pages;
    @Column(name = "date_received") LocalDate dateReceived;
    @Column(name = "source_of_fund") String sourceOfFund;
    BigDecimal cost;
    @Enumerated(EnumType.STRING) @Column(nullable = false) CopyStatus status;
    String remarks;
    @Column(unique = true) String barcode;
}

@Entity
@Table(name = "class_distributions")
class ClassDistribution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "teacher_id") Borrower teacher;
    @Column(name = "grade_section", nullable = false) String gradeSection;
    @Column(name = "school_year", nullable = false) String schoolYear;
    @Column(name = "distributed_at", nullable = false) Instant distributedAt;
    @Column(name = "expected_return_date", nullable = false) LocalDate expectedReturnDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) DistributionStatus status;
    String notes;
    @Column(name = "created_by", nullable = false) Long createdBy;
}

@Entity
@Table(name = "loans")
class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "copy_id") BookCopy copy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "borrower_id") Borrower borrower;
    @Column(name = "borrowed_at", nullable = false) Instant borrowedAt;
    @Column(name = "due_at", nullable = false) Instant dueAt;
    @Column(name = "returned_at") Instant returnedAt;
    String notes;
    @Column(name = "checked_out_by", nullable = false) Long checkedOutBy;
    @Column(name = "returned_by") Long returnedBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "distribution_id") ClassDistribution distribution;
}

@Entity
@Table(name = "reservations")
class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "book_id") Book book;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "borrower_id") Borrower borrower;
    @Enumerated(EnumType.STRING) @Column(nullable = false) RequestStatus status;
    @Column(name = "requested_at", nullable = false) Instant requestedAt;
}

@Entity
@Table(name = "acquisition_requests")
class AcquisitionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requester_id") User requester;
    @Column(nullable = false) String title;
    String author;
    String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false) RequestStatus status;
    @Column(name = "requested_at", nullable = false) Instant requestedAt;
}

@Entity
@Table(name = "library_visits")
class LibraryVisit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "borrower_id") Borrower borrower;
    @Column(name = "checked_in_at", nullable = false) Instant checkedInAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) VisitPurpose purpose;
    @Enumerated(EnumType.STRING) @Column(name = "entry_method", nullable = false) EntryMethod entryMethod;
}

@Entity
@Table(name = "service_feedback")
class ServiceFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "visit_id", unique = true) LibraryVisit visit;
    @Enumerated(EnumType.STRING) @Column(nullable = false) FeedbackRating rating;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}

@Entity
@Table(name = "audit_logs")
class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "actor_id") Long actorId;
    @Column(nullable = false) String action;
    @Column(name = "entity_type", nullable = false) String entityType;
    @Column(name = "entity_id") String entityId;
    String details;
    @Column(name = "ip_address") String ipAddress;
    String location;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}

@Entity
@Table(name = "settings")
class Setting {
    @Id @Column(name = "setting_key") String key;
    @Column(name = "setting_value", nullable = false) String value;
}

@Entity
@Table(name = "book_subjects")
class BookSubject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true) String name;
    @Column(name = "sort_order", nullable = false) int sortOrder;
    @Column(nullable = false) boolean active;
}
