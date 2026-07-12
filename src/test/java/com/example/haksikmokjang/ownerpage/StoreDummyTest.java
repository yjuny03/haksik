package com.example.haksikmokjang.ownerpage;

import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.AccountStatus;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.ownerpage.store.domain.BusinessStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class StoreDummyTest {

    @Autowired StoreRepository storeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    @Rollback(false) // 🚨 팩트: DB에 영구적으로 박아 넣습니다.
    @DisplayName("5명의 점주 계정(01~05)에 각각 가게 1개씩 강제 주입")
    void insertStoresForFiveOwners() {

        // 1. 타겟팅: 없으면 만들고 있으면 재사용합니다.
        Member owner1 = getOrCreateOwner("ownerpage01", 1);
        Member owner2 = getOrCreateOwner("ownerpage02", 2);
        Member owner3 = getOrCreateOwner("ownerpage03", 3);
        Member owner4 = getOrCreateOwner("ownerpage04", 4);
        Member owner5 = getOrCreateOwner("ownerpage05", 5);

        // 2. 타점 1: 광운대역 초근접 (ownerpage01 소유)
        Store store1 = Store.builder()
                .member(owner1)
                .name("광운대역 500m 돈까스")
                .category("일식")
                .address("서울특별시 노원구 광운로 20")
                .latitude(37.6250)
                .longitude(127.0630)
                .phone("02-111-1111")
                .operatingHours("10:00 - 22:00")
                .businessStatus(BusinessStatus.OPEN)
                .build();

        // 3. 타점 2: 반경 1.5km 석계역 방향 (ownerpage02 소유)
        Store store2 = Store.builder()
                .member(owner2)
                .name("석계역 1.5km 짬뽕")
                .category("중식")
                .address("서울특별시 노원구 화랑로 40")
                .latitude(37.6150)
                .longitude(127.0700)
                .phone("02-222-2222")
                .operatingHours("11:00 - 21:00")
                .businessStatus(BusinessStatus.OPEN)
                .build();

        // 4. 타점 3: 반경 2.5km 하계역 방향 (ownerpage03 소유)
        Store store3 = Store.builder()
                .member(owner3)
                .name("하계역 2.5km 제육볶음")
                .category("한식")
                .address("서울특별시 노원구 공릉로 58")
                .latitude(37.6400)
                .longitude(127.0600)
                .phone("02-333-3333")
                .operatingHours("09:00 - 20:00")
                .businessStatus(BusinessStatus.OPEN)
                .build();

        // 5. 타점 4: 반경 1km 월계역 방향 (ownerpage04 소유)
        Store store4 = Store.builder()
                .member(owner4)
                .name("월계역 1km 마라탕")
                .category("중식")
                .address("서울특별시 노원구 월계로 370")
                .latitude(37.6300)
                .longitude(127.0580)
                .phone("02-444-4444")
                .operatingHours("11:00 - 23:00")
                .businessStatus(BusinessStatus.OPEN)
                .build();

        // 6. 타점 5: 반경 2km 공릉역 방향 (ownerpage05 소유)
        Store store5 = Store.builder()
                .member(owner5)
                .name("공릉역 2km 국밥")
                .category("한식")
                .address("서울특별시 노원구 동일로 192길")
                .latitude(37.6270)
                .longitude(127.0720)
                .phone("02-555-5555")
                .operatingHours("00:00 - 24:00")
                .businessStatus(BusinessStatus.OPEN)
                .build();

        // 7. 재실행 시 같은 점주의 기존 가게를 재사용합니다.
        saveIfOwnerHasNoStore(owner1, store1);
        saveIfOwnerHasNoStore(owner2, store2);
        saveIfOwnerHasNoStore(owner3, store3);
        saveIfOwnerHasNoStore(owner4, store4);
        saveIfOwnerHasNoStore(owner5, store5);

        System.out.println("🚨 5명의 점주에게 각 1개씩, 총 5개의 가게 주입 완료! 지도를 켜십시오.");
    }

    private Member getOrCreateOwner(String loginId, int index) {
        return memberRepository.findByLoginId(loginId)
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .loginId(loginId)
                                .passwordHash(passwordEncoder.encode("1234"))
                                .email(loginId + "@test.com")
                                .phone("010-9100-" + String.format("%04d", index))
                                .role(MemberRole.OWNER)
                                .accountStatus(AccountStatus.ACTIVE)
                                .build()
                ));
    }

    private void saveIfOwnerHasNoStore(Member owner, Store store) {
        storeRepository.findByMember_LoginId(owner.getLoginId())
                .orElseGet(() -> storeRepository.save(store));
    }
}
