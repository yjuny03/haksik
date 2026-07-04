// signup-user.js

var loginIdChecked = false;
var nicknameChecked = false;

var sentEmail = '';
var verifiedSchoolName = '';
var currentTermCheckbox = null;

// toast 중복 방지 / 입력 경고 debounce 상태
var inputWarningTimers = {};
var inputWarningLastShownAt = {};
var inputWarningLastValue = {};
var toastCooldownState = {};

// 요청 중복 방지 상태
var duplicateCheckingState = {};
var emailSending = false;
var emailVerifying = false;
var signupSubmitting = false;

var INPUT_WARNING_DELAY = 600;
var INPUT_WARNING_COOLDOWN = 1800;
var TOAST_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    var signupForm = document.getElementById('signupForm');
    var loginIdInput = document.getElementById('loginId');
    var nicknameInput = document.getElementById('nickname');
    var modalEmailInput = document.getElementById('modalEmail');

    setupSignupInputRules();

    var emailModalOpenBtn = document.getElementById('emailModalOpenBtn');
    var emailModalCloseBtn = document.getElementById('emailModalCloseBtn');
    var emailSendBtn = document.getElementById('emailSendBtn');
    var emailVerifyBtn = document.getElementById('emailVerifyBtn');

    bindEventOnce(loginIdInput, 'input', 'loginIdResetBound', function () {
        loginIdChecked = false;
        resetDuplicateButton('loginId');
    });

    bindEventOnce(nicknameInput, 'input', 'nicknameResetBound', function () {
        nicknameChecked = false;
        resetDuplicateButton('nickname');
    });

    bindEventOnce(modalEmailInput, 'input', 'emailResetBound', resetEmailVerificationState);
    bindEventOnce(emailModalOpenBtn, 'click', 'emailModalOpenBound', openEmailModal);
    bindEventOnce(emailModalCloseBtn, 'click', 'emailModalCloseBound', closeEmailModal);
    bindEventOnce(emailSendBtn, 'click', 'emailSendBound', sendEmailCode);
    bindEventOnce(emailVerifyBtn, 'click', 'emailVerifyBound', verifyEmailCode);

    document.querySelectorAll('[data-check-type]').forEach(function (button) {
        bindEventOnce(button, 'click', 'duplicateCheckBound', function () {
            checkDuplicate(this.dataset.checkType);
        });
    });

    bindEventOnce(signupForm, 'submit', 'signupSubmitBound', signupUser);

    var termsAgreeBtn = document.getElementById('termsAgreeBtn');
    bindEventOnce(termsAgreeBtn, 'click', 'termsAgreeBound', function () {
        if (currentTermCheckbox) {
            currentTermCheckbox.checked = true;
        }

        closeTermsModal();
    });
});

// 같은 이벤트가 여러 번 붙는 것 방지
function bindEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === 'true') {
        return;
    }

    element.dataset[bindKey] = 'true';
    element.addEventListener(eventName, handler);
}

// 같은 toast가 짧은 시간 안에 계속 쌓이는 것 방지
function showToastLimited(message, type, key) {
    var toastKey = key || message;
    var now = Date.now();

    if (toastCooldownState[toastKey] && now - toastCooldownState[toastKey] < TOAST_COOLDOWN) {
        return;
    }

    toastCooldownState[toastKey] = now;
    showToast(message, type);
}

// 이메일 인증 모달 열기
function openEmailModal() {
    var modal = document.getElementById('emailModal');

    if (!modal) {
        return;
    }

    modal.classList.add('active');
}

// 이메일 인증 모달 닫기
function closeEmailModal() {
    var modal = document.getElementById('emailModal');

    if (!modal) {
        return;
    }

    modal.classList.remove('active');
}

// 이메일 인증 상태 초기화
function resetEmailVerificationState() {
    var schoolIdInput = document.getElementById('schoolId');
    var schoolEmailInput = document.getElementById('schoolEmail');
    var emailVerifiedInput = document.getElementById('emailVerified');
    var verifiedBox = document.getElementById('verifiedBox');
    var verifiedText = document.getElementById('verifiedText');
    var verifyBtn = document.getElementById('emailVerifyBtn');

    if (schoolIdInput) {
        schoolIdInput.value = '';
    }

    if (schoolEmailInput) {
        schoolEmailInput.value = '';
    }

    if (emailVerifiedInput) {
        emailVerifiedInput.value = 'false';
    }

    sentEmail = '';
    verifiedSchoolName = '';

    if (verifiedBox) {
        verifiedBox.classList.remove('active');
    }

    if (verifiedText) {
        verifiedText.innerText = '';
    }

    if (verifyBtn) {
        verifyBtn.innerText = '확인';
        verifyBtn.classList.remove('success');
    }
}

// 중복확인 버튼 초기화
function resetDuplicateButton(type) {
    var button = document.querySelector('[data-check-type="' + type + '"]');

    if (!button) {
        return;
    }

    button.innerText = '중복';
    button.classList.remove('success');
}

// 중복확인 버튼 완료 처리
function completeDuplicateButton(type) {
    var button = document.querySelector('[data-check-type="' + type + '"]');

    if (!button) {
        return;
    }

    button.innerText = '완료';
    button.classList.add('success');
}

// 회원가입 입력값 안내 설정
function setupSignupInputRules() {
    addInputWarning('loginId', {
        maxLength: 20,
        pattern: /^[a-zA-Z0-9]*$/,
        message: '아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.'
    });

    addInputWarning('name', {
        maxLength: 30,
        pattern: /^[가-힣a-zA-Z]*$/,
        message: '이름은 한글/영문 30자 이내로 입력해주세요.'
    });

    addInputWarning('nickname', {
        maxLength: 30,
        pattern: /^[가-힣a-zA-Z0-9]*$/,
        message: '닉네임은 한글/영문/숫자 30자 이내로 입력해주세요.'
    });

    addInputWarning('department', {
        maxLength: 40,
        pattern: /^[가-힣a-zA-Z0-9\s]*$/,
        message: '학과는 한글/영문/숫자 40자 이내로 입력해주세요.'
    });

    setPhoneFormatRule('phone');

    addInputWarning('password', {
        maxLength: 20,
        message: '비밀번호는 8자 이상, 20자 이내로 입력해주세요.'
    });

    addInputWarning('passwordConfirm', {
        maxLength: 20,
        message: '비밀번호 확인은 20자 이내로 입력해주세요.'
    });

    addInputWarning('modalCode', {
        maxLength: 6,
        pattern: /^[0-9]*$/,
        message: '인증번호는 6자리 숫자로 입력해주세요.'
    });

    addInputWarning('modalEmail', {
        maxLength: 100,
        pattern: /^\S*$/,
        message: '이메일은 공백 없이 100자 이내로 입력해주세요.'
    });

    setupPasswordToggleButtons();
}

// 입력값은 건드리지 않고 조건 위반 시 toast만 띄움
function addInputWarning(id, options) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (input.dataset.warningBound === 'true') {
        return;
    }

    input.dataset.warningBound = 'true';

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
    var delay = forceShow ? 0 : INPUT_WARNING_DELAY;

    clearInputWarningTimers(id);

    inputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id);

        if (!latestInput) {
            return;
        }

        var latestValue = latestInput.value;

        // 타이머가 기다리는 동안 값이 바뀌었으면 예전 경고는 무시
        if (latestValue !== currentValue) {
            return;
        }

        var now = Date.now();

        // 같은 입력창의 같은 오류가 너무 자주 뜨는 것 방지
        if (inputWarningLastShownAt[key] && now - inputWarningLastShownAt[key] < INPUT_WARNING_COOLDOWN) {
            return;
        }

        inputWarningLastValue[key] = latestValue;
        inputWarningLastShownAt[key] = now;
        showToastLimited(message, 'warning', key);
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

    Object.keys(inputWarningLastValue).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete inputWarningLastValue[key];
        }
    });
}

// 전화번호만 입력 중 자동 포맷 유지
function setPhoneFormatRule(id) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (input.dataset.phoneFormatBound === 'true') {
        return;
    }

    input.dataset.phoneFormatBound = 'true';

    input.addEventListener('input', function () {
        var before = input.value;
        var nextValue = formatPhone(before);

        if (before === nextValue) {
            return;
        }

        input.value = nextValue;
    });
}

// 비밀번호 보기 버튼 설정
function setupPasswordToggleButtons() {
    document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
        if (button.dataset.bound === 'true') {
            return;
        }

        button.dataset.bound = 'true';

        var targetId = button.dataset.target;
        var input = document.getElementById(targetId);

        if (!input) {
            return;
        }

        button.addEventListener('click', function () {
            var isPassword = input.type === 'password';

            input.type = isPassword ? 'text' : 'password';
            button.innerText = isPassword ? '숨김' : '보기';
        });
    });
}

// 전화번호 자동 포맷
// 01012345678 -> 010-1234-5678
// 050712345678 -> 0507-1234-5678
function formatPhone(value) {
    var numbers = value.replace(/\D/g, '').slice(0, 12);

    if (numbers.startsWith('0507')) {
        if (numbers.length <= 4) {
            return numbers;
        }

        if (numbers.length <= 8) {
            return numbers.slice(0, 4) + '-' + numbers.slice(4);
        }

        return numbers.slice(0, 4) + '-' + numbers.slice(4, 8) + '-' + numbers.slice(8);
    }

    if (numbers.length <= 3) {
        return numbers;
    }

    if (numbers.length <= 7) {
        return numbers.slice(0, 3) + '-' + numbers.slice(3);
    }

    return numbers.slice(0, 3) + '-' + numbers.slice(3, 7) + '-' + numbers.slice(7, 11);
}

// 서버로 보낼 때는 숫자만 전송
function normalizePhone(value) {
    return value.replace(/\D/g, '');
}

// 생년월일 과거 날짜 검증
function isPastDate(value) {
    var selectedDate = new Date(value);
    var today = new Date();

    selectedDate.setHours(0, 0, 0, 0);
    today.setHours(0, 0, 0, 0);

    return selectedDate < today;
}

// 회원가입 입력값 최종 검증
function validateSignupInput(data) {
    if (!/^[a-zA-Z0-9]{4,20}$/.test(data.loginId)) {
        return '아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.';
    }

    if (data.password.length < 8 || data.password.length > 20) {
        return '비밀번호는 8자 이상, 20자 이내로 입력해주세요.';
    }

    if (!/^[가-힣a-zA-Z]{1,30}$/.test(data.name)) {
        return '이름은 한글/영문 30자 이내로 입력해주세요.';
    }

    if (!/^[가-힣a-zA-Z0-9]{1,30}$/.test(data.nickname)) {
        return '닉네임은 한글/영문/숫자 30자 이내로 입력해주세요.';
    }

    if (!/^[가-힣a-zA-Z0-9\s]{1,40}$/.test(data.department)) {
        return '학과는 한글/영문/숫자 40자 이내로 입력해주세요.';
    }

    if (!data.birthDate || !isPastDate(data.birthDate)) {
        return '생년월일은 오늘보다 이전 날짜여야 합니다.';
    }

    var phoneNumbers = normalizePhone(data.phone);

    if (!/^(01[016789]\d{7,8}|0507\d{8})$/.test(phoneNumbers)) {
        return '전화번호는 010-1234-5678 또는 0507-1234-5678 형식으로 입력해주세요.';
    }

    return null;
}

// 중복확인 전 아이디/닉네임 형식 검증
function validateDuplicateValue(type, value) {
    if (type === 'loginId' && !/^[a-zA-Z0-9]{4,20}$/.test(value)) {
        return '아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.';
    }

    if (type === 'nickname' && !/^[가-힣a-zA-Z0-9]{1,30}$/.test(value)) {
        return '닉네임은 한글/영문/숫자 30자 이내로 입력해주세요.';
    }

    return null;
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

// 이메일 인증번호 전송
async function sendEmailCode() {
    if (emailSending) {
        return;
    }

    var emailInput = document.getElementById('modalEmail');
    var sendBtn = document.getElementById('emailSendBtn');

    if (!emailInput) {
        showToastLimited('이메일 입력창을 찾을 수 없습니다.', 'error', 'email-input-missing');
        return;
    }

    var email = emailInput.value.trim();

    if (!email) {
        showToastLimited('학교 이메일을 입력해주세요.', 'error', 'email-empty');
        return;
    }

    emailSending = true;

    if (sendBtn) {
        sendBtn.disabled = true;
        sendBtn.innerText = '전송중';
    }

    try {
        resetEmailVerificationState();
        emailInput.value = email;

        var response = await fetch('/api/auth/email/send', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: email
            })
        });

        var result = await safeJson(response);

        if (!response.ok || !result || result.success === false) {
            showToastLimited(getErrorMessage(result, '등록된 학교 이메일 도메인이 아니거나 이미 사용 중인 이메일입니다.'), 'error', 'email-send-error');
            return;
        }

        if (!result.data || !result.data.schoolId) {
            showToastLimited('학교 인증 응답 데이터가 올바르지 않습니다.', 'error', 'email-send-invalid-response');
            return;
        }

        document.getElementById('schoolId').value = result.data.schoolId;
        sentEmail = email;
        verifiedSchoolName = result.data.schoolName;

        showToastLimited(verifiedSchoolName + ' 인증번호가 전송되었습니다.', 'success', 'email-send-success');

    } catch (e) {
        showToastLimited('인증번호 전송 중 오류가 발생했습니다.', 'error', 'email-send-fetch-error');

    } finally {
        emailSending = false;

        if (sendBtn) {
            sendBtn.disabled = false;
            sendBtn.innerText = '전송';
        }
    }
}

// 이메일 인증번호 확인
async function verifyEmailCode() {
    if (emailVerifying) {
        return;
    }

    var emailInput = document.getElementById('modalEmail');
    var codeInput = document.getElementById('modalCode');
    var schoolIdInput = document.getElementById('schoolId');
    var verifyBtn = document.getElementById('emailVerifyBtn');

    if (!emailInput || !codeInput || !schoolIdInput) {
        showToastLimited('이메일 인증 입력 정보를 찾을 수 없습니다.', 'error', 'email-verify-input-missing');
        return;
    }

    var email = emailInput.value.trim();
    var code = codeInput.value.trim();
    var schoolId = schoolIdInput.value;

    if (!email || !code) {
        showToastLimited('이메일과 인증번호를 모두 입력해주세요.', 'error', 'email-verify-empty');
        return;
    }

    if (!/^\d{6}$/.test(code)) {
        showToastLimited('인증번호는 6자리 숫자로 입력해주세요.', 'warning', 'email-verify-code-invalid');
        return;
    }

    if (!schoolId || sentEmail !== email) {
        showToastLimited('먼저 현재 이메일로 인증번호 전송을 해주세요.', 'error', 'email-verify-not-sent');
        return;
    }

    emailVerifying = true;

    if (verifyBtn) {
        verifyBtn.disabled = true;
        verifyBtn.innerText = '확인중';
    }

    try {
        var response = await fetch('/api/auth/email/verify', {
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
            showToastLimited(getErrorMessage(result, '인증번호를 확인해주세요.'), 'error', 'email-verify-error');
            return;
        }

        document.getElementById('schoolEmail').value = email;
        document.getElementById('emailVerified').value = 'true';

        document.getElementById('verifiedText').innerText =
            verifiedSchoolName + ' / ' + email + ' 인증 완료';

        document.getElementById('verifiedBox').classList.add('active');

        if (verifyBtn) {
            verifyBtn.innerText = '완료';
            verifyBtn.classList.add('success');
        }

        showToastLimited('학교 이메일 인증이 완료되었습니다.', 'success', 'email-verify-success');

        setTimeout(function () {
            closeEmailModal();
        }, 700);

    } catch (e) {
        showToastLimited('인증번호 확인 중 오류가 발생했습니다.', 'error', 'email-verify-fetch-error');

    } finally {
        emailVerifying = false;

        if (verifyBtn && verifyBtn.innerText === '확인중') {
            verifyBtn.innerText = '확인';
        }

        if (verifyBtn) {
            verifyBtn.disabled = false;
        }
    }
}

// 아이디/닉네임 중복 확인
async function checkDuplicate(type) {
    if (duplicateCheckingState[type]) {
        return;
    }

    var target = document.getElementById(type);
    var button = document.querySelector('[data-check-type="' + type + '"]');

    if (!target) {
        showToastLimited('중복 확인 대상을 찾을 수 없습니다.', 'error', 'duplicate-target-error');
        return;
    }

    var value = target.value.trim();

    if (!value) {
        showToastLimited('값을 입력해주세요.', 'error', type + ':empty');
        return;
    }

    var duplicateValidationMessage = validateDuplicateValue(type, value);

    if (duplicateValidationMessage) {
        showToastLimited(duplicateValidationMessage, 'warning', type + ':invalid');
        return;
    }

    duplicateCheckingState[type] = true;

    if (button) {
        button.disabled = true;
        button.innerText = '확인중';
    }

    try {
        var url = type === 'loginId'
            ? '/api/members/check-login-id?loginId=' + encodeURIComponent(value)
            : '/api/members/check-nickname?nickname=' + encodeURIComponent(value);

        var response = await fetch(url);
        var result = await safeJson(response);

        // 요청 보내는 중에 사용자가 값을 바꿨으면 이전 응답은 무시
        if (target.value.trim() !== value) {
            return;
        }

        if (!response.ok || !result || result.success === false) {
            showToastLimited(getErrorMessage(result, '중복 확인 중 오류가 발생했습니다.'), 'error', type + ':server-error');
            resetDuplicateButton(type);
            return;
        }

        if (!result.data || typeof result.data.available !== 'boolean') {
            showToastLimited('중복 확인 응답 데이터가 올바르지 않습니다.', 'error', type + ':invalid-response');
            resetDuplicateButton(type);
            return;
        }

        if (result.data.available) {
            if (type === 'loginId') {
                loginIdChecked = true;
            }

            if (type === 'nickname') {
                nicknameChecked = true;
            }

            completeDuplicateButton(type);
            showToastLimited('사용 가능한 값입니다.', 'success', type + ':available');
            return;
        }

        if (type === 'loginId') {
            loginIdChecked = false;
        }

        if (type === 'nickname') {
            nicknameChecked = false;
        }

        resetDuplicateButton(type);
        showToastLimited('이미 사용 중입니다.', 'error', type + ':duplicated');

    } catch (e) {
        showToastLimited('중복 확인 중 오류가 발생했습니다.', 'error', type + ':fetch-error');
        resetDuplicateButton(type);

    } finally {
        duplicateCheckingState[type] = false;

        if (button && button.innerText === '확인중') {
            button.disabled = false;
            button.innerText = '중복';
        } else if (button) {
            button.disabled = false;
        }
    }
}

// 일반 이용자 회원가입
async function signupUser(event) {
    event.preventDefault();

    if (signupSubmitting) {
        return;
    }

    var loginId = document.getElementById('loginId').value.trim();
    var password = document.getElementById('password').value;
    var passwordConfirm = document.getElementById('passwordConfirm').value;
    var name = document.getElementById('name').value.trim();
    var nickname = document.getElementById('nickname').value.trim();
    var schoolId = document.getElementById('schoolId').value;
    var department = document.getElementById('department').value.trim();
    var schoolEmail = document.getElementById('schoolEmail').value.trim();
    var birthDate = document.getElementById('birthDate').value;
    var gender = document.getElementById('gender').value;
    var phone = document.getElementById('phone').value.trim();

    if (!loginId || !password || !passwordConfirm || !name || !nickname || !department || !birthDate || !gender || !phone) {
        showToastLimited('필수 입력값을 모두 입력해주세요.', 'error', 'signup-required');
        return;
    }

    var validationMessage = validateSignupInput({
        loginId: loginId,
        password: password,
        name: name,
        nickname: nickname,
        department: department,
        birthDate: birthDate,
        phone: phone
    });

    if (validationMessage) {
        showToastLimited(validationMessage, 'warning', 'signup-validation');
        return;
    }

    if (!loginIdChecked) {
        showToastLimited('아이디 중복 확인을 해주세요.', 'error', 'signup-loginId-check');
        return;
    }

    if (!nicknameChecked) {
        showToastLimited('닉네임 중복 확인을 해주세요.', 'error', 'signup-nickname-check');
        return;
    }

    if (document.getElementById('emailVerified').value !== 'true') {
        showToastLimited('학교 이메일 인증을 완료해주세요.', 'error', 'signup-email-check');
        return;
    }

    if (!schoolId || !schoolEmail) {
        showToastLimited('학교 이메일 인증 정보가 없습니다. 다시 인증해주세요.', 'error', 'signup-email-missing');
        return;
    }

    if (password !== passwordConfirm) {
        showToastLimited('비밀번호가 일치하지 않습니다.', 'error', 'signup-password-match');
        return;
    }

    var termsIds = Array.from(document.querySelectorAll('input[name="termsIds"]:checked'))
        .map(function (item) {
            return Number(item.value);
        });

    if (termsIds.length === 0) {
        showToastLimited('필수 약관에 동의해주세요.', 'error', 'signup-terms');
        return;
    }

    var requestData = {
        loginId: loginId,
        password: password,
        name: name,
        nickname: nickname,
        schoolId: Number(schoolId),
        department: department,
        schoolEmail: schoolEmail,
        birthDate: birthDate,
        gender: gender,
        phone: normalizePhone(phone),
        termsIds: termsIds
    };

    signupSubmitting = true;

    try {
        var response = await fetch('/api/members/signup/user', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        var result = await safeJson(response);

        if (!response.ok || !result || result.success === false) {
            showToastLimited(getErrorMessage(result, '회원가입에 실패했습니다.'), 'error', 'signup-server-error');
            signupSubmitting = false;
            return;
        }

        showToastLimited('회원가입이 완료되었습니다.', 'success', 'signup-success');

        setTimeout(function () {
            location.href = '/api/view/login';
        }, 900);

    } catch (e) {
        showToastLimited('회원가입 중 오류가 발생했습니다.', 'error', 'signup-fetch-error');
        signupSubmitting = false;
    }
}

// 약관 모달 열기
function openTermsModal(event, checkbox) {
    if (!checkbox.checked) {
        return;
    }

    event.preventDefault();
    checkbox.checked = false;
    currentTermCheckbox = checkbox;

    document.getElementById('termsModalTitle').innerText = checkbox.getAttribute('data-title');
    document.getElementById('termsModalContent').innerText = checkbox.getAttribute('data-content');

    document.getElementById('termsModal').classList.add('active');
}

// 약관 모달 닫기
function closeTermsModal() {
    var termsModal = document.getElementById('termsModal');

    if (termsModal) {
        termsModal.classList.remove('active');
    }

    currentTermCheckbox = null;
}