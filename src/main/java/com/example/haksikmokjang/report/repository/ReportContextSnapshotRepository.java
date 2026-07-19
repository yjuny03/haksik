package com.example.haksikmokjang.report.repository;

import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportContextSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportContextSnapshotRepository extends JpaRepository<ReportContextSnapshot, Long> {

    Optional<ReportContextSnapshot> findByReport(Report report);

    boolean existsByReport(Report report);
}
