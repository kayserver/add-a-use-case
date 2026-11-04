-- UC-STU-deactivate-student (design/stu.md): record the date a student was deactivated.
-- NULL while the student is active. Set from the injected Clock on deactivation; cleared when the interim
-- PUT /students/{studentId} re-enables her. Its ISO week is her deactivation week (BR-deactivated-student-weeks).
-- peer_evaluation_user holds both students and instructors; the column is mapped on Student only.

ALTER TABLE `peer_evaluation_user` ADD COLUMN `deactivated_on` date DEFAULT NULL;
