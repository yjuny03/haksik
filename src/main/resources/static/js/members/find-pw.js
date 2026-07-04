// find-pw.js

var findPwToastCooldownState = {};
var findPwCodeSending = false;
var findPwCodeVerifying = false;
var findPwMoving = false;

var FIND_PW_TOAST_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    var findPwForm = document.getElementById('findPwForm');
    var sendCodeBtn = document.getElementById('sendCodeBtn');
    var verifyBtn = document.getElementById('verifyBtn');
    var loginIdInput = document.getElementById('loginId');
    var emailInput = document.getElementById('email');

    setupFindPwInputRules();

    bindFindPwEventOnce(sendCodeBtn, 'click', 'findPwSendCodeBound', sendFindPwCode);
    bindFindPwEventOnce(verifyBtn, 'click', 'findPwVerifyBound', verifyFindPwCode);
    bindFindPwEventOnce(findPwForm, 'submit', 'findPwSubmitBound', goToResetPw);
    bindFindPwEventOnce(loginIdInput, 'input', 'findPwLoginIdResetBound', resetEmailVerified);
    bindFindPwEventOnce(emailInput, 'input', 'findPwEmailResetBound', resetEmailVerified);
});

function bindFindPwEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === 'true') {
        return;
    }

    element.dataset[bindKey] = 'true';
    element.addEventListener(eventName, handler);
}

function showFindPwToastLimited(message, type, key) {
    var toastKey = key || message;
    var now = Date.now();

    if (findPwToastCooldownState[toastKey] && now - findPwToastCooldownState[toastKey] < FIND_PW_TOAST_COOLDOWN) {
        return;
    }

    findPwToastCooldownState[toastKey] = now;
    showToast(message, type);
}

function setupFindPwInputRules() {
    setInputRule('loginId', 20, function (value) {
        return value.replace(/[^a-zA-Z0-9]/g, '');
    });

    setInputRule('email', 100, function (value) {
        return value.replace(/\s/g, '');
    });

    setInputRule('emailCode', 6, function (value) {
        return value.replace(/\D/g, '');
    });
}

function setInputRule(id, maxLength, formatter) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    input.maxLength = maxLength;

    if (input.dataset.findPwInputRuleBound === 'true') {
        return;
    }

    input.dataset.findPwInputRuleBound = 'true';

    input.addEventListener('input', function () {
        input.value = formatter(input.value).slice(0, maxLength);
    });
}

function isValidLoginId(loginId) {
    return /^[a-zA-Z0-9]{4,20}$/.test(loginId);
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

// 이메일 인증 상태 초기화
function resetEmailVerified() {
    var emailVerified = document.getElementById('emailVerified');

    if (emailVerified) {
        emailVerified.value = 'false';
    }

    var verifyBtn = document.getElementById('verifyBtn');

    if (verifyBtn) {
        verifyBtn.innerText = '확인';
        verifyBtn.classList.remove('success');
    }
}

// 응답 body를 JSON으로 변환
async function safeJson(response) {
    try {
        return await response.json();
    } catch (e) {
        return null;
    }
}

// API 에러 메시지 조회
function getErrorMessage(result, fallback) {
    if (result && result.message) {
        return result.message;
    }

    if (result && result.data && result.data.message) {
        return result.data.message;
    }

    return fallback;
}

// 비밀번호 찾기 인증번호 전송
async function sendFindPwCode() {
    if (findPwCodeSending) {
        return;
    }

    var loginIdInput = document.getElementById('loginId');
    var emailInput = document.getElementById('email');
    var sendCodeBtn = document.getElementById('sendCodeBtn');

    if (!loginIdInput || !emailInput) {
        showFindPwToastLimited('아이디 또는 이메일 입력창을 찾을 수 없습니다.', 'error', 'find-pw-input-missing');
        return;
    }

    var loginId = loginIdInput.value.trim();
    var email = emailInput.value.trim();

    if (!loginId || !email) {
        showFindPwToastLimited('아이디와 이메일을 모두 입력해주세요.', 'error', 'find-pw-send-required');
        return;
    }

    if (!isValidLoginId(loginId)) {
        showFindPwToastLimited('아이디는 영문/숫자 4~20자로 입력해주세요.', 'warning', 'find-pw-send-loginId-invalid');
        return;
    }

    if (!isValidEmail(email)) {
        showFindPwToastLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'find-pw-send-email-invalid');
        return;
    }

    findPwCodeSending = true;

    if (sendCodeBtn) {
        sendCodeBtn.disabled = true;
        sendCodeBtn.dataset.originalText = sendCodeBtn.innerText;
        sendCodeBtn.innerText = '전송중';
    }

    try {
        var response = await fetch('/api/members/find-pw/send-email', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                loginId: loginId,
                email: email
            })
        });

        var result = await safeJson(response);

        if (!response.ok || (result && result.success === false)) {
            showFindPwToastLimited(getErrorMessage(result, '아이디와 이메일 정보를 다시 확인해주세요.'), 'error', 'find-pw-send-error');
            return;
        }

        showFindPwToastLimited('비밀번호 재설정 인증번호가 전송되었습니다.', 'success', 'find-pw-send-success');

    } catch (e) {
        showFindPwToastLimited('인증번호 전송 중 오류가 발생했습니다.', 'error', 'find-pw-send-fetch-error');

    } finally {
        findPwCodeSending = false;

        if (sendCodeBtn) {
            sendCodeBtn.disabled = false;
            sendCodeBtn.innerText = sendCodeBtn.dataset.originalText || '전송';
            delete sendCodeBtn.dataset.originalText;
        }
    }
}

// 비밀번호 찾기 인증번호 확인
async function verifyFindPwCode() {
    if (findPwCodeVerifying) {
        return;
    }

    var emailInput = document.getElementById('email');
    var codeInput = document.getElementById('emailCode');
    var verifyBtn = document.getElementById('verifyBtn');

    if (!emailInput || !codeInput) {
        showFindPwToastLimited('이메일 인증 입력 정보를 찾을 수 없습니다.', 'error', 'find-pw-verify-input-missing');
        return;
    }

    var email = emailInput.value.trim();
    var code = codeInput.value.trim();

    if (!email || !code) {
        showFindPwToastLimited('이메일과 인증번호를 모두 입력해주세요.', 'error', 'find-pw-verify-required');
        return;
    }

    if (!isValidEmail(email)) {
        showFindPwToastLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'find-pw-verify-email-invalid');
        return;
    }

    if (!/^\d{6}$/.test(code)) {
        showFindPwToastLimited('인증번호는 6자리 숫자로 입력해주세요.', 'warning', 'find-pw-verify-code-invalid');
        return;
    }

    findPwCodeVerifying = true;
    var verified = false;

    if (verifyBtn) {
        verifyBtn.disabled = true;
        verifyBtn.dataset.originalText = verifyBtn.innerText;
        verifyBtn.innerText = '확인중';
    }

    try {
        var response = await fetch('/api/members/find-pw/verify', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: email,
                code: code
            })
        });

        var result = await safeJson(response);

        if (!response.ok || (result && result.success === false)) {
            showFindPwToastLimited(getErrorMessage(result, '인증번호를 확인해주세요.'), 'error', 'find-pw-verify-error');
            return;
        }

        verified = true;
        document.getElementById('emailVerified').value = 'true';

        if (verifyBtn) {
            verifyBtn.innerText = '완료';
            verifyBtn.classList.add('success');
        }

        showFindPwToastLimited('인증이 완료되었습니다.', 'success', 'find-pw-verify-success');

    } catch (e) {
        showFindPwToastLimited('인증번호 확인 중 오류가 발생했습니다.', 'error', 'find-pw-verify-fetch-error');

    } finally {
        findPwCodeVerifying = false;

        if (verifyBtn) {
            verifyBtn.disabled = false;

            if (!verified) {
                verifyBtn.innerText = verifyBtn.dataset.originalText || '확인';
            }

            delete verifyBtn.dataset.originalText;
        }
    }
}

// 비밀번호 재설정 화면 이동
function goToResetPw(event) {
    event.preventDefault();

    if (findPwMoving) {
        return;
    }

    var loginId = document.getElementById('loginId').value.trim();
    var email = document.getElementById('email').value.trim();
    var emailVerified = document.getElementById('emailVerified').value;

    if (!loginId || !email) {
        showFindPwToastLimited('아이디와 이메일을 입력해주세요.', 'error', 'find-pw-move-required');
        return;
    }

    if (emailVerified !== 'true') {
        showFindPwToastLimited('이메일 인증을 먼저 완료해주세요.', 'error', 'find-pw-move-email-not-verified');
        return;
    }

    if (!isValidLoginId(loginId)) {
        showFindPwToastLimited('아이디는 영문/숫자 4~20자로 입력해주세요.', 'warning', 'find-pw-move-loginId-invalid');
        return;
    }

    if (!isValidEmail(email)) {
        showFindPwToastLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'find-pw-move-email-invalid');
        return;
    }

    findPwMoving = true;
    window.location.href = '/api/view/reset-pw?email=' + encodeURIComponent(email);
}