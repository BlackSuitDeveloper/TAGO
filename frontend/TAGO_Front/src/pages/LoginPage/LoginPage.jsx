import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import './LoginPage.css';

export default function LoginPage() {
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [saveId, setSaveId] = useState(true);
  const navigate = useNavigate();

  const handleLoginSubmit = (e) => {
    e.preventDefault();
    if (!userId || !password) {
      alert('아이디와 비밀번호를 모두 입력해 주세요.');
      return;
    }
    alert(`${userId}님, 로그인이 완료되었습니다.`);
    navigate('/');
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

          {/* 자체 계정 로그인 폼 */}
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
                <a href="#find-id">아이디 찾기</a>
                <span className="dot">/</span>
                <a href="#find-pw">비밀번호 찾기</a>
              </div>
            </div>

            <button type="submit" className="login-btn-submit">
              로그인
            </button>
          </form>

          {/* 회원가입 이동 링크 */}
          <div className="signup-prompt">
            <span>아직 TAGO 계정이 없으신가요?</span>
            <Link to="/signup" className="signup-link">
              회원가입 바로가기 →
            </Link>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
