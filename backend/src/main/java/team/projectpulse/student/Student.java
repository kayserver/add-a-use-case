package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.system.WeekKeys;
import team.projectpulse.team.Team;
import team.projectpulse.user.PeerEvaluationUser;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class Student extends PeerEvaluationUser {

    @ManyToOne(optional = false)
    private Section section;
    @ManyToOne
    private Team team;
    /**
     * The date she was deactivated (UC-STU-deactivate-student), from the injected {@code Clock}; null while she is
     * active. Cleared when the interim {@code PUT /students/{studentId}} re-enables her.
     */
    private LocalDate deactivatedOn;


    public Student() {
    }

    public Student(String username, String firstName, String lastName, String email, String password, boolean enabled, String roles) {
        super(username, firstName, lastName, email, password, enabled, roles);
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public LocalDate getDeactivatedOn() {
        return deactivatedOn;
    }

    public void setDeactivatedOn(LocalDate deactivatedOn) {
        this.deactivatedOn = deactivatedOn;
    }

    /**
     * Her deactivation week, the ISO week key of her deactivation date, or null while she is active
     * (BR-deactivated-student-weeks).
     */
    public String deactivationWeek() {
        return this.deactivatedOn == null ? null : WeekKeys.of(this.deactivatedOn);
    }

    /**
     * Whether her deactivation week is earlier than the given week, so the course section peer evaluation report
     * leaves her out for it and her teammates may not evaluate her for it (BR-deactivated-student-weeks). Week keys
     * are zero-padded, so comparing them as strings orders them in time.
     */
    public boolean wasDeactivatedBefore(String week) {
        String deactivationWeek = deactivationWeek();
        return deactivationWeek != null && deactivationWeek.compareTo(week) < 0;
    }

}
