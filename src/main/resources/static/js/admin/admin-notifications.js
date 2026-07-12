document.addEventListener("DOMContentLoaded", () => {
    const button = document.getElementById("adminNotificationButton");
    const panel = document.getElementById("adminNotificationPanel");
    if (!button || !panel) return;

    button.addEventListener("click", event => {
        event.stopPropagation();
        panel.hidden = !panel.hidden;
        if (!panel.hidden) loadAdminNotifications();
    });
    panel.addEventListener("click", event => event.stopPropagation());
    document.addEventListener("click", () => { panel.hidden = true; });
    loadAdminNotifications();
    subscribeAdminNotifications();
});

async function loadAdminNotifications() {
    try {
        const response = await fetch("/api/admin/notifications");
        if (!response.ok) throw new Error("관리자 알림 조회 실패");
        const result = await response.json();
        renderAdminNotificationBadge(result.unreadCount || 0);
        renderAdminNotifications(result.notifications || []);
    } catch (error) {
        console.error(error);
        document.getElementById("adminNotificationList").innerHTML =
            '<div class="admin-notification-empty">알림을 불러오지 못했습니다.</div>';
    }
}

function subscribeAdminNotifications() {
    const eventSource = new EventSource("/api/notifications/subscribe");
    eventSource.addEventListener("notification", loadAdminNotifications);
    eventSource.onerror = () => eventSource.close();
}

function renderAdminNotificationBadge(count) {
    const badge = document.getElementById("adminNotificationBadge");
    badge.textContent = count > 99 ? "99+" : String(count);
    badge.hidden = count <= 0;
}

function renderAdminNotifications(notifications) {
    const list = document.getElementById("adminNotificationList");
    if (notifications.length === 0) {
        list.innerHTML = '<div class="admin-notification-empty">확인할 작업 알림이 없습니다.</div>';
        return;
    }
    list.innerHTML = notifications.map(notification => `
        <button class="admin-notification-item ${notification.read ? "read" : "unread"}"
                type="button" onclick="openAdminNotification(${notification.notificationId}, '${escapeAdminNotificationAttribute(notification.targetUrl)}')">
            <strong>${escapeAdminNotificationHtml(notification.title)}</strong>
            <span>${escapeAdminNotificationHtml(notification.content)}</span>
            <small>${formatAdminNotificationDate(notification.createdAt)}</small>
        </button>
    `).join("");
}

async function openAdminNotification(notificationId, targetUrl) {
    await fetch(`/api/admin/notifications/${notificationId}/read`, { method: "PUT" });
    window.location.href = targetUrl && targetUrl !== "#" ? targetUrl : "/api/view/admin/main";
}

async function readAllAdminNotifications(event) {
    event.stopPropagation();
    try {
        const response = await fetch("/api/admin/notifications/read-all", { method: "PUT" });
        if (!response.ok) throw new Error("관리자 알림 전체 읽음 실패");
        await loadAdminNotifications();
    } catch (error) {
        console.error(error);
    }
}

function formatAdminNotificationDate(value) {
    return value ? new Date(value).toLocaleString("ko-KR") : "";
}

function escapeAdminNotificationHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function escapeAdminNotificationAttribute(value) {
    return String(value ?? "#").replaceAll("\\", "\\\\").replaceAll("'", "\\'");
}
