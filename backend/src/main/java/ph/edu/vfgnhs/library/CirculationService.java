package ph.edu.vfgnhs.library;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
class CirculationService {
    private final LoanRepository loans;
    private final BookCopyRepository copies;
    private final ClassDistributionRepository distributions;
    private final ApiSupport api;

    CirculationService(LoanRepository loans, BookCopyRepository copies,
                       ClassDistributionRepository distributions, ApiSupport api) {
        this.loans = loans; this.copies = copies; this.distributions = distributions; this.api = api;
    }

    @Transactional
    Loan returnLoan(Loan loan, User actor) {
        if (loan.returnedAt != null) throw new ApiException(HttpStatus.CONFLICT, "Loan was already returned");
        loan.returnedAt = Instant.now();
        loan.returnedBy = actor.id;
        loans.save(loan);
        loan.copy.status = CopyStatus.AVAILABLE;
        copies.save(loan.copy);
        api.audit(actor, "RETURN", "LOAN", loan.id, loan.copy.accessionNumber);
        if (loan.distribution != null) syncDistributionStatus(loan.distribution.id);
        return loan;
    }

    void syncDistributionStatus(Long distributionId) {
        ClassDistribution distribution = distributions.findById(distributionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Distribution not found"));
        List<Loan> items = loans.findByDistributionDetailed(distributionId);
        long returned = items.stream().filter(item -> item.returnedAt != null).count();
        distribution.status = returned == 0 ? DistributionStatus.ACTIVE
                : returned == items.size() ? DistributionStatus.RETURNED
                : DistributionStatus.PARTIALLY_RETURNED;
        distributions.save(distribution);
    }
}
