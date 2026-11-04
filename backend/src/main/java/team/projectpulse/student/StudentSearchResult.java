package team.projectpulse.student;

import org.springframework.data.domain.Page;

/**
 * A page of search results and the course section they were scoped to, so the controller can ask once whether the
 * caller may manage that course section's students (UC-STU-deactivate-student 3b1) instead of once per row.
 */
public record StudentSearchResult(Page<Student> page, Integer sectionId) {
}
