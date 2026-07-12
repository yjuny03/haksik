package com.example.haksikmokjang.report.repository;


import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.report.domain.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 개발용 fixture 중복 생성을 막기 위한 전체 이력 조회. 실제 신고 접수 정책에는 사용하지 않는다.
    boolean existsByReporterAndTargetTypeAndTargetId(Member reporter, String targetType, Long targetId);

    @Query("""
        select count(r)
        from Report r
        where r.reporter = :reporter
          and r.targetType = :targetType
          and r.targetId = :targetId
          and r.status in :statuses
          and not exists (
              select h.reportProcessHistoryId
              from ReportProcessHistory h
              where h.report = r and h.canceledYn = 'Y'
          )
        """)
    long countActiveDuplicateReports(
            @Param("reporter") Member reporter,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("statuses") Collection<ReportStatus> statuses
    );

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    long countByStatus(ReportStatus status);

    List<Report> findByOrderByReportIdDesc(Limit limit);

    @Query("""
        select r.targetType as targetType, r.targetId as targetId, count(r) as reportCount
        from Report r
        group by r.targetType, r.targetId
        having count(r) > 1
        order by count(r) desc
        """)
    List<DuplicateTargetSummary> findMostReportedTargets(Pageable pageable);

    interface DuplicateTargetSummary {
        String getTargetType();
        Long getTargetId();
        long getReportCount();
    }

    // 관리자 신고 목록 검색 페이지 조회
    @Query("""
        select r
        from Report r
        join r.reporter reporter
        where (:status is null or r.status = :status)
          and (:targetType is null or r.targetType = :targetType)
          and (
              :keyword is null
              or :keyword = ''
              or reporter.loginId like concat('%', :keyword, '%')
              or r.reason like concat('%', :keyword, '%')
          )
        """)
    Page<Report> searchReports(
            @Param("status") ReportStatus status,
            @Param("targetType") String targetType,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
