package com.example.haksikmokjang.signuptest;

import com.example.haksikmokjang.member.emailverification.domain.EmailPurpose;
import com.example.haksikmokjang.member.emailverification.domain.EmailVerification;
import com.example.haksikmokjang.member.emailverification.repository.EmailVerificationRepository;
import com.example.haksikmokjang.member.terms.domain.Terms;
import com.example.haksikmokjang.member.terms.repository.TermsRepository;
import com.example.haksikmokjang.school.domain.School;
import com.example.haksikmokjang.school.repository.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignUpTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EmailVerificationRepository emailVerificationRepository;
    @Autowired
    SchoolRepository schoolRepository;
    @Autowired
    TermsRepository termsRepository;

    @MockitoBean
    JavaMailSender javaMailSender;

    @Test
    void signUpIntegrationTest() throws Exception {
        School school = getOrCreateSchool();
        List<Long> termsIds = getOrCreateTermsIds();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "signup-" + suffix + "@induk.ac.kr";
        String loginId = "signup" + suffix;
        String nickname = "가입테스트" + suffix;

        // 1. 이메일 인증번호 전송
        mockMvc.perform(post("/api/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk());

        // 2. DB에서 저장된 인증번호 조회
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, EmailPurpose.SIGNUP)
                .orElseThrow();

        String code = verification.getVerificationCode();

        // 3. 이메일 인증번호 검증
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "code": "%s"
                                }
                                """.formatted(email, code)))
                .andExpect(status().isOk());

        // 4. 최종 회원가입
        mockMvc.perform(post("/api/members/signup/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "password123!",
                                  "name": "홍길동",
                                  "nickname": "%s",
                                  "schoolId": %d,
                                  "department": "컴퓨터공학과",
                                  "schoolEmail": "%s",
                                  "birthDate": "2000-01-01",
                                  "gender": "M",
                                  "phone": "01012345678",
                                  "termsIds": %s
                                }
                                """.formatted(loginId, nickname, school.getSchoolId(), email, termsIds)))
                .andExpect(status().isOk());
    }

    private School getOrCreateSchool() {
        return schoolRepository.findByEmailDomain("induk.ac.kr")
                .orElseGet(() -> schoolRepository.save(
                        School.builder()
                                .schoolName("인덕대학교")
                                .emailDomain("induk.ac.kr")
                                .latitude(BigDecimal.valueOf(37.631668))
                                .longitude(BigDecimal.valueOf(127.054045))
                                .build()
                ));
    }

    private List<Long> getOrCreateTermsIds() {
        List<Terms> terms = termsRepository.findAllByOrderByEffectiveAtDesc();
        if (!terms.isEmpty()) {
            return terms.stream().map(Terms::getTermsId).toList();
        }

        return List.of(
                termsRepository.save(createTerms("서비스 이용약관", "Y")).getTermsId(),
                termsRepository.save(createTerms("개인정보 처리방침", "Y")).getTermsId(),
                termsRepository.save(createTerms("위치기반 서비스 이용약관", "Y")).getTermsId(),
                termsRepository.save(createTerms("마케팅 정보 수신", "N")).getTermsId()
        );
    }

    private Terms createTerms(String title, String requiredYn) {
        return Terms.builder()
                .title(title)
                .version("1.0")
                .content(title + " 테스트 약관")
                .requiredYn(requiredYn)
                .effectiveAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
