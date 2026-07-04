// find-id.js

var findIdInputWarningTimers = {};
var findIdInputWarningLastShownAt = {};
var findIdToastCooldownState = {};

var findIdCodeSending = false;
var findIdCodeVerifying = false;
var findIdSubmitting = false;

var FIND_ID_INPUT_WARNING_DELAY = 600;
var FIND_ID_INPUT_WARNING_COOLDOWN = 1800;
var FIND_ID_TOAST_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    var findIdForm = document.getElementById('findIdForm');
    var sendCodeBtn = document.getElementById('sendCodeBtn');
    var verifyBtn = document.getElementById('verifyBtn');
    var emailInput = document.getElementById('email');

    setupFindIdInputRules();

    bindFindIdEventOnce(sendCodeBtn, 'click', 'findIdSendCodeBound', function () {
        sendFindIdCode();
    });

    bindFindIdEventOnce(verifyBtn, 'click', 'findIdVerifyBound', verifyFindIdCode);
    bindFindIdEventOnce(findIdForm, 'submit', 'findIdSubmitBound', submitFindId);
    bindFindIdEventOnce(emailInput, 'input', 'findIdEmailResetBound', resetEmailVerified);
});

function bindFindIdEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === 'true') {
        return;
    }

    element.dataset[bindKey] = 'true';
    element.addEventListener(eventName, handler);
}

function showFindIdToastLimited(message, type, key, duration) {
    var toastKey = key || message;
    var now = Date.now();

    if (findIdToastCooldownState[toastKey] && now - findIdToastCooldownState[toastKey] < FIND_ID_TOAST_COOLDOWN) {
        return;
    }

    findIdToastCooldownState[toastKey] = now;

    if (duration) {
        showToast(message, type, duration);
        return;
    }

    showToast(message, type);
}

// 입력값 안내 설정
function setupFindIdInputRules() {
    // 입력 자체를 막지 않고, 조건에 안 맞으면 toast만 띄움

    addInputWarning('name', {
        maxLength: 30,
        pattern: /^[가-힣a-zA-Z]*$/,
        message: '이름은 한글/영문 30자 이내로 입력해주세요.'
    });

    addInputWarning('email', {
        maxLength: 100,
        pattern: /^\S*$/,
        message: '이메일은 공백 없이 100자 이내로 입력해주세요.'
    });

    addInputWarning('emailCode', {
        maxLength: 6,
        pattern: /^[0-9]*$/,
        message: '인증번호는 6자리 숫자로 입력해주세요.'
    });
}

// 입력값은 건드리지 않고 조건 위반 시 toast만 띄움
function addInputWarning(id, options) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (input.dataset.findIdWarningBound === 'true') {
        return;
    }

    input.dataset.findIdWarningBound = 'true';

    var isComposing = false;

    input.addEventListener('compositionstart', function () {
        isComposing = true;
    });

    input.addEventListener('compositionend', function () {
        isComposing = false;
        checkInputWarning(input, id, options);
    });

    input.addEventListener('input', function () {
        if (isComposing) {
            return;
        }

        checkInputWarning(input, id, options);
    });

    input.addEventListener('blur', function () {
        checkInputWarning(input, id, options, true);
    });
}

function checkInputWarning(input, id, options, forceShow) {
    var value = input.value;

    if (!value) {
        clearInputWarning(id);
        return;
    }

    if (options.maxLength && value.length > options.maxLength) {
        showInputWarningOnce(id, 'length', options.message, forceShow === true);
        return;
    }

    if (options.pattern && !options.pattern.test(value)) {
        showInputWarningOnce(id, 'pattern', options.message, forceShow === true);
        return;
    }

    clearInputWarning(id);
}

// 빠르게 입력 중이면 기다렸다가 한 번만 보여주고, 계속 틀리면 일정 시간 뒤 다시 보여줌
function showInputWarningOnce(id, type, message, forceShow) {
    var key = id + ':' + type;
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    var currentValue = input.value;
    var delay = forceShow ? 0 : FIND_ID_INPUT_WARNING_DELAY;

    clearInputWarningTimers(id);

    findIdInputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id);

        if (!latestInput) {
            return;
        }

        var latestValue = latestInput.value;

        if (latestValue !== currentValue) {
            return;
        }

        var now = Date.now();

        if (findIdInputWarningLastShownAt[key] && now - findIdInputWarningLastShownAt[key] < FIND_ID_INPUT_WARNING_COOLDOWN) {
            return;
        }

        findIdInputWarningLastShownAt[key] = now;
        showFindIdToastLimited(message, 'warning', key);
    }, delay);
}

function clearInputWarningTimers(id) {
    Object.keys(findIdInputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(findIdInputWarningTimers[key]);
            delete findIdInputWarningTimers[key];
        }
    });
}

function clearInputWarning(id) {
    clearInputWarningTimers(id);

    Object.keys(findIdInputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete findIdInputWarningLastShownAt[key];
        }
    });
}

function isValidEmail(email) {
    if (email.length > 100) {
        return false;
    }

    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidName(name) {
    return /^[가-힣a-zA-Z]{1,30}$/.test(name);
}

function isValidEmailCode(code) {
    return /^\d{6}$/.test(code);
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

// 아이디 찾기 인증번호 전송
async function sendFindIdCode() {
    if (findIdCodeSending) {
        return;
    }

    var emailInput = document.getElementById('email');
    var sendCodeBtn = document.getElementById('sendCodeBtn');

    if (!emailInput) {
        showFindIdToastLimited('이메일 입력창을 찾을 수 없습니다.', 'error', 'find-id-email-missing');
        return;
    }

    var email = emailInput.value.trim();

    if (!email) {
        showFindIdToastLimited('이메일을 먼저 입력해주세요.', 'error', 'find-id-email-empty');
        return;
    }

    if (email.length > 100) {
        showFindIdToastLimited('이메일은 100자 이내로 입력해주세요.', 'warning', 'find-id-email-length');
        return;
    }

    if (!isValidEmail(email)) {
        showFindIdToastLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'find-id-email-invalid');
        return;
    }

    findIdCodeSending = true;

    if (sendCodeBtn) {
        sendCodeBtn.disabled = true;
        sendCodeBtn.dataset.originalText = sendCodeBtn.innerText;
        sendCodeBtn.innerText = '전송중';
    }

    try {
        resetEmailVerified();

        var response = await fetch('/api/members/find-id/send-email', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: email,
                purpose: 'FIND_ID'
            })
        });

        var result = await safeJson(response);

        if (!response.ok || (result && result.success === false)) {
            showFindIdToastLimited(getErrorMessage(result, '인증번호 전송에 실패했습니다.'), 'error', 'find-id-send-error');
            return;
        }

        showFindIdToastLimited('인증번호가 전송되었습니다.', 'success', 'find-id-send-success');

    } catch (e) {
        showFindIdToastLimited('인증번호 전송 중 오류가 발생했습니다.', 'error', 'find-id-send-fetch-error');

    } finally {
        findIdCodeSending = false;

        if (sendCodeBtn) {
            sendCodeBtn.disabled = false;
            sendCodeBtn.innerText = sendCodeBtn.dataset.originalText || '전송';
            delete sendCodeBtn.dataset.originalText;
        }
    }
}

// 아이디 찾기 인증번호 확인
async function verifyFindIdCode() {
    if (findIdCodeVerifying) {
        return;
    }

    var emailInput = document.getElementById('email');
    var codeInput = document.getElementById('emailCode');
    var verifyBtn = document.getElementById('verifyBtn');

    if (!emailInput || !codeInput) {
        showFindIdToastLimited('이메일 인증 입력 정보를 찾을 수 없습니다.', 'error', 'find-id-verify-input-missing');
        return;
    }

    var email = emailInput.value.trim();
    var code = codeInput.value.trim();

    if (!email || !code) {
        showFindIdToastLimited('이메일과 인증번호를 모두 입력해주세요.', 'error', 'find-id-verify-empty');
        return;
    }

    if (!isValidEmail(email)) {
        showFindIdToastLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'find-id-verify-email-invalid');
        return;
    }

    if (!isValidEmailCode(code)) {
        showFindIdToastLimited('인증번호는 6자리 숫자로 입력해주세요.', 'warning', 'find-id-verify-code-invalid');
        return;
    }

    findIdCodeVerifying = true;
    var verified = false;

    if (verifyBtn) {
        verifyBtn.disabled = true;
        verifyBtn.dataset.originalText = verifyBtn.innerText;
        verifyBtn.innerText = '확인중';
    }

    try {
        var response = await fetch('/api/members/find-id/verify', {
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
            showFindIdToastLimited(getErrorMessage(result, '인증번호를 확인해주세요.'), 'error', 'find-id-verify-error');
            return;
        }

        verified = true;
        document.getElementById('emailVerified').value = 'true';

        if (verifyBtn) {
            verifyBtn.innerText = '완료';
            verifyBtn.classList.add('success');
        }

        showFindIdToastLimited('인증이 완료되었습니다.', 'success', 'find-id-verify-success');

    } catch (e) {
        showFindIdToastLimited('인증번호 확인 중 오류가 발생했습니다.', 'error', 'find-id-verify-fetch-error');

    } finally {
        findIdCodeVerifying = false;

        if (verifyBtn) {
            verifyBtn.disabled = false;

            if (!verified) {
                verifyBtn.innerText = verifyBtn.dataset.originalText || '확인';
            }

            delete verifyBtn.dataset.originalText;
        }
    }
}

// 아이디 찾기 요청
async function submitFindId(event) {
    event.preventDefault();

    if (findIdSubmitting) {
        return;
    }

    var name = document.getElementById('name').value.trim();
    var email = document.getElementById('email').value.trim();
    var code = document.getElementById('emailCode').value.trim();
    var emailVerified = document.getElementById('emailVerified').value;

    if (!name || !email || !code) {
        showFindIdToastLimited('이름, 이메일, 인증번호를 모두 입력해주세요.', 'error', 'find-id-submit-required');
        return;
    }

    if (emailVerified !== 'true') {
        showFindIdToastLimited('이메일 인증을 완료해주세요.', 'error', 'find-id-submit-email-not-verified');
        return;
    }

    if (!isValidName(name)) {
        showFindIdToastLimited('이름은 한글/영문 30자 이내로 입력해주세요.', 'warning', 'find-id-submit-name-invalid');
        return;
    }

    if (!isValidEmail(email)) {
        showFindIdToastLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'find-id-submit-email-invalid');
        return;
    }

    if (!isValidEmailCode(code)) {
        showFindIdToastLimited('인증번호는 6자리 숫자로 입력해주세요.', 'warning', 'find-id-submit-code-invalid');
        return;
    }

    var submitBtn = document.querySelector('#findIdForm button[type="submit"], #findIdForm input[type="submit"]');
    findIdSubmitting = true;

    if (submitBtn) {
        submitBtn.disabled = true;
    }

    try {
        var response = await fetch('/api/members/find-id', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                name: name,
                email: email,
                code: code
            })
        });

        var result = await safeJson(response);

        if (!response.ok || !result || result.success === false) {
            showFindIdToastLimited(getErrorMessage(result, '아이디 찾기에 실패했습니다.'), 'error', 'find-id-submit-error');
            findIdSubmitting = false;

            if (submitBtn) {
                submitBtn.disabled = false;
            }

            return;
        }

        var successMessage = result.data && result.data.message
            ? result.data.message
            : '인증한 이메일로 아이디를 전송했습니다.';

        showFindIdToastLimited(successMessage, 'success', 'find-id-submit-success', 4000);

        setTimeout(function () {
            location.href = '/api/view/login';
        }, 900);

    } catch (e) {
        showFindIdToastLimited('아이디 찾기 중 오류가 발생했습니다.', 'error', 'find-id-submit-fetch-error');
        findIdSubmitting = false;

        if (submitBtn) {
            submitBtn.disabled = false;
        }
    }
}