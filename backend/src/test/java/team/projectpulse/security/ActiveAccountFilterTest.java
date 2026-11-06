package team.projectpulse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActiveAccountFilterTest {

    @Mock
    UserRepository userRepository;
    @Mock
    CustomBearerTokenAuthenticationEntryPoint authenticationEntryPoint;
    @Mock
    HttpServletRequest request;
    @Mock
    HttpServletResponse response;
    @Mock
    FilterChain filterChain;


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testARequestWithNoBearerTokenContinues() throws Exception {
        ActiveAccountFilter filter = new ActiveAccountFilter(this.userRepository, this.authenticationEntryPoint);

        filter.doFilter(this.request, this.response, this.filterChain);

        verify(this.filterChain).doFilter(this.request, this.response);
        verify(this.authenticationEntryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void testAnEnabledAccountContinuesWhenTheClaimIsALong() throws Exception {
        Student john = new Student();
        john.setId(4);
        john.setEnabled(true);
        given(this.userRepository.findById(4)).willReturn(Optional.of(john));
        authenticate(4L);
        ActiveAccountFilter filter = new ActiveAccountFilter(this.userRepository, this.authenticationEntryPoint);

        filter.doFilter(this.request, this.response, this.filterChain);

        verify(this.filterChain).doFilter(this.request, this.response);
        verify(this.authenticationEntryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void testADeactivatedStudentIsRefused() throws Exception {
        Student john = new Student();
        john.setId(4);
        john.setEnabled(false);
        given(this.userRepository.findById(4)).willReturn(Optional.of(john));
        authenticate(4);
        ActiveAccountFilter filter = new ActiveAccountFilter(this.userRepository, this.authenticationEntryPoint);

        filter.doFilter(this.request, this.response, this.filterChain);

        verify(this.authenticationEntryPoint).commence(eq(this.request), eq(this.response), any(InvalidBearerTokenException.class));
        verify(this.filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testADeactivatedInstructorIsRefused() throws Exception {
        Instructor bill = new Instructor();
        bill.setId(2);
        bill.setEnabled(false);
        given(this.userRepository.findById(2)).willReturn(Optional.of(bill));
        authenticate(2);
        ActiveAccountFilter filter = new ActiveAccountFilter(this.userRepository, this.authenticationEntryPoint);

        filter.doFilter(this.request, this.response, this.filterChain);

        verify(this.authenticationEntryPoint).commence(eq(this.request), eq(this.response), any(InvalidBearerTokenException.class));
        verify(this.filterChain, never()).doFilter(any(), any());
    }

    private void authenticate(Object userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .issuedAt(Instant.parse("2023-08-20T00:00:00Z"))
                .expiresAt(Instant.parse("2023-08-20T02:00:00Z"))
                .claim("userId", userId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_student"))));
    }

}
