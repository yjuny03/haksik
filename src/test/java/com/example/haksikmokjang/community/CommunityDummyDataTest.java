package com.example.haksikmokjang.community;
import com.example.haksikmokjang.community.comment.domain.Comment;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.BoardType;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.domain.PostCategory;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.member.core.domain.AccountStatus;
import com.example.haksikmokjang.member.core.domain.Gender;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.signup.user.domain.UserProfile;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.school.domain.School;
import com.example.haksikmokjang.school.repository.SchoolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

// 🚨 Enum 패키지 경로는 Alt+Enter로 자동 임포트 하십시오.

@SpringBootTest
@Transactional
@Rollback(false)


public class CommunityDummyDataTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("커뮤니티 테스트용 유저 2명과 게시글, 댓글 더미 데이터 세팅")
    void insertDummyData() {

        // 🚨 팩트: DB에 이미 깔려있는 '인덕대학교' 데이터를 이메일 도메인으로 긁어옵니다.
        // 없으면 테스트 데이터 기준으로 생성합니다.
        School indukSchool = schoolRepository.findByEmailDomain("induk.ac.kr")
                .orElseGet(() -> schoolRepository.save(
                        School.builder()
                                .schoolName("인덕대학교")
                                .emailDomain("induk.ac.kr")
                                .latitude(BigDecimal.valueOf(37.631668))
                                .longitude(BigDecimal.valueOf(127.054045))
                                .build()
                ));

        // 1. 테스트 유저 A (작성자) 생성
        Member memberA = getOrCreateMember("qwer", "userA@induk.ac.kr");
        getOrCreateProfile(
                memberA,
                indukSchool,
                "김근육",
                "헬창대학생",
                "스포츠의학과",
                LocalDate.of(2004, 1, 1)
        );

        // 2. 테스트 유저 B (댓글러) 생성
        Member memberB = getOrCreateMember("1234", "userB@induk.ac.kr");
        getOrCreateProfile(
                memberB,
                indukSchool,
                "이단백",
                "3대500",
                "컴퓨터공학과",
                LocalDate.of(2004, 5, 5)
        );

        // 3. 테스트 게시글 생성
        Post post1 = Post.builder()
                .member(memberA)
                .title("오늘 학교 식당 제육볶음 매크로 팩트 체크합니다.")
                .content("단백질 30g 정도 되는 것 같습니다. 맛은 그럭저럭이네요. 다들 득근하십쇼.")
                .boardType(BoardType.SCHOOL)
                .category(PostCategory.FOOD)
                .anonymousYn("N")
                // Post 엔티티에도 School 매핑이 있다면 연결
                .school(indukSchool)
                .build();
        Post savedPost1 = postRepository.findAll().stream()
                .filter(post -> "오늘 학교 식당 제육볶음 매크로 팩트 체크합니다.".equals(post.getTitle()))
                .findFirst()
                .orElseGet(() -> postRepository.save(post1));

        Post post2 = Post.builder()
                .member(memberA)
                .title("프리웨이트 존 벤치프레스 사용 시간 질문")
                .content("원판 안 치우고 가는 사람들 신고 가능한가요? 랙 30분 이상 독점도 선 넘는 거 같습니다.")
                .boardType(BoardType.GLOBAL)
                .category(PostCategory.QUESTION)
                .anonymousYn("Y")
                .build();
        postRepository.findAll().stream()
                .filter(post -> "프리웨이트 존 벤치프레스 사용 시간 질문".equals(post.getTitle()))
                .findFirst()
                .orElseGet(() -> postRepository.save(post2));

        // 4. 테스트 댓글 생성
        Comment comment1 = Comment.builder()
                .post(savedPost1)
                .member(memberB)
                .content("제육에 닭가슴살 100g 챙겨가서 비벼 먹으면 벌크업 식단 뚝딱입니다.")
                .anonymousYn("N")
                .build();
        Comment savedComment1 = commentRepository.findAll().stream()
                .filter(comment -> "제육에 닭가슴살 100g 챙겨가서 비벼 먹으면 벌크업 식단 뚝딱입니다.".equals(comment.getContent()))
                .findFirst()
                .orElseGet(() -> commentRepository.save(comment1));

        // 5. 대댓글 생성
        Comment reply1 = Comment.builder()
                .post(savedPost1)
                .member(memberA)
                .parentComment(savedComment1)
                .content("오, 내일 바로 실행해 보겠습니다. 꿀팁 감사합니다.")
                .anonymousYn("N")
                .build();
        commentRepository.findAll().stream()
                .filter(comment -> "오, 내일 바로 실행해 보겠습니다. 꿀팁 감사합니다.".equals(comment.getContent()))
                .findFirst()
                .orElseGet(() -> commentRepository.save(reply1));

        System.out.println("========== 더미 데이터 세팅 완료 ==========");
    }

    private Member getOrCreateMember(String loginId, String email) {
        return memberRepository.findByLoginId(loginId)
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .loginId(loginId)
                                .passwordHash(passwordEncoder.encode("qwer1234"))
                                .email(email)
                                .accountStatus(AccountStatus.ACTIVE)
                                .role(MemberRole.USER)
                                .build()
                ));
    }

    private UserProfile getOrCreateProfile(
            Member member,
            School school,
            String name,
            String nickname,
            String department,
            LocalDate birthDate
    ) {
        return userProfileRepository.findByMember(member)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .member(member)
                                .name(name)
                                .nickname(nickname)
                                .department(department)
                                .gender(Gender.M)
                                .birthDate(birthDate)
                                .noShowCount(0)
                                .mannerTemperature(new BigDecimal("36.5"))
                                .school(school)
                                .build()
                ));
    }
}
