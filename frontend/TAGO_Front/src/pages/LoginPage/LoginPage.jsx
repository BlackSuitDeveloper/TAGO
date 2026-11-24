import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import client from '../Api/client';
import useAuthStore from '../../store/useAuthStore';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import './LoginPage.css';

export default function LoginPage() {
  const [userId, setUserId] = useState(() => localStorage.getItem('savedUserId') || '');
  const [password, setPassword] = useState('');
  const [saveId, setSaveId] = useState(() => Boolean(localStorage.getItem('savedUserId')));
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);

  // 모달 상태: null, 'find-id', 'find-pw'
  const [activeModal, setActiveModal] = useState(null);

  // 아이디 찾기 상태
  const [findIdName, setFindIdName] = useState('');
  const [findIdEmail, setFindIdEmail] = useState('');
  const [findIdCode, setFindIdCode] = useState('');
  const [findIdStep, setFindIdStep] = useState(1); // 1: 정보입력/발송, 2: 인증코드입력, 3: 결과확인
  const [foundIdInfo, setFoundIdInfo] = useState(null);
  const [findIdLoading, setFindIdLoading] = useState(false);

  // 비밀번호 재설정 상태
  const [resetId, setResetId] = useState('');
  const [resetEmail, setResetEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [resetStep, setResetStep] = useState(1); // 1: 정보입력/발송, 2: 인증코드입력, 3: 새비밀번호입력
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [resetLoading, setResetLoading] = useState(false);

  // 모달 닫기 및 초기화
  const handleCloseModal = () => {
    setActiveModal(null);
    setFindIdName('');
    setFindIdEmail('');
    setFindIdCode('');
    setFindIdStep(1);
    setFoundIdInfo(null);
    setResetId('');
    setResetEmail('');
    setResetCode('');
    setResetStep(1);
    setResetToken('');
    setNewPassword('');
    setConfirmNewPassword('');
  };

  // 로그인 제출
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    if (!userId || !password) {
      alert('아이디와 비밀번호를 모두 입력해 주세요.');
      return;
    }

    setLoading(true);
    try {
      const response = await client.post('/member/login', {
        id: userId,
        pw: password,
      });

      const data = response.data || {};
      const token = data.token || data.accessToken || data.jwt || (typeof data === 'string' ? data : null);
      const loggedInId = data.id || userId;

      if (token) {
        login({ token, id: loggedInId });
        localStorage.setItem('token', token);
      }
      localStorage.setItem('userId', loggedInId);

      if (saveId) {
        localStorage.setItem('savedUserId', userId);
      } else {
        localStorage.removeItem('savedUserId');
      }

      alert(`${loggedInId}님, 로그인이 완료되었습니다.`);
      navigate('/');
    } catch (error) {
      console.error('로그인 에러:', error);
      const message =
        error.response?.data?.message ||
        (typeof error.response?.data === 'string' ? error.response.data : null) ||
        '아이디 또는 비밀번호가 올바르지 않습니다.';
      alert(message);
    } finally {
      setLoading(false);
    }
  };

  // ==========================================
  // 아이디 찾기 핸들러
  // ==========================================
  const handleSendFindIdCode = async (e) => {
    e.preventDefault();
    if (!findIdName.trim() || !findIdEmail.trim()) {
      alert('이름과 가입 이메일을 입력해 주세요.');
      return;
    }

    setFindIdLoading(true);
    try {
      const res = await client.post('/auth/find-id/send-code', {
        name: findIdName.trim(),
        email: findIdEmail.trim(),
      });
      alert(res.message || '가입된 이메일로 인증번호가 발송되었습니다.');
      setFindIdStep(2);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '일치하는 회원 정보를 찾을 수 없습니다.');
    } finally {
      setFindIdLoading(false);
    }
  };

  const handleVerifyFindIdCode = async (e) => {
    e.preventDefault();
    if (!findIdCode.trim() || findIdCode.trim().length !== 6) {
      alert('6자리 인증번호를 정확히 입력해 주세요.');
      return;
    }

    setFindIdLoading(true);
    try {
      const res = await client.post('/auth/find-id/verify', {
        name: findIdName.trim(),
        email: findIdEmail.trim(),
        code: findIdCode.trim(),
      });
      setFoundIdInfo(res.data);
      setFindIdStep(3);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '인증번호가 일치하지 않거나 만료되었습니다.');
    } finally {
      setFindIdLoading(false);
    }
  };

  const handleUseFoundId = () => {
    if (foundIdInfo?.id) {
      setUserId(foundIdInfo.id);
    }
    handleCloseModal();
  };

  // ==========================================
  // 비밀번호 재설정 핸들러
  // ==========================================
  const handleSendResetPwCode = async (e) => {
    e.preventDefault();
    if (!resetId.trim() || !resetEmail.trim()) {
      alert('아이디와 가입 이메일을 입력해 주세요.');
      return;
    }

    setResetLoading(true);
    try {
      const res = await client.post('/auth/reset-password/send-code', {
        id: resetId.trim(),
        email: resetEmail.trim(),
      });
      alert(res.message || '가입된 이메일로 인증번호가 발송되었습니다.');
      setResetStep(2);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '입력하신 아이디와 이메일이 일치하는 회원을 찾을 수 없습니다.');
    } finally {
      setResetLoading(false);
    }
  };

  const handleVerifyResetPwCode = async (e) => {
    e.preventDefault();
    if (!resetCode.trim() || resetCode.trim().length !== 6) {
      alert('6자리 인증번호를 정확히 입력해 주세요.');
      return;
    }

    setResetLoading(true);
    try {
      const res = await client.post('/auth/reset-password/verify', {
        id: resetId.trim(),
        email: resetEmail.trim(),
        code: resetCode.trim(),
      });
      setResetToken(res.data.resetToken);
      setResetStep(3);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '인증번호가 일치하지 않거나 만료되었습니다.');
    } finally {
      setResetLoading(false);
    }
  };

  const handleResetPasswordSubmit = async (e) => {
    e.preventDefault();
    if (newPassword !== confirmNewPassword) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    if (newPassword.length < 8) {
      alert('비밀번호는 최소 8자리 이상이어야 합니다.');
      return;
    }

    setResetLoading(true);
    try {
      const res = await client.post('/auth/reset-password', {
        id: resetId.trim(),
        email: resetEmail.trim(),
        resetToken: resetToken,
        newPassword: newPassword,
      });
      alert(res.message || '비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요.');
      setUserId(resetId.trim());
      setPassword('');
      handleCloseModal();
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '비밀번호 재설정에 실패했습니다.');
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className="login-page-wrapper">
      <Header simple={true} showTopBar={false} />

      <main className="login-container">
        <div className="login-card">
          <div className="login-header">
            <span className="login-sub-title">TAGO</span>
            <h2>로그인</h2>
            <p className="login-desc">TAGO 계정으로 로그인해 주세요.</p>
          </div>

          <form onSubmit={handleLoginSubmit} className="login-form">
            <div className="form-group">
              <label>아이디</label>
              <div className="input-wrap">
                <input
                  type="text"
                  placeholder="아이디를 입력하세요"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  className="login-input"
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>비밀번호</label>
              <div className="input-wrap">
                <input
                  type="password"
                  placeholder="비밀번호를 입력하세요"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="login-input"
                  required
                />
              </div>
            </div>

            <div className="login-sub-row">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={saveId}
                  onChange={(e) => setSaveId(e.target.checked)}
                />
                <span>아이디 저장</span>
              </label>
              <div className="find-links">
                <button
                  type="button"
                  className="find-link-btn"
                  onClick={() => { setActiveModal('find-id'); setFindIdStep(1); }}
                >
                  아이디 찾기
                </button>
                <span className="dot">/</span>
                <button
                  type="button"
                  className="find-link-btn"
                  onClick={() => { setActiveModal('find-pw'); setResetStep(1); }}
                >
                  비밀번호 찾기
                </button>
              </div>
            </div>

            <button type="submit" className="login-btn-submit" disabled={loading}>
              {loading ? '로그인 중...' : '로그인'}
            </button>
          </form>

          <div className="signup-prompt">
            <span>아직 TAGO 계정이 없으신가요?</span>
            <Link to="/signup" className="signup-link">
              회원가입 바로가기 →
            </Link>
          </div>
        </div>
      </main>

      {/* ========================================================
          아이디 / 비밀번호 찾기 모달 팝업
          ======================================================== */}
      {activeModal && (
        <div className="modal-backdrop" onClick={handleCloseModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div className="modal-tabs">
                <button
                  type="button"
                  className={`modal-tab-btn ${activeModal === 'find-id' ? 'active' : ''}`}
                  onClick={() => setActiveModal('find-id')}
                >
                  아이디 찾기
                </button>
                <button
                  type="button"
                  className={`modal-tab-btn ${activeModal === 'find-pw' ? 'active' : ''}`}
                  onClick={() => setActiveModal('find-pw')}
                >
                  비밀번호 재설정
                </button>
              </div>
              <button type="button" className="modal-close-btn" onClick={handleCloseModal}>
                ✕
              </button>
            </div>

            <div className="modal-body">
              {/* ---------------- 1. 아이디 찾기 탭 ---------------- */}
              {activeModal === 'find-id' && (
                <div>
                  <p className="modal-desc">
                    회원가입 시 등록하신 <strong>이름</strong>과 <strong>이메일</strong>을 입력하시면 인증코드를 발송해 드립니다.
                  </p>

                  {findIdStep === 1 && (
                    <form onSubmit={handleSendFindIdCode} className="modal-form">
                      <div className="form-group">
                        <label>이름</label>
                        <div className="input-wrap">
                          <input
                            type="text"
                            placeholder="가입자 이름"
                            value={findIdName}
                            onChange={(e) => setFindIdName(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <div className="form-group">
                        <label>가입 이메일</label>
                        <div className="input-wrap">
                          <input
                            type="email"
                            placeholder="example@email.com"
                            value={findIdEmail}
                            onChange={(e) => setFindIdEmail(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <button type="submit" className="modal-btn-submit" disabled={findIdLoading}>
                        {findIdLoading ? '인증번호 발송 중...' : '인증번호 받기'}
                      </button>
                    </form>
                  )}

                  {findIdStep === 2 && (
                    <form onSubmit={handleVerifyFindIdCode} className="modal-form">
                      <div className="email-sent-badge">
                        <span>{findIdEmail}</span>(으)로 인증번호 6자리가 발송되었습니다.
                      </div>
                      <div className="form-group">
                        <label>인증번호 (6자리)</label>
                        <div className="input-wrap">
                          <input
                            type="text"
                            maxLength={6}
                            placeholder="6자리 숫자 입력"
                            value={findIdCode}
                            onChange={(e) => setFindIdCode(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <div className="modal-btn-group">
                        <button
                          type="button"
                          className="modal-btn-sub"
                          onClick={() => setFindIdStep(1)}
                        >
                          이전
                        </button>
                        <button type="submit" className="modal-btn-submit" disabled={findIdLoading}>
                          {findIdLoading ? '확인 중...' : '아이디 확인'}
                        </button>
                      </div>
                    </form>
                  )}

                  {findIdStep === 3 && foundIdInfo && (
                    <div className="found-result-box">
                      <p className="found-label">회원님의 가입 아이디는 다음과 같습니다.</p>
                      <div className="found-id-display">
                        <strong>{foundIdInfo.id}</strong>
                      </div>
                      <button type="button" className="modal-btn-submit" onClick={handleUseFoundId}>
                        이 아이디로 로그인하기
                      </button>
                    </div>
                  )}
                </div>
              )}

              {/* ---------------- 2. 비밀번호 재설정 탭 ---------------- */}
              {activeModal === 'find-pw' && (
                <div>
                  <p className="modal-desc">
                    가입하신 <strong>아이디</strong>와 <strong>이메일</strong>을 입력하여 본인인증 후 새로운 비밀번호를 설정할 수 있습니다.
                  </p>

                  {resetStep === 1 && (
                    <form onSubmit={handleSendResetPwCode} className="modal-form">
                      <div className="form-group">
                        <label>아이디</label>
                        <div className="input-wrap">
                          <input
                            type="text"
                            placeholder="가입자 아이디"
                            value={resetId}
                            onChange={(e) => setResetId(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <div className="form-group">
                        <label>가입 이메일</label>
                        <div className="input-wrap">
                          <input
                            type="email"
                            placeholder="example@email.com"
                            value={resetEmail}
                            onChange={(e) => setResetEmail(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <button type="submit" className="modal-btn-submit" disabled={resetLoading}>
                        {resetLoading ? '인증번호 발송 중...' : '인증번호 받기'}
                      </button>
                    </form>
                  )}

                  {resetStep === 2 && (
                    <form onSubmit={handleVerifyResetPwCode} className="modal-form">
                      <div className="email-sent-badge">
                        <span>{resetEmail}</span>(으)로 인증번호 6자리가 발송되었습니다.
                      </div>
                      <div className="form-group">
                        <label>인증번호 (6자리)</label>
                        <div className="input-wrap">
                          <input
                            type="text"
                            maxLength={6}
                            placeholder="6자리 숫자 입력"
                            value={resetCode}
                            onChange={(e) => setResetCode(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <div className="modal-btn-group">
                        <button
                          type="button"
                          className="modal-btn-sub"
                          onClick={() => setResetStep(1)}
                        >
                          이전
                        </button>
                        <button type="submit" className="modal-btn-submit" disabled={resetLoading}>
                          {resetLoading ? '인증 확인 중...' : '인증 확인'}
                        </button>
                      </div>
                    </form>
                  )}

                  {resetStep === 3 && (
                    <form onSubmit={handleResetPasswordSubmit} className="modal-form">
                      <div className="form-group">
                        <label>새 비밀번호 (8자리 이상)</label>
                        <div className="input-wrap">
                          <input
                            type="password"
                            placeholder="새로운 비밀번호 입력"
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

                      <button type="submit" className="modal-btn-submit" disabled={resetLoading}>
                        {resetLoading ? '변경 중...' : '비밀번호 재설정 완료'}
                      </button>
                    </form>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <Footer />
    </div>
  );
}
