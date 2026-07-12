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

// 채팅 화면 이동 후 알림 출력
function showChatToastIfExists() {
    const rawToast = sessionStorage.getItem("chatPendingToast");

    if (!rawToast) {
        return;
    }

    sessionStorage.removeItem("chatPendingToast");

    try {
        const toast = JSON.parse(rawToast);

        if (!toast || !toast.message) {
            return;
        }

        showToastLimited(toast.message, toast.type || "success");
    } catch (error) {
        // 저장된 알림 데이터가 잘못된 경우 조용히 무시한다.
    }
}

let allChatRooms = [];
let currentChatMode = "MEAL";

document.addEventListener("DOMContentLoaded", function () {
    showChatToastIfExists();
    loadChatRooms();
});

// 채팅방 목록 조회
function loadChatRooms() {
    fetch("/api/chat/rooms")
        .then(function (response) {
            return response.json();
        })
        .then(function (chatRooms) {
            allChatRooms = chatRooms;
            renderChatRooms();
        })
        .catch(function () {
            showToastLimited("채팅방 목록을 불러오지 못했습니다.", "error");
        });
}

// 채팅방 탭 변경
function changeChatTab(mode) {
    currentChatMode = mode;

    const tabs = document.querySelectorAll(".chat-tab");

    tabs.forEach(function (tab) {
        if (tab.dataset.mode === mode) {
            tab.classList.add("active");
        } else {
            tab.classList.remove("active");
        }
    });

    renderChatRooms();
}

// 채팅방 목록 출력
function renderChatRooms() {
    const chatRoomList = document.getElementById("chatRoomList");
    const emptyChatRoom = document.getElementById("emptyChatRoom");

    if (!chatRoomList || !emptyChatRoom) {
        return;
    }

    chatRoomList.innerHTML = "";

    const filteredRooms = allChatRooms.filter(function (room) {
        return room.matchingMode === currentChatMode;
    });

    if (filteredRooms.length === 0) {
        emptyChatRoom.classList.remove("hidden");
        return;
    }

    emptyChatRoom.classList.add("hidden");

    filteredRooms.forEach(function (room) {
        const unreadText = room.unreadCount > 0
            ? `<div class="unread-count">${room.unreadCount}</div>`
            : "";

        const closedText = room.roomStatus === "CLOSED"
            ? `<span class="room-status closed">종료됨</span>`
            : "";

        const roomName = escapeHtml(room.displayRoomName || room.roomName || "채팅방");
        const lastMessage = escapeHtml(room.lastMessage || "아직 메시지가 없습니다.");

        chatRoomList.innerHTML += `
            <button type="button" class="chat-room-item" onclick="goChatRoom(${room.chatRoomId})">
                <div class="chat-room-main">
                    <div class="chat-room-name">
                        <span class="chat-room-title-text">${roomName}</span>
                        ${closedText}
                    </div>
                    <div class="last-message">
                        ${lastMessage}
                    </div>
                </div>

                <div class="chat-room-right">
                    <div class="last-time">${formatListTime(room.lastMessageAt)}</div>
                    ${unreadText}
                </div>
            </button>
        `;
    });
}

// 채팅방 이동
function goChatRoom(chatRoomId) {
    location.href = "/api/view/user/chat/rooms/" + chatRoomId;
}

// 목록 시간 표시
function formatListTime(dateTime) {
    if (!dateTime) {
        return "";
    }

    const date = new Date(dateTime);

    return date.toLocaleDateString("ko-KR", {
        month: "2-digit",
        day: "2-digit"
    });
}

// HTML 특수문자 처리
function escapeHtml(value) {
    if (value === null || value === undefined) {
        return "";
    }

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#039;");
}