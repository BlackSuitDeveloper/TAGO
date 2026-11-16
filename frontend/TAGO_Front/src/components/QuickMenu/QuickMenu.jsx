import { Link } from 'react-router-dom';
import './QuickMenu.css';

export default function QuickMenu() {
  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <aside className="quick-menu">
      <div className="quick-menu-header">
        <span className="quick-menu-title">빠른 바로가기</span>
      </div>
      <div className="quick-menu-list">
        <Link to="/" className="quick-menu-item">
          승차권 예매
        </Link>
        <Link to="/schedule" className="quick-menu-item">
          운행 시각표
        </Link>
        <Link to="/mypage" className="quick-menu-item">
          발권 승차권
        </Link>
        <a href="#delay" className="quick-menu-item">
          정시 운행 안내
        </a>
      </div>
      <button type="button" className="quick-top-btn" onClick={scrollToTop}>
        TOP ↑
      </button>
    </aside>
  );
}
