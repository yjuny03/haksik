// reset-pw.js

var resetPwInputWarningTimers = {};
var resetPwInputWarningLastShownAt = {};
var resetPwToastCooldownState = {};
var resetPwSubmitting = false;

var RESET_PW_INPUT_WARNING_DELAY = 600;
var RESET_PW_INPUT_WARNING_COOLDOWN = 1800;
var RESET_PW_TOAST_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    setupResetPwInputRules();

    var resetPwForm = document.getElementById('resetPwForm');

    bindResetPwEventOnce(resetPwForm, 'submit', 'resetPwSubmitBound', submitResetPw);
});

function bindResetPwEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === 'true') {
        return;
    }

    element.dataset[bindKey] = 'true';
    element.addEventListener(eventName, handler);
}

function showResetPwToastLimited(message, type, key) {
    var toastKey = key || message;
    var now = Date.now();

    if (resetPwToastCooldownState[toastKey] && now - resetPwToastCooldownState[toastKey] < RESET_PW_TOAST_COOLDOWN) {
        return;
    }

    resetPwToastCooldownState[toastKey] = now;
    showToast(message, type);
}

// 비밀번호 재설정 입력값 안내 설정
function setupResetPwInputRules() {
    // 입력값을 막거나 자르지 않고, 조건을 벗어나면 toast만 띄움

    addResetPwInputWarning('newPassword', {
        maxLength: 20,
        message: '비밀번호는 8자 이상, 20자 이내로 입력해주세요.'
    });

    addResetPwInputWarning('confirmPassword', {
        maxLength: 20,
        message: '비밀번호 확인은 20자 이내로 입력해주세요.'
    });
}

// 입력값은 건드리지 않고 조건 위반 시 안내만 띄움
function addResetPwInputWarning(id, options) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (input.dataset.resetPwWarningBound === 'true') {
        return;
    }

    input.dataset.resetPwWarningBound = 'true';

    input.addEventListener('input', function () {
        checkResetPwInputWarning(input, id, options);
    });

    input.addEventListener('blur', function () {
        checkResetPwInputWarning(input, id, options, true);
    });
}

function checkResetPwInputWarning(input, id, options, forceShow) {
    var value = input.value;

    if (!value) {
        clearResetPwInputWarning(id);
        return;
    }

    if (options.maxLength && value.length > options.maxLength) {
        showResetPwInputWarningOnce(id, 'length', options.message, forceShow === true);
        return;
    }

    clearResetPwInputWarning(id);
}

// 빠르게 입력 중이면 기다렸다가 한 번만 보여주고, 계속 틀리면 일정 시간 뒤 다시 보여줌
function showResetPwInputWarningOnce(id, type, message, forceShow) {
    var key = id + ':' + type;
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    var currentValue = input.value;
    var delay = forceShow ? 0 : RESET_PW_INPUT_WARNING_DELAY;

    clearResetPwInputWarningTimers(id);

    resetPwInputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id);

        if (!latestInput) {
            return;
        }

        var latestValue = latestInput.value;

        if (latestValue !== currentValue) {
            return;
        }

        var now = Date.now();

        if (resetPwInputWarningLastShownAt[key] && now - resetPwInputWarningLastShownAt[key] < RESET_PW_INPUT_WARNING_COOLDOWN) {
            return;
        }

        resetPwInputWarningLastShownAt[key] = now;
        showResetPwToastLimited(message, 'warning', key);
    }, delay);
}

function clearResetPwInputWarningTimers(id) {
    Object.keys(resetPwInputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(resetPwInputWarningTimers[key]);
            delete resetPwInputWarningTimers[key];
        }
    });
}

function clearResetPwInputWarning(id) {
    clearResetPwInputWarningTimers(id);

    Object.keys(resetPwInputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete resetPwInputWarningLastShownAt[key];
        }
    });
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch (e) {
        return null;
    }
}

function getErrorMessage(result, fallback) {
    if (result && result.message) {
        return result.message;
    }

    if (result && result.data && result.data.message) {
        return result.data.message;
    }

    return fallback;
}

// 비밀번호 재설정 요청
async function submitResetPw(event) {
    event.preventDefault();

    if (resetPwSubmitting) {
        return;
    }

    var urlParams = new URLSearchParams(window.location.search);
    var email = urlParams.get('email');

    var newPassword = document.getElementById('newPassword').value;
    var confirmPassword = document.getElementById('confirmPassword').value;
    var resetPwError = document.getElementById('resetPwError');

    if (resetPwError) {
        resetPwError.classList.remove('visible');
    }

    if (!email) {
        showResetPwToastLimited('비밀번호 재설정 이메일 정보가 없습니다. 다시 인증해주세요.', 'error', 'reset-pw-email-missing');

        setTimeout(function () {
            window.location.href = '/api/view/find-pw';
        }, 900);

        return;
    }

    if (!newPassword || !confirmPassword) {
        showResetPwToastLimited('비밀번호를 모두 입력해주세요.', 'error', 'reset-pw-required');
        return;
    }

    if (newPassword.length < 8 || newPassword.length > 20) {
        showResetPwToastLimited('비밀번호는 8자 이상, 20자 이내로 입력해주세요.', 'warning', 'reset-pw-new-password-invalid');
        return;
    }

    if (confirmPassword.length > 20) {
        showResetPwToastLimited('비밀번호 확인은 20자 이내로 입력해주세요.', 'warning', 'reset-pw-confirm-password-invalid');
        return;
    }

    if (newPassword !== confirmPassword) {
        if (resetPwError) {
            resetPwError.classList.add('visible');
        }

        showResetPwToastLimited('비밀번호가 일치하지 않습니다.', 'error', 'reset-pw-password-mismatch');
        return;
    }

    var submitBtn = document.querySelector('#resetPwForm button[type="submit"], #resetPwForm input[type="submit"]');
    resetPwSubmitting = true;

    if (submitBtn) {
        submitBtn.disabled = true;
    }

    try {
        var response = await fetch('/api/members/find-pw/reset', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: email,
                newPassword: newPassword
            })
        });

        var result = await safeJson(response);

        if (!response.ok || !result || result.success === false) {
            showResetPwToastLimited(getErrorMessage(result, '비밀번호 변경에 실패했습니다. 다시 시도해주세요.'), 'error', 'reset-pw-submit-error');
            resetPwSubmitting = false;

            if (submitBtn) {
                submitBtn.disabled = false;
            }

            return;
        }

        showResetPwToastLimited('비밀번호가 변경되었습니다. 다시 로그인해주세요.', 'success', 'reset-pw-submit-success');

        setTimeout(function () {
            window.location.href = '/api/view/login';
        }, 900);

    } catch (e) {
        showResetPwToastLimited('비밀번호 변경 중 오류가 발생했습니다.', 'error', 'reset-pw-submit-fetch-error');
        resetPwSubmitting = false;

        if (submitBtn) {
            submitBtn.disabled = false;
        }
    }
}