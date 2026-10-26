package ph.edu.vfgnhs.library;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findBySchoolIdIgnoreCase(String schoolId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsBySchoolIdIgnoreCase(String schoolId);
}

interface BorrowerRepository extends JpaRepository<Borrower, Long> {
    @Override @EntityGraph(attributePaths = {"user", "classSection"})
    Optional<Borrower> findById(Long id);
    @EntityGraph(attributePaths = {"user", "classSection"})
    Optional<Borrower> findByUserId(Long userId);
    @EntityGraph(attributePaths = {"user", "classSection"})
    Optional<Borrower> findByCardNumberIgnoreCase(String cardNumber);

    @Query("select b from Borrower b join fetch b.user u left join fetch b.classSection where " +
            "lower(u.firstName) like lower(concat('%',:q,'%')) or " +
            "lower(u.lastName) like lower(concat('%',:q,'%')) or " +
            "lower(b.cardNumber) like lower(concat('%',:q,'%'))")
    List<Borrower> search(@Param("q") String query);

    @Query("select b from Borrower b join fetch b.user u where " +
            "u.status = ph.edu.vfgnhs.library.AccountStatus.ACTIVE and b.status = ph.edu.vfgnhs.library.BorrowerStatus.ACTIVE and (" +
            "lower(u.firstName) like lower(concat('%',:q,'%')) or " +
            "lower(u.lastName) like lower(concat('%',:q,'%')) or " +
            "lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%',:q,'%')) or " +
            "lower(u.schoolId) like lower(concat('%',:q,'%')) or " +
            "lower(b.cardNumber) like lower(concat('%',:q,'%'))) " +
            "order by u.lastName, u.firstName")
    List<Borrower> suggest(@Param("q") String query, Pageable pageable);

    @Query("select b from Borrower b join fetch b.user left join fetch b.classSection")
    List<Borrower> findAllWithUser();
    @EntityGraph(attributePaths = {"user", "classSection"})
    List<Borrower> findByClassSectionId(Long classSectionId);
}

interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {
    List<ClassSection> findAllByOrderBySchoolYearDescGradeLevelAscSectionNameAsc();
    List<ClassSection> findByActiveTrueOrderBySchoolYearDescGradeLevelAscSectionNameAsc();
    List<ClassSection> findBySchoolYearAndActiveTrueOrderByGradeLevelAscSectionNameAsc(String schoolYear);
    boolean existsBySchoolYearIgnoreCaseAndGradeLevelIgnoreCaseAndSectionNameIgnoreCase(
            String schoolYear, String gradeLevel, String sectionName);
    boolean existsBySchoolYearIgnoreCaseAndGradeLevelIgnoreCaseAndSectionNameIgnoreCaseAndIdNot(
            String schoolYear, String gradeLevel, String sectionName, Long id);
}

interface ClassAdviserAssignmentRepository extends JpaRepository<ClassAdviserAssignment, Long> {
    @EntityGraph(attributePaths = {"classSection", "teacher", "teacher.user"})
    Optional<ClassAdviserAssignment> findByClassSectionId(Long classSectionId);
    @EntityGraph(attributePaths = {"classSection", "teacher", "teacher.user"})
    Optional<ClassAdviserAssignment> findByTeacherId(Long teacherId);
    @EntityGraph(attributePaths = {"classSection", "teacher", "teacher.user"})
    List<ClassAdviserAssignment> findAll();
    void deleteByClassSectionId(Long classSectionId);
    long countByClassSectionId(Long classSectionId);
}

interface BookRepository extends JpaRepository<Book, Long> {
    @Query("select distinct b from Book b left join BookCopy c on c.book=b where " +
            ":q='' or lower(b.title) like lower(concat('%',:q,'%')) or " +
            "lower(coalesce(b.author,'')) like lower(concat('%',:q,'%')) or " +
            "lower(coalesce(b.subject,'')) like lower(concat('%',:q,'%')) or " +
            "lower(coalesce(b.isbn,'')) like lower(concat('%',:q,'%')) or " +
            "lower(coalesce(c.accessionNumber,'')) like lower(concat('%',:q,'%')) or " +
            "lower(coalesce(c.callNumber,'')) like lower(concat('%',:q,'%')) " +
            "order by b.title")
    List<Book> search(@Param("q") String query);

    @Query("select b from Book b where replace(replace(coalesce(b.isbn,''), '-', ''), ' ', '') = :normalized")
    List<Book> findByNormalizedIsbn(@Param("normalized") String normalized);

    List<Book> findBySubjectIgnoreCase(String subject);
}

interface BookSubjectRepository extends JpaRepository<BookSubject, Long> {
    List<BookSubject> findAllByOrderBySortOrderAscNameAsc();
    List<BookSubject> findByActiveTrueOrderBySortOrderAscNameAsc();
    Optional<BookSubject> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    Optional<BookSubject> findTopByOrderBySortOrderDesc();
}

interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    @Override @EntityGraph(attributePaths = "book")
    Optional<BookCopy> findById(Long id);
    boolean existsByAccessionNumberIgnoreCase(String accessionNumber);
    Optional<BookCopy> findByAccessionNumberIgnoreCase(String accessionNumber);
    Optional<BookCopy> findByBarcode(String barcode);
    @Query("select c.accessionNumber from BookCopy c where lower(c.accessionNumber) like lower(concat(:prefix, '%'))")
    List<String> findAccessionNumbersStartingWith(@Param("prefix") String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c.accessionNumber from BookCopy c where lower(c.accessionNumber) like lower(concat(:prefix, '%'))")
    List<String> lockAccessionNumbersStartingWith(@Param("prefix") String prefix);
    @Query("select c from BookCopy c join fetch c.book where c.book.id=:bookId")
    List<BookCopy> findByBookId(@Param("bookId") Long bookId);

    @Query("select c from BookCopy c join fetch c.book where c.book.id in :ids")
    List<BookCopy> findByBookIdIn(@Param("ids") Collection<Long> ids);

    long countByStatus(CopyStatus status);

    @Query("select c from BookCopy c join fetch c.book")
    List<BookCopy> findAllWithBook();

    @Query("select c from BookCopy c join fetch c.book where c.book.id=:bookId and c.status=ph.edu.vfgnhs.library.CopyStatus.AVAILABLE order by c.accessionNumber")
    List<BookCopy> findAvailableByBookId(@Param("bookId") Long bookId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c join fetch c.book where c.id in :ids")
    List<BookCopy> lockAllById(@Param("ids") List<Long> ids);
}

interface LoanRepository extends JpaRepository<Loan, Long> {
    @Query("select l from Loan l join fetch l.copy c join fetch c.book join fetch l.borrower br join fetch br.user left join fetch l.distribution where l.id=:id")
    Optional<Loan> findDetailedById(@Param("id") Long id);

    @Query("select l from Loan l join fetch l.copy c join fetch c.book join fetch l.borrower br join fetch br.user left join fetch l.distribution order by l.borrowedAt desc")
    List<Loan> findAllDetailed();

    @Query("select l from Loan l join fetch l.copy c join fetch c.book join fetch l.borrower br join fetch br.user left join fetch l.distribution where l.returnedAt is null order by l.dueAt")
    List<Loan> findActiveDetailed();

    @Query("select l from Loan l join fetch l.copy c join fetch c.book join fetch l.borrower br join fetch br.user left join fetch l.distribution where br.id=:borrowerId order by l.borrowedAt desc")
    List<Loan> findByBorrowerDetailed(@Param("borrowerId") Long borrowerId);

    @Query("select l from Loan l join fetch l.copy c join fetch c.book join fetch l.borrower br join fetch br.user left join fetch l.distribution where c.id=:copyId order by l.borrowedAt desc")
    List<Loan> findByCopyDetailed(@Param("copyId") Long copyId);

    @Query("select l from Loan l join fetch l.copy c join fetch c.book join fetch l.borrower br join fetch br.user join fetch l.distribution where l.distribution.id=:distributionId order by c.book.title, c.accessionNumber")
    List<Loan> findByDistributionDetailed(@Param("distributionId") Long distributionId);

    @Query("select count(l) from Loan l where l.borrower.id=:borrowerId and l.returnedAt is null and l.distribution is null")
    long countRegularActiveByBorrowerId(@Param("borrowerId") Long borrowerId);
    long countByReturnedAtIsNull();
    @Query("select count(l) from Loan l where l.returnedAt is null and l.dueAt < :now")
    long countOverdue(@Param("now") Instant now);
    @Query("select l.copy.book.title, count(l) from Loan l group by l.copy.book.title order by count(l) desc")
    List<Object[]> topTitles(org.springframework.data.domain.Pageable pageable);
    @Query("select concat(l.borrower.user.firstName, ' ', l.borrower.user.lastName), count(l) from Loan l group by l.borrower.user.firstName, l.borrower.user.lastName order by count(l) desc")
    List<Object[]> topBorrowers(org.springframework.data.domain.Pageable pageable);
    boolean existsByCopyIdAndReturnedAtIsNull(Long copyId);
    boolean existsByCopyId(Long copyId);
}

interface ClassDistributionRepository extends JpaRepository<ClassDistribution, Long> {
    @Override @EntityGraph(attributePaths = {"teacher", "teacher.user"})
    Optional<ClassDistribution> findById(Long id);

    @Query("select d from ClassDistribution d join fetch d.teacher t join fetch t.user order by d.distributedAt desc")
    List<ClassDistribution> findAllDetailed();
}

interface ReservationRepository extends JpaRepository<Reservation, Long> {
    boolean existsByBorrowerIdAndBookIdAndStatus(Long borrowerId, Long bookId, RequestStatus status);
    @Query("select r from Reservation r join fetch r.book join fetch r.borrower b join fetch b.user where b.id=:borrowerId order by r.requestedAt desc")
    List<Reservation> findByBorrowerIdOrderByRequestedAtDesc(@Param("borrowerId") Long borrowerId);
    @Query("select r from Reservation r join fetch r.book join fetch r.borrower b join fetch b.user")
    List<Reservation> findAllDetailed();
}

interface AcquisitionRequestRepository extends JpaRepository<AcquisitionRequest, Long> {
    @Query("select a from AcquisitionRequest a join fetch a.requester where a.requester.id=:requesterId order by a.requestedAt desc")
    List<AcquisitionRequest> findByRequesterIdOrderByRequestedAtDesc(@Param("requesterId") Long requesterId);
    @Query("select a from AcquisitionRequest a join fetch a.requester")
    List<AcquisitionRequest> findAllDetailed();
}

interface LibraryVisitRepository extends JpaRepository<LibraryVisit, Long> {
    boolean existsByBorrowerIdAndCheckedInAtAfter(Long borrowerId, Instant cutoff);

    @Query("select v from LibraryVisit v join fetch v.borrower b join fetch b.user " +
            "where v.checkedInAt >= :from and v.checkedInAt < :to " +
            "and (:q = '' or lower(b.user.firstName) like lower(concat('%',:q,'%')) " +
            "or lower(b.user.lastName) like lower(concat('%',:q,'%')) " +
            "or lower(b.cardNumber) like lower(concat('%',:q,'%')) " +
            "or lower(b.user.schoolId) like lower(concat('%',:q,'%'))) order by v.checkedInAt desc")
    List<LibraryVisit> report(@Param("from") Instant from, @Param("to") Instant to, @Param("q") String query);
}

interface ServiceFeedbackRepository extends JpaRepository<ServiceFeedback, Long> {
    boolean existsByVisitId(Long visitId);

    @Query("select f.rating, count(f) from ServiceFeedback f where f.visit.checkedInAt >= :from and f.visit.checkedInAt < :to group by f.rating")
    List<Object[]> summary(@Param("from") Instant from, @Param("to") Instant to);
}

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

interface SettingRepository extends JpaRepository<Setting, String> {
    default void saveIfAbsent(String key, String value) {
        if (!existsById(key)) {
            Setting setting = new Setting();
            setting.key = key;
            setting.value = value;
            save(setting);
        }
    }
}
