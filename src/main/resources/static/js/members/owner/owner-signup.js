// haksik owner signup js

var OWNER_SIGNUP_URL = '/api/members/signup/owner';
var LOGIN_ID_CHECK_URL = '/api/members/check-owner-login-id';
var EMAIL_CHECK_URL = '/api/members/check-owner-email';
var BUSINESS_NUMBER_CHECK_URL = '/api/members/check-business-number';

var isLoginIdChecked = false;
var isEmailChecked = false;
var isBusinessNumberChecked = false;

// 입력 경고 debounce / notify 중복 방지 상태
var ownerInputWarningTimers = {};
var ownerInputWarningLastShownAt = {};
var ownerNotifyCooldownState = {};

// 요청 중복 방지 상태
var ownerCheckRunningState = {};
var ownerSignupSubmitting = false;

var OWNER_INPUT_WARNING_DELAY = 600;
var OWNER_INPUT_WARNING_COOLDOWN = 1800;
var OWNER_NOTIFY_COOLDOWN = 1200;

document.addEventListener('DOMContentLoaded', function () {
    var ownerSignupForm = document.getElementById('ownerSignupForm');

    if (!ownerSignupForm) {
        return;
    }

    setupOwnerSignupInputRules();

    bindOwnerEventOnce(ownerSignupForm, 'submit', 'ownerSignupSubmitBound', submitOwnerSignup);

    initLoginIdInput();
    initEmailInput();
    initBusinessNumberInput();
});

// 같은 이벤트가 여러 번 붙는 것 방지
function bindOwnerEventOnce(element, eventName, bindKey, handler) {
    if (!element) {
        return;
    }

    if (element.dataset[bindKey] === 'true') {
        return;
    }

    element.dataset[bindKey] = 'true';
    element.addEventListener(eventName, handler);
}

// 같은 알림이 짧은 시간 안에 계속 쌓이는 것 방지
function notifyLimited(message, type, key) {
    var notifyKey = key || message;
    var now = Date.now();

    if (ownerNotifyCooldownState[notifyKey] && now - ownerNotifyCooldownState[notifyKey] < OWNER_NOTIFY_COOLDOWN) {
        return;
    }

    ownerNotifyCooldownState[notifyKey] = now;
    notify(message, type);
}

function setupOwnerSignupInputRules() {
    addOwnerInputWarning('loginId', {
        maxLength: 20,
        pattern: /^[a-zA-Z0-9]*$/,
        message: '아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.'
    });

    addOwnerInputWarning('email', {
        maxLength: 100,
        pattern: /^\S*$/,
        message: '이메일은 공백 없이 100자 이내로 입력해주세요.'
    });

    addOwnerInputWarning('password', {
        maxLength: 20,
        message: '비밀번호는 8자 이상, 20자 이내로 입력해주세요.'
    });

    addOwnerInputWarning('passwordConfirm', {
        maxLength: 20,
        message: '비밀번호 확인은 20자 이내로 입력해주세요.'
    });

    addOwnerInputWarning('ownerName', {
        maxLength: 30,
        pattern: /^[가-힣a-zA-Z\s]*$/,
        message: '대표자명은 한글/영문 30자 이내로 입력해주세요.'
    });

    addOwnerInputWarning('ownerPhone', {
        maxLength: 14,
        pattern: /^[0-9-]*$/,
        message: '대표자 연락처는 숫자와 -만 입력해주세요.'
    });

    addOwnerInputWarning('businessNumber', {
        maxLength: 12,
        pattern: /^[0-9-]*$/,
        message: '사업자등록번호는 숫자 10자리로 입력해주세요.'
    });

    addOwnerInputWarning('businessName', {
        maxLength: 50,
        message: '사업자명은 50자 이내로 입력해주세요.'
    });

    setupPasswordToggleButtons();
}

function addOwnerInputWarning(id, options) {
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    if (input.dataset.ownerWarningBound === 'true') {
        return;
    }

    input.dataset.ownerWarningBound = 'true';

    var isComposing = false;

    input.addEventListener('compositionstart', function () {
        isComposing = true;
    });

    input.addEventListener('compositionend', function () {
        isComposing = false;
        checkOwnerInputWarning(input, id, options);
    });

    input.addEventListener('input', function () {
        if (isComposing) {
            return;
        }

        checkOwnerInputWarning(input, id, options);
    });

    input.addEventListener('blur', function () {
        checkOwnerInputWarning(input, id, options, true);
    });
}

function checkOwnerInputWarning(input, id, options, forceShow) {
    var value = input.value;

    if (!value) {
        clearOwnerInputWarning(id);
        return;
    }

    if (options.maxLength && value.length > options.maxLength) {
        showOwnerInputWarningOnce(id, 'length', options.message, forceShow === true);
        return;
    }

    if (options.pattern && !options.pattern.test(value)) {
        showOwnerInputWarningOnce(id, 'pattern', options.message, forceShow === true);
        return;
    }

    clearOwnerInputWarning(id);
}

// 빠르게 입력 중이면 기다렸다가 한 번만 보여주고, 계속 틀리면 일정 시간 뒤 다시 보여줌
function showOwnerInputWarningOnce(id, type, message, forceShow) {
    var key = id + ':' + type;
    var input = document.getElementById(id);

    if (!input) {
        return;
    }

    var currentValue = input.value;
    var delay = forceShow ? 0 : OWNER_INPUT_WARNING_DELAY;

    clearOwnerInputWarningTimers(id);

    ownerInputWarningTimers[key] = setTimeout(function () {
        var latestInput = document.getElementById(id);

        if (!latestInput) {
            return;
        }

        var latestValue = latestInput.value;

        // 기다리는 동안 값이 바뀌었으면 이전 경고는 무시
        if (latestValue !== currentValue) {
            return;
        }

        var now = Date.now();

        // 같은 입력창의 같은 오류가 너무 자주 뜨는 것 방지
        if (ownerInputWarningLastShownAt[key] && now - ownerInputWarningLastShownAt[key] < OWNER_INPUT_WARNING_COOLDOWN) {
            return;
        }

        ownerInputWarningLastShownAt[key] = now;
        notifyLimited(message, 'warning', key);
    }, delay);
}

function clearOwnerInputWarningTimers(id) {
    Object.keys(ownerInputWarningTimers).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            clearTimeout(ownerInputWarningTimers[key]);
            delete ownerInputWarningTimers[key];
        }
    });
}

function clearOwnerInputWarning(id) {
    clearOwnerInputWarningTimers(id);

    Object.keys(ownerInputWarningLastShownAt).forEach(function (key) {
        if (key.startsWith(id + ':')) {
            delete ownerInputWarningLastShownAt[key];
        }
    });
}

function notify(message, type) {
    if (!type) {
        type = 'info';
    }

    if (typeof showToast === 'function') {
        showToast(message, type);
        return;
    }

    alert(message);
}

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

function initLoginIdInput() {
    var loginIdInput = document.getElementById('loginId');

    if (!loginIdInput) {
        return;
    }

    bindOwnerEventOnce(loginIdInput, 'input', 'ownerLoginIdResetBound', function () {
        isLoginIdChecked = false;
        resetCheckButton('loginIdCheckBtn');
    });
}

function initEmailInput() {
    var emailInput = document.getElementById('email');

    if (!emailInput) {
        return;
    }

    bindOwnerEventOnce(emailInput, 'input', 'ownerEmailResetBound', function () {
        isEmailChecked = false;
        resetCheckButton('emailCheckBtn');
    });
}

function initBusinessNumberInput() {
    var businessNumberInput = document.getElementById('businessNumber');

    if (!businessNumberInput) {
        return;
    }

    bindOwnerEventOnce(businessNumberInput, 'input', 'ownerBusinessNumberResetBound', function () {
        isBusinessNumberChecked = false;
        resetCheckButton('businessNumberCheckBtn');
    });
}

function resetCheckButton(buttonId) {
    var checkBtn = document.getElementById(buttonId);

    if (!checkBtn) {
        return;
    }

    checkBtn.innerText = '중복';
    checkBtn.classList.remove('success');
}

function completeCheckButton(buttonId) {
    var checkBtn = document.getElementById(buttonId);

    if (!checkBtn) {
        return;
    }

    checkBtn.innerText = '완료';
    checkBtn.classList.add('success');
}

function setCheckButtonLoading(buttonId, loadingText) {
    var checkBtn = document.getElementById(buttonId);

    if (!checkBtn) {
        return;
    }

    checkBtn.disabled = true;
    checkBtn.dataset.originalText = checkBtn.innerText;
    checkBtn.innerText = loadingText || '확인중';
}

function releaseCheckButtonLoading(buttonId, success) {
    var checkBtn = document.getElementById(buttonId);

    if (!checkBtn) {
        return;
    }

    checkBtn.disabled = false;

    if (!success) {
        checkBtn.innerText = checkBtn.dataset.originalText || '중복';
    }

    delete checkBtn.dataset.originalText;
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

function isValidLoginId(loginId) {
    return /^[a-zA-Z0-9]{4,20}$/.test(loginId);
}

function isValidEmail(email) {
    if (email.length > 100) {
        return false;
    }

    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidPassword(password) {
    return password.length >= 8 && password.length <= 20;
}

function isValidOwnerName(ownerName) {
    return /^[가-힣a-zA-Z\s]{1,30}$/.test(ownerName);
}

function isValidOwnerPhone(ownerPhone) {
    var phoneNumbers = normalizePhone(ownerPhone);

    return /^(01[016789]\d{7,8}|0507\d{8})$/.test(phoneNumbers);
}

function isValidBusinessNumber(businessNumber) {
    var numbers = normalizeBusinessNumber(businessNumber);

    return /^\d{10}$/.test(numbers);
}

function isValidBusinessName(businessName) {
    return businessName.length > 0 && businessName.length <= 50;
}

function normalizePhone(value) {
    return value.replace(/\D/g, '');
}

function normalizeBusinessNumber(value) {
    return value.replace(/\D/g, '');
}

function validateOwnerSignupInput(data) {
    if (!isValidLoginId(data.loginId)) {
        return '아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.';
    }

    if (!isValidEmail(data.email)) {
        return '이메일 형식이 올바르지 않습니다.';
    }

    if (!isValidPassword(data.password)) {
        return '비밀번호는 8자 이상, 20자 이내로 입력해주세요.';
    }

    if (!isValidOwnerName(data.ownerName)) {
        return '대표자명은 한글/영문 30자 이내로 입력해주세요.';
    }

    if (!isValidOwnerPhone(data.ownerPhone)) {
        return '대표자 연락처는 010-1234-5678 또는 0507-1234-5678 형식으로 입력해주세요.';
    }

    if (!isValidBusinessNumber(data.businessNumber)) {
        return '사업자등록번호는 숫자 10자리로 입력해주세요.';
    }

    if (!isValidBusinessName(data.businessName)) {
        return '사업자명은 50자 이내로 입력해주세요.';
    }

    return null;
}

async function checkLoginId() {
    if (ownerCheckRunningState.loginId) {
        return;
    }

    var loginIdInput = document.getElementById('loginId');

    if (!loginIdInput || !loginIdInput.value.trim()) {
        notifyLimited('아이디를 입력해주세요.', 'error', 'owner-login-id-empty');
        return;
    }

    var loginId = loginIdInput.value.trim();

    if (!isValidLoginId(loginId)) {
        notifyLimited('아이디는 영문/숫자로 4자 이상, 20자 이내로 입력해주세요.', 'warning', 'owner-login-id-invalid');
        return;
    }

    ownerCheckRunningState.loginId = true;
    setCheckButtonLoading('loginIdCheckBtn', '확인중');

    try {
        var response = await fetch(
            LOGIN_ID_CHECK_URL + '?loginId=' + encodeURIComponent(loginId)
        );

        var result = await safeJson(response);

        // 요청 중 사용자가 값을 바꿨으면 이전 응답은 무시
        if (loginIdInput.value.trim() !== loginId) {
            isLoginIdChecked = false;
            resetCheckButton('loginIdCheckBtn');
            return;
        }

        if (!response.ok || !result || result.success === false) {
            notifyLimited(getErrorMessage(result, '아이디 중복 확인 중 오류가 발생했습니다.'), 'error', 'owner-login-id-check-error');
            isLoginIdChecked = false;
            resetCheckButton('loginIdCheckBtn');
            return;
        }

        if (!result.data || typeof result.data.available !== 'boolean') {
            notifyLimited('아이디 중복 확인 응답 데이터가 올바르지 않습니다.', 'error', 'owner-login-id-check-invalid-response');
            isLoginIdChecked = false;
            resetCheckButton('loginIdCheckBtn');
            return;
        }

        if (result.data.available) {
            isLoginIdChecked = true;
            completeCheckButton('loginIdCheckBtn');
            notifyLimited('사용 가능한 아이디입니다.', 'success', 'owner-login-id-available');
            return;
        }

        isLoginIdChecked = false;
        resetCheckButton('loginIdCheckBtn');
        notifyLimited('이미 사용 중인 아이디입니다.', 'error', 'owner-login-id-duplicated');

    } catch (e) {
        isLoginIdChecked = false;
        resetCheckButton('loginIdCheckBtn');
        notifyLimited('아이디 중복 확인 중 오류가 발생했습니다.', 'error', 'owner-login-id-fetch-error');

    } finally {
        ownerCheckRunningState.loginId = false;

        var success = isLoginIdChecked && loginIdInput && loginIdInput.value.trim() === loginId;
        releaseCheckButtonLoading('loginIdCheckBtn', success);
    }
}

async function checkEmail() {
    if (ownerCheckRunningState.email) {
        return;
    }

    var emailInput = document.getElementById('email');

    if (!emailInput || !emailInput.value.trim()) {
        notifyLimited('이메일을 입력해주세요.', 'error', 'owner-email-empty');
        return;
    }

    var email = emailInput.value.trim();

    if (!isValidEmail(email)) {
        notifyLimited('이메일 형식이 올바르지 않습니다.', 'warning', 'owner-email-invalid');
        return;
    }

    ownerCheckRunningState.email = true;
    setCheckButtonLoading('emailCheckBtn', '확인중');

    try {
        var response = await fetch(
            EMAIL_CHECK_URL + '?email=' + encodeURIComponent(email)
        );

        var result = await safeJson(response);

        // 요청 중 사용자가 값을 바꿨으면 이전 응답은 무시
        if (emailInput.value.trim() !== email) {
            isEmailChecked = false;
            resetCheckButton('emailCheckBtn');
            return;
        }

        if (!response.ok || !result || result.success === false) {
            notifyLimited(getErrorMessage(result, '이메일 중복 확인 중 오류가 발생했습니다.'), 'error', 'owner-email-check-error');
            isEmailChecked = false;
            resetCheckButton('emailCheckBtn');
            return;
        }

        if (!result.data || typeof result.data.available !== 'boolean') {
            notifyLimited('이메일 중복 확인 응답 데이터가 올바르지 않습니다.', 'error', 'owner-email-check-invalid-response');
            isEmailChecked = false;
            resetCheckButton('emailCheckBtn');
            return;
        }

        if (result.data.available) {
            isEmailChecked = true;
            completeCheckButton('emailCheckBtn');
            notifyLimited('사용 가능한 이메일입니다.', 'success', 'owner-email-available');
            return;
        }

        isEmailChecked = false;
        resetCheckButton('emailCheckBtn');
        notifyLimited('이미 사용 중인 이메일입니다.', 'error', 'owner-email-duplicated');

    } catch (e) {
        isEmailChecked = false;
        resetCheckButton('emailCheckBtn');
        notifyLimited('이메일 중복 확인 중 오류가 발생했습니다.', 'error', 'owner-email-fetch-error');

    } finally {
        ownerCheckRunningState.email = false;

        var success = isEmailChecked && emailInput && emailInput.value.trim() === email;
        releaseCheckButtonLoading('emailCheckBtn', success);
    }
}

async function checkBusinessNumber() {
    if (ownerCheckRunningState.businessNumber) {
        return;
    }

    var businessNumberInput = document.getElementById('businessNumber');

    if (!businessNumberInput || !businessNumberInput.value.trim()) {
        notifyLimited('사업자등록번호를 입력해주세요.', 'error', 'owner-business-number-empty');
        return;
    }

    var businessNumber = businessNumberInput.value.trim();

    if (!isValidBusinessNumber(businessNumber)) {
        notifyLimited('사업자등록번호는 숫자 10자리로 입력해주세요.', 'warning', 'owner-business-number-invalid');
        return;
    }

    var normalizedBusinessNumber = normalizeBusinessNumber(businessNumber);

    ownerCheckRunningState.businessNumber = true;
    setCheckButtonLoading('businessNumberCheckBtn', '확인중');

    try {
        var response = await fetch(
            BUSINESS_NUMBER_CHECK_URL + '?businessNumber=' + encodeURIComponent(normalizedBusinessNumber)
        );

        var result = await safeJson(response);

        // 요청 중 사용자가 값을 바꿨으면 이전 응답은 무시
        if (normalizeBusinessNumber(businessNumberInput.value.trim()) !== normalizedBusinessNumber) {
            isBusinessNumberChecked = false;
            resetCheckButton('businessNumberCheckBtn');
            return;
        }

        if (!response.ok || !result || result.success === false) {
            notifyLimited(getErrorMessage(result, '사업자등록번호 중복 확인 중 오류가 발생했습니다.'), 'error', 'owner-business-number-check-error');
            isBusinessNumberChecked = false;
            resetCheckButton('businessNumberCheckBtn');
            return;
        }

        if (!result.data || typeof result.data.available !== 'boolean') {
            notifyLimited('사업자등록번호 중복 확인 응답 데이터가 올바르지 않습니다.', 'error', 'owner-business-number-check-invalid-response');
            isBusinessNumberChecked = false;
            resetCheckButton('businessNumberCheckBtn');
            return;
        }

        if (result.data.available) {
            isBusinessNumberChecked = true;
            completeCheckButton('businessNumberCheckBtn');
            notifyLimited('사용 가능한 사업자등록번호입니다.', 'success', 'owner-business-number-available');
            return;
        }

        isBusinessNumberChecked = false;
        resetCheckButton('businessNumberCheckBtn');
        notifyLimited('이미 사용 중인 사업자등록번호입니다.', 'error', 'owner-business-number-duplicated');

    } catch (e) {
        isBusinessNumberChecked = false;
        resetCheckButton('businessNumberCheckBtn');
        notifyLimited('사업자등록번호 중복 확인 중 오류가 발생했습니다.', 'error', 'owner-business-number-fetch-error');

    } finally {
        ownerCheckRunningState.businessNumber = false;

        var success = isBusinessNumberChecked &&
            businessNumberInput &&
            normalizeBusinessNumber(businessNumberInput.value.trim()) === normalizedBusinessNumber;

        releaseCheckButtonLoading('businessNumberCheckBtn', success);
    }
}

async function submitOwnerSignup(event) {
    event.preventDefault();

    if (ownerSignupSubmitting) {
        return;
    }

    var loginId = document.getElementById('loginId').value.trim();
    var email = document.getElementById('email').value.trim();
    var password = document.getElementById('password').value;
    var passwordConfirm = document.getElementById('passwordConfirm').value;
    var ownerName = document.getElementById('ownerName').value.trim();
    var ownerPhone = document.getElementById('ownerPhone').value.trim();
    var businessNumber = document.getElementById('businessNumber').value.trim();
    var businessName = document.getElementById('businessName').value.trim();

    if (!loginId || !email || !password || !passwordConfirm || !ownerName || !ownerPhone || !businessNumber || !businessName) {
        notifyLimited('필수 입력값을 모두 입력해주세요.', 'error', 'owner-signup-required');
        return;
    }

    var validationMessage = validateOwnerSignupInput({
        loginId: loginId,
        email: email,
        password: password,
        ownerName: ownerName,
        ownerPhone: ownerPhone,
        businessNumber: businessNumber,
        businessName: businessName
    });

    if (validationMessage) {
        notifyLimited(validationMessage, 'warning', 'owner-signup-validation');
        return;
    }

    if (password !== passwordConfirm) {
        notifyLimited('비밀번호가 일치하지 않습니다.', 'error', 'owner-signup-password-mismatch');
        return;
    }

    if (!isLoginIdChecked) {
        notifyLimited('아이디 중복 확인을 먼저 완료해주세요.', 'error', 'owner-signup-login-id-not-checked');
        return;
    }

    if (!isEmailChecked) {
        notifyLimited('이메일 중복 확인을 먼저 완료해주세요.', 'error', 'owner-signup-email-not-checked');
        return;
    }

    if (!isBusinessNumberChecked) {
        notifyLimited('사업자등록번호 중복 확인을 먼저 완료해주세요.', 'error', 'owner-signup-business-number-not-checked');
        return;
    }

    var requestData = {
        loginId: loginId,
        password: password,
        email: email,
        businessNumber: normalizeBusinessNumber(businessNumber),
        businessName: businessName,
        ownerName: ownerName,
        ownerPhone: normalizePhone(ownerPhone)
    };

    var submitBtn = document.querySelector('#ownerSignupForm button[type="submit"], #ownerSignupForm input[type="submit"]');
    ownerSignupSubmitting = true;

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.dataset.originalText = submitBtn.innerText || submitBtn.value || '';
        if (submitBtn.tagName === 'INPUT') {
            submitBtn.value = '처리중';
        } else {
            submitBtn.innerText = '처리중';
        }
    }

    try {
        var response = await fetch(OWNER_SIGNUP_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        var result = await safeJson(response);

        if (!response.ok || !result || result.success === false) {
            notifyLimited(getErrorMessage(result, '점주 회원가입에 실패했습니다.'), 'error', 'owner-signup-submit-error');
            ownerSignupSubmitting = false;

            if (submitBtn) {
                submitBtn.disabled = false;

                if (submitBtn.tagName === 'INPUT') {
                    submitBtn.value = submitBtn.dataset.originalText || '가입 신청';
                } else {
                    submitBtn.innerText = submitBtn.dataset.originalText || '가입 신청';
                }

                delete submitBtn.dataset.originalText;
            }

            return;
        }

        notifyLimited('점주 회원가입 신청이 완료되었습니다. 로그인 후 승인 상태를 확인해주세요.', 'success', 'owner-signup-success');

        setTimeout(function () {
            window.location.href = '/api/view/login';
        }, 900);

    } catch (e) {
        notifyLimited('점주 회원가입 중 오류가 발생했습니다.', 'error', 'owner-signup-fetch-error');
        ownerSignupSubmitting = false;

        if (submitBtn) {
            submitBtn.disabled = false;

            if (submitBtn.tagName === 'INPUT') {
                submitBtn.value = submitBtn.dataset.originalText || '가입 신청';
            } else {
                submitBtn.innerText = submitBtn.dataset.originalText || '가입 신청';
            }

            delete submitBtn.dataset.originalText;
        }
    }
}