import { useState } from 'react';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import './MyPage.css';

export default function MyPage() {
  const [activeMenu, setActiveMenu] = useState('ticket'); // 'ticket' or 'profile'

  // 발권 승차권 데이터
  const activeTicket = {
    trainName: 'KTX 011',
    trainType: 'KTX',
    depDate: '2026. 11. 16 (월)',
    depStation: '서울',
    arrStation: '부산',
    depTime: '05:15',
    arrTime: '07:42',
    car: '3호차',
    seat: '4B (순방향)',
    passenger: '어른 1명',
    price: '59,800원',
    ticketNumber: '1029-3847-5690-11',
  };

  // 회원 기본 정보 원본 데이터
  const [originalUserInfo, setOriginalUserInfo] = useState({
    userId: 'user_tago',
    name: '홍길동',
    email: 'hong@tago.kr',
    phone: '010-1234-5678',
    postcode: '04320',
    address: '서울특별시 중구 청파로 432 (철도빌딩)',
    detailAddress: '12층 1201호',
  });

  // 수정 중인 정보 데이터
  const [editUserInfo, setEditUserInfo] = useState({ ...originalUserInfo });

  // 회원 정보 수정 모드 여부 (false: 조회 모드, true: 수정 모드)
  const [isEditing, setIsEditing] = useState(false);

  // 비밀번호 변경 (이메일 인증 플로우) 상태
  const [emailAuthStep, setEmailAuthStep] = useState(1); // 1: 인증번호 발송 대기, 2: 인증번호 입력 대기, 3: 인증 완료 & 새 비밀번호 입력
  const [authCodeInput, setAuthCodeInput] = useState('');
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');

  // 회원정보 수정 입력 핸들러
  const handleUserInfoChange = (e) => {
    const { name, value } = e.target;
    setEditUserInfo((prev) => ({ ...prev, [name]: value }));
  };

  // 수정 시작
  const handleStartEdit = () => {
    setEditUserInfo({ ...originalUserInfo });
    setIsEditing(true);
  };

  // 수정 취소
  const handleCancelEdit = () => {
    setEditUserInfo({ ...originalUserInfo });
    setIsEditing(false);
  };

  // 수정 완료 저장
  const handleSaveProfile = (e) => {
    e.preventDefault();
    setOriginalUserInfo({ ...editUserInfo });
    setIsEditing(false);
    alert('회원 정보가 성공적으로 수정되었습니다.');
  };

  // 카카오 우편번호 검색 연동
  const handleOpenKakaoPostcode = () => {
    if (!isEditing) return;

    if (window.daum && window.daum.Postcode) {
      new window.daum.Postcode({
        oncomplete: (data) => {
          const fullAddress = data.roadAddress || data.jibunAddress;
          let extraAddress = '';

          if (data.bname !== '' && /[동|로|가]$/g.test(data.bname)) {
            extraAddress += data.bname;
          }
          if (data.buildingName !== '' && data.apartment === 'Y') {
            extraAddress += extraAddress !== '' ? `, ${data.buildingName}` : data.buildingName;
          }

          const finalAddress = extraAddress !== '' ? `${fullAddress} (${extraAddress})` : fullAddress;

          setEditUserInfo((prev) => ({
            ...prev,
            postcode: data.zonecode,
            address: finalAddress,
          }));

          const detailInput = document.getElementById('editDetailAddressInput');
          if (detailInput) {
            detailInput.focus();
          }
        },
      }).open();
    } else {
      alert('주소 검색 서비스를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.');
    }
  };

  // 이메일 인증번호 발송
  const handleSendEmailAuth = () => {
    alert(`[이메일 발송 완료]\n회원님의 이메일(${originalUserInfo.email})로 6자리 인증번호가 발송되었습니다.\n(테스트 인증번호: 123456)`);
    setEmailAuthStep(2);
  };

  // 이메일 인증번호 확인
  const handleVerifyEmailCode = () => {
    if (authCodeInput.trim() === '123456' || authCodeInput.trim().length === 6) {
      alert('이메일 본인인증이 완료되었습니다.\n새로운 비밀번호를 설정해 주세요.');
      setIsEmailVerified(true);
      setEmailAuthStep(3);
    } else {
      alert('인증번호 6자리가 올바르지 않습니다. 다시 확인해 주세요.');
    }
  };

  // 새 비밀번호 변경 완료
  const handleSavePassword = (e) => {
    e.preventDefault();
    if (!isEmailVerified) {
      alert('먼저 이메일 인증을 완료해 주세요.');
      return;
    }
    if (newPassword !== confirmNewPassword) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    if (newPassword.length < 8) {
      alert('비밀번호는 최소 8자리 이상이어야 합니다.');
      return;
    }

    alert('비밀번호가 성공적으로 변경되었습니다. 다음 로그인부터 적용됩니다.');
    // 초기화
    setNewPassword('');
    setConfirmNewPassword('');
    setAuthCodeInput('');
    setIsEmailVerified(false);
    setEmailAuthStep(1);
  };

  return (
    <div className="mypage-wrapper">
      <Header isLoggedIn={true} showTopBar={true} />

      <main className="mypage-container">
        <div className="mypage-title-row">
          <h1>마이페이지</h1>
          <p>승차권 확인 및 회원 정보를 관리할 수 있습니다.</p>
        </div>

        <div className="mypage-grid">
          {/* Left Sidebar */}
          <aside className="mypage-sidebar">
            <div className="profile-card">
              <span className="user-icon">TAGO MEMBER</span>
              <strong className="member-name">{originalUserInfo.name} 님</strong>
              <span className="member-id">@{originalUserInfo.userId}</span>
            </div>

            <nav className="mypage-menu">
              <button 
                type="button" 
                className={`menu-btn ${activeMenu === 'ticket' ? 'active' : ''}`}
                onClick={() => setActiveMenu('ticket')}
              >
                발권 승차권 확인
              </button>
              <button 
                type="button" 
                className={`menu-btn ${activeMenu === 'profile' ? 'active' : ''}`}
                onClick={() => setActiveMenu('profile')}
              >
                회원 정보 관리
              </button>
            </nav>
          </aside>

          {/* Right Content */}
          <section className="content-section">
            {/* 1. 발권 승차권 확인 탭 */}
            {activeMenu === 'ticket' && (
              <div className="ticket-display-card">
                <div className="card-top-header">
                  <h2>발권된 승차권 (탑승 대기)</h2>
                  <span className="live-status-badge">정상 발권</span>
                </div>

                <div className="mobile-ticket-container">
                  <div className="ticket-header">
                    <div className="ticket-train-meta">
                      <span className="badge-ktx">{activeTicket.trainType}</span>
                      <strong className="ticket-train-no">{activeTicket.trainName}</strong>
                    </div>
                    <span className="ticket-date">{activeTicket.depDate}</span>
                  </div>

                  <div className="ticket-route-block">
                    <div className="route-station dep">
                      <span className="station-city">{activeTicket.depStation}</span>
                      <span className="station-time">{activeTicket.depTime}</span>
                    </div>

                    <div className="route-indicator">
                      <span className="indicator-label">직통</span>
                      <div className="indicator-line"></div>
                    </div>

                    <div className="route-station arr">
                      <span className="station-city">{activeTicket.arrStation}</span>
                      <span className="station-time">{activeTicket.arrTime}</span>
                    </div>
                  </div>

                  <div className="ticket-seat-details">
                    <div className="detail-col">
                      <span className="col-label">호차</span>
                      <strong className="col-val highlight">{activeTicket.car}</strong>
                    </div>
                    <div className="detail-col">
                      <span className="col-label">좌석 번호</span>
                      <strong className="col-val highlight">{activeTicket.seat}</strong>
                    </div>
                    <div className="detail-col">
                      <span className="col-label">구분</span>
                      <strong className="col-val">{activeTicket.passenger}</strong>
                    </div>
                    <div className="detail-col">
                      <span className="col-label">운임</span>
                      <strong className="col-val">{activeTicket.price}</strong>
                    </div>
                  </div>

                  <div className="ticket-qr-section">
                    <div className="qr-box">
                      <div className="qr-fake-visual">
                        <span>■ ■ ■ ■ ■<br/>■ □ □ □ ■<br/>■ □ ■ □ ■<br/>■ □ □ □ ■<br/>■ ■ ■ ■ ■</span>
                      </div>
                      <span className="qr-hint">개찰구 리더기에 바코드를 태그하세요.</span>
                    </div>
                    <div className="ticket-actions">
                      <button type="button" className="btn-ticket-action outline">영수증 발급</button>
                      <button type="button" className="btn-ticket-action danger" onClick={() => alert('승차권 반환 신청이 완료되었습니다.')}>승차권 반환(취소)</button>
                    </div>
                  </div>

                  <div className="ticket-footer-number">
                    승차권 발권번호: {activeTicket.ticketNumber}
                  </div>
                </div>
              </div>
            )}

            {/* 2. 회원 정보 관리 탭 */}
            {activeMenu === 'profile' && (
              <div className="profile-edit-container">
                {/* 회원 기본 정보 카드 */}
                <div className="edit-card">
                  <div className="card-top-header">
                    <div>
                      <h2>회원 기본 정보</h2>
                      <p className="card-subtitle">
                        {isEditing ? '회원 정보를 수정한 후 [수정 완료]를 눌러주세요.' : '[회원정보 수정하기]를 누르면 정보를 변경할 수 있습니다.'}
                      </p>
                    </div>
                    {!isEditing && (
                      <button type="button" className="btn-start-edit" onClick={handleStartEdit}>
                        회원정보 수정하기
                      </button>
                    )}
                  </div>

                  <form onSubmit={handleSaveProfile} className="profile-form">
                    <div className="form-group">
                      <label>아이디</label>
                      <div className="input-wrap disabled">
                        <input type="text" value={editUserInfo.userId} disabled />
                      </div>
                      <span className="form-note">아이디는 고유 식별자로 변경할 수 없습니다.</span>
                    </div>

                    <div className="form-group">
                      <label>이름</label>
                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`}>
                        <input 
                          type="text" 
                          name="name" 
                          value={editUserInfo.name} 
                          onChange={handleUserInfoChange} 
                          disabled={!isEditing}
                          required 
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <label>이메일</label>
                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`}>
                        <input 
                          type="email" 
                          name="email" 
                          value={editUserInfo.email} 
                          onChange={handleUserInfoChange} 
                          disabled={!isEditing}
                          required 
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <label>휴대전화 번호</label>
                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`}>
                        <input 
                          type="tel" 
                          name="phone" 
                          value={editUserInfo.phone} 
                          onChange={handleUserInfoChange} 
                          disabled={!isEditing}
                          required 
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <label>주소</label>
                      <div className="postcode-row">
                        <div className={`input-wrap postcode-input-wrap ${!isEditing ? 'read-only' : ''}`}>
                          <input 
                            type="text" 
                            name="postcode" 
                            value={editUserInfo.postcode} 
                            readOnly 
                          />
                        </div>
                        {isEditing && (
                          <button 
                            type="button" 
                            className="btn-address-search" 
                            onClick={handleOpenKakaoPostcode}
                          >
                            우편번호 찾기
                          </button>
                        )}
                      </div>

                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`} style={{ marginTop: '8px' }}>
                        <input 
                          type="text" 
                          name="address" 
                          value={editUserInfo.address} 
                          readOnly 
                        />
                      </div>

                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`} style={{ marginTop: '8px' }}>
                        <input 
                          type="text" 
                          id="editDetailAddressInput"
                          name="detailAddress" 
                          value={editUserInfo.detailAddress} 
                          onChange={handleUserInfoChange} 
                          placeholder="상세 주소를 입력하세요"
                          disabled={!isEditing}
                        />
                      </div>
                    </div>

                    {/* 수정 모드일 때만 저장 및 취소 버튼 노출 */}
                    {isEditing && (
                      <div className="edit-action-row">
                        <button type="submit" className="btn-save-profile">
                          수정 완료
                        </button>
                        <button type="button" className="btn-cancel-profile" onClick={handleCancelEdit}>
                          취소
                        </button>
                      </div>
                    )}
                  </form>
                </div>

                {/* 이메일 인증 기반 비밀번호 변경 카드 */}
                <div className="edit-card" style={{ marginTop: '24px' }}>
                  <div className="card-top-header">
                    <div>
                      <h2>비밀번호 변경 (이메일 인증)</h2>
                      <p className="card-subtitle">안전한 계정 보호를 위해 등록된 이메일 본인인증 후 비밀번호를 변경할 수 있습니다.</p>
                    </div>
                  </div>

                  <form onSubmit={handleSavePassword} className="profile-form">
                    {/* 1단계: 이메일 확인 및 인증번호 발송 */}
                    <div className="form-group">
                      <label>인증 대상 이메일</label>
                      <div className="email-auth-row">
                        <div className="input-wrap flex-1 disabled">
                          <input type="email" value={originalUserInfo.email} disabled />
                        </div>
                        <button 
                          type="button" 
                          className="btn-email-auth"
                          onClick={handleSendEmailAuth}
                          disabled={isEmailVerified}
                        >
                          {isEmailVerified ? '인증 완료됨' : emailAuthStep === 1 ? '인증번호 발송' : '재발송'}
                        </button>
                      </div>
                    </div>

                    {/* 2단계: 6자리 인증번호 입력창 (발송 후 활성화) */}
                    {emailAuthStep >= 2 && !isEmailVerified && (
                      <div className="form-group auth-code-group">
                        <label>이메일 인증번호 6자리</label>
                        <div className="email-auth-row">
                          <div className="input-wrap flex-1">
                            <input 
                              type="text" 
                              maxLength="6"
                              placeholder="인증번호 6자리를 입력하세요" 
                              value={authCodeInput}
                              onChange={(e) => setAuthCodeInput(e.target.value)}
                            />
                          </div>
                          <button 
                            type="button" 
                            className="btn-email-verify"
                            onClick={handleVerifyEmailCode}
                          >
                            인증 확인
                          </button>
                        </div>
                        <span className="auth-hint">이메일 수신함에서 인증번호를 확인해 주세요. (테스트용: 123456)</span>
                      </div>
                    )}

                    {/* 3단계: 인증 완료 시 새 비밀번호 입력창 노출 */}
                    {isEmailVerified && (
                      <div className="new-password-section">
                        <div className="verified-badge-row">
                          <span className="verified-icon">✓</span>
                          <span>이메일 본인인증이 정상적으로 완료되었습니다.</span>
                        </div>

                        <div className="form-group" style={{ marginTop: '14px' }}>
                          <label>새 비밀번호</label>
                          <div className="input-wrap">
                            <input 
                              type="password" 
                              placeholder="새로운 비밀번호 (8자리 이상)" 
                              value={newPassword}
                              onChange={(e) => setNewPassword(e.target.value)}
                              required 
                            />
                          </div>
                        </div>

                        <div className="form-group">
                          <label>새 비밀번호 확인</label>
                          <div className="input-wrap">
                            <input 
                              type="password" 
                              placeholder="새로운 비밀번호 재입력" 
                              value={confirmNewPassword}
                              onChange={(e) => setConfirmNewPassword(e.target.value)}
                              required 
                            />
                          </div>
                        </div>

                        <button type="submit" className="btn-save-profile">
                          비밀번호 변경 완료
                        </button>
                      </div>
                    )}
                  </form>
                </div>
              </div>
            )}
          </section>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
