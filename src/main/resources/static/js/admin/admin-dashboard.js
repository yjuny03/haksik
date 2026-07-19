document.addEventListener("DOMContentLoaded", loadAdminDashboard);

async function loadAdminDashboard() {
    try {
        const response = await fetch("/api/admin/dashboard");
        if (!response.ok) throw new Error("대시보드 조회 실패");
        const data = await response.json();
        renderDashboard(data);
        document.getElementById("dashboardLoading").hidden = true;
        document.getElementById("dashboardContent").hidden = false;
    } catch (error) {
        console.error(error);
        document.getElementById("dashboardLoading").hidden = true;
        const errorBox = document.getElementById("dashboardError");
        errorBox.textContent = "운영 현황을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        errorBox.hidden = false;
    }
}

function renderDashboard(data) {
    renderCards("memberCards", [
        card("전체 회원", data.members.total, "/api/view/admin/members"),
        card("일반 사용자", data.members.users, "/api/view/admin/members?role=USER"),
        card("점주", data.members.owners, "/api/view/admin/members?role=OWNER"),
        card("관리자", data.members.admins, "/api/view/admin/members?role=ADMIN"),
        card("잠금 계정", data.members.locked, "/api/view/admin/members"),
        card("오늘 가입", data.members.joinedToday, "/api/view/admin/members")
    ]);
    renderCards("ownerCards", [
        card("승인 대기", data.owners.pending, "/api/view/admin/owners?status=PENDING", "attention"),
        card("승인 완료", data.owners.approved, "/api/view/admin/owners?status=APPROVED"),
        card("반려", data.owners.rejected, "/api/view/admin/owners?status=REJECTED")
    ]);
    renderCards("reportCards", [
        card("신고 접수", data.reports.total, "/api/view/admin/reports"),
        card("AI 자동 처리", data.aiModeration.autoProcessed, "/api/view/admin/reports", "success"),
        card("검토 필요", data.aiModeration.reviewRequired + data.reports.pending, "/api/view/admin/reports?status=PENDING", "attention"),
        card("처리 완료", data.reports.resolved, "/api/view/admin/reports?status=RESOLVED", "success")
    ]);
    renderCards("aiCards", [
        card("AI 자동 처리", data.aiModeration.autoProcessed, "/api/view/admin/reports"),
        card("관리자 검토 필요", data.aiModeration.reviewRequired, "/api/view/admin/reports?status=PENDING", "attention"),
        card("분석 실패", data.aiModeration.failed, "/api/view/admin/reports?status=PENDING", "danger"),
        card("복구·취소", data.aiModeration.restored, "/api/view/admin/reports?status=CANCELED")
    ]);
    renderCards("trustCards", [
        card("오늘 매너온도 감소", data.trust.penaltiesToday, "/api/view/admin/members"),
        card("AI 제재", data.trust.aiPenalties, "/api/view/admin/reports"),
        card("관리자 수동 제재", data.trust.adminPenalties, "/api/view/admin/reports"),
        card("매너온도 10 이하", data.trust.lowMannerUsers, "/api/view/admin/members", "attention")
    ]);

    renderDuplicateTargets(data.reports.mostReportedTargets);
    renderAiList("recentAiAuto", data.aiModeration.recentAutoProcessed);
    renderAiList("recentAiReview", data.aiModeration.recentReviewRequired);
    renderRecentActivities(data.recent);
}

function card(label, value, href, tone = "") { return { label, value, href, tone }; }

function renderCards(id, cards) {
    document.getElementById(id).innerHTML = cards.map(item => `
        <a class="dashboard-stat-card ${item.tone}" href="${item.href}">
            <span>${escapeDashboardHtml(item.label)}</span>
            <strong>${Number(item.value || 0).toLocaleString("ko-KR")}</strong>
        </a>
    `).join("");
}

function renderDuplicateTargets(items) {
    const target = document.getElementById("duplicateTargets");
    if (!items || items.length === 0) {
        target.innerHTML = emptyDashboardItem("중복 신고 대상이 없습니다.");
        return;
    }
    target.innerHTML = items.map(item => `
        <a class="dashboard-list-item" href="/api/view/admin/reports?targetType=${encodeURIComponent(item.targetType)}">
            <span>${targetTypeName(item.targetType)} #${item.targetId}</span><strong>${item.reportCount}건</strong>
        </a>
    `).join("");
}

function renderAiList(id, items) {
    const target = document.getElementById(id);
    if (!items || items.length === 0) {
        target.innerHTML = emptyDashboardItem("표시할 신고가 없습니다.");
        return;
    }
    target.innerHTML = items.map(item => `
        <a class="dashboard-list-item dashboard-list-stack" href="/api/view/admin/reports?reportId=${item.reportId}">
            <span>신고 #${item.reportId} · ${targetTypeName(item.targetType)}</span>
            <small>
                <span class="dashboard-pill">${categoryLabel(item.category)}</span>
                <span class="dashboard-pill ${decisionTone(item.decision)}">${decisionLabel(item.decision)}</span>
                ${formatDashboardDate(item.analyzedAt)}
            </small>
        </a>
    `).join("");
}

function renderRecentActivities(recent) {
    const groups = [
        ["최근 신고 접수", recent.reports],
        ["최근 AI 자동 처리", recent.aiAutoProcessed],
        ["최근 관리자 처리", recent.adminProcessed],
        ["최근 점주 가입 신청", recent.ownerApplications],
        ["최근 복구·취소", recent.restored]
    ];
    document.getElementById("recentActivity").innerHTML = groups.map(([title, items]) => `
        <div class="dashboard-panel">
            <h4>${title}</h4>
            <div class="dashboard-list">${renderActivityItems(items, title)}</div>
        </div>
    `).join("");
}

function renderActivityItems(items, groupTitle = "") {
    if (!items || items.length === 0) return emptyDashboardItem("최근 활동이 없습니다.");
    return items.map(item => `
        <a class="dashboard-list-item dashboard-list-stack" href="${item.targetUrl || '#'}">
            <span>${activityTitleLabel(item.title)}</span>
            <small><span class="dashboard-pill ${statusTone(item.status)}">${activityStatusLabel(item, groupTitle)}</span> ${formatDashboardDate(item.occurredAt)}</small>
        </a>
    `).join("");
}

function emptyDashboardItem(message) { return `<div class="dashboard-empty">${message}</div>`; }
function targetTypeName(type) { return ({POST:"게시글", COMMENT:"댓글", CHAT_MESSAGE:"채팅 메시지", CHAT_MEMBER:"채팅 상대", MEMBER:"회원", USER:"회원", REVIEW:"리뷰"})[type] || type; }
function formatDashboardDate(value) { return value ? new Date(value).toLocaleString("ko-KR") : "시간 정보 없음"; }
function escapeDashboardHtml(value) {
    return String(value ?? "-").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
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
    })[value] || escapeDashboardHtml(value);
}

function decisionLabel(value) {
    return ({
        AUTO_HIDE: "자동 블라인드",
        REVIEW_REQUIRED: "관리자 검토 필요",
        NO_ACTION: "조치 없음"
    })[value] || escapeDashboardHtml(value);
}

function statusLabel(value) {
    return ({
        APPROVED: "승인 완료",
        PENDING: "접수됨",
        PROCESSING: "처리 중",
        RESOLVED: "처리 완료",
        REJECTED: "반려됨",
        CANCELED: "취소됨",
        AUTO_PROCESSED: "자동 처리 완료",
        REVIEW_REQUIRED: "관리자 검토 필요",
        FAILED: "분석 실패",
        RESTORED: "복구 완료"
    })[value] || escapeDashboardHtml(value);
}

function activityStatusLabel(item, groupTitle = "") {
    if (item && item.status === "PENDING" && groupTitle.includes("점주")) {
        return "승인 대기";
    }
    return statusLabel(item ? item.status : null);
}

function activityTitleLabel(value) {
    const text = String(value ?? "-");
    const aiMatch = text.match(/^AI\s+([A-Z_]+)$/);
    if (aiMatch) {
        const category = categoryLabel(aiMatch[1]);
        if (aiMatch[1] === "PERSONAL_INFO") {
            return `AI ${category} 감지`;
        }
        if (aiMatch[1] === "FLOODING") {
            return `AI ${category} 감지`;
        }
        return `AI ${category} 자동 처리`;
    }
    let normalized = text
        .replaceAll("AI SPAM_AD", "AI 광고/스팸 자동 처리")
        .replaceAll("AI ABUSE", "AI 욕설/비방 자동 처리")
        .replaceAll("AI PERSONAL_INFO", "AI 개인정보 노출 감지")
        .replaceAll("AI FLOODING", "AI 도배 감지")
        .replaceAll("APPROVED", "승인 완료")
        .replaceAll("PENDING", "승인 대기")
        .replaceAll("REJECTED", "반려됨")
        .replaceAll("AUTO_PROCESSED", "자동 처리 완료")
        .replaceAll("REVIEW_REQUIRED", "관리자 검토 필요")
        .replaceAll("RESOLVED", "처리 완료")
        .replaceAll("CANCELED", "취소됨")
        .replaceAll("FAILED", "분석 실패")
        .replaceAll("RESTORED", "복구 완료");
    return escapeDashboardHtml(normalized);
}

function decisionTone(value) {
    if (value === "AUTO_HIDE") return "success";
    if (value === "REVIEW_REQUIRED") return "warning";
    if (value === "NO_ACTION") return "neutral";
    return "";
}

function statusTone(value) {
    if (value === "RESOLVED" || value === "AUTO_PROCESSED") return "success";
    if (value === "PENDING" || value === "PROCESSING" || value === "REVIEW_REQUIRED") return "warning";
    if (value === "REJECTED" || value === "FAILED") return "danger";
    if (value === "CANCELED" || value === "RESTORED") return "restored";
    return "";
}
