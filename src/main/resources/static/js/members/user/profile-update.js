// profile-update.js

let originalNickname = '';
let isNicknameChecked = true;

let sentEmail = '';
let verifiedSchoolName = '';
let newSchoolId = '';

let checkingNickname = false;
let updatingProfile = false;
let sendingEmailAuth = false;
let verifyingEmailAuth = false;

// 입력 중 경고(디바운스) 및 쿨다운 처리를 위한 변수 선언
const inputWarningState = {};
const inputWarningTimers = {};
const inputWarningLastShownAt = {};
const INPUT_WARNING_DELAY = 600;
const INPUT_WARNING_COOLDOWN = 1800;

//  버튼 연타 방지용 전역 토스트 쿨다운 변수 선언
const toastCooldownState = {};
const TOAST_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    setupProfileInputRules();

    const emailModalOpenBtn = document.getElementById('emailModalOpenBtn');
    const emailModalCloseBtn = document.getElementById('emailModalCloseBtn');
    const emailSendBtn = document.getElementById('emailSendBtn');
    const emailVerifyBtn = document.getElementById('emailVerifyBtn');
    const profileForm = document.getElementById('profileUpdateForm');
    const nicknameInput = document.getElementById('nickname');
    const profileImageInput = document.getElementById('profileImage');
    const nicknameCheckBtn = document.getElementById('nicknameCheckBtn');

    if (emailModalOpenBtn) emailModalOpenBtn.addEventListener('click', openEmailModal);
    if (emailModalCloseBtn) emailModalCloseBtn.addEventListener('click', closeEmailModal);
    if (emailSendBtn) emailSendBtn.addEventListener('click', sendAuthCode);
    if (emailVerifyBtn) emailVerifyBtn.addEventListener('click', verifyAuthCode);

    if (!profileForm) {
        return;
    }

    originalNickname = nicknameInput.value;
    isNicknameChecked = true;

    nicknameInput.addEventListener('input', function () {
        isNicknameChecked = this.value === originalNickname;
    });

    profileImageInput.addEventListener('change', function () {
        previewImage(this);
    });

    nicknameCheckBtn.addEventListener('click', checkNickname);
    profileForm.addEventListener('submit', updateProfile);
});

//짧은 시간 내 중복 토스트 표시를 막는 래퍼 함수 추가
function showToastLimited(message, type, key) {
    const toastKey = key || message;
    const now = Date.now();

    if (toastCooldownState[toastKey] && now - toastCooldownState[toastKey] < TOAST_COOLDOWN) {
        return;
    }

    toastCooldownState[toastKey] = now;
    showToast(message, type);
}

function setupProfileInputRules() {
    addInputWarning('nickname', {
        maxLength: 10,
        pattern: /^[가-힣a-zA-Z0-9]*$/,
        message: '닉네임은 특수문자 없이 10자 이내로 입력해주세요.'
    });

    document.getElementById('department') && addInputWarning('department', {
        maxLength: 30,
        pattern: /^[가-힣a-zA-Z]*$/,
        message: '학과는 한글/영문 30자 이내로 입력해주세요.'
    });

    document.getElementById('name') && addInputWarning('name', {
        maxLength: 30,
        pattern: /^[가-힣a-zA-Z]*$/,
        message: '이름은 한글/영문 30자 이내로 입력해주세요.'
    });

    document.getElementById('modalEmail') && addInputWarning('modalEmail', {
        maxLength: 100,
        pattern: /^\S*$/,
        message: '이메일은 공백 없이 100자 이내로 입력해주세요.'
    });

    document.getElementById('modalCode') && addInputWarning('modalCode', {
        maxLength: 6,
        pattern: /^[0-9]*$/,
        message: '인증번호는 6자리 숫자로 입력해주세요.'
    });
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
    if (options.maxLength && value.length > options.maxLength) {
        showInputWarningOnce(id, 'length', options.message, forceShow);
        return;
    }
    if (options.pattern && !options.pattern.test(value)) {
        showInputWarningOnce(id, 'pattern', options.message, forceShow);
        return;
    }
    clearInputWarning(id);
}

// 0.6초 대기 후 한 번만 토스트를 띄우는 디바운스 로직 적용
function showInputWarningOnce(id, type, message, forceShow = false) {
    const key = id + ':' + type;
    const input = document.getElementById(id);
    if (!input) return;

    const currentValue = input.value;
    const delay = forceShow ? 0 : INPUT_WARNING_DELAY;

    clearInputWarningTimers(id);

    inputWarningTimers[key] = setTimeout(function () {
        const latestInput = document.getElementById(id);
        if (!latestInput) return;

        const latestValue = latestInput.value;
        if (latestValue !== currentValue) return; // 대기 중 값이 변경되었으면 무시

        const now = Date.now();
        if (inputWarningLastShownAt[key] && now - inputWarningLastShownAt[key] < INPUT_WARNING_COOLDOWN) {
            return;
        }

        inputWarningLastShownAt[key] = now;
        showToastLimited(message, 'warning', key);
    }, delay);
}

// : 정상값 입력 시 동작 중인 타이머를 초기화하는 기능 추가
function clearInputWarningTimers(id) {
    Object.keys(inputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(inputWarningTimers[key]);
            delete inputWarningTimers[key];
        }
    });
}

// 경고 상태뿐만 아니라 타이머 및 최근 표시 시간 기록도 초기화
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

function previewImage(input) {
    if (!input.files || !input.files[0]) return;
    const reader = new FileReader();
    reader.onload = function (event) {
        document.getElementById('imagePreview').src = event.target.result;
    };
    reader.readAsDataURL(input.files[0]);
}

// 닉네임 중복 확인
async function checkNickname() {
    if (checkingNickname) return;

    const nickname = document.getElementById('nickname').value.trim();

    //  모든 알림 호출을 showToastLimited로 변경하여 도배 차단
    if (!nickname) {
        showToastLimited('닉네임을 입력해주세요.','error', 'nick-empty');
        return;
    }
    if (nickname.length > 10) {
        showToastLimited('닉네임은 10자 이내로 설정해야 합니다.', 'error', 'nick-len');
        return;
    }

    if (!/^[가-힣a-zA-Z0-9]*$/.test(nickname)) {
        showToastLimited('닉네임은 특수문자 없이 입력해주세요.', 'error', 'nick-pattern');
        return;
    }

    if (nickname === originalNickname) {
        showToastLimited('현재 사용 중인 닉네임입니다.','error', 'nick-same');
        isNicknameChecked = true;
        return;
    }

    checkingNickname = true;
    const btn = document.getElementById('nicknameCheckBtn');
    if (btn) {
        btn.disabled = true;
        btn.dataset.originalText = btn.innerText;
        btn.innerText = '확인중';
    }

    try {
        const response = await fetch(`/api/members/check-nickname?nickname=${encodeURIComponent(nickname)}`);
        const result = await response.json();

        if (result.success && result.data.available) {
            showToastLimited('사용 가능한 닉네임입니다.','success', 'nick-avail');
            isNicknameChecked = true;
        } else {
            showToastLimited('이미 사용 중인 닉네임입니다.','error', 'nick-dup');
            isNicknameChecked = false;
        }
    } catch (error) {
        console.error(error);
        showToastLimited('중복 확인 중 오류가 발생했습니다.','error', 'nick-err');
    } finally {
        checkingNickname = false;
        if (btn) {
            btn.disabled = false;
            btn.innerText = btn.dataset.originalText || '중복확인';
            delete btn.dataset.originalText;
        }
    }
}

// 프로필 수정 요청
async function updateProfile(event) {
    event.preventDefault();

    if (updatingProfile) return;

    const nickname = document.getElementById('nickname').value.trim();

    // showToastLimited 적용
    if (!/^[가-힣a-zA-Z0-9]*$/.test(nickname)) {
        showToastLimited('닉네임에 특수문자를 포함할 수 없습니다.', 'error', 'upd-pattern');
        return;
    }

    if (!isNicknameChecked) {
        showToastLimited('닉네임 중복 확인을 진행해주세요.','error', 'upd-uncheck');
        return;
    }

    const formData = new FormData();
    formData.append('nickname', nickname);
    formData.append('schoolName', document.getElementById('school').value.trim());
    formData.append('department', document.getElementById('department').value.trim());
    formData.append('name', document.getElementById('name').value.trim());
    formData.append('birthDate', document.getElementById('birthDate').value.trim());

    const imageFile = document.getElementById('profileImage').files[0];
    if (imageFile) {
        formData.append('profileImage', imageFile);
    }

    updatingProfile = true;
    const btn = event.target.querySelector('button[type="submit"]');
    if (btn) {
        btn.disabled = true;
        btn.dataset.originalText = btn.innerText;
        btn.innerText = '수정중';
    }

    try {
        const response = await fetch('/api/members/profile-update', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();

        if (response.ok && result.success) {
            showToastLimited('프로필이 성공적으로 수정되었습니다!','success', 'upd-succ');
            setTimeout(() => {
                location.href = '/api/view/user/my-page';
            }, 500);
        } else {
            showToastLimited(result.message || '프로필 수정에 실패했습니다.','error', 'upd-fail');
            updatingProfile = false;
            if (btn) {
                btn.disabled = false;
                btn.innerText = btn.dataset.originalText || '수정';
            }
        }
    } catch (error) {
        console.error(error);
        showToastLimited('서버와의 통신에 실패했습니다.','error', 'upd-err');
        updatingProfile = false;
        if (btn) {
            btn.disabled = false;
            btn.innerText = btn.dataset.originalText || '수정';
        }
    }
}

function openEmailModal() {
    document.getElementById('emailModal').classList.add('active');
}

function closeEmailModal() {
    document.getElementById('emailModal').classList.remove('active');
}

async function sendAuthCode() {
    if (sendingEmailAuth) return;

    const email = document.getElementById('modalEmail').value.trim();

    //showToastLimited 적용
    if (!email) {
        showToastLimited('새로운 학교 이메일을 입력해주세요.', 'error', 'send-empty');
        return;
    }

    sendingEmailAuth = true;
    const btn = document.getElementById('emailSendBtn');
    if (btn) {
        btn.disabled = true;
        btn.dataset.originalText = btn.innerText;
        btn.innerText = '전송중';
    }

    try {
        const response = await fetch('/api/auth/email/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: email })
        });
        const result = await response.json();

        if (!response.ok || !result || result.success === false) {
            const errorMsg = result && result.message ? result.message : '등록된 학교 도메인이 아니거나 이미 사용 중인 이메일입니다.';
            showToastLimited(errorMsg, 'error', 'send-fail');
            return;
        }

        if (!result.data || !result.data.schoolId) {
            showToastLimited('학교 인증 데이터가 올바르지 않습니다.', 'error', 'send-invalid');
            return;
        }

        newSchoolId = result.data.schoolId;
        sentEmail = email;
        verifiedSchoolName = result.data.schoolName;

        showToastLimited(verifiedSchoolName + ' 인증번호가 전송되었습니다.', 'success', 'send-succ');
        document.getElementById('codeGroup').style.display = 'flex';

    } catch (error) {
        console.error('Error:', error);
        showToastLimited('서버 통신 중 문제가 발생했습니다.', 'error', 'send-err');
    } finally {
        sendingEmailAuth = false;
        if (btn) {
            btn.disabled = false;
            btn.innerText = btn.dataset.originalText || '인증번호 전송';
            delete btn.dataset.originalText;
        }
    }
}

async function verifyAuthCode() {
    if (verifyingEmailAuth) return;

    const email = document.getElementById('modalEmail').value.trim();
    const code = document.getElementById('modalCode').value.trim();

    // showToastLimited 적용
    if (!email || !code) {
        showToastLimited('이메일과 인증번호를 모두 입력해주세요.', 'error', 'vrf-empty');
        return;
    }
    if (sentEmail !== email) {
        showToastLimited('먼저 현재 이메일로 인증번호 전송을 해주세요.', 'error', 'vrf-not-sent');
        return;
    }

    verifyingEmailAuth = true;
    const btn = document.getElementById('emailVerifyBtn');
    if (btn) {
        btn.disabled = true;
        btn.dataset.originalText = btn.innerText;
        btn.innerText = '확인중';
    }

    try {
        const response = await fetch('/api/auth/email/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: email, code: code })
        });
        const result = await response.json();

        if (!response.ok || (result && result.success === false)) {
            const errorMsg = result && result.message ? result.message : '인증번호를 확인해주세요.';
            showToastLimited(errorMsg, 'error', 'vrf-fail');
            return;
        }

        if (result.data && result.data.schoolName) {
            document.getElementById('school').value = result.data.schoolName;
        }

        showToastLimited('학교 이메일 인증이 완료되었습니다!', 'success', 'vrf-succ');
        document.getElementById('email').value = email;

        const schoolInput = document.getElementById('school');
        schoolInput.value = verifiedSchoolName;

        setTimeout(function () {
            closeEmailModal();
        }, 700);

    } catch (error) {
        console.error('Error:', error);
        showToastLimited('서버 통신 중 문제가 발생했습니다.', 'error', 'vrf-err');
    } finally {
        verifyingEmailAuth = false;
        if (btn) {
            btn.disabled = false;
            btn.innerText = btn.dataset.originalText || '인증 완료';
            delete btn.dataset.originalText;
        }
    }
}