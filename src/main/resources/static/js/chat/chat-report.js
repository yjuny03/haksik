let reportTargetType = null;
let reportTargetId = null;
let reportTargetUrl = null;
let reportSubmitting = false;

const MAX_REPORT_REASON_LENGTH = 200;
const REPORT_REASON_LENGTH_ERROR = "신고 사유는 최대 200자까지 입력할 수 있습니다.";
var TOAST_COOLDOWN = 1200;
var INPUT_WARNING_DELAY = 600;
var INPUT_WARNING_COOLDOWN = 1800;

window.toastCooldownState = window.toastCooldownState || {};
window.inputWarningTimers = window.inputWarningTimers || {};
window.inputWarningLastShownAt = window.inputWarningLastShownAt || {};
window.inputWarningSequence = window.inputWarningSequence || 0;

// 같은 toast가 짧은 시간 안에 반복 표시되는 것을 방지
function showToastLimited(message, type, key) {
    var toastKey = key || message;
    var now = Date.now();

    if (window.toastCooldownState[toastKey] && now - window.toastCooldownState[toastKey] < TOAST_COOLDOWN) {
        return;
    }

    window.toastCooldownState[toastKey] = now;
    showToast(message, type);
}

// input 경고 타이머 제거
function clearInputWarningTimers(id) {
    Object.keys(window.inputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ":")) {
            clearTimeout(window.inputWarningTimers[key]);
            delete window.inputWarningTimers[key];
        }
    });
}

// 정상 입력으로 돌아오면 해당 input의 경고 상태 초기화
function clearInputWarning(id) {
    clearInputWarningTimers(id);

    Object.keys(window.inputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ":")) {
            delete window.inputWarningLastShownAt[key];
        }
    });
}

// 입력 중 경고 toast는 debounce 후 표시
function showInputWarningOnce(id, type, message, forceShow) {
    var key = id + ":" + type;
    var input = document.getElementById(id) || document.querySelector('[data-input-warning-id="' + id + '"]');

    if (!input) {
        return;
    }

    var currentValue = input.value;
    var delay = forceShow ? 0 : INPUT_WARNING_DELAY;

    clearInputWarningTimers(id);

    window.inputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id) || document.querySelector('[data-input-warning-id="' + id + '"]');

        if (!latestInput) {
            return;
        }

        var latestValue = latestInput.value;

        if (latestValue !== currentValue) {
            return;
        }

        var now = Date.now();

        if (window.inputWarningLastShownAt[key] && now - window.inputWarningLastShownAt[key] < INPUT_WARNING_COOLDOWN) {
            return;
        }

        window.inputWarningLastShownAt[key] = now;
        showToastLimited(message, "warning", key);
    }, delay);
}

// 입력 중 안내 검증
function checkInputWarning(input, id, options, forceShow) {
    var value = input.value;

    if (!value) {
        clearInputWarning(id);
        return;
    }

    if (options.maxLength && value.length > options.maxLength) {
        showInputWarningOnce(id, "length", options.message, forceShow === true);
        return;
    }

    if (options.pattern && !options.pattern.test(value)) {
        showInputWarningOnce(id, "pattern", options.message, forceShow === true);
        return;
    }

    clearInputWarning(id);
}

// 같은 이벤트 리스너가 여러 번 등록되는 것을 방지
function bindEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === "true") {
        return;
    }

    element.dataset[bindKey] = "true";
    element.addEventListener(eventName, handler);
}

// 입력창에 입력 중 안내 검증 적용
function addInputWarning(id, options) {
    var input = document.getElementById(id);
    addInputWarningToElement(input, id, options);
}

// 동적으로 생성되는 입력창에 입력 중 안내 검증 적용
function addInputWarningToElement(input, id, options) {
    if (!input) {
        return;
    }

    if (options && options.maxLength) {
        input.removeAttribute("maxlength");
    }

    var warningId = id || input.dataset.inputWarningId;

    if (!warningId) {
        window.inputWarningSequence += 1;
        warningId = "inputWarning" + window.inputWarningSequence;
        input.dataset.inputWarningId = warningId;
    }

    if (input.dataset.inputWarningInitBound === "true") {
        return;
    }

    input.dataset.inputWarningInitBound = "true";

    var isComposing = false;

    bindEventOnce(input, "compositionstart", "compositionStartBound", function () {
        isComposing = true;
    });

    bindEventOnce(input, "compositionend", "compositionEndBound", function () {
        isComposing = false;
        checkInputWarning(input, warningId, options, false);
    });

    bindEventOnce(input, "input", "inputWarningBound", function () {
        if (isComposing) {
            return;
        }

        checkInputWarning(input, warningId, options, false);
    });
}


document.addEventListener("DOMContentLoaded", function () {
    addInputWarning("customReportReason", {
        maxLength: MAX_REPORT_REASON_LENGTH,
        message: REPORT_REASON_LENGTH_ERROR
    });
});

// 메시지 신고 모달 열기
function openMessageReportModal() {
    if (!selectedMessageId) {
        return;
    }

    const selectedMessage = messageCache[selectedMessageId];

    if (!selectedMessage) {
        showToastLimited("신고할 메시지를 찾을 수 없습니다.", "error");
        return;
    }

    if (selectedMessage.mine) {
        showToastLimited("내 메시지는 신고할 수 없습니다.", "error");
        closeMessageMenu();
        return;
    }

    if (selectedMessage.deleted) {
        showToastLimited("삭제된 메시지는 신고할 수 없습니다.", "error");
        closeMessageMenu();
        return;
    }

    const chatRoomId = getChatRoomId();

    if (!chatRoomId) {
        return;
    }

    reportTargetType = "CHAT_MESSAGE";
    reportTargetId = selectedMessageId;
    reportTargetUrl = "/api/reports/chat/rooms/" + chatRoomId + "/messages/" + selectedMessageId;

    closeMessageMenu();
    openReportModal();
}

// 참여자 신고 모달 열기
function openMemberReportModal(memberId) {
    const chatRoomId = getChatRoomId();

    if (!chatRoomId) {
        return;
    }

    if (!memberId) {
        showToastLimited("신고할 회원 정보를 찾을 수 없습니다.", "error");
        return;
    }

    reportTargetType = "CHAT_MEMBER";
    reportTargetId = memberId;
    reportTargetUrl = "/api/reports/chat/rooms/" + chatRoomId + "/members/" + memberId;

    closeMemberModal();
    openReportModal();
}

// 평가 대상 신고 모달 열기
function openReviewTargetReportModal(memberId) {
    const chatRoomId = getChatRoomId();

    if (!chatRoomId) {
        return;
    }

    if (!memberId) {
        showToastLimited("신고할 회원 정보를 찾을 수 없습니다.", "error");
        return;
    }

    reportTargetType = "CHAT_MEMBER";
    reportTargetId = memberId;
    reportTargetUrl = "/api/reports/chat/rooms/" + chatRoomId + "/members/" + memberId;

    openReportModal();
}

// 신고 사유 길이 검사
function validateReportReasonLength(reason) {
    if (reason && reason.length > MAX_REPORT_REASON_LENGTH) {
        showToastLimited(REPORT_REASON_LENGTH_ERROR, "error");
        return false;
    }

    return true;
}

// 신고 모달 열기
function openReportModal() {
    const reportModal = document.getElementById("reportModal");
    const reportReasons = document.querySelectorAll("input[name='reportReason']");
    const customReportReason = document.getElementById("customReportReason");

    reportReasons.forEach(function (reason) {
        reason.checked = false;
    });

    if (customReportReason) {
        customReportReason.value = "";
        customReportReason.classList.add("hidden");
    }

    if (reportModal) {
        reportModal.classList.remove("hidden");
    }
}

// 신고 모달 닫기
function closeReportModal() {
    const reportModal = document.getElementById("reportModal");
    const reportReasons = document.querySelectorAll("input[name='reportReason']");
    const customReportReason = document.getElementById("customReportReason");

    reportReasons.forEach(function (reason) {
        reason.checked = false;
    });

    if (customReportReason) {
        customReportReason.value = "";
        customReportReason.classList.add("hidden");
    }

    reportTargetType = null;
    reportTargetId = null;
    reportTargetUrl = null;

    if (reportModal) {
        reportModal.classList.add("hidden");
    }
}

// 기타 선택 여부에 따라 직접 입력창 표시
function handleReportReasonChange() {
    const checkedReason = document.querySelector("input[name='reportReason']:checked");
    const customReportReason = document.getElementById("customReportReason");

    if (!checkedReason || !customReportReason) {
        return;
    }

    if (checkedReason.value === "기타") {
        customReportReason.classList.remove("hidden");
        customReportReason.focus();
    } else {
        customReportReason.classList.add("hidden");
        customReportReason.value = "";
    }
}

// 선택된 신고 사유 가져오기
function getSelectedReportReason() {
    const checkedReason = document.querySelector("input[name='reportReason']:checked");
    const customReportReason = document.getElementById("customReportReason");

    if (!checkedReason) {
        return "";
    }

    if (checkedReason.value === "기타") {
        if (!customReportReason) {
            return "";
        }

        return customReportReason.value.trim();
    }

    return checkedReason.value;
}

// 신고 전송
function submitReport() {
    const reason = getSelectedReportReason();

    if (!reportTargetType || !reportTargetId || !reportTargetUrl) {
        showToastLimited("신고 대상 정보를 찾을 수 없습니다.", "error");
        return;
    }

    if (reason === "") {
        showToastLimited("신고 사유를 선택하거나 입력해 주세요.", "error");
        return;
    }

    if (!validateReportReasonLength(reason)) {
        return;
    }

    if (reportSubmitting) {
        return;
    }

    reportSubmitting = true;

    const submitButton = document.querySelector(".report-submit-button");

    if (submitButton) {
        submitButton.disabled = true;
    }

    fetch(reportTargetUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            targetType: reportTargetType,
            targetId: reportTargetId,
            reason: reason
        })
    })
        .then(function (response) {
            if (!response.ok) {
                return response.text().then(function (errorText) {
                    if (errorText.includes("ALREADY_REPORTED") || errorText.includes("이미")) {
                        showToastLimited("이미 신고한 상태입니다.", "info");
                    } else {
                        showToastLimited("신고 접수에 실패했습니다.", "error");
                    }

                    throw new Error("report failed");
                });
            }

            return response.text();
        })
        .then(function () {
            closeReportModal();
            showToastLimited("신고가 접수되었습니다.", "success");
        })
        .catch(function () {
            // 실패 alert는 위에서 이미 처리
        })
        .finally(function () {
            reportSubmitting = false;

            if (submitButton) {
                submitButton.disabled = false;
            }
        });
}


