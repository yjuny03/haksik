// owner-store.js

let currentStoreId = null;
let isEditMode = false;

// 비동기 알림 처리 및 도배 방지를 위한 상태 저장소
const inputWarningTimers = {};
const inputWarningLastShownAt = {};
const INPUT_WARNING_DELAY = 600;
const INPUT_WARNING_COOLDOWN = 1800;

document.addEventListener('DOMContentLoaded', function () {
    fetchStoreDetails();

    setupStoreInputRules();

    const form = document.getElementById('storeUpdateForm');
    if (form) {
        form.addEventListener('submit', submitStoreForm);
    }
});

//  영업시간 제한은 삭제 이름, 주소, 카테고리, 전화번호만 제한
function setupStoreInputRules() {
    addInputWarning('storeName', {
        maxLength: 30,
        lengthMessage: '가게 이름은 30자 이내로 입력해주세요.',
        pattern: /^[가-힣a-zA-Z0-9\s]*$/,
        patternMessage: '가게 이름에 특수문자는 입력할 수 없습니다.',
        replaceRegex: /[^가-힣a-zA-Z0-9\s]/g
    });

    addInputWarning('storeCategory', {
        pattern: /^[가-힣a-zA-Z0-9\s]*$/,
        patternMessage: '카테고리에 특수문자는 입력할 수 없습니다.',
        replaceRegex: /[^가-힣a-zA-Z0-9\s]/g
    });

    addInputWarning('storeAddress', {
        maxLength: 50,
        lengthMessage: '가게 주소는 50자 이내로 입력해주세요.',
        pattern: /^[가-힣a-zA-Z0-9\s]*$/,
        patternMessage: '가게 주소에 특수문자는 입력할 수 없습니다.',
        replaceRegex: /[^가-힣a-zA-Z0-9\s]/g
    });

    addInputWarning('storePhone', {
        maxLength: 20,
        lengthMessage: '전화번호는 20자 이내로 입력해주세요.',
        pattern: /^[0-9]*$/,
        patternMessage: '전화번호는 숫자만 입력해주세요.',
        replaceRegex: /[^0-9]/g
    });
}

function addInputWarning(id, options) {
    const input = document.getElementById(id);
    if (!input) return;

    if (input.hasAttribute('maxlength')) {
        input.removeAttribute('maxlength');
    }

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
}

function checkInputWarning(input, id, options) {
    let value = input.value;

    //  특수문자나 숫자 외의 값이 들어오면 즉시 싹둑 자르고 알림!
    if (options.pattern && !options.pattern.test(value)) {
        input.value = value.replace(options.replaceRegex, '');
        showInputWarningOnce(id, 'pattern', options.patternMessage, true);
        value = input.value;
    }

    //  글자 수 제한 초과 시 싹둑 자르고 알림!
    if (options.maxLength && value.length > options.maxLength) {
        input.value = value.slice(0, options.maxLength);
        showInputWarningOnce(id, 'length', options.lengthMessage, true);
    }
}

// 메인 스레드를 멈추지 않는 비동기 알림 래퍼 함수
function showInputWarningOnce(id, type, message, forceShow = false) {
    const key = id + ':' + type;
    const delay = forceShow ? 0 : INPUT_WARNING_DELAY;

    if (inputWarningTimers[key]) {
        clearTimeout(inputWarningTimers[key]);
        delete inputWarningTimers[key];
    }

    inputWarningTimers[key] = setTimeout(function () {
        const now = Date.now();
        // 1.8초 쿨다운 적용으로 알림창 스팸 차단
        if (inputWarningLastShownAt[key] && now - inputWarningLastShownAt[key] < INPUT_WARNING_COOLDOWN) {
            return;
        }

        inputWarningLastShownAt[key] = now;

        showToast(message, 'warning');
    }, delay);
}

async function extractErrorMessage(response) {
    try {
        const errData = await response.json();
        return errData.message || '요청 처리에 실패했습니다.';
    } catch (e) {
        return '서버와 통신 중 알 수 없는 오류가 발생했습니다.';
    }
}

// 기존 가게 정보 불러오기
async function fetchStoreDetails() {
    try {
        const response = await fetch('/api/stores/my');
        if (!response.ok) throw new Error('가게 없음');

        const store = await response.json();
        currentStoreId = store.storeId;
        isEditMode = true;

        document.getElementById('storeName').value = store.name || '';
        document.getElementById('storeCategory').value = store.category || '';
        document.getElementById('storeAddress').value = store.address || '';
        document.getElementById('storePhone').value = store.phone || '';
        document.getElementById('storeHours').value = store.operatingHours || '';

        document.getElementById('submitBtn').innerText = '수정 완료';
        document.getElementById('submitBtn').disabled = false;

        document.getElementById('newMenuSection').style.display = 'none';

    } catch (error) {
        console.log("등록된 가게가 없습니다. 신규 등록 모드로 전환합니다.");
        isEditMode = false;

        document.getElementById('submitBtn').innerText = '내 가게 등록하기';
        document.getElementById('submitBtn').disabled = false;
        document.getElementById('newMenuSection').style.display = 'block';
    }
}

//  폼 제출
async function submitStoreForm(event) {
    event.preventDefault();

    const storeNameVal = document.getElementById('storeName').value.trim();
    const addressInput = document.getElementById('storeAddress').value.trim();
    const storePhoneVal = document.getElementById('storePhone').value.trim();

    if (storeNameVal.length > 30) {
        showToast('가게 이름은 30자 이내로 입력해주세요.', 'error');
        return;
    }
    if (addressInput.length > 50) {
        showToast('가게 주소는 50자 이내로 입력해주세요.', 'error');
        return;
    }
    if (storePhoneVal.length > 20) {
        showToast('전화번호는 20자 이내로 입력해주세요.', 'error');
        return;
    }

    const submitBtn = document.getElementById('submitBtn');
    submitBtn.disabled = true;
    submitBtn.innerText = '위치 좌표 추출 중...';

    let lat = 37.5511694;
    let lng = 126.9882266;

    try {
        if (typeof naver === 'undefined' || !naver.maps || !naver.maps.Service) {
            throw new Error("네이버 API 인증 실패");
        }

        await new Promise((resolve, reject) => {
            naver.maps.Service.geocode({ query: addressInput }, function(status, response) {
                if (status === naver.maps.Service.Status.ERROR || response.v2.meta.totalCount === 0) {
                    reject(new Error("주소 검색 실패"));
                } else {
                    lat = parseFloat(response.v2.addresses[0].y);
                    lng = parseFloat(response.v2.addresses[0].x);
                    resolve();
                }
            });
        });
    } catch (e) {
        console.warn(e.message + " - 네이버 API가 응답하지 않아 임시 좌표로 강제 돌파합니다.");
        showToast('지도 API 연동 문제로 임시 위치가 등록됩니다.', 'info');
    }

    submitBtn.innerText = '서버 데이터 전송 중...';

    try {
        if (isEditMode) {
            const requestData = {
                name: storeNameVal,
                category: document.getElementById('storeCategory').value.trim(),
                address: addressInput,
                latitude: lat,
                longitude: lng,
                phone: storePhoneVal,
                operatingHours: document.getElementById('storeHours').value.trim()
            };

            const res = await fetch(`/api/stores/${currentStoreId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!res.ok) throw new Error(await extractErrorMessage(res));
            showToast('가게 정보가 성공적으로 수정되었습니다.', 'success');

        } else {
            const formData = new FormData();
            formData.append('name', storeNameVal);
            formData.append('category', document.getElementById('storeCategory').value.trim());
            formData.append('address', addressInput);
            formData.append('latitude', lat);
            formData.append('longitude', lng);
            formData.append('phone', storePhoneVal);
            formData.append('operatingHours', document.getElementById('storeHours').value.trim());

            const storeImageFile = document.getElementById('storeImage').files[0];
            if(storeImageFile) formData.append('storeImage', storeImageFile);

            const menuName = document.getElementById('menuName').value.trim();
            const menuPrice = document.getElementById('menuPrice').value.trim();
            const menuImageFile = document.getElementById('menuImage').files[0];

            if (menuName) formData.append('menuNames', menuName);
            if (menuPrice) formData.append('menuPrices', menuPrice);
            if (menuImageFile) formData.append('menuImages', menuImageFile);

            const res = await fetch(`/api/stores`, {
                method: 'POST',
                body: formData
            });

            if (!res.ok) throw new Error(await extractErrorMessage(res));
            showToast('새로운 가게가 성공적으로 등록되었습니다.', 'success');
        }

        setTimeout(() => location.href = '/api/view/owner/main', 1000);

    } catch (error) {
        console.error(error);
        showToast(error.message, 'error');
        submitBtn.disabled = false;
        submitBtn.innerText = isEditMode ? '수정 완료' : '내 가게 등록하기';
    }
}