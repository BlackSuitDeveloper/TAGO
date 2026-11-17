import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import './LoginPage.css';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export default function LoginPage() {
  const [userId, setUserId] = useState(() => localStorage.getItem('savedUserId') || '');
  const [password, setPassword] = useState('');
  const [saveId, setSaveId] = useState(() => Boolean(localStorage.getItem('savedUserId')));
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    if (!userId || !password) {
      alert('아이디와 비밀번호를 모두 입력해 주세요.');
      return;
    }

    setLoading(true);
    try {
      // POST /auth/login - 요청 데이터: id, pw (AuthDTO)
      const response = await axios.post(`${API_BASE_URL}/auth/login`, {
        id: userId,
        pw: password,
      });

      // 응답: 200, JWT token, id
      const data = response.data || {};
      const token = data.token || data.accessToken || data.jwt || (typeof data === 'string' ? data : null);
      const loggedInId = data.id || userId;

      if (token) {
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
                <a href="#find-id">아이디 찾기</a>
                <span className="dot">/</span>
                <a href="#find-pw">비밀번호 찾기</a>
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

      <Footer />
    </div>
  );
}
