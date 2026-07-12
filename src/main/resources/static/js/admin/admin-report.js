let currentReportPage = 0;
const reportPageSize = 10;

// 신고 목록 초기 조회
document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    const status = params.get("status");
    const targetType = params.get("targetType");
    if (status && document.querySelector(`#statusFilter option[value="${status}"]`)) {
        document.getElementById("statusFilter").value = status;
    }
    if (targetType && document.querySelector(`#targetTypeFilter option[value="${targetType}"]`)) {
        document.getElementById("targetTypeFilter").value = targetType;
    }
    fetchReports(0);
    const reportId = params.get("reportId");
    if (reportId && /^\d+$/.test(reportId)) {
        openReportDetail(Number(reportId));
    }
});

// 신고 목록 검색 페이지 조회
function fetchReports(page = 0) {
    currentReportPage = page;

    const status = document.getElementById("statusFilter").value;
    const targetType = document.getElementById("targetTypeFilter").value;
    const keyword = document.getElementById("reportKeywordInput").value;

    const url = `/api/admin/reports?status=${status}&targetType=${targetType}&keyword=${encodeURIComponent(keyword)}&page=${page}&size=${reportPageSize}`;

    fetch(url)
        .then(response => {
            if (!response.ok) {
                throw new Error("신고 목록 조회 실패");
            }
            return response.json();
        })
        .then(data => {
            renderReports(data.content);
            renderReportPagination(data);
        })
        .catch(error => {
            console.error(error);
            alert("신고 목록 조회 중 오류가 발생했습니다.");
        });
}

// 신고 목록 출력
function renderReports(reports) {
    const tbody = document.getElementById("reportTableBody");
    tbody.innerHTML = "";

    if (!reports || reports.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="11">조회된 신고가 없습니다.</td>
            </tr>
        `;
        return;
    }

    reports.forEach(report => {
        const processedBy = report.processorType === "AI" ? "Mock AI" : (report.processedByLoginId || "-");
        const processedAt = report.processedAt || "-";

        tbody.innerHTML += `
            <tr>
                <td>${report.reportId}</td>
                <td>${report.reporterLoginId}</td>
                <td>${renderTargetType(report.targetType)}</td>
                <td>${report.targetId}</td>
                <td>${report.sameTargetReportCount || 1}건</td>
                <td>${report.reason}</td>
                <td>${renderReportStatusBadge(report.status)}</td>
                <td>${processedBy}</td>
                <td>${processedAt}</td>
                <td>
                    <button class="action-btn" onclick="openReportDetail(${report.reportId})">상세</button>
                </td>
                <td class="action-cell">
                    <div class="action-group">
                        ${renderReportActionButtons(report)}
                    </div>
                </td>
            </tr>
        `;
    });
}

// 신고 대상 표시
function renderTargetType(targetType) {
    if (targetType === "POST") return "게시글";
    if (targetType === "COMMENT") return "댓글";
    if (targetType === "CHAT_MESSAGE") return "채팅 메시지";
    if (targetType === "CHAT_MEMBER") return "채팅 상대";
    if (targetType === "REVIEW") return "리뷰";
    return targetType;
}

// 신고 상태 뱃지 출력
function renderReportStatusBadge(status) {
    if (status === "PENDING") {
        return `<span class="badge pending">대기</span>`;
    }

    if (status === "PROCESSING") {
        return `<span class="badge penalty">처리중</span>`;
    }

    if (status === "RESOLVED") {
        return `<span class="badge normal">처리완료</span>`;
    }

    if (status === "REJECTED") {
        return `<span class="badge banned">반려</span>`;
    }

    if (status === "CANCELED") {
        return `<span class="badge">처리취소</span>`;
    }

    return `<span class="badge">${status}</span>`;
}

// 검색 엔터 처리
function handleReportSearchEnter(event) {
    if (event.key === "Enter") {
        fetchReports(0);
    }
}

// 페이지 버튼 출력
function renderReportPagination(pageData) {
    const area = document.getElementById("reportPaginationArea");
    area.innerHTML = "";

    if (!pageData || pageData.totalPages <= 1) {
        return;
    }

    let html = "";

    html += `
        <button class="page-btn" onclick="fetchReports(${pageData.page - 1})" ${pageData.first ? "disabled" : ""}>
            이전
        </button>
    `;

    for (let i = 0; i < pageData.totalPages; i++) {
        html += `
            <button class="page-btn ${i === pageData.page ? "active" : ""}" onclick="fetchReports(${i})">
                ${i + 1}
            </button>
        `;
    }

    html += `
        <button class="page-btn" onclick="fetchReports(${pageData.page + 1})" ${pageData.last ? "disabled" : ""}>
            다음
        </button>
    `;

    area.innerHTML = html;
}

// 신고 상세 조회
function openReportDetail(reportId) {
    fetch(`/api/admin/reports/${reportId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error("신고 상세 조회 실패");
            }
            return response.json();
        })
        .then(report => {
            renderReportDetail(report);
            document.getElementById("reportDetailModal").classList.add("active");
        })
        .catch(error => {
            console.error(error);
            alert("신고 상세 조회 중 오류가 발생했습니다.");
        });
}

// 신고 상세 모달 출력
function renderReportDetail(report) {
    const body = document.getElementById("reportDetailBody");

    body.innerHTML = `
        <div class="detail-summary">
            <div>
                <p class="detail-summary-label">신고</p>
                <h4>#${report.reportId}</h4>
                <span>${renderTargetType(report.targetType)}</span>
            </div>
            ${renderReportDetailStatus(report.status)}
        </div>

        <div class="detail-section">
            <h4 class="detail-section-title">신고 정보</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <span class="detail-label">신고번호</span>
                    <strong>${report.reportId}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">신고상태</span>
                    <strong>${report.status}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">신고자 아이디</span>
                    <strong>${report.reporterLoginId}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">신고자 이메일</span>
                    <strong>${report.reporterEmail || "-"}</strong>
                </div>
            </div>
        </div>

        <div class="detail-section">
            <h4 class="detail-section-title">대상 정보</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <span class="detail-label">대상 유형</span>
                    <strong>${renderTargetType(report.targetType)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">대상 번호</span>
                    <strong>${report.targetId}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">처리자</span>
                    <strong>${report.processorType === "AI" ? "Mock AI" : (report.processedByLoginId || "-")}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">처리일시</span>
                    <strong>${report.processedAt || "-"}</strong>
                </div>
            </div>
        </div>
        
        <div class="detail-section">
            <h4 class="detail-section-title">신고 대상 내용</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <span class="detail-label">대상 제목</span>
                    <strong>${report.targetTitle || "-"}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">작성자</span>
                    <strong>${report.targetWriterLoginId || "-"}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">대상 상태</span>
                    <strong>${report.targetStatus || "-"}</strong>
                </div>
            </div>

            <div class="detail-content-box">
                ${report.targetContent || "-"}
            </div>
            
            ${renderAttachmentImages(report.attachments)}
        </div>

        <div class="detail-section">
            <h4 class="detail-section-title">신고 사유</h4>
            <div class="detail-reason-box">
                ${report.reason || "-"}
            </div>
        </div>
        <div class="detail-section">
            <h4 class="detail-section-title">처리 사유</h4>
            <div class="detail-reason-box">
                ${report.processedReason || "-"}
            </div>
        </div>

        ${renderAiAnalysis(report.aiAnalysis, report.attachments)}

        ${renderDetailCancelButton(report)}
    `;
}

function renderAiAnalysis(ai, attachments) {
    if (!ai) {
        return `
            <div class="detail-section">
                <h4 class="detail-section-title">AI 자동 분석</h4>
                <div class="detail-reason-box">AI 분석 결과가 없습니다.</div>
            </div>
        `;
    }

    const policyCodes = Array.isArray(ai.policyCodes) && ai.policyCodes.length > 0
        ? ai.policyCodes.map(escapeAdminHtml).join(", ")
        : "-";
    const attachmentNotice = Array.isArray(attachments) && attachments.length > 0
        ? `<div class="detail-reason-box">첨부 이미지는 AI 분석 대상이 아닙니다. 텍스트만 분석했습니다.</div>`
        : "";

    return `
        <div class="detail-section">
            <h4 class="detail-section-title">AI 자동 분석</h4>
            <div class="detail-grid">
                <div class="detail-item"><span class="detail-label">유형</span><strong>${escapeAdminHtml(ai.category)}</strong></div>
                <div class="detail-item"><span class="detail-label">판정</span><strong>${escapeAdminHtml(ai.decision)}</strong></div>
                <div class="detail-item"><span class="detail-label">처리 상태</span><strong>${escapeAdminHtml(ai.processingStatus)}</strong></div>
                <div class="detail-item"><span class="detail-label">서버 규칙 일치</span><strong>${ai.ruleMatched ? "Y" : "N"}</strong></div>
                <div class="detail-item"><span class="detail-label">위험도</span><strong>${escapeAdminHtml(ai.riskScore)}</strong></div>
                <div class="detail-item"><span class="detail-label">판단 확실성</span><strong>${escapeAdminHtml(ai.confidenceScore)}</strong></div>
                <div class="detail-item"><span class="detail-label">정책 코드</span><strong>${policyCodes}</strong></div>
                <div class="detail-item"><span class="detail-label">모델</span><strong>${escapeAdminHtml(ai.modelName)}</strong></div>
            </div>
            <div class="detail-reason-box">${escapeAdminHtml(ai.reasonSummary)}</div>
            ${attachmentNotice}
        </div>
    `;
}

function escapeAdminHtml(value) {
    return String(value ?? "-")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

// 상세 모달 처리 취소 버튼 출력
function renderDetailCancelButton(report) {
    if (report.status === "PENDING" || report.status === "CANCELED") {
        return "";
    }

    return `
        <div class="detail-footer-actions">
            <button class="btn-cancel" onclick="closeReportDetail()">닫기</button>
            <button class="btn-danger" onclick="openReportCancelModal(${report.reportId})">처리 취소</button>
        </div>
    `;
}

// 신고 상세 상태 출력
function renderReportDetailStatus(status) {
    if (status === "PENDING") {
        return `<span class="detail-status danger">대기</span>`;
    }

    if (status === "PROCESSING") {
        return `<span class="detail-status danger">처리중</span>`;
    }

    if (status === "RESOLVED") {
        return `<span class="detail-status good">처리완료</span>`;
    }

    if (status === "REJECTED") {
        return `<span class="detail-status good">반려</span>`;
    }

    if (status === "CANCELED") {
        return `<span class="detail-status">처리취소</span>`;
    }

    return `<span class="detail-status">${status}</span>`;
}

// 신고 상세 모달 닫기
function closeReportDetail() {
    document.getElementById("reportDetailModal").classList.remove("active");
}

// 신고 처리 버튼 출력
function renderReportActionButtons(report) {
    if (report.status === "PENDING") {
        return `
            <button class="action-btn" onclick="changeReportStatus(${report.reportId}, 'processing')">처리중</button>
            <button class="action-btn" onclick="changeReportStatus(${report.reportId}, 'resolve')">완료</button>
            <button class="action-btn" onclick="changeReportStatus(${report.reportId}, 'reject')">반려</button>
        `;
    }

    if (report.status === "PROCESSING") {
        return `
            <button class="action-btn" onclick="changeReportStatus(${report.reportId}, 'resolve')">완료</button>
            <button class="action-btn" onclick="changeReportStatus(${report.reportId}, 'reject')">반려</button>
            <button class="action-btn cancel" onclick="openReportCancelModal(${report.reportId})">취소</button>
        `;
    }

    if (report.status === "RESOLVED" || report.status === "REJECTED") {
        return `
            <button class="action-btn cancel" onclick="openReportCancelModal(${report.reportId})">처리취소</button>
        `;
    }

    return "-";
}

// 신고 상태 변경
function changeReportStatus(reportId, action) {
    if (action === "processing") {
        processReportWithoutReason(reportId, action);
        return;
    }

    openReportProcessModal(reportId, action);
}

// 신고 처리 사유 모달 열기
function openReportProcessModal(reportId, action) {
    document.getElementById("processReportId").value = reportId;
    document.getElementById("processAction").value = action;
    document.getElementById("processedReasonInput").value = "";

    document.getElementById("reportProcessModal").classList.add("active");
}

// 신고 처리 사유 모달 닫기
function closeReportProcessModal() {
    document.getElementById("reportProcessModal").classList.remove("active");
}

// 신고 처리 사유 제출
function submitReportProcess() {
    const reportId = document.getElementById("processReportId").value;
    const action = document.getElementById("processAction").value;
    const processedReason = document.getElementById("processedReasonInput").value;

    if (!processedReason || processedReason.trim() === "") {
        alert("처리 사유는 필수입니다.");
        return;
    }

    processReportWithReason(reportId, action, processedReason);
}

// 신고 처리중 변경
function processReportWithoutReason(reportId, action) {
    fetch(`/api/admin/reports/${reportId}/${action}`, {
        method: "POST"
    })
        .then(response => {
            if (!response.ok) {
                throw new Error("신고 상태 변경 실패");
            }

            alert("신고 상태가 변경되었습니다.");
            fetchReports(currentReportPage);
        })
        .catch(error => {
            console.error(error);
            alert("신고 상태 변경 중 오류가 발생했습니다.");
        });
}

// 신고 완료/반려 처리
function processReportWithReason(reportId, action, processedReason) {
    fetch(`/api/admin/reports/${reportId}/${action}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            processedReason: processedReason
        })
    })
        .then(response => {
            if (!response.ok) {
                throw new Error("신고 상태 변경 실패");
            }

            closeReportProcessModal();
            alert("신고 상태가 변경되었습니다.");
            fetchReports(currentReportPage);
        })
        .catch(error => {
            console.error(error);
            alert("신고 상태 변경 중 오류가 발생했습니다.");
        });
}

// 신고 처리 취소 모달 열기
function openReportCancelModal(reportId) {
    document.getElementById("cancelReportId").value = reportId;
    document.getElementById("cancelReasonInput").value = "";

    document.getElementById("reportCancelModal").classList.add("active");
}

// 신고 처리 취소 모달 닫기
function closeReportCancelModal() {
    document.getElementById("reportCancelModal").classList.remove("active");
}

// 신고 처리 취소 제출
function submitReportCancel() {
    const reportId = document.getElementById("cancelReportId").value;
    const cancelReason = document.getElementById("cancelReasonInput").value;

    if (!confirm("신고 처리를 취소하고 이전 상태로 복구하시겠습니까?")) {
        return;
    }

    cancelReportProcess(reportId, cancelReason);
}

// 신고 처리 취소 요청
function cancelReportProcess(reportId, cancelReason) {
    fetch(`/api/admin/reports/${reportId}/cancel`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            cancelReason: cancelReason
        })
    })
        .then(response => {
            if (!response.ok) {
                throw new Error("신고 처리 취소 실패");
            }

            closeReportCancelModal();
            closeReportDetail();
            alert("신고 처리가 취소되었습니다.");
            fetchReports(currentReportPage);
        })
        .catch(error => {
            console.error(error);
            alert("신고 처리 취소 중 오류가 발생했습니다.");
        });
}

// 첨부 사진 섹션 출력
function renderAttachmentImages(attachments) {
    if (!attachments || attachments.length === 0) {
        return "";
    }

    let html = `
        <div class="detail-section">
            <h4 class="detail-section-title">첨부 사진</h4>
            <div class="attachment-grid">
    `;

    attachments.forEach(file => {
        const imageUrl = file.imageUrl || `/api/images/${file.fileId}`;
        const originalName = file.originalName || "첨부 이미지";

        html += `
            <div class="attachment-image-card">
                <img src="${imageUrl}" alt="${originalName}">
            </div>
        `;
    });

    html += `
            </div>
        </div>
    `;

    return html;
}
