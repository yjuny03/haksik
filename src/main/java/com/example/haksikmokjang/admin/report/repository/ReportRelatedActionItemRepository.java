package com.example.haksikmokjang.admin.report.repository;

import com.example.haksikmokjang.admin.report.domain.ReportRelatedActionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRelatedActionItemRepository extends JpaRepository<ReportRelatedActionItem, Long> {

    boolean existsByReportIdAndTargetTypeAndTargetIdAndActionType(
            Long reportId,
            String targetType,
            Long targetId,
            String actionType
    );

    List<ReportRelatedActionItem> findByReportIdOrderByIdAsc(Long reportId);

    List<ReportRelatedActionItem> findByReportIdAndRestoredFalseOrderByIdAsc(Long reportId);
}
