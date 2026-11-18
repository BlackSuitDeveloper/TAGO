import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import client from '../Api/client';
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
  const [isIdChecked, setIsIdChecked] = useState(false);
  const [checkedId, setCheckedId] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'userId') {
      setIsIdChecked(false);
      setCheckedId('');
    }
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

  const handleOpenKakaoPostcode = () => {
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

          setFormData((prev) => ({
            ...prev,
            postcode: data.zonecode,
            address: finalAddress,
          }));

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

  // 아이디 중복확인 (GET /member/checkId?id=...)
  // MemberController: public int checkId(@RequestParam String id) -> 0: 사용 가능, 1 이상: 중복됨
  const handleCheckDuplicateId = async () => {
    const userId = formData.userId.trim();

    if (!userId) {
      alert('아이디를 입력해 주세요.');
      return;
    }

    try {
      const response = await client.get('/member/checkId', {
        params: { id: userId },
      });

      const count = Number(response.data);

      if (count === 0) {
        alert('사용 가능한 아이디입니다.');
        setIsIdChecked(true);
        setCheckedId(userId);
      } else {
        alert('이미 사용 중인 아이디입니다.');
        setIsIdChecked(false);
        setCheckedId('');
      }
    } catch (error) {
      console.error('아이디 중복 확인 에러:', error);
      const message =
        error.response?.data?.message ||
        (typeof error.response?.data === 'string' ? error.response.data : null) ||
        '아이디 중복 확인 중 오류가 발생했습니다.';
      alert(message);
    }
  };

  // 회원가입 (POST /member)
  // MemberController: public int signup(@RequestBody MemberDTO dto) -> 성공 시 1 반환
  const handleSignupSubmit = async (e) => {
    e.preventDefault();

    const currentId = formData.userId.trim();
    if (!isIdChecked || checkedId !== currentId) {
      alert('아이디 중복확인을 진행해 주세요.');
      return;
    }

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

    setLoading(true);
    try {
      const payload = {
        id: currentId,
        pw: formData.password,
        name: formData.name,
        phone: formData.phone.replace(/[^0-9]/g, ''),
        email: formData.email,
        zipcode: formData.postcode,
        address1: formData.address,
        address2: formData.detailAddress,
      };

      const response = await client.post('/member', payload);

      if (response.status === 200 && Number(response.data) === 1) {
        alert(`[회원가입 완료]\n${formData.name}님, 회원가입이 성공적으로 완료되었습니다.\n로그인 페이지로 이동합니다.`);
        navigate('/login');
      } else {
        alert('회원가입 처리에 실패했습니다. 입력 정보를 다시 확인해 주세요.');
      }
    } catch (error) {
      console.error('회원가입 에러:', error);
      const message =
        error.response?.data?.message ||
        (typeof error.response?.data === 'string' ? error.response.data : null) ||
        '회원가입 중 오류가 발생했습니다. 다시 시도해 주세요.';
      alert(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="signup-page-wrapper">
      <Header simple={true} showTopBar={false} />

      <main className="signup-container">
        <div className="signup-card">
          <div className="signup-header">
            <span className="signup-sub-title">TAGO 회원가입</span>
            <h2>회원가입</h2>
            <p className="signup-desc">TAGO 자체 회원으로 가입하여 승차권을 안전하게 예매하세요.</p>
          </div>

          <form onSubmit={handleSignupSubmit} className="signup-form">
            {/* 아이디 입력 + 중복체크 버튼 */}
            <div className="form-group">
              <label>아이디 <span className="req">*</span></label>
              <div className="postcode-row" style={{ display: 'flex', gap: '12px' }}>
                <div className="input-wrap postcode-input-wrap" style={{ flex: 1 }}>
                  <input
                    type="text"
                    name="userId"
                    placeholder="아이디를 입력하세요"
                    value={formData.userId}
                    onChange={handleChange}
                    required
                  />
                </div>
                <button
                  type="button"
                  className="btn-address-search"
                  style={{ whiteSpace: 'nowrap' }}
                  onClick={handleCheckDuplicateId}
                >
                  중복확인
                </button>
              </div>
            </div>

            <div className="form-group">
              <label>비밀번호 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="password"
                  name="password"
                  placeholder="비밀번호 (8자 이상 영문/숫자/특수문자)"
                  value={formData.password}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>비밀번호 재확인 <span className="req">*</span></label>
              <div className={`input-wrap ${passwordMatchError ? 'error' : ''}`}>
                <input
                  type="password"
                  name="confirmPassword"
                  placeholder="비밀번호를 한 번 더 입력하세요"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  required
                />
              </div>
              {passwordMatchError && (
                <span className="field-error-msg">비밀번호가 일치하지 않습니다.</span>
              )}
            </div>

            <div className="form-group">
              <label>이름 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="text"
                  name="name"
                  placeholder="이름(실명)을 입력하세요"
                  value={formData.name}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>이메일 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="email"
                  name="email"
                  placeholder="예: user@example.com"
                  value={formData.email}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>휴대전화 번호 <span className="req">*</span></label>
              <div className="input-wrap">
                <input
                  type="tel"
                  name="phone"
                  placeholder="예: 010-1234-5678"
                  value={formData.phone}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>주소 <span className="req">*</span></label>
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

              <div className="input-wrap" style={{ marginTop: '8px' }}>
                <input
                  type="text"
                  name="address"
                  placeholder="기본 주소 (우편번호 찾기로 자동 입력)"
                  value={formData.address}
                  readOnly
                  required
                />
              </div>

              <div className="input-wrap" style={{ marginTop: '8px' }}>
                <input
                  type="text"
                  id="detailAddressInput"
                  name="detailAddress"
                  placeholder="상세 주소를 입력하세요 (동/호수 등)"
                  value={formData.detailAddress}
                  onChange={handleChange}
                />
              </div>
            </div>

            <div className="terms-group">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={agreed}
                  onChange={(e) => setAgreed(e.target.checked)}
                />
                <span>[필수] TAGO 이용약관 및 개인정보 수집/이용에 동의합니다.</span>
              </label>
            </div>

            <button type="submit" className="signup-btn-submit" disabled={loading}>
              {loading ? '가입 처리 중...' : '가입하기'}
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
