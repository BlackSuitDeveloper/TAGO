import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import client from '../Api/client';
import useWishStore from '../../store/useWishStore';
import './MainPage.css';

const DEFAULT_STATIONS = ['서울', '용산', '부산', '목포', '여수EXPO', '진주', '포항', '광주송정'];

function getLocalDateYMD(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatLocalDateYMD(timestamp) {
  if (!timestamp) return '';
  const d = new Date(timestamp);
  if (Number.isNaN(d.getTime())) return '';
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatTimeHHMM(timestamp) {
  if (!timestamp) return '--:--';
  const d = new Date(timestamp);
  if (Number.isNaN(d.getTime())) return '--:--';
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function calcDurationText(depTs, arrTs) {
  if (!depTs || !arrTs) return '-';
  const dep = new Date(depTs).getTime();
  const arr = new Date(arrTs).getTime();
  if (Number.isNaN(dep) || Number.isNaN(arr) || arr <= dep) return '-';
  const mins = Math.round((arr - dep) / 60000);
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
}

function mapPreviewSchedule(item) {
  const trainNoStr = String(item.trainno ?? '').padStart(3, '0');
  const availableCount = item.availableSeats ?? 0;
  return {
    id: item.schedule_seq,
    scheduleSeq: item.schedule_seq,
    name: `KTX ${trainNoStr}`,
    type: item.vehiclekndnm || 'KTX',
    depStationName: item.depStationName || item.depplacename || '-',
    arrStationName: item.arrStationName || item.arrplacename || '-',
    depTime: formatTimeHHMM(item.depplandtime),
    arrTime: formatTimeHHMM(item.arrplandtime),
    depDateRaw: formatLocalDateYMD(item.depplandtime),
    duration: calcDurationText(item.depplandtime, item.arrplandtime),
    price: Number(item.standard_price || 0).toLocaleString(),
    status: availableCount > 0 ? '예매가능' : '매진',
  };
}

export default function MainPage() {
  const todayStr = getLocalDateYMD(0);
  const [stationOptions, setStationOptions] = useState(DEFAULT_STATIONS);
  const [tripType, setTripType] = useState('편도');
  const [departureStation, setDepartureStation] = useState('서울');
  const [arrivalStation, setArrivalStation] = useState('부산');
  const [departureDate, setDepartureDate] = useState(todayStr);
  const [returnDate, setReturnDate] = useState(() => getLocalDateYMD(1));
  const [hasSelectedCondition, setHasSelectedCondition] = useState(false);
  const [isLoadingTrains, setIsLoadingTrains] = useState(false);
  const navigate = useNavigate();

  const handleDepartureDateChange = (nextDate) => {
    const safeDate = nextDate && nextDate < todayStr ? todayStr : nextDate;
    setDepartureDate(safeDate);
    setHasSelectedCondition(true);
    if (safeDate && returnDate && returnDate < safeDate) {
      setReturnDate(safeDate);
    }
  };

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
    setHasSelectedCondition(true);
    if (arrivalStation === newDep) {
      const fallback = stationOptions.find((st) => st !== newDep) || '부산';
      setArrivalStation(fallback);
    }
  };

  const handleArrivalStationChange = (newArr) => {
    setArrivalStation(newArr);
    setHasSelectedCondition(true);
    if (departureStation === newArr) {
      const fallback = stationOptions.find((st) => st !== newArr) || '서울';
      setDepartureStation(fallback);
    }
  };

  const handleSwapStations = () => {
    const temp = departureStation;
    setDepartureStation(arrivalStation);
    setArrivalStation(temp);
    setHasSelectedCondition(true);
  };

  const handleSearch = () => {
    navigate('/schedule', {
      state: {
        departureStation,
        arrivalStation,
        departureDate,
        returnDate: tripType === '왕복' ? returnDate : null,
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
    const formattedDate = formatDepartureDate(train.depDateRaw || departureDate);
    navigate('/reservation', {
      state: {
        scheduleSeq: train.scheduleSeq || train.id || 1,
        trainName: train.name,
        trainType: 'KTX',
        depTime: train.depTime,
        arrTime: train.arrTime,
        duration: train.duration,
        depStation: train.depStationName || departureStation,
        arrStation: train.arrStationName || arrivalStation,
        depDate: formattedDate,
        returnDate: tripType === '왕복' ? formatDepartureDate(returnDate) : null,
        tripType,
        passenger: passengerSummaryText,
        passengerDetail: passengers,
        seatType: '일반실',
        price: train.price,
      }
    });
  };

  // 찜하기 중앙 스토어 연동 (메인·시각표·마이페이지 공통)
  const { fetchWishList, toggleWish, isWish } = useWishStore();

  useEffect(() => {
    fetchWishList();
  }, [fetchWishList]);

  useEffect(() => {
    client
      .get('/station')
      .then((res) => {
        if (Array.isArray(res.data) && res.data.length > 0) {
          const names = Array.from(new Set(res.data.map((st) => st.nodename).filter(Boolean)));
          if (names.length > 0) {
            setStationOptions(names);
          }
        }
      })
      .catch(() => {});
  }, []);

  const [previewTrains, setPreviewTrains] = useState([]);

  useEffect(() => {
    let cancelled = false;
    const loadPreviewSchedules = async () => {
      setIsLoadingTrains(true);
      try {
        let rows = [];
        if (hasSelectedCondition) {
          const res = await client.get('/schedule', {
            params: {
              depStation: departureStation,
              arrStation: arrivalStation,
              date: departureDate,
              page: 1,
              size: 5,
            },
          });
          rows = Array.isArray(res.data?.schedules) ? res.data.schedules.map(mapPreviewSchedule) : [];
        } else {
          // 기본: 현재 시각 기준 가장 최신(곧 출발하는) 스케줄 5개
          const res = await client.get('/schedule', {
            params: { page: 1, size: 5, upcoming: true },
          });
          rows = Array.isArray(res.data?.schedules) ? res.data.schedules.map(mapPreviewSchedule) : [];
          if (rows.length === 0) {
            const fallbackRes = await client.get('/schedule', {
              params: { page: 1, size: 5 },
            });
            rows = Array.isArray(fallbackRes.data?.schedules)
              ? fallbackRes.data.schedules.map(mapPreviewSchedule)
              : [];
          }
        }

        if (!cancelled) {
          setPreviewTrains(rows.slice(0, 5));
        }
      } catch (err) {
        if (!cancelled) {
          setPreviewTrains([]);
        }
      } finally {
        if (!cancelled) {
          setIsLoadingTrains(false);
        }
      }
    };

    loadPreviewSchedules();
    return () => {
      cancelled = true;
    };
  }, [hasSelectedCondition, departureStation, arrivalStation, departureDate]);

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
              {stationOptions
                .filter((st) => st !== departureStation)
                .map((st) => (
                  <button
                    type="button"
                    key={st}
                    className={`chip-btn ${hasSelectedCondition && arrivalStation === st ? 'selected' : ''}`}
                    onClick={() => handleArrivalStationChange(st)}
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
                  {stationOptions.map((st) => (
                    <option key={st} value={st}>{st}</option>
                  ))}
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
                  onChange={(e) => handleArrivalStationChange(e.target.value)}
                  className="station-select"
                >
                  {stationOptions.map((st) => (
                    <option key={st} value={st}>{st}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="input-group">
              <span className="input-label">출발일</span>
              <div className="input-field-box">
                <input 
                  type="date" 
                  value={departureDate} 
                  min={todayStr}
                  onChange={(e) => handleDepartureDateChange(e.target.value)}
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
                    <input
                      type="date"
                      value={returnDate}
                      min={departureDate || todayStr}
                      onChange={(e) => setReturnDate(e.target.value)}
                      className="date-input"
                    />
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
              <p className="section-subtitle">
                {hasSelectedCondition
                  ? `${departureStation} ➔ ${arrivalStation} (${departureDate}) 구간의 주요 배차 현황입니다. (최대 5건)`
                  : '현재 시각 기준 곧 출발하는 최신 KTX 열차 5편성입니다.'}
              </p>
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
            {isLoadingTrains ? (
              <div className="empty-train-message">
                <p>실시간 KTX 운행 정보를 불러오는 중입니다...</p>
              </div>
            ) : previewTrains && previewTrains.length > 0 ? (
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
                      <span className="station-name">{train.depStationName}</span>
                    </div>
                    <div className="arrow-col">
                      <span className="duration-text">{train.duration}</span>
                      <div className="route-line"></div>
                    </div>
                    <div className="time-col">
                      <span className="time-val">{train.arrTime}</span>
                      <span className="station-name">{train.arrStationName}</span>
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
                        className={`btn-favorite-heart ${isWish(train.scheduleSeq || train.id) ? 'active' : ''}`}
                        onClick={() => toggleWish(train.scheduleSeq || train.id)}
                        title={isWish(train.scheduleSeq || train.id) ? '찜 해제' : '열차 찜하기'}
                        aria-label="열차 찜하기"
                      >
                        {isWish(train.scheduleSeq || train.id) ? '♥' : '♡'}
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
