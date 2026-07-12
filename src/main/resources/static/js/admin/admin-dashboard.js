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
        card("전체 신고", data.reports.total, "/api/view/admin/reports"),
        card("대기 중", data.reports.pending, "/api/view/admin/reports?status=PENDING", "attention"),
        card("처리 완료", data.reports.resolved, "/api/view/admin/reports?status=RESOLVED"),
        card("반려", data.reports.rejected, "/api/view/admin/reports?status=REJECTED"),
        card("취소·복구", data.reports.canceled, "/api/view/admin/reports?status=CANCELED")
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
            <small>${escapeDashboardHtml(item.category)} / ${escapeDashboardHtml(item.decision)} · ${formatDashboardDate(item.analyzedAt)}</small>
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
            <div class="dashboard-list">${renderActivityItems(items)}</div>
        </div>
    `).join("");
}

function renderActivityItems(items) {
    if (!items || items.length === 0) return emptyDashboardItem("최근 활동이 없습니다.");
    return items.map(item => `
        <a class="dashboard-list-item dashboard-list-stack" href="${item.targetUrl || '#'}">
            <span>${escapeDashboardHtml(item.title)}</span>
            <small>${escapeDashboardHtml(item.status)} · ${formatDashboardDate(item.occurredAt)}</small>
        </a>
    `).join("");
}

function emptyDashboardItem(message) { return `<div class="dashboard-empty">${message}</div>`; }
function targetTypeName(type) { return ({POST:"게시글", COMMENT:"댓글", CHAT_MESSAGE:"채팅", REVIEW:"리뷰"})[type] || type; }
function formatDashboardDate(value) { return value ? new Date(value).toLocaleString("ko-KR") : "시간 정보 없음"; }
function escapeDashboardHtml(value) {
    return String(value ?? "-").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}
