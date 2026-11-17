import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import './MainPage.css';

export default function MainPage() {
  const quickStations = ['서울', '용산', '광명', '대전', '동대구', '부산', '광주송정', '강릉'];
  const [tripType, setTripType] = useState('편도');
  const [departureStation, setDepartureStation] = useState('서울');
  const [arrivalStation, setArrivalStation] = useState('부산');
  const [departureDate, setDepartureDate] = useState('2026-11-16');
  const navigate = useNavigate();

  const [passengers, setPassengers] = useState({
    adult: 1,
  });

  const [isPassengerOpen, setIsPassengerOpen] = useState(false);
  const passengerRef = useRef(null);

  useEffect(() => {
    function handleClickOutside(event) {
      if (passengerRef.current && !passengerRef.current.contains(event.target)) {
        setIsPassengerOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const totalPassengerCount = passengers.adult;

  const handlePassengerChange = (delta) => {
    setPassengers((prev) => {
      const nextVal = prev.adult + delta;
      if (nextVal > 9) {
        alert('승차권은 1회 최대 9명까지 예매하실 수 있습니다.');
        return prev;
      }
      if (nextVal < 1) {
        alert('최소 1명 이상의 승객을 선택해야 합니다.');
        return prev;
      }
      return { adult: nextVal };
    });
  };

  const passengerSummaryText = `어른 ${passengers.adult}명`;

  const handleDepartureStationChange = (newDep) => {
    setDepartureStation(newDep);
    if (arrivalStation === newDep) {
      const fallback = quickStations.find((st) => st !== newDep) || '부산';
      setArrivalStation(fallback);
    }
  };

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
        passengerCount: passengerSummaryText,
        passengerDetail: passengers,
        tripType,
      }
    });
  };

  const formatDepartureDate = (dateStr) => {
    if (!dateStr) return '2026-11-16 (월)';
    const match = dateStr.match(/(\d{4})-(\d{2})-(\d{2})/);
    if (!match) return dateStr;
    const days = ['일', '월', '화', '수', '목', '금', '토'];
    const d = new Date(parseInt(match[1], 10), parseInt(match[2], 10) - 1, parseInt(match[3], 10));
    return `${dateStr} (${days[d.getDay()]})`;
  };

  const handleGoToScheduleBooking = (train) => {
    const formattedDate = formatDepartureDate(departureDate);
    navigate('/reservation', {
      state: {
        trainName: train.name,
        trainType: 'KTX',
        depTime: train.depTime,
        arrTime: train.arrTime,
        duration: train.duration,
        depStation: departureStation,
        arrStation: arrivalStation,
        depDate: formattedDate,
        passenger: passengerSummaryText,
        passengerDetail: passengers,
        seatType: '일반실',
        price: train.price,
      }
    });
  };

  const [favorites, setFavorites] = useState(() => {
    const saved = localStorage.getItem('tago_favorite_trains');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        return [];
      }
    }
    return [
      {
        id: 'fav-1',
        name: 'KTX 001',
        type: 'KTX',
        depTime: '05:15',
        arrTime: '07:42',
        duration: '2시간 27분',
        depStation: '서울',
        arrStation: '부산',
        price: '59,800',
      }
    ];
  });

  const handleToggleFavorite = (train) => {
    setFavorites((prev) => {
      const exists = prev.some((item) => item.name === train.name);
      let next;
      if (exists) {
        next = prev.filter((item) => item.name !== train.name);
      } else {
        const newFav = {
          id: `fav-${Date.now()}`,
          name: train.name,
          type: 'KTX',
          depTime: train.depTime,
          arrTime: train.arrTime,
          duration: train.duration,
          depStation: departureStation,
          arrStation: arrivalStation,
          price: train.price,
        };
        next = [...prev, newFav];
      }
      localStorage.setItem('tago_favorite_trains', JSON.stringify(next));
      return next;
    });
  };

  const [previewTrains, setPreviewTrains] = useState([
    {
      id: 1,
      name: 'KTX 001',
      type: 'KTX',
      depTime: '05:15',
      arrTime: '07:42',
      duration: '2시간 27분',
      price: '59,800',
      status: '예매가능',
    },
  ]);

  return (
    <div className="main-page-wrapper">
      <Header showTopBar={true} />

      <section className="rail-hero">
        <div className="hero-content">
          <span className="hero-tag">TAGO KTX 공식 예매</span>
          <h1 className="hero-title">더 빠르고 정밀한 고속철도 여정</h1>
          <p className="hero-subtitle">전국 KTX 전 노선의 실시간 운행 시각표를 확인하고 간편하게 예매하세요.</p>
        </div>
      </section>

      <main className="main-container">
        <section className="booking-panel ticket-booking-card">
          <div className="booking-header-row booking-top-nav">
            <div className="booking-nav-tabs trip-type-selector">
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

            <div className="station-quick-select">
              <span className="quick-label">주요 정차역</span>
              {quickStations
                .filter((st) => st !== departureStation)
                .map((st) => (
                  <button
                    type="button"
                    key={st}
                    className={`chip-btn ${arrivalStation === st ? 'selected' : ''}`}
                    onClick={() => setArrivalStation(st)}
                  >
                    {st}
                  </button>
                ))}
            </div>
          </div>

          <div className="station-route-grid">
            <div className="input-group">
              <span className="input-label">출발역</span>
              <div className="input-field-box">
                <select 
                  value={departureStation} 
                  onChange={(e) => handleDepartureStationChange(e.target.value)}
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

            <div className="input-group passenger-input-group" ref={passengerRef}>
              <span className="input-label">승객 인원</span>
              <div 
                className={`input-field-box passenger-trigger-btn ${isPassengerOpen ? 'active' : ''}`}
                onClick={() => setIsPassengerOpen((prev) => !prev)}
              >
                <span className="passenger-summary-text">{passengerSummaryText}</span>
                <span className="passenger-chevron">{isPassengerOpen ? '▲' : '▼'}</span>
              </div>

              {isPassengerOpen && (
                <div className="passenger-popover-panel">
                  <div className="popover-header">
                    <h4>인원 선택</h4>
                    <span className="max-hint">1회 최대 9명</span>
                  </div>

                  <div className="passenger-category-list">
                    <div className="passenger-row">
                      <div className="category-info">
                        <strong>어른</strong>
                        <span className="category-desc">만 19세 이상</span>
                      </div>
                      <div className="stepper-box">
                        <button
                          type="button"
                          className="step-btn"
                          disabled={passengers.adult <= 1}
                          onClick={() => handlePassengerChange(-1)}
                        >
                          -
                        </button>
                        <span className="step-count">{passengers.adult}</span>
                        <button
                          type="button"
                          className="step-btn"
                          disabled={passengers.adult >= 9}
                          onClick={() => handlePassengerChange(1)}
                        >
                          +
                        </button>
                      </div>
                    </div>
                  </div>

                  <div className="popover-footer">
                    <span className="footer-summary">총 <strong>{passengers.adult}명</strong></span>
                    <button 
                      type="button" 
                      className="btn-popover-close"
                      onClick={() => setIsPassengerOpen(false)}
                    >
                      선택 완료
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          {tripType === '왕복' && (
            <div className="return-trip-row">
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
                    <input type="text" readOnly value={passengerSummaryText} className="readonly-input" />
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className="booking-footer-row">
            <button type="button" className="search-submit-btn" onClick={handleSearch}>
              <span>KTX 열차 조회</span>
              <span className="btn-arrow">→</span>
            </button>
          </div>
        </section>

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
            {previewTrains && previewTrains.length > 0 ? (
              previewTrains.map((train) => (
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
                    <div className="card-btn-group">
                      <button 
                        type="button" 
                        className={`btn-favorite-heart ${favorites.some((f) => f.name === train.name) ? 'active' : ''}`}
                        onClick={() => handleToggleFavorite(train)}
                        title={favorites.some((f) => f.name === train.name) ? '찜 해제' : '열차 찜하기'}
                        aria-label="열차 찜하기"
                      >
                        {favorites.some((f) => f.name === train.name) ? '♥' : '♡'}
                      </button>
                      <button 
                        type="button" 
                        className="reserve-quick-btn"
                        disabled={train.status === '매진'}
                        onClick={() => handleGoToScheduleBooking(train)}
                      >
                        {train.status === '매진' ? '매진' : '예매'}
                      </button>
                    </div>
                  </div>
                </div>
              ))
            ) : (
              <div className="empty-train-message">
                <p>실시간 KTX 운행 정보를 불러오는 중이거나 조회된 열차가 없습니다.</p>
              </div>
            )}
          </div>
        </section>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
