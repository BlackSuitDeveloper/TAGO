import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import useWishStore from '../../store/useWishStore';
import './SchedulePage.css';

const STATION_OPTIONS = ['서울', '용산', '광명', '대전', '동대구', '부산', '광주송정', '강릉', '포항'];

function normalizeDateInput(dateStr) {
  if (!dateStr) return '2026-11-16';
  const match = String(dateStr).match(/(\d{4}-\d{2}-\d{2})/);
  return match ? match[1] : '2026-11-16';
}

function formatDateWithDay(dateStr) {
  const normalized = normalizeDateInput(dateStr);
  const [y, m, d] = normalized.split('-').map(Number);
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const dateObj = new Date(y, m - 1, d);
  return `${normalized} (${days[dateObj.getDay()]})`;
}

export default function SchedulePage() {
  const navigate = useNavigate();
  const location = useLocation();

  const [tripType, setTripType] = useState(location.state?.tripType || '편도');
  const [depStation, setDepStation] = useState(location.state?.departureStation || '서울');
  const [arrStation, setArrStation] = useState(location.state?.arrivalStation || '부산');
  const [currentDate, setCurrentDate] = useState(() => normalizeDateInput(location.state?.departureDate));
  const [returnDate, setReturnDate] = useState(() => normalizeDateInput(location.state?.returnDate || '2026-11-17'));
  const [passengers, setPassengers] = useState(() => location.state?.passengerDetail || { adult: 1 });
  const [isPassengerOpen, setIsPassengerOpen] = useState(false);
  const passengerRef = useRef(null);

  const targetTrainName = location.state?.targetTrainName || null;
  const [selectedFilter, setSelectedFilter] = useState('ALL');

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

  const passengerSummaryText = `어른 ${passengers.adult}명`;

  const handleDepartureStationChange = (newDep) => {
    setDepStation(newDep);
    if (arrStation === newDep) {
      const fallback = STATION_OPTIONS.find((st) => st !== newDep) || '부산';
      setArrivalStationSafe(fallback);
    }
  };

  const setArrivalStationSafe = (nextArr) => {
    setArrStation(nextArr);
  };

  const handleArrivalStationChange = (newArr) => {
    setArrStation(newArr);
    if (depStation === newArr) {
      const fallback = STATION_OPTIONS.find((st) => st !== newArr) || '서울';
      setDepStation(fallback);
    }
  };

  const handleSwapStations = () => {
    setDepStation(arrStation);
    setArrStation(depStation);
  };

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

  // 찜하기 중앙 스토어 연동 (메인·시각표·마이페이지 공통)
  const { fetchWishList, toggleWish, isWish } = useWishStore();

  useEffect(() => {
    fetchWishList();
  }, [fetchWishList]);

  const [scheduleList, setScheduleList] = useState([
    {
      id: 1,
      trainNo: '001',
      name: 'KTX 001',
      type: 'KTX',
      depTime: '05:15',
      arrTime: '07:42',
      duration: '2시간 27분',
      generalPrice: '59,800',
      generalStatus: '예매가능',
    },
  ]);

  const handleSearchSchedule = () => {
    setIsPassengerOpen(false);
    // 추후 GET /api/schedule 연동 시 depStation, arrStation, currentDate 조건으로 재조회
  };

  const filteredList = scheduleList.filter((item) => {
    if (selectedFilter === 'AVAILABLE') return item.generalStatus === '예매가능';
    if (selectedFilter === 'MORNING') {
      const hour = parseInt(item.depTime.split(':')[0], 10);
      return hour < 12;
    }
    if (selectedFilter === 'AFTERNOON') {
      const hour = parseInt(item.depTime.split(':')[0], 10);
      return hour >= 12;
    }
    return true;
  });

  const handleSelectTrain = (train) => {
    navigate('/reservation', {
      state: {
        trainName: train.name,
        trainType: 'KTX',
        depTime: train.depTime,
        arrTime: train.arrTime,
        duration: train.duration,
        depStation,
        arrStation,
        depDate: formatDateWithDay(currentDate),
        returnDate: tripType === '왕복' ? formatDateWithDay(returnDate) : null,
        tripType,
        passenger: passengerSummaryText,
        passengerDetail: passengers,
        seatType: '일반실',
        price: train.generalPrice,
      }
    });
  };

  return (
    <div className="schedule-page-wrapper">
      <Header showTopBar={true} />

      <main className="schedule-container">
        <section className="schedule-search-bar">
          <div className="schedule-search-top">
            <div className="schedule-trip-tabs">
              <button
                type="button"
                className={`schedule-trip-tab ${tripType === '편도' ? 'active' : ''}`}
                onClick={() => setTripType('편도')}
              >
                편도
              </button>
              <button
                type="button"
                className={`schedule-trip-tab ${tripType === '왕복' ? 'active' : ''}`}
                onClick={() => setTripType('왕복')}
              >
                왕복
              </button>
            </div>
            <span className="tag-ktx">KTX 전용</span>
          </div>

          <div className="schedule-search-grid">
            <div className="schedule-input-group">
              <span className="schedule-input-label">출발역</span>
              <div className="schedule-field-box">
                <select
                  value={depStation}
                  onChange={(e) => handleDepartureStationChange(e.target.value)}
                  className="schedule-select"
                >
                  {STATION_OPTIONS.map((station) => (
                    <option key={station} value={station}>{station}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="schedule-swap-cell">
              <button
                type="button"
                className="schedule-swap-btn"
                onClick={handleSwapStations}
                title="출발/도착역 전환"
              >
                ⇄
              </button>
            </div>

            <div className="schedule-input-group">
              <span className="schedule-input-label">도착역</span>
              <div className="schedule-field-box">
                <select
                  value={arrStation}
                  onChange={(e) => handleArrivalStationChange(e.target.value)}
                  className="schedule-select"
                >
                  {STATION_OPTIONS.map((station) => (
                    <option key={station} value={station}>{station}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="schedule-input-group">
              <span className="schedule-input-label">출발일</span>
              <div className="schedule-field-box">
                <input
                  type="date"
                  value={currentDate}
                  onChange={(e) => setCurrentDate(e.target.value)}
                  className="schedule-date-input"
                />
              </div>
            </div>

            <div className="schedule-input-group schedule-passenger-group" ref={passengerRef}>
              <span className="schedule-input-label">승객 인원</span>
              <div
                className={`schedule-field-box schedule-passenger-trigger ${isPassengerOpen ? 'active' : ''}`}
                onClick={() => setIsPassengerOpen((prev) => !prev)}
              >
                <span className="passenger-summary-text">{passengerSummaryText}</span>
                <span className="passenger-chevron">{isPassengerOpen ? '▲' : '▼'}</span>
              </div>

              {isPassengerOpen && (
                <div className="schedule-passenger-popover">
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

            <div className="schedule-search-action">
              <button
                type="button"
                className="schedule-submit-btn"
                onClick={handleSearchSchedule}
              >
                열차 조회
              </button>
            </div>
          </div>

          {tripType === '왕복' && (
            <div className="schedule-return-row">
              <div className="schedule-search-grid return-grid">
                <div className="schedule-input-group">
                  <span className="schedule-input-label">귀가 출발역</span>
                  <div className="schedule-field-box disabled">
                    <input type="text" readOnly value={arrStation} className="schedule-readonly-input" />
                  </div>
                </div>
                <div className="schedule-swap-cell">
                  <span className="arrow-static">➔</span>
                </div>
                <div className="schedule-input-group">
                  <span className="schedule-input-label">귀가 도착역</span>
                  <div className="schedule-field-box disabled">
                    <input type="text" readOnly value={depStation} className="schedule-readonly-input" />
                  </div>
                </div>
                <div className="schedule-input-group">
                  <span className="schedule-input-label">귀가일</span>
                  <div className="schedule-field-box">
                    <input
                      type="date"
                      value={returnDate}
                      min={currentDate}
                      onChange={(e) => setReturnDate(e.target.value)}
                      className="schedule-date-input"
                    />
                  </div>
                </div>
                <div className="schedule-input-group">
                  <span className="schedule-input-label">인원</span>
                  <div className="schedule-field-box disabled">
                    <input type="text" readOnly value={passengerSummaryText} className="schedule-readonly-input" />
                  </div>
                </div>
              </div>
            </div>
          )}
        </section>

        <div className="schedule-filter-row">
          <div className="filter-buttons">
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'ALL' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('ALL')}
            >
              전체 열차 ({scheduleList.length})
            </button>
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'AVAILABLE' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('AVAILABLE')}
            >
              예매 가능만
            </button>
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'MORNING' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('MORNING')}
            >
              오전 출발 (~12:00)
            </button>
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'AFTERNOON' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('AFTERNOON')}
            >
              오후 출발 (12:00~)
            </button>
          </div>
        </div>

        {targetTrainName && (
          <div className="target-train-notice">
            <span className="notice-badge">선택 열차 안내</span>
            <span className="notice-text">
              메인에서 선택하신 <strong>[{targetTrainName}]</strong> 열차입니다. [좌석선택 및 예매]를 눌러 좌석을 지정해 주세요.
            </span>
          </div>
        )}

        <div className="schedule-table-head">
          <span className="col-train">열차 번호 (KTX)</span>
          <span className="col-time">출발 / 도착 시각</span>
          <span className="col-duration">소요시간</span>
          <span className="col-class-single">일반실 운임 및 좌석 예매</span>
        </div>

        <div className="schedule-list">
          {filteredList.length > 0 ? (
            filteredList.map((item) => {
              const isGeneralSoldout = item.generalStatus === '매진';
              const isTarget = targetTrainName === item.name;

              return (
                <div key={item.id} className={`schedule-row-card ${isTarget ? 'highlighted' : ''}`}>
                  <div className="col-train">
                    <span className="badge-ktx-gen1">KTX</span>
                    <div className="train-id-box">
                      <strong className="train-title">{item.name}</strong>
                      <span className="train-sub-desc">1~18호차 일반실</span>
                    </div>
                  </div>

                  <div className="col-time">
                    <div className="time-block">
                      <span className="time-text dep">{item.depTime}</span>
                      <span className="station-text">{depStation}</span>
                    </div>
                    <div className="time-arrow">➔</div>
                    <div className="time-block">
                      <span className="time-text arr">{item.arrTime}</span>
                      <span className="station-text">{arrStation}</span>
                    </div>
                  </div>

                  <div className="col-duration">
                    <span className="duration-pill">{item.duration}</span>
                  </div>

                  <div className="col-class-single">
                    <button 
                      type="button" 
                      className={`btn-schedule-favorite ${isWish(item.scheduleSeq || item.id) ? 'active' : ''}`}
                      onClick={() => toggleWish(item.scheduleSeq || item.id)}
                      title={isWish(item.scheduleSeq || item.id) ? '찜 해제' : '열차 찜하기'}
                      aria-label="열차 찜하기"
                    >
                      {isWish(item.scheduleSeq || item.id) ? '♥' : '♡'}
                    </button>
                    <button 
                      type="button" 
                      className={`ktx-booking-btn ${isGeneralSoldout ? 'soldout' : 'available'}`}
                      disabled={isGeneralSoldout}
                      onClick={() => handleSelectTrain(item)}
                    >
                      <div className="fare-badge-wrap">
                        <span className="fare-class-tag">일반실</span>
                        <strong className="fare-price-text">{item.generalPrice}원</strong>
                      </div>
                      <div className="booking-action-wrap">
                        <span className="action-label-pill">
                          {isGeneralSoldout ? '매진' : '좌석선택 →'}
                        </span>
                      </div>
                    </button>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="schedule-empty-box">
              <p>조회된 KTX 운행 시각표가 없습니다.</p>
              <span className="empty-sub">출발일 또는 여정을 변경하여 다시 조회해 주세요.</span>
            </div>
          )}
        </div>

        <div className="schedule-notice-box">
          <h4>TAGO KTX 승차권 예매 안내</h4>
          <ul>
            <li>TAGO는 KTX(20량 편성, 전 차량 일반실) 직통 열차를 기준으로 실시간 좌석 배정 및 예매를 제공합니다.</li>
            <li>[좌석선택 →] 버튼을 누르면 1호차부터 18호차까지 원하는 좌석을 직접 지정하여 결제하실 수 있습니다.</li>
          </ul>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
