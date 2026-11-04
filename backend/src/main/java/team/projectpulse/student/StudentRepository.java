package team.projectpulse.student;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);

    /**
     * The student, only if the caller may manage her lifecycle (BR-student-lifecycle): an instructor of her course
     * section or the course admin of its course. The scope is in the query, not checked after an unscoped
     * {@code findById} (architecture-of-record, Authorization point 2).
     *
     * <p>Locks the student's row so two simultaneous deactivations serialize, and the second one sees her already
     * deactivated (UC-STU-deactivate-student 3a2). The scope conditions are subqueries rather than joins, so only
     * her row is locked, not her course section or course.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Student s
            where s.id = :studentId
              and (exists (select 1 from Section sec join sec.instructors i where sec = s.section and i.id = :callerId)
                   or exists (select 1 from Section sec where sec = s.section and sec.course.courseAdmin.id = :callerId))
            """)
    Optional<Student> findManageableByIdForUpdate(@Param("studentId") Integer studentId, @Param("callerId") Integer callerId);

}
