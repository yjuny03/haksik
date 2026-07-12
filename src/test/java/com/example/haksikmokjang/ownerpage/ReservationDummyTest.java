package com.example.haksikmokjang.ownerpage;

import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.AccountStatus;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.notification.service.NotificationService;
import com.example.haksikmokjang.ownerpage.store.domain.BusinessStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Reservation;
import com.example.haksikmokjang.ownerpage.store.domain.ReservationStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.repository.ReservationRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@SpringBootTest
class ReservationDummyTest {

    @Autowired StoreRepository storeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired NotificationService notificationService;
    @Autowired PasswordEncoder passwordEncoder;
    @Test
    @Transactional
    @Rollback(false) // 🚨 팩트: 롤백을 끄고 DB에 영구적으로 때려 박습니다.
    @DisplayName("user1이 owner1의 가게에 신규 예약 3건 강제 주입")
    void insertRequestedReservations() {

        // 1. 타겟팅 1: 예약을 신청할 특정 유저를 없으면 만들고 있으면 재사용합니다.
        Member user = getOrCreateMember("user1", MemberRole.USER, 1);

        // 2. 타겟팅 2: 타겟 점주를 없으면 만들고 있으면 재사용합니다.
        Member owner = getOrCreateMember("owner1", MemberRole.OWNER, 1);

        // 3. 타겟팅 3: owner1이 소유한 가게를 없으면 만들고 있으면 재사용합니다.
        Store store = storeRepository.findByMember_LoginId(owner.getLoginId())
                .orElseGet(() -> storeRepository.save(
                        Store.builder()
                                .member(owner)
                                .name("광운대역 500m 돈까스")
                                .category("일식")
                                .address("서울특별시 노원구 광운로 20")
                                .latitude(37.6250)
                                .longitude(127.0630)
                                .phone("02-111-1111")
                                .operatingHours("10:00 - 22:00")
                                .businessStatus(BusinessStatus.OPEN)
                                .build()
                ));

        // 4. 상태가 'REQUESTED(승인 대기)'인 예약 3건을 미래 시간으로 세팅하여 주입
        for (int i = 1; i <= 3; i++) {
            String memo = "[예약 더미] owner1 신규 예약 " + i;
            boolean exists = reservationRepository.findAll().stream()
                    .anyMatch(reservation -> memo.equals(reservation.getRequestMemo()));
            if (exists) {
                continue;
            }

            Reservation res = Reservation.builder()
                    .member(user)
                    .store(store)
                    .reservationAt(LocalDateTime.now().plusDays(i).withSecond(0).withNano(0))
                    .peopleCount(2)
                    .requestMemo(memo)
                    .status(ReservationStatus.REQUESTED)
                    .build();
            reservationRepository.save(res);

            // 🚨 팩트: 더미 예약이 꽂힐 때 점주에게 강제로 알림도 같이 꽂아버립니다.
            notificationService.sendNotification(
                    store.getMember(), // 수신자: 점주
                    "RESERVATION",     // 타입
                    "새 예약 접수",      // 알림 제목
                    "새로운 예약 요청이 들어왔습니다.", // 알림 내용
                    "RESERVATION",     // 타겟 타입
                    res.getReservationId() // 타겟 ID
            );
        }
    }

    private Member getOrCreateMember(String loginId, MemberRole role, int index) {
        return memberRepository.findByLoginId(loginId)
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .loginId(loginId)
                                .passwordHash(passwordEncoder.encode("1234"))
                                .email(loginId + "@test.com")
                                .phone("010-9300-" + String.format("%04d", index))
                                .role(role)
                                .accountStatus(AccountStatus.ACTIVE)
                                .build()
                ));
    }
}
