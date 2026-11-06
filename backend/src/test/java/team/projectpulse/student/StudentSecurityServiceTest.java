package team.projectpulse.student;

import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    private static final int STUDENT_ID = 3;
    private static final int COURSE_ADMIN_ID = 10;
    private static final int SECTION_INSTRUCTOR_ID = 11;

    @Mock
    StudentRepository studentRepository;
    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Student student;


    @BeforeEach
    void setUp() {
        Instructor courseAdmin = new Instructor();
        courseAdmin.setId(COURSE_ADMIN_ID);
        Course course = new Course("Software Engineering", "Capstone");
        course.setCourseAdmin(courseAdmin);

        Instructor sectionInstructor = new Instructor();
        sectionInstructor.setId(SECTION_INSTRUCTOR_ID);
        Section section = new Section();
        section.setCourse(course);
        section.addInstructor(sectionInstructor);

        this.student = new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        this.student.setId(STUDENT_ID);
        this.student.setSection(section);
    }

    @Test
    void testCourseAdminOfHerCourseMayDeactivateHer() {
        // She is the course admin and is not on the course section's instructor list.
        assertThat(this.student.getSection().getInstructors()).extracting(Instructor::getId).doesNotContain(COURSE_ADMIN_ID);
        given(this.userUtils.getUserId()).willReturn(COURSE_ADMIN_ID);
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(this.student));

        assertThat(this.studentSecurityService.canDeactivateStudent(STUDENT_ID)).isTrue();
    }

    @Test
    void testInstructorOfHerCourseSectionMayDeactivateHer() {
        given(this.userUtils.getUserId()).willReturn(SECTION_INSTRUCTOR_ID);
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(this.student));

        assertThat(this.studentSecurityService.canDeactivateStudent(STUDENT_ID)).isTrue();
    }

    @Test
    void testInstructorOfAnotherCourseSectionMayNot() {
        given(this.userUtils.getUserId()).willReturn(99);
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(this.student));

        assertThat(this.studentSecurityService.canDeactivateStudent(STUDENT_ID)).isFalse();
    }

    @Test
    void testTheStudentHerselfMayNot() {
        given(this.userUtils.getUserId()).willReturn(STUDENT_ID);
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(this.student));

        assertThat(this.studentSecurityService.canDeactivateStudent(STUDENT_ID)).isFalse();
    }

    @Test
    void testUnknownStudentIsDenied() {
        given(this.userUtils.getUserId()).willReturn(COURSE_ADMIN_ID);
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.canDeactivateStudent(STUDENT_ID)).isFalse();
    }

    @Test
    void testMissingCallerIsDenied() {
        given(this.userUtils.getUserId()).willReturn(null);

        assertThat(this.studentSecurityService.canDeactivateStudent(STUDENT_ID)).isFalse();
        verify(this.studentRepository, never()).findById(STUDENT_ID);
    }

}
