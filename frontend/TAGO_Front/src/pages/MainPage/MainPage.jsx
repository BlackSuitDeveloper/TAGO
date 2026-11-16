import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import './MainPage.css';

export default function MainPage() {
  const [tripType, setTripType] = useState('편도');
  const [departureStation, setDepartureStation] = useState('서울');
  const [arrivalStation, setArrivalStation] = useState('부산');
  const [departureDate, setDepartureDate] = useState('2026-11-16');
  const [passengerCount, setPassengerCount] = useState('어른 1명');
  const [seatPreference, setSeatPreference] = useState('일반실');
  const navigate = useNavigate();

  const handleSwapStations = () => {
    const temp = departureStation;
    setDepartureStation(arrivalStation);
    setArrivalStation(temp);
  };

  const handleSearch = () => {
    navigate('/schedule', {
      state: {
        departureStation,
        arrivalStation,
        departureDate,
        passengerCount,
        tripType,
        seatPreference,
      }
    });
  };

  const handleDirectBooking = (train) => {
    alert(`[KTX 예매 완료]\n열차: ${train.name}\n구간: ${departureStation} ➔ ${arrivalStation}\n일시: ${departureDate} (${train.depTime})\n운임: ${train.price}원\n\n승차권이 자동 배정 발권되었습니다. 마이페이지로 이동합니다.`);
    navigate('/mypage');
  };

  const quickStations = ['서울', '용산', '광명', '대전', '동대구', '부산', '광주송정', '강릉'];

  const previewTrains = [
    { id: 1, name: 'KTX 011', type: 'KTX', depTime: '05:15', arrTime: '07:42', duration: '2시간 27분', price: '59,800', status: '예매가능' },
    { id: 2, name: 'KTX-청룡 001', type: 'KTX-청룡', depTime: '06:00', arrTime: '08:15', duration: '2시간 15분', price: '59,800', status: '예매가능' },
    { id: 3, name: 'KTX-산천 403', type: 'KTX-산천', depTime: '06:30', arrTime: '08:58', duration: '2시간 28분', price: '59,800', status: '매진' },
    { id: 4, name: 'KTX 015', type: 'KTX', depTime: '07:00', arrTime: '09:25', duration: '2시간 25분', price: '59,800', status: '예매가능' },
  ];

  return (
    <div className="main-page-wrapper">
      <Header showTopBar={true} />

      {/* Hero Header */}
      <section className="rail-hero">
        <div className="hero-content">
          <span className="hero-tag">TAGO KTX 공식 예매</span>
          <h1 className="hero-title">더 빠르고 정밀한 고속철도 여정</h1>
          <p className="hero-subtitle">전국 KTX 전 노선의 실시간 운행 시각표를 확인하고 간편하게 예매하세요.</p>
        </div>
      </section>

      <main className="main-container">
        {/* Booking Card Panel */}
        <section className="booking-panel">
          <div className="booking-header-row">
            <div className="booking-nav-tabs">
              <button
                type="button"
                className={`booking-tab ${tripType === '편도' ? 'active' : ''}`}
                onClick={() => setTripType('편도')}
              >
                편도
              </button>
              <button
                type="button"
                className={`booking-tab ${tripType === '왕복' ? 'active' : ''}`}
                onClick={() => setTripType('왕복')}
              >
                왕복
              </button>
            </div>

            {/* Quick Stations */}
            <div className="station-quick-select">
              <span className="quick-label">주요 정차역</span>
              {quickStations.map((st) => (
                <button
                  type="button"
                  key={st}
                  className={`chip-btn ${arrivalStation === st ? 'selected' : ''}`}
                  onClick={() => {
                    if (departureStation !== st) {
                      setArrivalStation(st);
                    }
                  }}
                >
                  {st}
                </button>
              ))}
            </div>
          </div>

          {/* Departure Info Grid */}
          <div className="station-route-grid">
            {/* Departure */}
            <div className="input-group">
              <span className="input-label">출발역</span>
              <div className="input-field-box">
                <select 
                  value={departureStation} 
                  onChange={(e) => setDepartureStation(e.target.value)}
                  className="station-select"
                >
                  <option value="서울">서울</option>
                  <option value="용산">용산</option>
                  <option value="광명">광명</option>
                  <option value="대전">대전</option>
                  <option value="동대구">동대구</option>
                  <option value="부산">부산</option>
                  <option value="광주송정">광주송정</option>
                  <option value="강릉">강릉</option>
                </select>
              </div>
            </div>

            {/* Swap Button */}
            <div className="swap-cell">
              <button 
                type="button" 
                className="swap-btn" 
                onClick={handleSwapStations}
                title="출발/도착역 전환"
              >
                ⇄
              </button>
            </div>

            {/* Arrival */}
            <div className="input-group">
              <span className="input-label">도착역</span>
              <div className="input-field-box">
                <select 
                  value={arrivalStation} 
                  onChange={(e) => setArrivalStation(e.target.value)}
                  className="station-select"
                >
                  <option value="부산">부산</option>
                  <option value="동대구">동대구</option>
                  <option value="대전">대전</option>
                  <option value="광주송정">광주송정</option>
                  <option value="강릉">강릉</option>
                  <option value="포항">포항</option>
                  <option value="서울">서울</option>
                  <option value="용산">용산</option>
                </select>
              </div>
            </div>

            {/* Date */}
            <div className="input-group">
              <span className="input-label">출발일</span>
              <div className="input-field-box">
                <input 
                  type="date" 
                  value={departureDate} 
                  onChange={(e) => setDepartureDate(e.target.value)}
                  className="date-input"
                />
              </div>
            </div>

            {/* Passenger */}
            <div className="input-group">
              <span className="input-label">인원</span>
              <div className="input-field-box">
                <select 
                  value={passengerCount} 
                  onChange={(e) => setPassengerCount(e.target.value)}
                  className="station-select"
                >
                  <option value="어른 1명">어른 1명</option>
                  <option value="어른 2명">어른 2명</option>
                  <option value="어른 3명">어른 3명</option>
                  <option value="어른 4명">어른 4명</option>
                  <option value="어른 1명, 어린이 1명">어른 1명 + 어린이 1명</option>
                </select>
              </div>
            </div>
          </div>

          {/* 왕복 선택 시 오는 편 정보 */}
          {tripType === '왕복' && (
            <div className="return-trip-box">
              <div className="return-header">
                <span className="return-tag">오는 편 여정</span>
                <span className="return-text">{arrivalStation} ➔ {departureStation}</span>
              </div>
              <div className="station-route-grid return-grid">
                <div className="input-group">
                  <span className="input-label">귀가 출발역</span>
                  <div className="input-field-box disabled">
                    <input type="text" readOnly value={arrivalStation} className="readonly-input" />
                  </div>
                </div>
                <div className="swap-cell">
                  <span className="arrow-static">➔</span>
                </div>
                <div className="input-group">
                  <span className="input-label">귀가 도착역</span>
                  <div className="input-field-box disabled">
                    <input type="text" readOnly value={departureStation} className="readonly-input" />
                  </div>
                </div>
                <div className="input-group">
                  <span className="input-label">귀가일</span>
                  <div className="input-field-box">
                    <input type="date" defaultValue="2026-11-17" className="date-input" />
                  </div>
                </div>
                <div className="input-group">
                  <span className="input-label">인원</span>
                  <div className="input-field-box disabled">
                    <input type="text" readOnly value={passengerCount} className="readonly-input" />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Options & Search Action */}
          <div className="booking-footer-row">
            <div className="seat-pref-group">
              <span className="opt-title">좌석 등급 :</span>
              <div className="opt-buttons">
                {['일반실', '특실/우등'].map((opt) => (
                  <button
                    key={opt}
                    type="button"
                    className={`pref-btn ${seatPreference === opt ? 'active' : ''}`}
                    onClick={() => setSeatPreference(opt)}
                  >
                    {opt}
                  </button>
                ))}
              </div>
            </div>

            <button type="button" className="search-submit-btn" onClick={handleSearch}>
              <span>KTX 열차 조회</span>
              <span className="btn-arrow">→</span>
            </button>
          </div>
        </section>

        {/* Real-time Schedule Section */}
        <section className="train-schedule-section">
          <div className="section-head">
            <div>
              <h2>실시간 KTX 운행 현황</h2>
              <p className="section-subtitle">{departureStation} ➔ {arrivalStation} 구간의 주요 배차 현황입니다.</p>
            </div>
            <button 
              type="button" 
              className="more-btn"
              onClick={() => navigate('/schedule')} 
            >
              전체 시간표 확인
            </button>
          </div>
          
          <div className="preview-trains-grid">
            {previewTrains.map((train) => (
              <div key={train.id} className="preview-train-card">
                <div className="card-top">
                  <div className="train-badge-group">
                    <span className="badge-ktx">{train.type}</span>
                    <strong className="train-no">{train.name}</strong>
                  </div>
                  <span className={`status-pill ${train.status === '매진' ? 'soldout' : 'available'}`}>
                    {train.status}
                  </span>
                </div>
                
                <div className="card-time-row">
                  <div className="time-col">
                    <span className="time-val">{train.depTime}</span>
                    <span className="station-name">{departureStation}</span>
                  </div>
                  <div className="arrow-col">
                    <span className="duration-text">{train.duration}</span>
                    <div className="route-line"></div>
                  </div>
                  <div className="time-col">
                    <span className="time-val">{train.arrTime}</span>
                    <span className="station-name">{arrivalStation}</span>
                  </div>
                </div>

                <div className="card-bottom">
                  <div className="price-tag">
                    <span className="price-label">운임</span>
                    <strong className="price-num">{train.price}원</strong>
                  </div>
                  <button 
                    type="button" 
                    className="reserve-quick-btn"
                    disabled={train.status === '매진'}
                    onClick={() => handleDirectBooking(train)}
                  >
                    {train.status === '매진' ? '매진' : '바로예매'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Operational Information */}
        <div className="info-two-grid">
          <div className="info-card">
            <div className="card-header">
              <h3>KTX 운행 및 안전 공지</h3>
              <a href="#more" className="card-more">더보기</a>
            </div>
            <ul className="info-list">
              <li>
                <span className="list-date">2026.11.10</span>
                <span className="list-text">KTX-청룡 경부선 고속 운행 추가 배차 일정 안내</span>
              </li>
              <li>
                <span className="list-date">2026.11.01</span>
                <span className="list-text">동절기 선로 안전 점검에 따른 일부 열차 서행 안내</span>
              </li>
              <li>
                <span className="list-date">2026.10.20</span>
                <span className="list-text">KTX 특실 무료 어메니티 패키지 리뉴얼 안내</span>
              </li>
            </ul>
          </div>

          <div className="info-card">
            <div className="card-header">
              <h3>TAGO KTX 이용 안내</h3>
            </div>
            <div className="benefit-list">
              <div className="benefit-item">
                <span className="benefit-num">01</span>
                <div>
                  <strong>모바일 전자 승차권 발권</strong>
                  <p>예매 완료 즉시 QR 코드가 포함된 전자 승차권이 마이페이지에 자동 발권됩니다.</p>
                </div>
              </div>
              <div className="benefit-item">
                <span className="benefit-num">02</span>
                <div>
                  <strong>출발 3시간 전 무료 취소</strong>
                  <p>열차 출발 전까지 마이페이지에서 위약금 없이 간편하게 반환(취소)할 수 있습니다.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
