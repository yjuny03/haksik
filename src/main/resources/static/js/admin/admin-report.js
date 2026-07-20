let currentReportPage = 0;
let selectedReportId = null;
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
                <td colspan="8">조회된 신고가 없습니다.</td>
            </tr>
        `;
        return;
    }

    reports.forEach(report => {
        const selectedClass = Number(report.reportId) === Number(selectedReportId) ? "selected-report-row" : "";
        tbody.innerHTML += `
            <tr class="${selectedClass}" data-report-id="${escapeAdminHtml(report.reportId)}">
                <td><strong class="report-id">#${escapeAdminHtml(report.reportId)}</strong></td>
                <td>
                    <div class="report-target-cell">
                        <span>${renderTargetType(report.targetType)}</span>
                        <small>#${escapeAdminHtml(report.targetId)} · 동일 대상 ${Number(report.sameTargetReportCount || 1)}건</small>
                    </div>
                </td>
                <td>${renderReasonPill(report.reason)}</td>
                <td>${renderListDecisionBadge(report)}</td>
                <td>${renderReportStatusBadge(report.status)}</td>
                <td>${formatAdminDate(report.createdAt || report.reportedAt || report.processedAt)}</td>
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
    if (targetType === "MEMBER") return "회원";
    if (targetType === "USER") return "회원";
    if (targetType === "REVIEW") return "리뷰";
    return targetType;
}

// 신고 상태 뱃지 출력
function renderReportStatusBadge(status) {
    const labels = {
        PENDING: "접수됨",
        PROCESSING: "처리 중",
        RESOLVED: "처리 완료",
        REJECTED: "반려됨",
        CANCELED: "취소됨"
    };
    const tones = {
        PENDING: "pending",
        PROCESSING: "review",
        RESOLVED: "success",
        REJECTED: "danger",
        CANCELED: "restored"
    };
    return `<span class="badge ${tones[status] || ""}">${labels[status] || escapeAdminHtml(status)}</span>`;
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
    selectedReportId = reportId;
    markSelectedReportRow(reportId);
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
    const snapshot = parseContextSnapshot(report.contextSnapshotJson);
    const targetSummary = snapshot && snapshot.reportedTarget ? snapshot.reportedTarget : {};

    body.innerHTML = `
        <div class="detail-summary">
            <div>
                <p class="detail-summary-label">신고</p>
                <h4>#${escapeAdminHtml(report.reportId)}</h4>
                <span>${renderTargetType(report.targetType)}</span>
            </div>
            ${renderReportDetailStatus(report.status)}
        </div>

        <div class="detail-section">
            <h4 class="detail-section-title">요약</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <span class="detail-label">신고번호</span>
                    <strong>#${escapeAdminHtml(report.reportId)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">처리 상태</span>
                    <strong>${statusLabel(report.status)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">신고 대상 유형</span>
                    <strong>${renderTargetType(report.targetType)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">접수 시간</span>
                    <strong>${formatAdminDate(report.createdAt || report.reportedAt || report.processedAt)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">신고자</span>
                    <strong>${escapeAdminHtml(report.reporterLoginId)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">피신고자</span>
                    <strong>${escapeAdminHtml(report.targetWriterLoginId || targetSummary.writerLoginId || targetSummary.senderLoginId || "-")}</strong>
                </div>
            </div>
        </div>

        ${renderAiAnalysis(report.aiAnalysis, report.attachments)}

        <div class="detail-section">
            <h4 class="detail-section-title">신고 대상</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <span class="detail-label">대상 번호</span>
                    <strong>#${escapeAdminHtml(report.targetId)}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">대상 제목</span>
                    <strong>${escapeAdminHtml(report.targetTitle || "-")}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">작성자</span>
                    <strong>${escapeAdminHtml(report.targetWriterLoginId || "-")}</strong>
                </div>
                <div class="detail-item">
                    <span class="detail-label">대상 상태</span>
                    <strong>${targetStatusLabel(report.targetStatus)}</strong>
                </div>
            </div>

            <div class="detail-content-box reported-target-card">
                ${escapeAdminHtml(report.targetContent || "-")}
            </div>
            
            ${renderAttachmentImages(report.attachments)}
        </div>

        ${renderContextSnapshot(report)}

        ${renderRelatedActionItems(report.relatedActionItems)}

        <div class="detail-section">
            <h4 class="detail-section-title">신고 사유</h4>
            <div class="detail-reason-box">
                ${escapeAdminHtml(report.reason || "-")}
            </div>
        </div>

        ${renderProcessHistory(report)}

        ${renderDetailCancelButton(report)}
    `;
}

function markSelectedReportRow(reportId) {
    document.querySelectorAll("#reportTableBody tr").forEach(row => {
        row.classList.toggle("selected-report-row", row.dataset.reportId === String(reportId));
    });
}

function renderContextSnapshot(report) {
    if (report.targetType !== "COMMENT" && report.targetType !== "CHAT_MESSAGE") {
        return "";
    }

    const snapshot = parseContextSnapshot(report.contextSnapshotJson);
    if (!snapshot || !snapshot.context) {
        return "";
    }

    if (report.targetType === "COMMENT") {
        return renderCommentContextSnapshot(snapshot);
    }

    if (report.targetType === "CHAT_MESSAGE") {
        return renderChatMessageContextSnapshot(snapshot);
    }

    return "";
}

function parseContextSnapshot(snapshotJson) {
    if (!snapshotJson) {
        return null;
    }

    try {
        return JSON.parse(snapshotJson);
    } catch (error) {
        console.error("신고 맥락 스냅샷 파싱 실패", error);
        return null;
    }
}

function renderCommentContextSnapshot(snapshot) {
    const post = snapshot.context.post || {};
    const comments = Array.isArray(snapshot.context.comments) ? snapshot.context.comments : [];

    const commentHtml = comments.length > 0
        ? comments.map(comment => `
            <div class="context-timeline-item ${comment.reportedTarget ? "reported-context-target" : ""}">
                <div class="context-timeline-meta">
                    <strong>#${escapeAdminHtml(comment.commentId)}</strong>
                    ${comment.reportedTarget ? `<span class="badge danger">신고 대상 댓글</span>` : `<span class="badge subtle">주변 댓글</span>`}
                    <small>${escapeAdminHtml(comment.writerLoginId)} · ${formatAdminDate(comment.createdAt)}</small>
                </div>
                <div>${escapeAdminHtml(comment.content)}</div>
            </div>
        `).join("")
        : `<div class="detail-reason-box">저장된 주변 댓글이 없습니다.</div>`;

    return `
        <div class="detail-section">
            <h4 class="detail-section-title">신고 접수 시점 맥락</h4>
            <div class="detail-reason-box">
                <strong>부모 게시글 #${escapeAdminHtml(post.postId)}</strong>
                <div class="context-muted">제목: ${escapeAdminHtml(post.title)}</div>
                <div class="context-muted">작성자: ${escapeAdminHtml(post.writerLoginId)}</div>
                <div>${escapeAdminHtml(post.content)}</div>
            </div>
            <div class="context-timeline">${commentHtml}</div>
        </div>
    `;
}

function renderChatMessageContextSnapshot(snapshot) {
    const messages = Array.isArray(snapshot.context.messages) ? snapshot.context.messages : [];

    const messageHtml = messages.length > 0
        ? messages.map(message => `
            <div class="context-timeline-item ${message.reportedTarget ? "reported-context-target" : ""}">
                <div class="context-timeline-meta">
                    <strong>#${escapeAdminHtml(message.chatMessageId)}</strong>
                    ${message.reportedTarget ? `<span class="badge danger">신고된 메시지</span>` : `<span class="badge subtle">주변 메시지</span>`}
                    <small>${escapeAdminHtml(message.senderLoginId)} · ${formatAdminDate(message.createdAt)} · ${messageTypeLabel(message.messageType)}</small>
                </div>
                <div>${escapeAdminHtml(message.message)}</div>
            </div>
        `).join("")
        : `<div class="detail-reason-box">저장된 주변 채팅 메시지가 없습니다.</div>`;

    return `
        <div class="detail-section">
            <h4 class="detail-section-title">신고 접수 시점 맥락</h4>
            <div class="context-timeline">${messageHtml}</div>
        </div>
    `;
}

function renderRelatedActionItems(items) {
    const relatedItems = Array.isArray(items) ? items : [];
    if (relatedItems.length === 0) {
        return "";
    }

    return `
        <div class="detail-section">
            <h4 class="detail-section-title">함께 블라인드된 관련 항목 ${relatedItems.length}건</h4>
            <div class="context-timeline">
                ${relatedItems.map(item => `
                    <div class="context-timeline-item ${item.restored ? "" : "reported-context-target"}">
                        <div class="context-timeline-meta">
                            <strong>${targetTypeLabel(item.targetType)} #${escapeAdminHtml(item.targetId)}</strong>
                            <span class="badge ${item.restored ? "restored" : "success"}">${item.restored ? "복구 완료" : "블라인드됨"}</span>
                            <small>${actionTypeLabel(item.actionType)} · ${formatAdminDate(item.createdAt)}</small>
                        </div>
                        <div>${escapeAdminHtml(item.reason || "-")}</div>
                    </div>
                `).join("")}
            </div>
        </div>
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
        ? ai.policyCodes.map(policyCodeLabel).join(", ")
        : "-";
    const attachmentNotice = Array.isArray(attachments) && attachments.length > 0
        ? `<div class="detail-reason-box">첨부 이미지는 AI 분석 대상이 아닙니다. 텍스트만 분석했습니다.</div>`
        : "";
    const techDetail = ai.processingStatus === "FAILED"
        ? ""
        : `
            <details class="tech-detail">
                <summary>기술 상세</summary>
                <div>모델: ${escapeAdminHtml(ai.modelName)}</div>
                <div>AI 원문 요약: ${escapeAdminHtml(sanitizeAiReasonForDisplay(ai))}</div>
            </details>
        `;

    return `
        <div class="detail-section ai-result-card">
            <h4 class="detail-section-title">AI 자동 분석</h4>
            <div class="detail-grid">
                <div class="detail-item"><span class="detail-label">신고 유형</span><strong>${categoryLabel(ai.category)}</strong></div>
                <div class="detail-item"><span class="detail-label">판정</span><strong>${decisionLabel(ai.decision)}</strong></div>
                <div class="detail-item"><span class="detail-label">처리 상태</span><strong>${processingStatusLabel(ai.processingStatus)}</strong></div>
                <div class="detail-item"><span class="detail-label">자동 처리 기준</span><strong>${autoHideCriteriaLabel(ai)}</strong></div>
                <div class="detail-item"><span class="detail-label">위험도</span><strong>${formatPercent(ai.riskScore)}</strong></div>
                <div class="detail-item"><span class="detail-label">판단 확실성</span><strong>${formatPercent(ai.confidenceScore)}</strong></div>
                <div class="detail-item"><span class="detail-label">정책 코드</span><strong>${policyCodes}</strong></div>
            </div>
            <div class="detail-reason-box">${escapeAdminHtml(aiFinalDescription(ai))}</div>
            ${techDetail}
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

function categoryLabel(value) {
    return ({
        SPAM_AD: "광고/스팸",
        ABUSE: "욕설/비방",
        PERSONAL_INFO: "개인정보 노출",
        FLOODING: "도배",
        DEFAMATION: "명예훼손",
        SEXUAL_HARASSMENT: "성희롱",
        OTHER: "기타",
        SAFE: "문제 없음"
    })[value] || escapeAdminHtml(value);
}

function decisionLabel(value) {
    return ({
        AUTO_HIDE: "자동 블라인드",
        REVIEW_REQUIRED: "관리자 검토 필요",
        NO_ACTION: "조치 없음"
    })[value] || escapeAdminHtml(value);
}

function processingStatusLabel(value) {
    return ({
        AUTO_PROCESSED: "자동 처리 완료",
        REVIEW_REQUIRED: "관리자 검토 필요",
        FAILED: "AI 분석 실패",
        RESTORED: "복구 완료"
    })[value] || escapeAdminHtml(value);
}

function statusLabel(value) {
    return ({
        PENDING: "접수됨",
        PROCESSING: "처리 중",
        RESOLVED: "처리 완료",
        REJECTED: "반려됨",
        CANCELED: "취소됨"
    })[value] || escapeAdminHtml(value);
}

function targetStatusLabel(value) {
    return ({
        ACTIVE: "게시 중",
        HIDDEN: "숨김",
        DELETE: "삭제됨",
        DELETED: "삭제됨",
        NOT_FOUND: "대상 없음"
    })[value] || escapeAdminHtml(value || "-");
}

function policyCodeLabel(value) {
    return ({
        "SPAM-COMMERCIAL": "상업성 광고",
        "SPAM-SUSPICIOUS-URL": "의심 URL",
        "PII-DETECTED": "개인정보 감지",
        "FLOOD-CONTENT-REPEAT": "반복 도배",
        "ABUSE-DIRECT": "직접 모욕",
        "ABUSE-SEVERE": "고강도 욕설",
        "SEXUAL-HARASSMENT-DIRECT": "직접 성희롱",
        "SEXUAL-HARASSMENT-SEVERE": "고강도 성희롱",
        "AI-PROVIDER-FAILURE": "AI 분석 실패",
        "IMAGE-NOT-ANALYZED": "이미지 미분석"
    })[value] || escapeAdminHtml(value);
}

function autoHideCriteriaLabel(ai) {
    if (!ai) {
        return "미충족";
    }
    if (ai.autoHideBasis) {
        return autoHideBasisLabel(ai.autoHideBasis);
    }
    if (ai.ruleMatched === true || ai.ruleMatched === "Y") {
        return "서버 규칙 충족";
    }
    if (isExcludedContextReview(ai)) {
        return "예외 맥락으로 관리자 검토";
    }
    if (isHighRiskReview(ai)) {
        return "고위험 검토 필요";
    }
    if (ai.processingStatus === "AUTO_PROCESSED") {
        return "AI 고확신 기준 충족";
    }
    return "미충족";
}

function autoHideBasisLabel(value) {
    return ({
        SERVER_RULE_MATCHED: "서버 규칙 충족",
        AI_HIGH_CONFIDENCE: "AI 고확신 기준 충족",
        EXCLUDED_CONTEXT_REVIEW_REQUIRED: "예외 맥락으로 관리자 검토",
        HIGH_RISK_REVIEW_REQUIRED: "고위험 검토 필요",
        REVIEW_REQUIRED: "관리자 검토 필요",
        NOT_MATCHED: "미충족",
        NOT_ELIGIBLE_CATEGORY: "자동 처리 제외 대상",
        AI_FAILED: "AI 분석 실패"
    })[value] || escapeAdminHtml(value);
}

function aiFinalDescription(ai) {
    if (!ai) {
        return "AI 분석 결과가 없습니다.";
    }
    if (isAiAutoProcessed(ai)) {
        return ai.autoHideBasis === "AI_HIGH_CONFIDENCE"
            ? "AI 고확신 기준으로 자동 블라인드 처리되었습니다."
            : "AI가 위반 가능성이 높다고 판단하여 자동 숨김 처리했습니다.";
    }
    if (ai.autoHideBasis === "EXCLUDED_CONTEXT_REVIEW_REQUIRED") {
        return "인용/피해 신고/예시 등 예외 문맥 가능성이 있어 관리자 검토가 필요합니다.";
    }
    if (ai.autoHideBasis === "HIGH_RISK_REVIEW_REQUIRED") {
        return "AI가 위험도가 높다고 판단했지만 자동 처리 조건을 충족하지 않아 관리자 검토가 필요합니다.";
    }
    if (ai.autoHideBasis === "NOT_MATCHED") {
        return "자동 처리 기준을 충족하지 않아 관리자 검토가 필요합니다.";
    }
    if (ai.autoHideBasis === "NOT_ELIGIBLE_CATEGORY") {
        return "자동 처리 제외 대상이므로 관리자 검토가 필요합니다.";
    }
    if (ai.autoHideBasis === "AI_FAILED" || ai.processingStatus === "FAILED") {
        if (String(ai.reasonSummary || "").includes("AI 사용량 한도 초과")) {
            return "AI 사용량 한도 초과로 자동 분석을 완료하지 못했습니다. 관리자가 직접 검토해주세요.";
        }
        return "AI 분석 실패로 관리자 검토가 필요합니다.";
    }
    if (ai.decision === "REVIEW_REQUIRED" || ai.processingStatus === "REVIEW_REQUIRED") {
        return "AI가 위험도를 감지했지만 자동 처리 조건을 충족하지 않아 관리자 검토가 필요합니다.";
    }
    if (ai.decision === "NO_ACTION") {
        return "AI가 자동 조치가 필요하지 않은 것으로 판단했습니다.";
    }
    return sanitizeAiReasonForDisplay(ai) || "AI 자동 분석이 수행되었습니다.";
}

function isAiAutoProcessed(ai) {
    return ai
        && (
            ai.processingStatus === "AUTO_PROCESSED"
            || (ai.decision === "AUTO_HIDE"
                && ai.processingStatus !== "REVIEW_REQUIRED"
                && ai.processingStatus !== "FAILED")
        );
}

function sanitizeAiReasonForDisplay(ai) {
    const reason = String(ai?.reasonSummary || "");
    if (isAiAutoProcessed(ai)) {
        return reason;
    }
    return reason
        .replace(/자동\s*숨김\s*처리(?:합니다|했습니다|됩니다|되었습니다)?/g, "관리자 검토가 필요합니다")
        .replace(/자동\s*블라인드\s*처리(?:합니다|했습니다|됩니다|되었습니다)?/g, "관리자 검토가 필요합니다")
        .replace(/자동\s*처리(?:합니다|했습니다|됩니다|되었습니다)?/g, "관리자 검토가 필요합니다");
}

function isHighRiskReview(ai) {
    return ai
        && ai.decision === "REVIEW_REQUIRED"
        && Number(ai.riskScore) >= 0.9
        && Number(ai.confidenceScore) >= 0.9;
}

function isExcludedContextReview(ai) {
    if (!ai || ai.decision !== "REVIEW_REQUIRED") {
        return false;
    }
    const text = [
        ai.reasonSummary,
        ...(Array.isArray(ai.evidence) ? ai.evidence : []),
        ...(Array.isArray(ai.missingContext) ? ai.missingContext : [])
    ].filter(Boolean).join(" ");
    return /인용|피해|교육|예시|신고|농담|장난|자기표현|문맥/.test(text);
}

function messageTypeLabel(value) {
    return ({TEXT: "텍스트", IMAGE: "이미지", FORM: "폼"})[value] || escapeAdminHtml(value);
}

function targetTypeLabel(value) {
    return ({
        CHAT_MESSAGE: "채팅 메시지",
        COMMENT: "댓글",
        POST: "게시글",
        REVIEW: "리뷰"
    })[value] || escapeAdminHtml(value);
}

function actionTypeLabel(value) {
    return ({
        AI_RELATED_HIDE: "AI 관련 항목 블라인드"
    })[value] || escapeAdminHtml(value);
}

function renderReasonPill(reason) {
    return `<span class="badge subtle">${escapeAdminHtml(reason || "기타")}</span>`;
}

function renderListDecisionBadge(report) {
    if (report.processorType === "AI" && report.status === "RESOLVED") {
        return `<span class="badge success">자동 블라인드</span>`;
    }
    if (report.status === "PENDING" || report.status === "PROCESSING") {
        return `<span class="badge review">관리자 검토 필요</span>`;
    }
    if (report.status === "REJECTED") {
        return `<span class="badge danger">반려됨</span>`;
    }
    if (report.status === "CANCELED") {
        return `<span class="badge restored">복구 완료</span>`;
    }
    return `<span class="badge success">처리 완료</span>`;
}

function formatPercent(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return "-";
    }
    return `${Math.round(number * 100)}%`;
}

function formatAdminDate(value) {
    return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

function renderProcessHistory(report) {
    const items = [];
    if (report.aiAnalysis) {
        items.push({
            title: processingStatusLabel(report.aiAnalysis.processingStatus),
            content: aiHistorySentence(report),
            meta: aiScoreMeta(report.aiAnalysis),
            time: report.processedAt
        });
    }
    if (report.processedReason) {
        items.push({
            title: report.processorType === "AI" ? "자동 블라인드 처리" : "관리자 처리",
            content: processReasonLabel(report.processedReason),
            meta: report.processorType === "AI" && report.aiAnalysis ? aiScoreMeta(report.aiAnalysis) : "",
            time: report.processedAt
        });
    }
    const relatedItems = Array.isArray(report.relatedActionItems) ? report.relatedActionItems : [];
    const hiddenRelatedCount = relatedItems.length;
    const restoredRelatedCount = relatedItems.filter(item => item.restored).length;
    if (hiddenRelatedCount > 0) {
        items.push({
            title: "관련 메시지 함께 블라인드",
            content: relatedHideHistorySentence(report, hiddenRelatedCount),
            meta: restoredRelatedCount > 0 ? `복구 완료 ${restoredRelatedCount}건` : "",
            time: relatedItems[0]?.createdAt
        });
    }
    if (restoredRelatedCount > 0) {
        items.push({
            title: "관련 메시지 복구",
            content: relatedRestoreHistorySentence(report, restoredRelatedCount),
            meta: "",
            time: relatedItems.find(item => item.restoredAt)?.restoredAt
        });
    }
    if (items.length === 0) {
        items.push({
            title: "접수됨",
            content: "관리자 검토를 기다리고 있습니다.",
            time: report.createdAt || report.reportedAt
        });
    }

    return `
        <div class="detail-section">
            <h4 class="detail-section-title">처리 이력</h4>
            <div class="process-timeline">
                ${items.map(item => `
                    <div class="process-timeline-item">
                        <strong>${escapeAdminHtml(item.title)}</strong>
                        <span>${escapeAdminHtml(item.content)}</span>
                        <small>
                            ${item.meta ? `<span>${escapeAdminHtml(item.meta)}</span>` : ""}
                            <span>${formatAdminDate(item.time)}</span>
                        </small>
                    </div>
                `).join("")}
            </div>
        </div>
    `;
}

function relatedHideHistorySentence(report, count) {
    const category = report.aiAnalysis?.category;
    if (report.targetType === "COMMENT") {
        if (category === "ABUSE") {
            return `AI가 욕설/비방 맥락으로 판단하여 관련 댓글 ${count}건을 함께 블라인드 처리했습니다.`;
        }
        if (category === "SEXUAL_HARASSMENT") {
            return `AI가 성적 괴롭힘 맥락으로 판단하여 관련 댓글 ${count}건을 함께 블라인드 처리했습니다.`;
        }
        if (category === "SPAM_AD") {
            return `AI가 광고/스팸 맥락으로 판단하여 관련 댓글 ${count}건을 함께 블라인드 처리했습니다.`;
        }
        if (category === "FLOODING") {
            return `AI가 도배 맥락으로 판단하여 관련 댓글 ${count}건을 함께 블라인드 처리했습니다.`;
        }
        if (category === "PERSONAL_INFO") {
            return `AI가 개인정보 노출 맥락으로 판단하여 관련 댓글 ${count}건을 함께 블라인드 처리했습니다.`;
        }
    }
    if (category === "SPAM_AD") {
        return `AI가 광고/스팸 맥락으로 판단하여 관련 메시지 ${count}건을 함께 블라인드 처리했습니다.`;
    }
    if (category === "FLOODING") {
        return `AI가 도배 맥락으로 판단하여 관련 메시지 ${count}건을 함께 블라인드 처리했습니다.`;
    }
    if (category === "ABUSE") {
        return `AI가 욕설/비방 맥락으로 판단하여 관련 메시지 ${count}건을 함께 블라인드 처리했습니다.`;
    }
    if (category === "SEXUAL_HARASSMENT") {
        return `AI가 성적 괴롭힘 맥락으로 판단하여 관련 메시지 ${count}건을 함께 블라인드 처리했습니다.`;
    }
    if (category === "PERSONAL_INFO") {
        return `AI가 개인정보 노출 맥락으로 판단하여 관련 메시지 ${count}건을 함께 블라인드 처리했습니다.`;
    }
    return `AI가 정책 위반 맥락으로 판단하여 관련 메시지 ${count}건을 함께 블라인드 처리했습니다.`;
}

function relatedRestoreHistorySentence(report, count) {
    const category = report.aiAnalysis?.category;
    if (report.targetType === "COMMENT") {
        if (category === "ABUSE") {
            return `AI 자동 처리 취소로 관련 욕설/비방 댓글 ${count}건을 함께 복구했습니다.`;
        }
        if (category === "SEXUAL_HARASSMENT") {
            return `AI 자동 처리 취소로 관련 성적 괴롭힘 댓글 ${count}건을 함께 복구했습니다.`;
        }
        if (category === "SPAM_AD") {
            return `AI 자동 처리 취소로 관련 광고/스팸 댓글 ${count}건을 함께 복구했습니다.`;
        }
        if (category === "FLOODING") {
            return `AI 자동 처리 취소로 관련 도배 댓글 ${count}건을 함께 복구했습니다.`;
        }
        if (category === "PERSONAL_INFO") {
            return `AI 자동 처리 취소로 관련 개인정보 노출 댓글 ${count}건을 함께 복구했습니다.`;
        }
    }
    if (category === "ABUSE") {
        return `AI 자동 처리 취소로 관련 욕설/비방 메시지 ${count}건을 함께 복구했습니다.`;
    }
    if (category === "SPAM_AD") {
        return `AI 자동 처리 취소로 관련 광고/스팸 메시지 ${count}건을 함께 복구했습니다.`;
    }
    if (category === "FLOODING") {
        return `AI 자동 처리 취소로 관련 도배 메시지 ${count}건을 함께 복구했습니다.`;
    }
    if (category === "SEXUAL_HARASSMENT") {
        return `AI 자동 처리 취소로 관련 성적 괴롭힘 메시지 ${count}건을 함께 복구했습니다.`;
    }
    if (category === "PERSONAL_INFO") {
        return `AI 자동 처리 취소로 관련 개인정보 노출 메시지 ${count}건을 함께 복구했습니다.`;
    }
    return `AI 자동 처리 취소로 관련 메시지 ${count}건을 함께 복구했습니다.`;
}

function aiHistorySentence(report) {
    const ai = report.aiAnalysis;
    if (!ai) {
        return "AI 자동 분석 결과가 없습니다.";
    }
    if (ai.processingStatus === "AUTO_PROCESSED" || ai.decision === "AUTO_HIDE") {
        return `AI가 ${categoryLabel(ai.category)}으로 판단하여 자동 블라인드 처리했습니다.`;
    }
    if (ai.processingStatus === "REVIEW_REQUIRED" || ai.decision === "REVIEW_REQUIRED") {
        return `AI가 ${categoryLabel(ai.category)} 가능성을 감지해 관리자 검토 대상으로 분류했습니다.`;
    }
    if (ai.processingStatus === "FAILED") {
        return "AI 분석을 완료하지 못해 관리자 확인이 필요합니다.";
    }
    if (ai.processingStatus === "RESTORED") {
        return "관리자 복구 처리로 AI 자동 처리 결과가 취소되었습니다.";
    }
    return ai.reasonSummary || "AI 자동 분석이 수행되었습니다.";
}

function processReasonLabel(value) {
    const text = String(value || "");
    const aiMatch = text.match(/^AI 자동 블라인드:\s*([A-Z_]+)\s*\(risk=([0-9.]+),\s*confidence=([0-9.]+)\)$/);
    if (aiMatch) {
        return `AI가 ${categoryLabel(aiMatch[1])}으로 판단하여 자동 블라인드 처리했습니다.`;
    }
    return text || "-";
}

function aiScoreMeta(ai) {
    if (!ai) {
        return "";
    }
    return `위험도 ${formatPercent(ai.riskScore)} · 판단 확실성 ${formatPercent(ai.confidenceScore)}`;
}

// 상세 모달 처리 취소 버튼 출력
function renderDetailCancelButton(report) {
    return `
        <div class="detail-section admin-action-panel">
            <h4 class="detail-section-title">관리자 작업</h4>
            <div class="detail-footer-actions">
                <button class="btn-cancel" onclick="closeReportDetail()">처리 유지</button>
                ${report.status === "PENDING" ? `<button class="btn-submit" onclick="closeReportDetail(); changeReportStatus(${report.reportId}, 'processing')">검토 시작</button>` : ""}
                ${(report.status === "PENDING" || report.status === "PROCESSING") ? `<button class="btn-success" onclick="closeReportDetail(); openReportProcessModal(${report.reportId}, 'resolve')">검토 완료 처리</button>` : ""}
                ${(report.status === "PENDING" || report.status === "PROCESSING") ? `<button class="btn-danger" onclick="closeReportDetail(); openReportProcessModal(${report.reportId}, 'reject')">오분류 신고</button>` : ""}
                ${(report.status !== "PENDING" && report.status !== "CANCELED") ? `<button class="btn-danger" onclick="openReportCancelModal(${report.reportId})">복구 처리</button>` : ""}
            </div>
        </div>
    `;
}

// 신고 상세 상태 출력
function renderReportDetailStatus(status) {
    const tone = status === "RESOLVED" ? "good"
        : status === "REJECTED" ? "danger"
            : status === "CANCELED" ? "restored"
                : "warning";
    return `<span class="detail-status ${tone}">${statusLabel(status)}</span>`;
}

// 신고 상세 모달 닫기
function closeReportDetail() {
    document.getElementById("reportDetailModal").classList.remove("active");
}

// 신고 처리 버튼 출력
function renderReportActionButtons(report) {
    if (report.status === "PENDING") {
        return `
            <button class="action-btn primary" onclick="changeReportStatus(${report.reportId}, 'processing')">처리 중</button>
            <button class="action-btn success" onclick="changeReportStatus(${report.reportId}, 'resolve')">완료</button>
            <button class="action-btn danger" onclick="changeReportStatus(${report.reportId}, 'reject')">반려</button>
        `;
    }

    if (report.status === "PROCESSING") {
        return `
            <button class="action-btn success" onclick="changeReportStatus(${report.reportId}, 'resolve')">완료</button>
            <button class="action-btn danger" onclick="changeReportStatus(${report.reportId}, 'reject')">반려</button>
            <button class="action-btn danger" onclick="openReportCancelModal(${report.reportId})">취소</button>
        `;
    }

    if (report.status === "RESOLVED" || report.status === "REJECTED") {
        return `
            <button class="action-btn danger" onclick="openReportCancelModal(${report.reportId})">처리 취소</button>
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
