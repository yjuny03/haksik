// 비동기 알림 처리 및 도배 방지를 위한 상태 저장소
const inputWarningTimers = {};
const inputWarningLastShownAt = {};
const INPUT_WARNING_DELAY = 600;      // 기본 0.6초 대기 (forceShow=true 시 0초)
const INPUT_WARNING_COOLDOWN = 1800;  // 1.8초 도배 방지 쿨다운

let currentStoreId = null;

document.addEventListener('DOMContentLoaded', function () {
    fetchMyStoreId();

    // 🛡️ [수정 부분]: 오빠가 말한 깔끔한 세팅 패턴 적용!
    setupMenuInputRules();

    const form = document.getElementById('menuAddForm');
    if (form) {
        form.addEventListener('submit', submitNewMenu);
    }
});

// 🛡️ [수정 부분]: 신규 메뉴 입력창 룰 세팅
function setupMenuInputRules() {
    addInputWarning('menuName', {
        maxLength: 30,
        message: '메뉴 이름은 30자 이내로 입력해주세요.'
    });

    addInputWarning('menuPrice', {
        maxLength: 10,
        message: '메뉴 가격은 10자 이내로 입력해주세요.'
    });
}

// 🛡️ [수정 부분]: owner-signup.js 스타일의 한글 꼬임 방지 범용 함수 도입!
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
}

// 🛡️ [수정 부분]: 글자 수를 초과하면 자르고 즉시 알림을 띄우는 핵심 로직!
function checkInputWarning(input, id, options) {
    const value = input.value;

    if (options.maxLength && value.length > options.maxLength) {
        // 입력값을 maxLength까지만 물리적으로 싹둑 자름
        input.value = value.slice(0, options.maxLength);
        // 딜레이 없이 0초 만에 즉시(true) 알림창 호출!
        showInputWarningOnce(id, 'length', options.message, true);
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
        // 1.8초 쿨다운 적용으로 알림창 스팸 완벽 차단
        if (inputWarningLastShownAt[key] && now - inputWarningLastShownAt[key] < INPUT_WARNING_COOLDOWN) {
            return;
        }

        inputWarningLastShownAt[key] = now;

        // 오빠의 기존 코드 스타일에 맞춰 안전하게 showToast 호출
        if (typeof showToast === 'function') {
            showToast(message, 'warning');
        } else {
            alert(message);
        }
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

// 1. 내 가게 ID 확보 & 하단 메뉴 리스트 격발
async function fetchMyStoreId() {
    try {
        const response = await fetch('/api/stores/my');
        if (!response.ok) throw new Error('가게 정보를 불러올 수 없습니다.');

        const store = await response.json();
        currentStoreId = store.storeId;

        fetchStoreMenus(currentStoreId);
    } catch (error) {
        console.error(error);
        alert('먼저 가게를 등록해야 메뉴를 추가할 수 있습니다.');
        location.href = '/api/view/owner/main';
    }
}

// 2. 신메뉴 폼 전송
async function submitNewMenu(event) {
    event.preventDefault();
    if (!currentStoreId) return;

    const nameValue = document.getElementById('menuName').value.trim();
    const priceValue = document.getElementById('menuPrice').value.trim();

    // 제출 버튼 누르는 순간 한 번 더 확인!
    if (nameValue.length > 30) {
        if (typeof showToast === 'function') showToast('메뉴 이름은 30자 이내로 입력해주세요.', 'error');
        return;
    }

    if (priceValue.length > 10) {
        if (typeof showToast === 'function') showToast('메뉴 가격은 10자 이내로 입력해주세요.', 'error');
        return;
    }

    const submitBtn = document.getElementById('submitBtn');
    submitBtn.disabled = true;
    submitBtn.innerText = '메뉴 등록 중...';

    const formData = new FormData();
    formData.append('name', nameValue);
    formData.append('price', priceValue);

    const imageFile = document.getElementById('menuImage').files[0];
    if (imageFile) formData.append('image', imageFile);

    try {
        const response = await fetch(`/api/stores/${currentStoreId}/menus`, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) throw new Error(await extractErrorMessage(response));

        if (typeof showToast === 'function') showToast('새로운 메뉴가 성공적으로 등록되었습니다.', 'success');

        document.getElementById('menuAddForm').reset();
        submitBtn.disabled = false;
        submitBtn.innerText = '메뉴 등록하기';
        fetchStoreMenus(currentStoreId);

    } catch (error) {
        console.error(error);
        if (typeof showToast === 'function') showToast(error.message, 'error');
        submitBtn.disabled = false;
        submitBtn.innerText = '메뉴 등록하기';
    }
}

// 3. 기존 메뉴 리스트 긁어오기
async function fetchStoreMenus(storeId) {
    try {
        const response = await fetch(`/api/stores/${storeId}/menus`);
        if (!response.ok) throw new Error('메뉴 목록 조회 실패');

        const menus = await response.json();
        renderMenus(menus);
    } catch (e) {
        document.getElementById('menuListContainer').innerHTML = `<div style="color:var(--red); text-align:center; padding: 20px; font-weight:700;">${e.message}</div>`;
    }
}

// 4. 메뉴 리스트 렌더링
function renderMenus(menus) {
    const container = document.getElementById('menuListContainer');
    container.innerHTML = '';

    if(menus.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding: 20px; color: var(--muted); font-weight: 700;">등록된 메뉴가 없습니다.</div>`;
        return;
    }

    menus.forEach(menu => {
        const li = document.createElement('li');
        li.style.cssText = "background: var(--paper); border: 2px solid var(--line); border-radius: 16px; padding: 12px; display: flex; gap: 12px; align-items: center; box-shadow: var(--shadow); flex-wrap: wrap;";

        // 사진 렌더링
        let imgHTML = menu.imageId
            ? `<img src="/api/images/${menu.imageId}" style="width: 80px; height: 80px; object-fit: cover; border-radius: 12px; border: 1px solid var(--line);" onerror="this.style.display='none'">`
            : `<div style="width: 80px; height: 80px; background: var(--card); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 900; color: var(--muted); border: 1px dashed var(--line);">NO IMG</div>`;

        li.innerHTML = `
            ${imgHTML}
            <div style="flex: 1; display: flex; flex-direction: column; gap: 6px; min-width: 200px;">
                <input type="text" id="edit-name-${menu.menuId}" value="${menu.name}" class="board-write-input" style="padding: 8px; font-size: 14px; margin-bottom: 0;">
                <input type="number" id="edit-price-${menu.menuId}" value="${menu.price}" class="board-write-input" style="padding: 8px; font-size: 14px; margin-bottom: 0;">
                
                <input type="file" id="edit-image-${menu.menuId}" accept="image/*" class="board-write-input" style="padding: 8px; font-size: 12px; margin-bottom: 0;">

                <div style="display: flex; gap: 8px;">
                    <select id="edit-status-${menu.menuId}" class="board-write-input" style="padding: 8px; font-size: 13px; margin-bottom: 0; flex: 1;">
                        <option value="ON_SALE" ${menu.salesStatus === 'ON_SALE' ? 'selected' : ''}>판매중</option>
                        <option value="SOLD_OUT" ${menu.salesStatus === 'SOLD_OUT' ? 'selected' : ''}>품절 (숨김)</option>
                    </select>
                    <button onclick="updateExistingMenu(${menu.menuId})" style="background: var(--forest); color: white; border: none; border-radius: 8px; font-weight: 900; padding: 0 12px; cursor: pointer;">수정</button>
                    <button onclick="deleteExistingMenu(${menu.menuId})" style="background: var(--red); color: white; border: none; border-radius: 8px; font-weight: 900; padding: 0 12px; cursor: pointer;">삭제</button>
                </div>
            </div>
        `;
        container.appendChild(li);

        // 🛡️ [수정 부분]: 리스트가 화면에 그려진 직후에, 동적으로 생성된 input들에도 규칙을 걸어줌!
        addInputWarning(`edit-name-${menu.menuId}`, {
            maxLength: 30,
            message: '메뉴 이름은 30자 이내로 입력해주세요.'
        });

        addInputWarning(`edit-price-${menu.menuId}`, {
            maxLength: 10,
            message: '메뉴 가격은 10자 이내로 입력해주세요.'
        });
    });
}

// 5. 수정한 데이터 백엔드로 쏘기
async function updateExistingMenu(menuId) {
    const name = document.getElementById(`edit-name-${menuId}`).value.trim();
    const price = document.getElementById(`edit-price-${menuId}`).value.trim();
    const status = document.getElementById(`edit-status-${menuId}`).value;
    const imageFile = document.getElementById(`edit-image-${menuId}`).files[0];

    // 수정 요청 직전에 글자 수 한 번 더 확인!
    if(name.length > 30) {
        if (typeof showToast === 'function') showToast('메뉴 이름은 30자 이내로 입력해주세요.', 'error');
        return;
    }

    if(price.length > 10) {
        if (typeof showToast === 'function') showToast('메뉴 가격은 10자 이내로 입력해주세요.', 'error');
        return;
    }

    if(!name || !price) {
        if (typeof showToast === 'function') showToast('메뉴 이름과 가격을 정확히 입력해주세요.', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('name', name);
    formData.append('price', price);
    formData.append('salesStatus', status);
    if (imageFile) {
        formData.append('image', imageFile);
    }

    try {
        const response = await fetch(`/api/stores/${currentStoreId}/menus/${menuId}`, {
            method: 'PATCH',
            body: formData
        });

        if (!response.ok) throw new Error(await extractErrorMessage(response));

        if (typeof showToast === 'function') showToast('메뉴 정보가 변경되었습니다.', 'success');

        fetchStoreMenus(currentStoreId);
    } catch(e) {
        console.error(e);
        if (typeof showToast === 'function') showToast(e.message, 'error');
    }
}

// 6. 메뉴 완전 삭제 액션 (DELETE)
async function deleteExistingMenu(menuId) {
    if (!confirm('정말 이 메뉴를 삭제하시겠습니까?')) return;

    try {
        const response = await fetch(`/api/stores/${currentStoreId}/menus/${menuId}`, {
            method: 'DELETE'
        });

        if (!response.ok) throw new Error(await extractErrorMessage(response));

        if (typeof showToast === 'function') showToast('메뉴가 삭제되었습니다.', 'success');

        fetchStoreMenus(currentStoreId);
    } catch(e) {
        console.error(e);
        if (typeof showToast === 'function') showToast(e.message, 'error');
    }
}