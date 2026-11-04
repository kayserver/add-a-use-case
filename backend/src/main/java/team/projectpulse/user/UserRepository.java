package team.projectpulse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PeerEvaluationUser, Integer> {

    Optional<PeerEvaluationUser> findByUsername(String username);

    Optional<PeerEvaluationUser> findByEmail(String email);

    List<PeerEvaluationUser> findByEmailIn(Collection<String> emails);

    /**
     * Whether the account exists and is active. Read on every bearer-token request by
     * {@code ActiveAccountJwtValidator} (FR-SEC-active-account), so it is a single primary-key lookup.
     */
    boolean existsByIdAndEnabledTrue(Integer id);

}
