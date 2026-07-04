// password-update.js

let passwordUpdating = false;

// 타이머 및 쿨다운 상태 저장소
const inputWarningState = {};
const inputWarningTimers = {};
const inputWarningLastShownAt = {};
const INPUT_WARNING_DELAY = 600;      // 0.6초 대기
const INPUT_WARNING_COOLDOWN = 1800;  // 1.8초 도배 방지

document.addEventListener('DOMContentLoaded', function () {
    setupPasswordInputRules();

    const passwordForm = document.getElementById('passwordForm');

    if (!passwordForm) {
        return;
    }

    passwordForm.addEventListener('submit', updatePassword);
});

// 🛡️ [수정]: 이벤트를 쪼개지 않고, 원래 구조에 20자 옵션을 통합!
function setupPasswordInputRules() {
    const passwordOptions = {
        maxLength: 20,
        pattern: /^\S*$/,
        message: '비밀번호는 공백 없이 20자 이내로 입력해주세요.'
    };

    addInputWarning('currentPassword', passwordOptions);
    addInputWarning('newPassword', passwordOptions);
    addInputWarning('newPasswordConfirm', passwordOptions);
}

function addInputWarning(id, options) {
    const input = document.getElementById(id);
    if (!input) return;

    let isComposing = false;

    input.addEventListener('compositionstart', function () {
        isComposing = true;
    });

    input.addEventListener('compositionend', function () {
        isComposing = false;
        checkInputWarning(input, id, options);
    });

    input.addEventListener('input', function () {
        if (isComposing) return;
        checkInputWarning(input, id, options);
    });

    input.addEventListener('blur', function () {
        checkInputWarning(input, id, options, true);
    });
}

function checkInputWarning(input, id, options, forceShow = false) {
    const value = input.value;
    if (!value) {
        clearInputWarning(id);
        return;
    }

    // 🚨 20자 초과 시 딜레이 없이 즉시(forceShow=true) 비동기 큐에 알림 등록!
    if (options.maxLength && value.length > options.maxLength) {
        showInputWarningOnce(id, 'length', options.message, true);
        return;
    }

    // 공백 검사는 원래 설정된 0.6초 딜레이 유지
    if (options.pattern && !options.pattern.test(value)) {
        showInputWarningOnce(id, 'pattern', options.message, forceShow);
        return;
    }

    clearInputWarning(id);
}

// setTimeout을 활용한 비동기 처리 알림 함수
function showInputWarningOnce(id, type, message, forceShow = false) {
    const key = id + ':' + type;
    const input = document.getElementById(id);
    if (!input) return;

    const currentValue = input.value;

    // forceShow가 true면 0초 만에 즉시 비동기 실행!
    const delay = forceShow ? 0 : INPUT_WARNING_DELAY;

    clearInputWarningTimers(id);

    inputWarningTimers[key] = setTimeout(function () {
        const latestInput = document.getElementById(id);
        if (!latestInput) return;

        const latestValue = latestInput.value;
        if (latestValue !== currentValue) return;

        const now = Date.now();
        // 1.8초 쿨다운으로 도배 완벽 차단
        if (inputWarningLastShownAt[key] && now - inputWarningLastShownAt[key] < INPUT_WARNING_COOLDOWN) {
            return;
        }

        inputWarningLastShownAt[key] = now;

        // 오빠의 순정 showToast 띄우기!
        showToast(message, 'warning');
    }, delay);
}

function clearInputWarningTimers(id) {
    Object.keys(inputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(inputWarningTimers[key]);
            delete inputWarningTimers[key];
        }
    });
}

function clearInputWarning(id) {
    clearInputWarningTimers(id);

    Object.keys(inputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete inputWarningLastShownAt[key];
        }
    });

    Object.keys(inputWarningState).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete inputWarningState[key];
        }
    });
}

// 비밀번호 변경 요청 로직
function updatePassword(event) {
    event.preventDefault();

    if (passwordUpdating) return;

    const currentPassword = document.getElementById('currentPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const newPasswordConfirm = document.getElementById('newPasswordConfirm').value;

    if (!currentPassword || !newPassword || !newPasswordConfirm) {
        showToast('모든 항목을 입력해주세요!','error');
        return;
    }

    if (newPassword.length < 8) {
        showToast('새 비밀번호는 8자 이상으로 입력해주세요.', 'error');
        return;
    }

    // 20자 넘은 채로 버튼 누르면 제출 전 튕겨내기!
    if (newPassword.length > 20) {
        showToast('새 비밀번호는 20자 이내로 입력해주세요.', 'error');
        return;
    }

    if (newPassword !== newPasswordConfirm) {
        showToast('새 비밀번호와 비밀번호 확인이 일치하지 않습니다.','error');
        return;
    }

    if (/\s/.test(newPassword) || /\s/.test(newPasswordConfirm)) {
        showToast('비밀번호에는 공백을 포함할 수 없습니다.', 'error');
        return;
    }

    const requestData = {
        currentPassword: currentPassword,
        newPassword: newPassword,
        newPasswordConfirm: newPasswordConfirm
    };

    passwordUpdating = true;
    const submitBtn = event.target.querySelector('button[type="submit"]');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.dataset.originalText = submitBtn.innerText;
        submitBtn.innerText = '변경중';
    }

    fetch('/api/view/user/password-update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestData)
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                showToast('비밀번호가 성공적으로 변경되었습니다. \n 다시 로그인 해주세요', 'success');

                setTimeout(() => {
                    window.location.href = '/api/auth/logout';
                }, 1000);
            } else {
                showToast(data.message, 'error');
                passwordUpdating = false;
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerText = submitBtn.dataset.originalText || '변경';
                    delete submitBtn.dataset.originalText;
                }
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showToast('서버와 통신 중 문제가 발생했습니다.','error');
            passwordUpdating = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerText = submitBtn.dataset.originalText || '변경';
                delete submitBtn.dataset.originalText;
            }
        });
}