import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import './SignupPage.css';

export default function SignupPage() {
  const [formData, setFormData] = useState({
    userId: '',
    password: '',
    confirmPassword: '',
    name: '',
    email: '',
    phone: '',
    postcode: '',
    address: '',
    detailAddress: '',
  });

  const [passwordMatchError, setPasswordMatchError] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => {
      const next = { ...prev, [name]: value };
      if (name === 'confirmPassword' || name === 'password') {
        const p1 = name === 'password' ? value : next.password;
        const p2 = name === 'confirmPassword' ? value : next.confirmPassword;
        setPasswordMatchError(p2.length > 0 && p1 !== p2);
      }
      return next;
    });
  };

  // 우편번호 찾기 버튼 클릭 시 카카오(Daum) 주소 검색 팝업 연동
  const handleOpenKakaoPostcode = () => {
    if (window.daum && window.daum.Postcode) {
      new window.daum.Postcode({
        oncomplete: (data) => {
          // 사용자가 도로명 주소(roadAddress) 또는 지번 주소(jibunAddress)를 선택했을 때
          const fullAddress = data.roadAddress || data.jibunAddress;
          let extraAddress = '';

          if (data.bname !== '' && /[동|로|가]$/g.test(data.bname)) {
            extraAddress += data.bname;
          }
          if (data.buildingName !== '' && data.apartment === 'Y') {
            extraAddress += extraAddress !== '' ? `, ${data.buildingName}` : data.buildingName;
          }

          const finalAddress = extraAddress !== '' ? `${fullAddress} (${extraAddress})` : fullAddress;

          setFormData((prev) => ({
            ...prev,
            postcode: data.zonecode, // 5자리 새 우편번호
            address: finalAddress,   // 기본 주소
          }));

          // 상세 주소 입력칸으로 포커스 자동 이동
          const detailInput = document.getElementById('detailAddressInput');
          if (detailInput) {
            detailInput.focus();
          }
        },
      }).open();
    } else {
      alert('주소 검색 서비스를 로드하는 중입니다. 잠시 후 다시 클릭해 주세요.');
    }
  };

  const handleSignupSubmit = (e) => {
    e.preventDefault();

    if (formData.password !== formData.confirmPassword) {
      alert('비밀번호가 일치하지 않습니다. 다시 확인해 주세요.');
      return;
    }

    if (!formData.address) {
      alert('우편번호 찾기를 통해 주소를 입력해 주세요.');
      return;
    }

    if (!agreed) {
      alert('이용약관 및 개인정보 처리방침에 동의해 주세요.');
      return;
    }

    alert(`[회원가입 완료]\n${formData.name}님, 회원가입이 성공적으로 완료되었습니다.\n로그인 페이지로 이동합니다.`);
    navigate('/login');
  };

  return (
    <div className="signup-page-wrapper">
      <Header simple={true} showTopBar={false} />

      <main className="signup-container">
        <div className="signup-card">
          <div className="signup-header">
            <span className="signup-sub-title">TAGO</span>
            <h2>회원가입</h2>
            <p className="signup-desc">TAGO 서비스 이용을 위해 회원 정보를 입력해 주세요.</p>
          </div>

          <form onSubmit={handleSignupSubmit} className="signup-form">
            {/* 1. 아이디 */}
            <div className="form-group">
              <label>아이디 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="text"
                  name="userId"
                  placeholder="아이디를 입력하세요"
                  value={formData.userId}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            {/* 2. 비밀번호 */}
            <div className="form-group">
              <label>비밀번호 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="password"
                  name="password"
                  placeholder="비밀번호를 입력하세요"
                  value={formData.password}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            {/* 3. 비밀번호 재확인 */}
            <div className="form-group">
              <label>비밀번호 재확인 <span className="req">*</span></label>
              <div className={`input-wrap ${passwordMatchError ? 'has-error' : ''}`}>
                <input
                  type="password"
                  name="confirmPassword"
                  placeholder="비밀번호를 다시 한 번 입력하세요"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  required
                />
              </div>
              {passwordMatchError && (
                <span className="error-text">비밀번호가 일치하지 않습니다.</span>
              )}
            </div>

            {/* 4. 이름 */}
            <div className="form-group">
              <label>이름 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="text"
                  name="name"
                  placeholder="이름을 입력하세요"
                  value={formData.name}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            {/* 5. 이메일 */}
            <div className="form-group">
              <label>이메일 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="email"
                  name="email"
                  placeholder="example@tago.kr"
                  value={formData.email}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            {/* 6. 휴대전화 번호 */}
            <div className="form-group">
              <label>휴대전화 번호 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="tel"
                  name="phone"
                  placeholder="010-0000-0000"
                  value={formData.phone}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            {/* 7, 8, 9. 우편번호, 주소, 상세 주소 (카카오 주소검색 연동) */}
            <div className="form-group">
              <label>주소 <span className="req">*</span></label>
              
              {/* 우편번호 & 우편번호 찾기 버튼 */}
              <div className="postcode-row">
                <div className="input-wrap postcode-input-wrap">
                  <input
                    type="text"
                    name="postcode"
                    placeholder="우편번호"
                    value={formData.postcode}
                    readOnly
                    required
                  />
                </div>
                <button 
                  type="button" 
                  className="btn-address-search" 
                  onClick={handleOpenKakaoPostcode}
                >
                  우편번호 찾기
                </button>
              </div>

              {/* 기본 주소 */}
              <div className="input-wrap" style={{ marginTop: '8px' }}>
                <input
                  type="text"
                  name="address"
                  placeholder="기본 주소 (우편번호 찾기 시 자동 입력)"
                  value={formData.address}
                  readOnly
                  required
                />
              </div>

              {/* 상세 주소 */}
              <div className="input-wrap" style={{ marginTop: '8px' }}>
                <input
                  type="text"
                  id="detailAddressInput"
                  name="detailAddress"
                  placeholder="상세 주소를 입력하세요 (동, 호수 등)"
                  value={formData.detailAddress}
                  onChange={handleChange}
                />
              </div>
            </div>

            {/* 약관 동의 */}
            <div className="terms-card">
              <label className="terms-agree-all">
                <input
                  type="checkbox"
                  checked={agreed}
                  onChange={(e) => setAgreed(e.target.checked)}
                />
                <strong>[필수] 이용약관 및 개인정보 수집·이용 동의</strong>
              </label>
              <div className="terms-scroll-text">
                제 1 조 (목적) 본 약관은 TAGO 철도 시스템이 제공하는 KTX 고속철도 승차권 예매 서비스의 이용조건 및 절차를 규정함을 목적으로 합니다.<br/>
                제 2 조 (수집 항목) 아이디, 비밀번호, 이름, 이메일, 휴대전화번호, 우편번호, 주소.
              </div>
            </div>

            <button type="submit" className="signup-btn-submit">
              가입하기
            </button>
          </form>

          <div className="login-prompt">
            <span>이미 계정이 있으신가요?</span>
            <Link to="/login" className="login-link">
              로그인 바로가기 →
            </Link>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
