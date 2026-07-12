// login.js

var loginInputWarningTimers = {};
var loginInputWarningLastShownAt = {};
var loginWarningCooldownState = {};
var loginSubmitting = false;

var LOGIN_INPUT_WARNING_DELAY = 600;
var LOGIN_INPUT_WARNING_COOLDOWN = 1800;
var LOGIN_WARNING_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    setupLoginInputRules();
    setupLoginSubmitValidation();
    showLoginError();
});

function bindLoginEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === 'true') {
        return;
    }

    element.dataset[bindKey] = 'true';
    element.addEventListener(eventName, handler);
}

function showLoginWarningLimited(message, key) {
    var warningKey = key || message;
    var now = Date.now();

    if (loginWarningCooldownState[warningKey] && now - loginWarningCooldownState[warningKey] < LOGIN_WARNING_COOLDOWN) {
        return;
    }

    loginWarningCooldownState[warningKey] = now;
    showLoginWarning(message);
}

// 로그인 입력값 안내 설정
function setupLoginInputRules() {
    // 입력값을 막거나 지우지 않고, 조건을 벗어나면 안내만 띄움

    addLoginInputWarning('loginId', {
        maxLength: 20,
        pattern: /^[a-zA-Z0-9]*$/,
        message: '아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.'
    });

    addLoginInputWarning('password', {
        maxLength: 20,
        message: '비밀번호는 20자 이내로 입력해주세요.'
    });
}

// 입력값은 건드리지 않고 조건 위반 시 안내만 띄움
function addLoginInputWarning(id, options) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (input.dataset.loginWarningBound === 'true') {
        return;
    }

    input.dataset.loginWarningBound = 'true';

    input.addEventListener('input', function () {
        checkLoginInputWarning(input, id, options);
    });

    input.addEventListener('blur', function () {
        checkLoginInputWarning(input, id, options, true);
    });
}

function checkLoginInputWarning(input, id, options, forceShow) {
    var value = input.value;

    if (!value) {
        clearLoginInputWarning(id);
        clearInlineLoginError();
        return;
    }

    if (options.maxLength && value.length > options.maxLength) {
        showLoginInputWarningOnce(id, 'length', options.message, forceShow === true);
        return;
    }

    if (options.pattern && !options.pattern.test(value)) {
        showLoginInputWarningOnce(id, 'pattern', options.message, forceShow === true);
        return;
    }

    clearLoginInputWarning(id);
}

// 빠르게 입력 중이면 기다렸다가 한 번만 보여주고, 계속 틀리면 일정 시간 뒤 다시 보여줌
function showLoginInputWarningOnce(id, type, message, forceShow) {
    var key = id + ':' + type;
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    var currentValue = input.value;
    var delay = forceShow ? 0 : LOGIN_INPUT_WARNING_DELAY;

    clearLoginInputWarningTimers(id);

    loginInputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id);

        if (!latestInput) {
            return;
        }

        var latestValue = latestInput.value;

        if (latestValue !== currentValue) {
            return;
        }

        var now = Date.now();

        if (loginInputWarningLastShownAt[key] && now - loginInputWarningLastShownAt[key] < LOGIN_INPUT_WARNING_COOLDOWN) {
            return;
        }

        loginInputWarningLastShownAt[key] = now;
        showLoginWarningLimited(message, key);
    }, delay);
}

function clearLoginInputWarningTimers(id) {
    Object.keys(loginInputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(loginInputWarningTimers[key]);
            delete loginInputWarningTimers[key];
        }
    });
}

function clearLoginInputWarning(id) {
    clearLoginInputWarningTimers(id);

    Object.keys(loginInputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete loginInputWarningLastShownAt[key];
        }
    });
}

// toast가 있으면 toast 사용, 없으면 화면 안쪽 에러 사용
function showLoginWarning(message) {
    if (typeof showToast === 'function') {
        showToast(message, 'warning');
        return;
    }

    showInlineLoginError(message);
}

// 로그인 submit 전 최소 검증
function setupLoginSubmitValidation() {
    var loginForm = document.getElementById('loginForm');

    if (!loginForm) {
        return;
    }

    bindLoginEventOnce(loginForm, 'submit', 'loginSubmitBound', function (event) {
        if (loginSubmitting) {
            event.preventDefault();
            return;
        }

        var loginId = document.getElementById('loginId').value.trim();
        var password = document.getElementById('password').value;

        if (!loginId || !password) {
            event.preventDefault();
            showInlineLoginError('아이디와 비밀번호를 모두 입력해주세요.');
            return;
        }

        if (!/^[a-zA-Z0-9]{4,20}$/.test(loginId)) {
            event.preventDefault();
            showInlineLoginError('아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.');
            return;
        }

        if (password.length > 20) {
            event.preventDefault();
            showInlineLoginError('비밀번호는 20자 이내로 입력해주세요.');
            return;
        }

        clearInlineLoginError();
        loginSubmitting = true;

        var submitBtn = loginForm.querySelector('button[type="submit"], input[type="submit"]');

        if (submitBtn) {
            submitBtn.disabled = true;
        }
    });
}

// 로그인 실패 메시지 표시
function showLoginError() {
    var params = new URLSearchParams(window.location.search);

    var errorMessage = params.get('error');
    var ownerRejected = params.get('ownerRejected');
    var rejectedReason = params.get('reason');

    if (ownerRejected === 'true') {
        showInlineLoginError(rejectedReason || '점주 가입 신청이 반려되었습니다.');
        return;
    }

    if (!errorMessage) {
        return;
    }

    showInlineLoginError(errorMessage);
}

// 화면 안쪽 에러 표시
function showInlineLoginError(message) {
    var loginError = document.getElementById('loginError');

    if (!loginError) {
        return;
    }

    loginError.innerText = message;
    loginError.classList.add('visible');
}

// 화면 안쪽 에러 제거
function clearInlineLoginError() {
    var loginError = document.getElementById('loginError');

    if (!loginError) {
        return;
    }

    loginError.innerText = '';
    loginError.classList.remove('visible');
}