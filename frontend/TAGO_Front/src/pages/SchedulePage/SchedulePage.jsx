import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import useWishStore from '../../store/useWishStore';
import client from '../Api/client';
import errorText from '../Api/errorText';
import { dialog, notify } from '../../components/Dialog/dialogService';
import './SchedulePage.css';

const DEFAULT_STATIONS = ['서울', '용산', '광명', '대전', '동대구', '부산', '광주송정', '강릉', '포항'];

function normalizeDateInput(dateStr) {
  if (!dateStr) return '';
  const match = String(dateStr).match(/(\d{4}-\d{2}-\d{2})/);
  return match ? match[1] : '';
}

function formatDateWithDay(dateStr) {
  const normalized = normalizeDateInput(dateStr) || '2026-11-16';
  const [y, m, d] = normalized.split('-').map(Number);
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const dateObj = new Date(y, m - 1, d);
  return `${normalized} (${days[dateObj.getDay()]})`;
}

function formatLocalDateYMD(timestamp) {
  if (!timestamp) return '';
  const dateObj = new Date(timestamp);
  if (Number.isNaN(dateObj.getTime())) return normalizeDateInput(timestamp);
  const y = dateObj.getFullYear();
  const m = String(dateObj.getMonth() + 1).padStart(2, '0');
  const d = String(dateObj.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatTimeHHMM(timestamp) {
  if (!timestamp) return '--:--';
  const dateObj = new Date(timestamp);
  if (Number.isNaN(dateObj.getTime())) return '--:--';
  const hh = String(dateObj.getHours()).padStart(2, '0');
  const mm = String(dateObj.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

function calcDurationText(depTimestamp, arrTimestamp) {
  if (!depTimestamp || !arrTimestamp) return '-';
  const dep = new Date(depTimestamp).getTime();
  const arr = new Date(arrTimestamp).getTime();
  if (Number.isNaN(dep) || Number.isNaN(arr) || arr <= dep) return '-';
  const totalMinutes = Math.round((arr - dep) / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}시간 ${minutes}분` : `${minutes}분`;
}

function mapScheduleItem(item) {
  const trainNoStr = String(item.trainNo ?? '').padStart(3, '0');
  const availableCount = item.availableSeats ?? 0;

  return {
    id: item.scheduleSeq,
    trainNo: trainNoStr,
    name: `KTX ${trainNoStr}`,
    type: item.vehicleKndNm || 'KTX',
    depStationName: item.depStationName || item.depPlaceName || '-',
    arrStationName: item.arrStationName || item.arrPlaceName || '-',
    depTime: formatTimeHHMM(item.depPlandTime),
    arrTime: formatTimeHHMM(item.arrPlandTime),
    duration: calcDurationText(item.depPlandTime, item.arrPlandTime),
    generalPrice: Number(item.standardPrice || 0).toLocaleString(),
    generalStatus: availableCount > 0 ? '예매가능' : '매진',
    availableSeats: availableCount,
    depDateRaw: item.depPlandTime ? formatLocalDateYMD(item.depPlandTime) : '',
  };
}

function getTodayYMD(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export default function SchedulePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const todayStr = getTodayYMD(0);

  const [stationOptions, setStationOptions] = useState(DEFAULT_STATIONS);
  const [tripType, setTripType] = useState(location.state?.tripType || '편도');
  const [depStation, setDepStation] = useState(location.state?.departureStation || '');
  const [arrStation, setArrStation] = useState(location.state?.arrivalStation || '');
  const [currentDate, setCurrentDate] = useState(() => {
    const init = normalizeDateInput(location.state?.departureDate);
    return init && init < todayStr ? todayStr : init;
  });
  const [returnDate, setReturnDate] = useState(() => {
    const init = normalizeDateInput(location.state?.returnDate) || getTodayYMD(1);
    return init < todayStr ? getTodayYMD(1) : init;
  });

  const handleCurrentDateChange = (nextDate) => {
    const safeDate = nextDate && nextDate < todayStr ? todayStr : nextDate;
    setCurrentDate(safeDate);
    if (safeDate && returnDate && returnDate < safeDate) {
      setReturnDate(safeDate);
    }
  };
  const [passengers, setPassengers] = useState(() => location.state?.passengerDetail || { adult: 1 });
  const [isPassengerOpen, setIsPassengerOpen] = useState(false);
  const passengerRef = useRef(null);

  const targetTrainName = location.state?.targetTrainName || null;
  const [selectedFilter, setSelectedFilter] = useState('ALL');
  const [page, setPage] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(false);

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

  useEffect(() => {
    client
      .get('/station')
      .then((res) => {
        if (Array.isArray(res.data) && res.data.length > 0) {
          const names = Array.from(
            new Set(res.data.map((st) => st.nodeName).filter(Boolean))
          );
          if (names.length > 0) {
            setStationOptions(names);
          }
        }
      })
      .catch(() => {
        // 백엔드 미연결 시 기본 역 목록 유지
      });
  }, []);

  const passengerSummaryText = `어른 ${passengers.adult}명`;

  const handleDepartureStationChange = (newDep) => {
    setDepStation(newDep);
    if (newDep && arrStation === newDep) {
      setArrStation('');
    }
  };

  const handleArrivalStationChange = (newArr) => {
    setArrStation(newArr);
    if (newArr && depStation === newArr) {
      setDepStation('');
    }
  };

  const handleSwapStations = () => {
    setDepStation(arrStation);
    setArrStation(depStation);
  };

  const handlePassengerChange = (delta) => {
    const nextVal = passengers.adult + delta;
    if (nextVal > 9) {
      notify.warning('승차권은 1회 최대 9명까지 예매하실 수 있습니다.');
      return;
    }
    if (nextVal < 1) {
      notify.warning('최소 1명 이상의 승객을 선택해야 합니다.');
      return;
    }
    setPassengers({ adult: nextVal });
  };

  // 찜하기 중앙 스토어 연동 (메인·시각표·마이페이지 공통)
  const { fetchWishList, toggleWish, isWish } = useWishStore();

  useEffect(() => {
    fetchWishList();
  }, [fetchWishList]);

  const [scheduleList, setScheduleList] = useState([]);

  const fetchSchedules = useCallback(
    async ({ dep, arr, date, filter, targetPage }) => {
      setIsLoading(true);
      try {
        const params = {
          filter,
          page: targetPage,
          size: 10,
          upcoming: true,
        };
        if (dep) params.depStation = dep;
        if (arr) params.arrStation = arr;
        if (date) params.date = date;

        const res = await client.get('/schedule', { params });
        const rows = Array.isArray(res.data) ? res.data.map(mapScheduleItem) : [];
        const total = res.totalCount ?? rows.length;

        setScheduleList(rows);
        setTotalCount(total);
        setTotalPages(Math.ceil(total / params.size));
      } catch (err) {
        setScheduleList([]);
        setTotalCount(0);
        setTotalPages(0);
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    fetchSchedules({
      dep: depStation,
      arr: arrStation,
      date: currentDate,
      filter: selectedFilter,
      targetPage: page,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedFilter, page, fetchSchedules]);

  const handleSearchSchedule = () => {
    setIsPassengerOpen(false);
    if (page !== 1) {
      setPage(1);
    } else {
      fetchSchedules({
        dep: depStation,
        arr: arrStation,
        date: currentDate,
        filter: selectedFilter,
        targetPage: 1,
      });
    }
  };

  const handleFilterChange = (nextFilter) => {
    setSelectedFilter(nextFilter);
    setPage(1);
  };

  const [queueModal, setQueueModal] = useState(null); // { train, queueToken, queueRank }
  const queuePollTimerRef = useRef(null);

  const clearQueuePolling = useCallback(() => {
    if (queuePollTimerRef.current) {
      clearInterval(queuePollTimerRef.current);
      queuePollTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => clearQueuePolling();
  }, [clearQueuePolling]);

  const goToReservation = useCallback((train, queueToken) => {
    if (queueToken && train?.id) {
      sessionStorage.setItem(`tago_queue_token_${train.id}`, queueToken);
    }
    navigate('/reservation', {
      state: {
        scheduleSeq: train.id,
        queueToken: queueToken || null,
        trainName: train.name,
        trainType: 'KTX',
        depTime: train.depTime,
        arrTime: train.arrTime,
        duration: train.duration,
        depStation: train.depStationName,
        arrStation: train.arrStationName,
        depDate: formatDateWithDay(train.depDateRaw || currentDate),
        returnDate: tripType === '왕복' ? formatDateWithDay(returnDate) : null,
        tripType,
        passenger: passengerSummaryText,
        passengerDetail: passengers,
        seatType: '일반실',
        price: train.generalPrice,
      }
    });
  }, [navigate, currentDate, tripType, returnDate, passengerSummaryText, passengers]);

  const handleSelectTrain = async (train) => {
    clearQueuePolling();
    try {
      const res = await client.post('/queue', { scheduleSeq: train.id });
      const data = res.data || {};
      if (data.isAvailable) {
        goToReservation(train, data.queueToken);
        return;
      }

      const token = data.queueToken;
      setQueueModal({ train, queueToken: token, queueRank: data.queueRank ?? 1 });

      queuePollTimerRef.current = setInterval(async () => {
        try {
          const pollRes = await client.get(`/queue/${token}`);
          const pollData = pollRes.data || {};
          if (pollData.isAvailable) {
            clearQueuePolling();
            setQueueModal(null);
            goToReservation(train, token);
          } else {
            setQueueModal((prev) => prev ? { ...prev, queueRank: pollData.queueRank ?? prev.queueRank } : null);
          }
        } catch (pollErr) {
          console.error('대기열 순번 조회 실패:', pollErr);
          const status = pollErr.response?.status;
          if (status === 400 || status === 403 || status === 503) {
            clearQueuePolling();
            setQueueModal(null);
            dialog.alert(errorText(pollErr, '대기열 연결이 만료되었습니다. 다시 시도해 주세요.'), { title: '대기열 안내', tone: 'warning' });
          }
        }
      }, 3000);
    } catch (err) {
      if (err.response?.status === 401) {
        dialog.alert('로그인 후 승차권 예매가 가능합니다.', { title: '로그인 필요' }).then(() => navigate('/login'));
        return;
      }
      dialog.alert(errorText(err, '대기열 진입에 실패했습니다. 잠시 후 다시 시도해 주세요.'), { title: '대기열 안내', tone: 'error' });
    }
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
                  <option value="">전체</option>
                  {stationOptions.map((station) => (
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
                  <option value="">전체</option>
                  {stationOptions.map((station) => (
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
                  min={todayStr}
                  onChange={(e) => handleCurrentDateChange(e.target.value)}
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
                      min={currentDate || todayStr}
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
              onClick={() => handleFilterChange('ALL')}
            >
              전체 열차 ({totalCount})
            </button>
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'AVAILABLE' ? 'active' : ''}`}
              onClick={() => handleFilterChange('AVAILABLE')}
            >
              예매 가능만
            </button>
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'MORNING' ? 'active' : ''}`}
              onClick={() => handleFilterChange('MORNING')}
            >
              오전 출발 (~12:00)
            </button>
            <button
              type="button"
              className={`filter-btn ${selectedFilter === 'AFTERNOON' ? 'active' : ''}`}
              onClick={() => handleFilterChange('AFTERNOON')}
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
          {isLoading ? (
            <div className="schedule-empty-box">
              <p>KTX 운행 일정을 불러오는 중입니다...</p>
            </div>
          ) : scheduleList.length > 0 ? (
            scheduleList.map((item) => {
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
                      <span className="station-text">{item.depStationName}</span>
                    </div>
                    <div className="time-arrow">➔</div>
                    <div className="time-block">
                      <span className="time-text arr">{item.arrTime}</span>
                      <span className="station-text">{item.arrStationName}</span>
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

        {totalPages > 1 && (
          <div className="schedule-pagination" style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginBottom: '24px' }}>
            <button
              type="button"
              className="filter-btn"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              이전
            </button>
            <span style={{ display: 'flex', alignItems: 'center', fontSize: '13px', fontWeight: 700 }}>
              {page} / {totalPages}
            </span>
            <button
              type="button"
              className="filter-btn"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              다음
            </button>
          </div>
        )}

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

      {queueModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.55)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '16px',
          }}
        >
          <div
            style={{
              backgroundColor: '#ffffff',
              borderRadius: '14px',
              padding: '28px 24px',
              width: '100%',
              maxWidth: '400px',
              textAlign: 'center',
              boxShadow: '0 20px 40px rgba(0, 0, 0, 0.2)',
            }}
          >
            <div style={{ fontSize: '13px', fontWeight: 700, color: '#0054a6', marginBottom: '6px' }}>
              TAGO 접속 대기열 안내
            </div>
            <h3 style={{ margin: '0 0 12px', fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
              {queueModal.train?.name} 예매 대기 중
            </h3>
            <p style={{ margin: '0 0 18px', fontSize: '13px', color: '#475569', lineHeight: 1.5 }}>
              동시 접속자가 많아 순서대로 입장하고 있습니다.
              <br />
              잠시만 기다려 주시면 자동으로 좌석 선택 화면으로 이동합니다.
            </p>
            <div
              style={{
                backgroundColor: '#eff6ff',
                border: '1px solid #bfdbfe',
                borderRadius: '10px',
                padding: '14px',
                marginBottom: '18px',
              }}
            >
              <div style={{ fontSize: '12px', color: '#1e40af', fontWeight: 600 }}>현재 나의 대기 순번</div>
              <div style={{ fontSize: '28px', fontWeight: 900, color: '#0054a6', marginTop: '4px' }}>
                {queueModal.queueRank}번
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                clearQueuePolling();
                setQueueModal(null);
              }}
              style={{
                width: '100%',
                padding: '10px 16px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                backgroundColor: '#f8fafc',
                color: '#475569',
                fontSize: '13px',
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              대기 취소
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
