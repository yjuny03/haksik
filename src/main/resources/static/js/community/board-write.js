// board-write.js

// 🚨 주소창에서 postId 추출 (존재하면 수정 모드, 없으면 작성 모드)
const urlParams = new URLSearchParams(window.location.search);
const editPostId = urlParams.get('postId');

var MAX_BOARD_TITLE_LENGTH = 100;
var MAX_BOARD_CONTENT_LENGTH = 1000;
var BOARD_TITLE_LENGTH_ERROR = '게시글 제목은 최대 100자까지 입력할 수 있습니다.';
var BOARD_CONTENT_LENGTH_ERROR = '게시글 내용은 최대 1000자까지 입력할 수 있습니다.';

var BOARD_TOAST_COOLDOWN = 1200;
var BOARD_INPUT_WARNING_DELAY = 600;
var BOARD_INPUT_WARNING_COOLDOWN = 1800;

window.boardToastCooldownState = window.boardToastCooldownState || {};
window.boardInputWarningTimers = window.boardInputWarningTimers || {};
window.boardInputWarningLastShownAt = window.boardInputWarningLastShownAt || {};

// 같은 toast가 짧은 시간 안에 반복 표시되는 것을 방지
function showBoardToastLimited(message, type, key) {
    var toastKey = key || message;
    var now = Date.now();

    if (window.boardToastCooldownState[toastKey] && now - window.boardToastCooldownState[toastKey] < BOARD_TOAST_COOLDOWN) {
        return;
    }

    window.boardToastCooldownState[toastKey] = now;

    if (typeof showToast === 'function') {
        showToast(message, type);
    }
}

// input 경고 타이머 제거
function clearBoardInputWarningTimers(id) {
    Object.keys(window.boardInputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(window.boardInputWarningTimers[key]);
            delete window.boardInputWarningTimers[key];
        }
    });
}

// 정상 입력으로 돌아오면 해당 input의 경고 상태 초기화
function clearBoardInputWarning(id) {
    clearBoardInputWarningTimers(id);

    Object.keys(window.boardInputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete window.boardInputWarningLastShownAt[key];
        }
    });
}

// 입력 중 경고 toast는 debounce 후 표시
function showBoardInputWarningOnce(id, type, message, forceShow) {
    var key = id + ':' + type;
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    var currentValue = input.value;
    var delay = forceShow ? 0 : BOARD_INPUT_WARNING_DELAY;

    clearBoardInputWarningTimers(id);

    window.boardInputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id);

        if (!latestInput) {
            return;
        }

        if (latestInput.value !== currentValue) {
            return;
        }

        var now = Date.now();

        if (window.boardInputWarningLastShownAt[key] && now - window.boardInputWarningLastShownAt[key] < BOARD_INPUT_WARNING_COOLDOWN) {
            return;
        }

        window.boardInputWarningLastShownAt[key] = now;
        showBoardToastLimited(message, 'warning', key);
    }, delay);
}

// 입력 중 안내 검증
function checkBoardInputWarning(input, id, options, forceShow) {
    var value = input.value;

    if (!value) {
        clearBoardInputWarning(id);
        return;
    }

    if (options.maxLength && value.length > options.maxLength) {
        showBoardInputWarningOnce(id, 'length', options.message, forceShow === true);
        return;
    }

    clearBoardInputWarning(id);
}

// 입력창에 입력 중 안내 검증 적용
function addBoardInputWarning(id, options) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (options && options.maxLength) {
        input.removeAttribute('maxlength');
    }

    if (input.dataset.boardInputWarningInitBound === 'true') {
        return;
    }

    input.dataset.boardInputWarningInitBound = 'true';

    var isComposing = false;

    input.addEventListener('compositionstart', function () {
        isComposing = true;
    });

    input.addEventListener('compositionend', function () {
        isComposing = false;
        checkBoardInputWarning(input, id, options, false);
    });

    input.addEventListener('input', function () {
        if (isComposing) {
            return;
        }

        checkBoardInputWarning(input, id, options, false);
    });
}


document.addEventListener('DOMContentLoaded', function () {
    const writeForm = document.getElementById('writeForm');
    const anonymousCheck = document.getElementById('anonymousCheck');

    initBoardWriteInputWarnings();

    if (anonymousCheck) {
        anonymousCheck.addEventListener('change', function () {
            document.getElementById('anonymousYn').value = this.checked ? 'Y' : 'N';
        });
    }

    if (writeForm) {
        writeForm.addEventListener('submit', submitPost);
    }

    // 🚨 수정 모드 진입 시 자동 세팅
    if (editPostId) {
        setupEditMode();
    }
});


// 게시글 작성/수정 입력창 입력 중 안내 검증 초기화
function initBoardWriteInputWarnings() {
    addBoardInputWarning('title', {
        maxLength: MAX_BOARD_TITLE_LENGTH,
        message: BOARD_TITLE_LENGTH_ERROR
    });

    addBoardInputWarning('content', {
        maxLength: MAX_BOARD_CONTENT_LENGTH,
        message: BOARD_CONTENT_LENGTH_ERROR
    });
}

// 게시글 작성/수정 최종 글자 수 검사
function validateBoardWriteTextLength(input, maxLength, errorMessage, toastKey) {
    if (input && input.value.length > maxLength) {
        showBoardToastLimited(errorMessage, 'error', toastKey);
        input.focus();
        return false;
    }

    return true;
}

// 기존 게시글 땡겨와서 세팅하기
async function setupEditMode() {
    document.querySelector('.app-header-title').innerText = '게시글 수정';
    document.getElementById('submitBtn').innerText = '수정하기';

    // 백엔드 PUT 요청에 맞게 불필요한 입력 필드 숨김 처리
    document.getElementById('boardType').closest('.board-write-field').style.display = 'none';
    document.getElementById('category').closest('.board-write-field').style.display = 'none';
    document.getElementById('anonymousCheck').closest('.board-write-field').style.display = 'none';

    // 🚨 팩트: 기존에 사진 버튼을 숨기던 코드(display = 'none')를 삭제했습니다.
    // 기존 사진 렌더링 로직도 없습니다. 유저는 빈 사진 첨부 버튼만 보게 됩니다.

    try {
        const response = await fetch('/api/posts/' + editPostId);
        if (!response.ok) throw new Error('데이터 로드 실패');
        const post = await response.json();

        // 입력창에 기존 데이터 꽂아 넣기
        document.getElementById('title').value = post.title || '';
        document.getElementById('content').value = post.content || '';
    } catch (error) {
        console.error(error);
        if (typeof showToast === 'function') showToast('게시글 정보를 불러오지 못했습니다.', 'error');
        setTimeout(() => history.back(), 1000);
    }
}

// 게시글 등록/수정 분기 통신
async function submitPost(event) {
    event.preventDefault();

    const form = document.getElementById('writeForm');
    const titleInput = document.getElementById('title');
    const contentInput = document.getElementById('content');
    const title = titleInput.value.trim();
    const content = contentInput.value.trim();

    if (!title || !content) {
        if (typeof showToast === 'function') showToast('제목과 내용을 입력해주세요.', 'error');
        return;
    }

    if (!validateBoardWriteTextLength(titleInput, MAX_BOARD_TITLE_LENGTH, BOARD_TITLE_LENGTH_ERROR, 'board-title-length')) {
        return;
    }

    if (!validateBoardWriteTextLength(contentInput, MAX_BOARD_CONTENT_LENGTH, BOARD_CONTENT_LENGTH_ERROR, 'board-content-length')) {
        return;
    }

    const btn = document.getElementById('submitBtn');
    btn.disabled = true;
    btn.innerText = '처리 중...';

    try {
        // 🚨 팩트: 신규 작성이든 수정이든 이제 파일이 포함될 수 있으므로 무조건 FormData로 묶습니다.
        const formData = new FormData(form);

        if (editPostId) {
            // 🏋️‍♂️ 1. 수정 모드 로직 (PUT 전송 + FormData)
            const response = await fetch('/api/posts/' + editPostId, {
                method: 'PUT',
                // 🚨 경고: FormData를 보낼 때는 headers에 'Content-Type'을 절대 수동으로 적으면 안 됩니다.
                // 브라우저가 알아서 multipart/form-data와 boundary 경계선을 세팅합니다.
                body: formData
            });

            if (!response.ok) throw new Error('수정 실패');
            if (typeof showToast === 'function') showToast('게시글이 수정되었습니다.', 'success');
            setTimeout(() => location.replace('/api/view/community/' + editPostId), 900);

        } else {
            // 🏋️‍♂️ 2. 신규 작성 모드 로직 (POST 전송 + FormData)
            const response = await fetch('/api/posts', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) throw new Error('등록 실패');
            if (typeof showToast === 'function') showToast('게시글이 등록되었습니다.', 'success');
            setTimeout(() => location.replace('/api/view/community'), 900);
        }
    } catch (error) {
        console.error(error);
        if (typeof showToast === 'function') showToast('요청 처리 중 오류가 발생했습니다.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerText = editPostId ? '수정하기' : '등록하기';
    }
}