// owner-reviews.js

// 🛡️ [수정 부분]: 비동기 알림 처리 및 도배 방지를 위한 상태 저장소
const inputWarningTimers = {};
const inputWarningLastShownAt = {};
const INPUT_WARNING_DELAY = 600;      // 기본 0.6초 대기 (forceShow=true 시 0초)
const INPUT_WARNING_COOLDOWN = 1800;  // 1.8초 도배 방지 쿨다운

document.addEventListener('DOMContentLoaded', function () {
    fetchOwnerReviews();
});

// 🛡️ [수정 부분]: 한글 꼬임 방지가 적용된 범용 입력 제한 함수
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

// 🛡️ [수정 부분]: 지정된 글자 수를 초과하면 자르고 즉시 알림을 띄우는 로직
function checkInputWarning(input, id, options) {
    const value = input.value;

    if (options.maxLength && value.length > options.maxLength) {
        // 200자 넘으면 즉시 자름!
        input.value = value.slice(0, options.maxLength);
        // 0초 만에 즉시 알림창 띄우기
        showInputWarningOnce(id, 'length', options.message, true);
    }
}

// 🛡️ [수정 부분]: 메인 스레드를 멈추지 않는 비동기 알림 래퍼 함수
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

async function fetchOwnerReviews() {
    try {
        const response = await fetch('/api/reviews/owner');
        if (!response.ok) throw new Error(await extractErrorMessage(response));

        const reviews = await response.json();
        renderReviews(reviews);
    } catch (error) {
        console.error(error);
        const container = document.getElementById('reviewContainer');
        container.innerHTML = `<div style="text-align:center; padding: 30px; color: var(--red); font-weight: 700;">${error.message}</div>`;
    }
}

function renderReviews(reviews) {
    const container = document.getElementById('reviewContainer');
    container.innerHTML = '';

    if (!reviews || reviews.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding: 30px; color: var(--muted); font-weight: 700;">아직 작성된 리뷰가 없습니다.</div>`;
        return;
    }

    reviews.forEach(review => {
        const li = document.createElement('li');
        li.className = 'board-post-card';
        // 카드를 누르면 상세 영역이 열리도록 클릭 이벤트 장착
        li.style.cursor = 'pointer';
        li.onclick = () => toggleReviewDetail(review.reviewId);

        const dateStr = review.createdAt ? review.createdAt.split('T')[0] : '';
        const starRating = '⭐'.repeat(review.rating);

        let innerHTML = `
            <div class="board-post-head" style="justify-content: flex-end;">
                <button class="board-options-btn" style="color: var(--red); font-size: 13px; width: auto; padding: 0 8px;" 
                        onclick="event.stopPropagation(); reportReview(${review.reviewId})">🚨 신고</button>
            </div>
            <div class="review-rating">${starRating}</div>
            <div class="board-post-title" style="font-size: 14px; font-weight: 700; white-space: pre-wrap;">${review.content}</div>
            <div class="board-post-meta">
                <span>${review.writerLoginId || '익명'}님</span>
                <span>${dateStr}</span>
            </div>

            <div id="detail-${review.reviewId}" style="display: none; margin-top: 15px; border-top: 1px dashed var(--line); padding-top: 15px;" onclick="event.stopPropagation();">
        `;

        // 1. 원본 사진 크게 렌더링
        if (review.imageIds && review.imageIds.length > 0) {
            innerHTML += `<div style="display: flex; flex-direction: column; gap: 10px; margin-bottom: 15px;">`;
            review.imageIds.forEach(id => {
                innerHTML += `<img src="/api/images/${id}" style="width: 100%; border-radius: 12px; border: 1px solid var(--line);" onerror="this.style.display='none'">`;
            });
            innerHTML += `</div>`;
        }

        // 2. 답글 UI 분기 처리 (수정 기능 완벽 장착)
        if (review.ownerReply) {
            // 이미 답글이 있을 때: [일반 보기 모드] 와 [수정 모드]
            innerHTML += `
                <div id="reply-view-${review.reviewId}" style="background-color: var(--ivory2); padding: 12px; border-radius: 12px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                        <span style="font-weight: 900; color: var(--forest);">↳ 사장님 답글</span>
                        <button onclick="enableEditReply(${review.reviewId})" style="background: none; border: none; color: var(--muted); font-size: 12px; font-weight: 900; cursor: pointer;">✏️ 수정</button>
                    </div>
                    <div style="font-size: 13px; font-weight: 700; color: #333; white-space: pre-wrap;">${review.ownerReply}</div>
                </div>

                <div id="reply-edit-${review.reviewId}" style="display: none;">
                    <textarea id="reply-input-${review.reviewId}" class="board-write-textarea" style="min-height: 80px; font-size: 13px; margin-bottom: 10px; border-radius: 12px;">${review.ownerReply}</textarea>
                    <div style="display: flex; gap: 8px;">
                        <button style="flex: 1; background-color: var(--card); color: var(--muted); border: 1px solid var(--line); padding: 10px; border-radius: 10px; font-weight: 900; cursor: pointer;" onclick="cancelEditReply(${review.reviewId})">취소</button>
                        <button style="flex: 1; background-color: var(--green); color: white; border: none; padding: 10px; border-radius: 10px; font-weight: 900; cursor: pointer;" onclick="submitReply(${review.reviewId})">수정 완료</button>
                    </div>
                </div>
            `;
        } else {
            // 답글이 없을 때: 최초 등록 모드
            innerHTML += `
                <textarea id="reply-input-${review.reviewId}" class="board-write-textarea" style="min-height: 80px; font-size: 13px; margin-bottom: 10px; border-radius: 12px;" placeholder="고객님께 따뜻한 답글을 남겨보세요."></textarea>
                <button style="width: 100%; background-color: var(--green); color: white; border: none; padding: 10px; border-radius: 10px; font-weight: 900; cursor: pointer;" 
                        onclick="submitReply(${review.reviewId})">답글 등록</button>
            `;
        }

        innerHTML += `</div>`; // 상세 영역 끝
        li.innerHTML = innerHTML;
        container.appendChild(li);

        // 🛡️ [수정 부분]: li 요소가 화면에 추가된 직후, 생성된 textarea에 200자 제한 룰을 걸어줍니다!
        addInputWarning(`reply-input-${review.reviewId}`, {
            maxLength: 200,
            message: '답글은 200자 이내로 입력해주세요.'
        });
    });
}

// 아코디언 토글 기능
function toggleReviewDetail(reviewId) {
    const detailDiv = document.getElementById(`detail-${reviewId}`);
    if (detailDiv.style.display === 'none') {
        detailDiv.style.display = 'block';
    } else {
        detailDiv.style.display = 'none';
        if (document.getElementById(`reply-view-${reviewId}`)) cancelEditReply(reviewId);
    }
}

// 수정 모드 ON/OFF 함수
function enableEditReply(reviewId) {
    document.getElementById(`reply-view-${reviewId}`).style.display = 'none';
    document.getElementById(`reply-edit-${reviewId}`).style.display = 'block';
}

function cancelEditReply(reviewId) {
    document.getElementById(`reply-view-${reviewId}`).style.display = 'block';
    document.getElementById(`reply-edit-${reviewId}`).style.display = 'none';
}

// 사장님 답글 전송 (PATCH)
async function submitReply(reviewId) {
    const replyText = document.getElementById(`reply-input-${reviewId}`).value.trim();

    // 🛡️ [수정 부분]: 제출 버튼을 눌렀을 때도 200자가 넘는지 마지막으로 든든하게 체크!
    if (replyText.length > 200) {
        if (typeof showToast === 'function') showToast('답글은 200자 이내로 입력해주세요.', 'error');
        return;
    }

    if (!replyText) {
        if (typeof showToast === 'function') showToast('답글 내용을 입력해주세요.', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/reviews/owner/${reviewId}/reply`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reply: replyText })
        });

        if (!response.ok) throw new Error(await extractErrorMessage(response));

        if (typeof showToast === 'function') showToast('답글이 성공적으로 등록되었습니다.', 'success');
        fetchOwnerReviews(); // 성공 시 화면 리렌더링
    } catch (error) {
        console.error(error);
        if (typeof showToast === 'function') showToast(error.message, 'error');
    }
}

// 리뷰 신고
async function reportReview(reviewId) {
    const reason = prompt("신고 사유를 입력해주세요. 200자 이내. (예: 욕설, 허위사실)");
    if (!reason || reason.trim() === '') return;

    try {
        const response = await fetch('/api/reports', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                targetType: 'REVIEW',
                targetId: reviewId,
                reason: reason.trim()
            })
        });

        if (!response.ok) throw new Error(await extractErrorMessage(response));
        if (typeof showToast === 'function') showToast('리뷰 신고가 접수되었습니다.', 'success');
    } catch (error) {
        console.error(error);
        if (typeof showToast === 'function') showToast(error.message, 'error');
    }
}