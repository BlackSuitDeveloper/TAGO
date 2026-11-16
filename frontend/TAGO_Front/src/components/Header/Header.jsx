import { Link, useLocation } from 'react-router-dom';
import './Header.css';

export default function Header({ showTopBar = true, simple = false, isLoggedIn = false }) {
  const location = useLocation();

  return (
    <>
      {showTopBar && (
        <div className="top-bar">
          <div className="top-bar-inner">
            <div className="top-bar-left">
              <span>TAGO KTX 고속철도 통합 예매 서비스</span>
            </div>
            <div className="top-bar-right">
              <Link to="/login">로그인</Link>
              <span className="divider">/</span>
              <Link to="/signup">회원가입</Link>
              <span className="divider">/</span>
              <a href="#support">고객센터 1544-7788</a>
              <span className="divider">/</span>
              <span className="lang-tag">KR</span>
            </div>
          </div>
        </div>
      )}

      <header className={`header ${showTopBar ? 'sticky' : ''}`}>
        <div className="header-container">
          <Link to="/" className="logo">
            <span className="logo-brand">TAGO</span>
            <span className="logo-badge-ktx">KTX</span>
          </Link>

          {!simple && (
            <>
              <nav className="main-menu">
                <Link to="/" className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}>
                  승차권 예매
                </Link>
                <Link to="/schedule" className={`nav-link ${location.pathname === '/schedule' ? 'active' : ''}`}>
                  운행 시각표
                </Link>
                <Link to="/mypage" className={`nav-link ${location.pathname === '/mypage' ? 'active' : ''}`}>
                  마이페이지
                </Link>
              </nav>

              <div className="member-menu">
                {isLoggedIn ? (
                  <Link to="/" className="btn-auth logout">로그아웃</Link>
                ) : (
                  <>
                    <Link to="/login" className="btn-auth login">로그인</Link>
                    <Link to="/signup" className="btn-auth signup">회원가입</Link>
                  </>
                )}
              </div>
            </>
          )}
        </div>
      </header>
    </>
  );
}
