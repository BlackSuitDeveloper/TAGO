import './Footer.css';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer-container">
        <div className="footer-top">
          <div className="footer-links">
            <a href="#terms" className="highlight">개인정보처리방침</a>
            <span className="dot">/</span>
            <a href="#conditions">이용약관</a>
            <span className="dot">/</span>
            <a href="#passenger">여객운송약관</a>
            <span className="dot">/</span>
            <a href="#service">KTX 운행안내</a>
            <span className="dot">/</span>
            <a href="#support">철도고객센터 1544-7788</a>
          </div>
          <div className="footer-info-tag">
            <span>한국철도공사 KTX 전용 발권 시스템</span>
          </div>
        </div>

        <div className="footer-bottom">
          <p className="company-details">
            한국철도공사 (대전광역시 동구 중앙로 240) · 대표자: 한문희 · 사업자등록번호: 314-82-10042 · 통신판매업신고: 제 2005-대전동구-0036호
          </p>
          <p className="footer-copyright">
            Copyright © KOREA RAILROAD. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
