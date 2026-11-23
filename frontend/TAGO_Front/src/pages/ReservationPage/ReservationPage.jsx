import { useState, useMemo, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import client from '../Api/client';
import './ReservationPage.css';

const KTX_CARS = [
  { carNo: 1, seats: 56, rows: 14, status: '여유' },
  { carNo: 2, seats: 35, rows: 9, status: '여유' },
  { carNo: 3, seats: 46, rows: 12, status: '여유' },
  { carNo: 4, seats: 50, rows: 13, status: '여유' },
  { carNo: 5, seats: 55, rows: 14, status: '여유' },
  { carNo: 6, seats: 56, rows: 14, status: '여유' },
  { carNo: 7, seats: 56, rows: 14, status: '여유' },
  { carNo: 8, seats: 56, rows: 14, status: '여유' },
  { carNo: 9, seats: 56, rows: 14, status: '여유' },
  { carNo: 10, seats: 56, rows: 14, status: '여유' },
  { carNo: 11, seats: 56, rows: 14, status: '여유' },
  { carNo: 12, seats: 56, rows: 14, status: '여유' },
  { carNo: 13, seats: 56, rows: 14, status: '여유' },
  { carNo: 14, seats: 56, rows: 14, status: '여유' },
  { carNo: 15, seats: 56, rows: 14, status: '여유' },
  { carNo: 16, seats: 56, rows: 14, status: '여유' },
  { carNo: 17, seats: 56, rows: 14, status: '여유' },
  { carNo: 18, seats: 21, rows: 6, status: '여유' },
];

export default function ReservationPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const bookingInfo = useMemo(() => {
    const state = location.state || {};
    const rawPrice = state.price ?? state.train?.price;
    const parsedPrice = rawPrice
      ? parseInt(String(rawPrice).replace(/[^0-9]/g, ''), 10)
      : 59800;

    return {
      scheduleSeq: state.scheduleSeq || state.train?.scheduleSeq || 1,
      trainName: state.trainName || state.train?.name || 'KTX 001',
      trainType: 'KTX',
      depTime: state.depTime || state.train?.depTime || '05:15',
      arrTime: state.arrTime || state.train?.arrTime || '07:42',
      duration: state.duration || state.train?.duration || '2시간 27분',
      depStation: state.depStation || '서울',
      arrStation: state.arrStation || '부산',
      depDate: state.depDate || '2026-11-16 (월)',
      returnDate: state.returnDate || null,
      tripType: state.tripType || '편도',
      passenger: state.passenger || state.passengerCount || '어른 1명',
      unitPrice: Number.isNaN(parsedPrice) || parsedPrice <= 0 ? 59800 : parsedPrice,
    };
  }, [location.state]);

  const targetSeatCount = useMemo(() => {
    if (location.state?.passengerDetail?.adult) {
      return parseInt(location.state.passengerDetail.adult, 10);
    }
    const passengerStr = String(bookingInfo.passenger || '');
    const match = passengerStr.match(/(\d+)명/);
    if (match) {
      return parseInt(match[1], 10);
    }
    const anyDigit = passengerStr.match(/\d+/);
    if (anyDigit) {
      return parseInt(anyDigit[0], 10);
    }
    return 1;
  }, [bookingInfo.passenger, location.state]);

  const [selectedCarNo, setSelectedCarNo] = useState(1);
  const currentCar = useMemo(() => {
    return KTX_CARS.find((c) => c.carNo === selectedCarNo) || KTX_CARS[0];
  }, [selectedCarNo]);

  const CARS_PER_PAGE = 6;
  const [carPage, setCarPage] = useState(1);

  const currentGroupCars = useMemo(() => {
    const startIndex = (carPage - 1) * CARS_PER_PAGE;
    return KTX_CARS.slice(startIndex, startIndex + CARS_PER_PAGE);
  }, [carPage]);

  const handleSelectCar = (carNo) => {
    setSelectedCarNo(carNo);
  };

  const isRoundTrip = bookingInfo.tripType === '왕복';
  const [activeLeg, setActiveLeg] = useState('outbound'); // 'outbound' | 'return'
  const [outboundSeats, setOutboundSeats] = useState([]);
  const [returnSeats, setReturnSeats] = useState([]);
  const selectedSeats = activeLeg === 'outbound' ? outboundSeats : returnSeats;
  const [paymentMethod, setPaymentMethod] = useState('card');
  const [isProcessing, setIsProcessing] = useState(false);

  const [occupiedSeatsByCar, setOccupiedSeatsByCar] = useState({
    1: ['1A', '1B'],
  });

  // 호차 변경 또는 스케줄 변경 시 백엔드 좌석 목록(GET /api/seat) 실시간 연동
  useEffect(() => {
    let isMounted = true;
    const fetchSeats = async () => {
      try {
        const res = await client.get(`/seat`, {
          params: {
            scheduleSeq: bookingInfo.scheduleSeq,
            carNo: selectedCarNo,
          },
        });
        if (isMounted && res.data && res.data.seatList) {
          const occupied = res.data.seatList
            .filter((s) => s.status !== 'AVAILABLE')
            .map((s) => s.seatNo);
          setOccupiedSeatsByCar((prev) => ({
            ...prev,
            [selectedCarNo]: occupied,
          }));
        }
      } catch (err) {
        console.warn('좌석 상태 조회 실패 (서버 연결 대기 또는 Mock 유지):', err.message);
      }
    };

    fetchSeats();

    // SSE 실시간 좌석 스트림 연결 (GET /api/seat/stream)
    let eventSource = null;
    try {
      eventSource = new EventSource(
        `http://localhost:80/api/seat/stream?scheduleSeq=${bookingInfo.scheduleSeq}`
      );
      eventSource.addEventListener('SEAT_UPDATE', (e) => {
        try {
          const data = JSON.parse(e.data);
          if (data && data.carNo && data.seatNo) {
            setOccupiedSeatsByCar((prev) => {
              const current = prev[data.carNo] || [];
              if (data.status === 'AVAILABLE') {
                return { ...prev, [data.carNo]: current.filter((no) => no !== data.seatNo) };
              } else {
                return { ...prev, [data.carNo]: Array.from(new Set([...current, data.seatNo])) };
              }
            });
          }
        } catch (parseErr) {
          console.error('SSE 파싱 에러:', parseErr);
        }
      });
    } catch (sseErr) {
      console.warn('SSE 연결 대기:', sseErr.message);
    }

    return () => {
      isMounted = false;
      if (eventSource) eventSource.close();
    };
  }, [bookingInfo.scheduleSeq, selectedCarNo]);

  const currentCarOccupied = occupiedSeatsByCar[selectedCarNo] || [];

  const currentCarSelectedSeatIds = useMemo(() => {
    return selectedSeats
      .filter((s) => s.carNo === selectedCarNo)
      .map((s) => s.seatId);
  }, [selectedSeats, selectedCarNo]);

  const leftColumns = ['A', 'B'];
  const rightColumns = ['C', 'D'];

  const handleSeatClick = (seatId) => {
    if (currentCarOccupied.includes(seatId)) return;

    const currentSeats = activeLeg === 'outbound' ? outboundSeats : returnSeats;
    const setCurrentSeats = activeLeg === 'outbound' ? setOutboundSeats : setReturnSeats;
    const legLabel = activeLeg === 'outbound' ? '가는 편' : '오는 편';

    const existingIndex = currentSeats.findIndex(
      (s) => s.carNo === selectedCarNo && s.seatId === seatId
    );

    if (existingIndex > -1) {
      setCurrentSeats(currentSeats.filter((_, idx) => idx !== existingIndex));
    } else {
      if (currentSeats.length >= targetSeatCount) {
        alert(`${legLabel}은 선택하신 승객 인원(${targetSeatCount}명)에 맞춰 최대 ${targetSeatCount}석까지만 선택할 수 있습니다.`);
        return;
      }
      setCurrentSeats([...currentSeats, { carNo: selectedCarNo, seatId }]);
    }
  };

  const handleRemoveSeat = (carNo, seatId, leg = activeLeg) => {
    if (leg === 'return') {
      setReturnSeats((prev) => prev.filter((s) => !(s.carNo === carNo && s.seatId === seatId)));
    } else {
      setOutboundSeats((prev) => prev.filter((s) => !(s.carNo === carNo && s.seatId === seatId)));
    }
  };

  const isOutboundComplete = outboundSeats.length === targetSeatCount;
  const isReturnComplete = !isRoundTrip || returnSeats.length === targetSeatCount;
  const isSelectionComplete = isOutboundComplete && isReturnComplete;
  const totalSelectedSeatCount = outboundSeats.length + (isRoundTrip ? returnSeats.length : 0);
  const totalPrice = totalSelectedSeatCount * bookingInfo.unitPrice;

  // 최종 결제 및 발권 확정 (POST /api/reservation 선점 -> POST /api/payment 확정)
  const handleFinalPayment = async () => {
    if (!isSelectionComplete) {
      if (!isOutboundComplete) {
        alert(`가는 편 좌석을 모두 선택해 주세요. (현재 ${outboundSeats.length}/${targetSeatCount}석 선택됨)`);
      } else if (isRoundTrip && !isReturnComplete) {
        setActiveLeg('return');
        alert(`오는 편 좌석을 모두 선택해 주세요. (현재 ${returnSeats.length}/${targetSeatCount}석 선택됨)`);
      }
      return;
    }

    if (isProcessing) return;
    setIsProcessing(true);

    try {
      const seatNoList = outboundSeats.map((s) => s.seatId);
      const outboundCarNo = outboundSeats[0]?.carNo || selectedCarNo;

      // 1. 좌석 선점 (POST /api/reservation)
      const resHold = await client.post('/reservation', {
        scheduleSeq: bookingInfo.scheduleSeq,
        carNo: outboundCarNo,
        seatList: seatNoList,
        count: seatNoList.length,
      });

      const reservationId = resHold.data.reservationId || resHold.data.reservationSeq;
      const reservationIds = resHold.data.reservationIds || (reservationId ? [reservationId] : []);

      // 2. 결제 및 발권 확정 (POST /api/payment)
      const resPay = await client.post('/payment', {
        reservationId: reservationId,
        reservationIds: reservationIds,
        payMethod: paymentMethod === 'card' ? 'CARD' : paymentMethod === 'easy' ? 'EASY' : 'TRANSFER',
        totalPrice: resHold.data.totalPrice ?? totalPrice,
      });

      const ticketNumber = resPay.data.ticketNumber || '1029-3847-5690-11';
      const outboundSeatString = outboundSeats.map((s) => `${s.carNo}호차 ${s.seatId}`).join(', ');
      const returnSeatString = returnSeats.map((s) => `${s.carNo}호차 ${s.seatId}`).join(', ');

      alert(
        `[KTX 승차권 결제 및 발권 완료]\n\n` +
        `• 승차권 번호: ${ticketNumber}\n` +
        `• 열차: ${bookingInfo.trainName} (KTX · ${bookingInfo.tripType})\n` +
        `• 가는 편 (${bookingInfo.depStation} ➔ ${bookingInfo.arrStation}): ${outboundSeatString}\n` +
        (isRoundTrip ? `• 오는 편 (${bookingInfo.arrStation} ➔ ${bookingInfo.depStation}): ${returnSeatString}\n` : '') +
        `• 결제 금액: ${totalPrice.toLocaleString()}원\n\n` +
        `승차권이 정상적으로 발권되었습니다. 마이페이지로 이동합니다.`
      );

      setOutboundSeats([]);
      setReturnSeats([]);
      navigate('/mypage', { replace: true });
    } catch (err) {
      console.error('예매/결제 실패:', err);
      const errMsg = err.response?.data?.message || err.response?.data || '좌석 선점 또는 결제 처리 중 오류가 발생했습니다.';
      alert(`[예매 실패]\n${errMsg}`);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="reservation-page-wrapper">
      <Header showTopBar={true} />

      <main className="reservation-container">
        <section className="reservation-header-card">
          <div className="res-card-top">
            <div className="train-id-badge">
              <span className="badge-logo">KTX</span>
              <strong className="badge-name">{bookingInfo.trainName}</strong>
              <span className="badge-gen">KTX · 전 차량 일반실</span>
            </div>

            <div className="booking-stepper">
              <div className="step-item done">
                <span className="step-num">✓</span>
                <span className="step-name">열차 조회</span>
              </div>
              <span className="step-arrow">➔</span>
              <div className="step-item active">
                <span className="step-num">2</span>
                <span className="step-name">좌석 선택</span>
              </div>
              <span className="step-arrow">➔</span>
              <div className="step-item">
                <span className="step-num">3</span>
                <span className="step-name">결제 및 발권</span>
              </div>
            </div>
          </div>

          <div className="res-card-main">
            <div className="route-display">
              <div className="station-box">
                <span className="station-tag">출발</span>
                <strong className="st-name">{bookingInfo.depStation}</strong>
                <span className="st-time">{bookingInfo.depTime}</span>
              </div>
              <div className="route-mid">
                <span className="duration-tag">{bookingInfo.duration}</span>
                <div className="route-arrow-line">
                  <span className="arrow-head">▶</span>
                </div>
                <span className="date-tag">{bookingInfo.depDate}</span>
              </div>
              <div className="station-box">
                <span className="station-tag arr">도착</span>
                <strong className="st-name">{bookingInfo.arrStation}</strong>
                <span className="st-time">{bookingInfo.arrTime}</span>
              </div>
            </div>

            <div className="header-meta-box">
              <div className="meta-pill">
                <span className="meta-k">승객 인원</span>
                <strong className="meta-v">{bookingInfo.passenger}</strong>
              </div>
              <div className="meta-pill">
                <span className="meta-k">여정 / 좌석 등급</span>
                <strong className="meta-v">
                  {bookingInfo.tripType} · 일반실 ({bookingInfo.unitPrice.toLocaleString()}원)
                </strong>
              </div>
              <button 
                type="button" 
                className="btn-change-journey"
                onClick={() => navigate('/schedule')}
              >
                다른 시간대 열차 변경
              </button>
            </div>
          </div>
        </section>

        <div className="reservation-layout-grid">
          <div className="train-coach-section">
            {isRoundTrip && (
              <div className="round-trip-leg-switcher">
                <button
                  type="button"
                  className={`leg-switch-card ${activeLeg === 'outbound' ? 'active' : ''} ${isOutboundComplete ? 'done' : ''}`}
                  onClick={() => setActiveLeg('outbound')}
                >
                  <div className="leg-card-top">
                    <span className="leg-badge">가는 편</span>
                    <span className={`leg-progress ${isOutboundComplete ? 'complete' : ''}`}>
                      {isOutboundComplete ? '✓ 선택 완료' : `${outboundSeats.length}/${targetSeatCount}석`}
                    </span>
                  </div>
                  <strong className="leg-route">
                    {bookingInfo.depStation} ➔ {bookingInfo.arrStation}
                  </strong>
                  <span className="leg-date">{bookingInfo.depDate}</span>
                </button>

                <button
                  type="button"
                  className={`leg-switch-card ${activeLeg === 'return' ? 'active' : ''} ${isReturnComplete ? 'done' : ''}`}
                  onClick={() => setActiveLeg('return')}
                >
                  <div className="leg-card-top">
                    <span className="leg-badge return">오는 편</span>
                    <span className={`leg-progress ${isReturnComplete ? 'complete' : ''}`}>
                      {isReturnComplete ? '✓ 선택 완료' : `${returnSeats.length}/${targetSeatCount}석`}
                    </span>
                  </div>
                  <strong className="leg-route">
                    {bookingInfo.arrStation} ➔ {bookingInfo.depStation}
                  </strong>
                  <span className="leg-date">{bookingInfo.returnDate || '귀가일 선택됨'}</span>
                </button>
              </div>
            )}

            <div className="formation-panel">
              <div className="formation-header">
                <div className="formation-title-group">
                  <h3 className="section-title">
                    {isRoundTrip
                      ? `${activeLeg === 'outbound' ? '가는 편' : '오는 편'} 좌석 선택`
                      : '좌석 선택'}
                  </h3>
                  <span className="selected-car-badge">
                    현재 선택: <strong>{currentCar.carNo}호차</strong> (일반실 {currentCar.seats}석)
                  </span>
                </div>

                <div className="car-page-tabs">
                  {[1, 2, 3].map((page) => {
                    const startNo = (page - 1) * CARS_PER_PAGE + 1;
                    const endNo = page * CARS_PER_PAGE;
                    const isCurrentPage = carPage === page;
                    const hasSelectedSeatInPage = selectedSeats.some(
                      (s) => s.carNo >= startNo && s.carNo <= endNo
                    );

                    return (
                      <button
                        key={page}
                        type="button"
                        className={`car-page-tab-btn ${isCurrentPage ? 'active' : ''}`}
                        onClick={() => setCarPage(page)}
                      >
                        <span>{startNo}~{endNo}호차</span>
                        {hasSelectedSeatInPage && <span className="tab-seat-dot"></span>}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="cars-paged-grid">
                {currentGroupCars.map((car) => {
                  const isSelected = selectedCarNo === car.carNo;
                  const seatsChosenInThisCar = selectedSeats.filter((s) => s.carNo === car.carNo).length;

                  return (
                    <button
                      key={car.carNo}
                      type="button"
                      className={`car-paged-card ${isSelected ? 'selected' : ''}`}
                      onClick={() => handleSelectCar(car.carNo)}
                    >
                      <div className="car-card-header">
                        <span className="car-no-badge">{car.carNo}호차</span>
                        {seatsChosenInThisCar > 0 && (
                          <span className="chosen-count-pill">{seatsChosenInThisCar}석</span>
                        )}
                      </div>
                      <span className="car-spec-label">일반실 {car.seats}석</span>
                      <span className={`car-status-tag ${car.status === '여유' ? 'ok' : 'full'}`}>
                        {car.status}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="coach-seatmap-card">
              <div className="seatmap-top-bar">
                <div className="direction-indicator">
                  <span className="dir-icon">▲</span>
                  <span>
                    열차 진행 방향 ({activeLeg === 'outbound' ? bookingInfo.arrStation : bookingInfo.depStation} 방면)
                  </span>
                </div>

                <div className="seatmap-legend">
                  <div className="legend-item">
                    <span className="legend-box available"></span>
                    <span>선택가능</span>
                  </div>
                  <div className="legend-item">
                    <span className="legend-box selected"></span>
                    <span>선택한 좌석</span>
                  </div>
                  <div className="legend-item">
                    <span className="legend-box occupied"></span>
                    <span>예약됨 (매진)</span>
                  </div>
                </div>
              </div>

              <div className="coach-fuselage">
                <div className="seatmap-columns-head">
                  <div className="col-pair-head">
                    <span className="col-title window">A (창가)</span>
                    <span className="col-title">B (복도)</span>
                  </div>
                  <div className="aisle-title-space">통로</div>
                  <div className="col-pair-head">
                    <span className="col-title">C (복도)</span>
                    <span className="col-title window">D (창가)</span>
                  </div>
                </div>

                <div className="coach-rows-container">
                  {Array.from({ length: currentCar.rows }, (_, i) => i + 1).map((row) => (
                    <div key={row} className="coach-row">
                      <span className="window-mark left"></span>

                      <div className="seat-pair left">
                        {leftColumns.map((col, colIdx) => {
                          const seatOrdinal = (row - 1) * 4 + colIdx;
                          if (seatOrdinal >= currentCar.seats) {
                            return (
                              <span
                                key={`${row}${col}`}
                                className="modern-seat-btn"
                                style={{ visibility: 'hidden', pointerEvents: 'none' }}
                                aria-hidden="true"
                              />
                            );
                          }
                          const seatId = `${row}${col}`;
                          const isOccupied = currentCarOccupied.includes(seatId);
                          const isSelected = currentCarSelectedSeatIds.includes(seatId);

                          return (
                            <button
                              key={seatId}
                              type="button"
                              className={`modern-seat-btn ${col === 'A' ? 'window-seat' : ''} ${
                                isOccupied ? 'occupied' : ''
                              } ${isSelected ? 'selected' : ''}`}
                              disabled={isOccupied}
                              onClick={() => handleSeatClick(seatId)}
                              title={
                                isOccupied
                                  ? `${seatId} (예약 완료)`
                                  : isSelected
                                  ? `${seatId} (선택 해제 클릭)`
                                  : `${seatId} 좌석 선택`
                              }
                            >
                              <span className="seat-id-label">{seatId}</span>
                              {isSelected && <span className="seat-check-mark">✓</span>}
                            </button>
                          );
                        })}
                      </div>

                      <div className="aisle-divider-box">
                        <span className="row-num-badge">{row}</span>
                      </div>

                      <div className="seat-pair right">
                        {rightColumns.map((col, colIdx) => {
                          const seatOrdinal = (row - 1) * 4 + 2 + colIdx;
                          if (seatOrdinal >= currentCar.seats) {
                            return (
                              <span
                                key={`${row}${col}`}
                                className="modern-seat-btn"
                                style={{ visibility: 'hidden', pointerEvents: 'none' }}
                                aria-hidden="true"
                              />
                            );
                          }
                          const seatId = `${row}${col}`;
                          const isOccupied = currentCarOccupied.includes(seatId);
                          const isSelected = currentCarSelectedSeatIds.includes(seatId);

                          return (
                            <button
                              key={seatId}
                              type="button"
                              className={`modern-seat-btn ${col === 'D' ? 'window-seat' : ''} ${
                                isOccupied ? 'occupied' : ''
                              } ${isSelected ? 'selected' : ''}`}
                              disabled={isOccupied}
                              onClick={() => handleSeatClick(seatId)}
                              title={
                                isOccupied
                                  ? `${seatId} (예약 완료)`
                                  : isSelected
                                  ? `${seatId} (선택 해제 클릭)`
                                  : `${seatId} 좌석 선택`
                              }
                            >
                              <span className="seat-id-label">{seatId}</span>
                              {isSelected && <span className="seat-check-mark">✓</span>}
                            </button>
                          );
                        })}
                      </div>

                      <span className="window-mark right"></span>
                    </div>
                  ))}
                </div>

                <div className="coach-exit-foot">
                  <span>▼ 객실 연결 통로 및 화장실 ▼</span>
                </div>
              </div>
            </div>
          </div>

          <aside className="checkout-sidebar-area">
            <div className="ticket-checkout-card">
              <div className="checkout-card-header">
                <span className="card-sub-title">승차권 예매 내역</span>
                <h3 className="card-main-title">{bookingInfo.trainName}</h3>
                <span className="card-gen-info">KTX 20량 편성 · 일반실</span>
              </div>

              <div className="checkout-section-block">
                <div className="block-title-row">
                  <span className="block-name">
                    {isRoundTrip ? `가는 편 지정 좌석 (${targetSeatCount}석)` : `지정 좌석 (${targetSeatCount}석)`}
                  </span>
                  <span className={`status-tag ${isOutboundComplete ? 'complete' : 'pending'}`}>
                    {isOutboundComplete
                      ? '선택 완료'
                      : `${outboundSeats.length}/${targetSeatCount}석 선택 중`}
                  </span>
                </div>

                <div className="selected-seat-chips-box">
                  {outboundSeats.length > 0 ? (
                    outboundSeats.map((s) => (
                      <div key={`out-${s.carNo}-${s.seatId}`} className="seat-ticket-chip">
                        <span className="chip-car">{s.carNo}호차</span>
                        <strong className="chip-seat">{s.seatId}석</strong>
                        <button
                          type="button"
                          className="chip-remove-btn"
                          onClick={() => handleRemoveSeat(s.carNo, s.seatId, 'outbound')}
                          title="좌석 선택 취소"
                        >
                          ✕
                        </button>
                      </div>
                    ))
                  ) : (
                    <div className="empty-seat-notice">
                      좌측 객실 배치도에서 원하시는 좌석을 클릭하여 지정해 주세요.
                    </div>
                  )}
                </div>
              </div>

              {isRoundTrip && (
                <div className="checkout-section-block">
                  <div className="block-title-row">
                    <span className="block-name">오는 편 지정 좌석 ({targetSeatCount}석)</span>
                    <span className={`status-tag ${isReturnComplete ? 'complete' : 'pending'}`}>
                      {isReturnComplete
                        ? '선택 완료'
                        : `${returnSeats.length}/${targetSeatCount}석 선택 중`}
                    </span>
                  </div>

                  <div className="selected-seat-chips-box">
                    {returnSeats.length > 0 ? (
                      returnSeats.map((s) => (
                        <div key={`ret-${s.carNo}-${s.seatId}`} className="seat-ticket-chip">
                          <span className="chip-car">{s.carNo}호차</span>
                          <strong className="chip-seat">{s.seatId}석</strong>
                          <button
                            type="button"
                            className="chip-remove-btn"
                            onClick={() => handleRemoveSeat(s.carNo, s.seatId, 'return')}
                            title="좌석 선택 취소"
                          >
                            ✕
                          </button>
                        </div>
                      ))
                    ) : (
                      <div className="empty-seat-notice">
                        {activeLeg === 'return'
                          ? '좌측 객실 배치도에서 오는 편 좌석을 선택해 주세요.'
                          : (
                            <button
                              type="button"
                              className="btn-switch-return-inline"
                              onClick={() => setActiveLeg('return')}
                            >
                              오는 편 좌석 선택하러 가기 →
                            </button>
                          )}
                      </div>
                    )}
                  </div>
                </div>
              )}

              <div className="checkout-section-block">
                <span className="block-name">결제 수단 선택</span>
                <div className="pay-methods-grid">
                  <button
                    type="button"
                    className={`pay-method-btn ${paymentMethod === 'card' ? 'active' : ''}`}
                    onClick={() => setPaymentMethod('card')}
                  >
                    <span className="pay-icon">💳</span>
                    <span className="pay-label">신용/체크카드</span>
                  </button>
                  <button
                    type="button"
                    className={`pay-method-btn ${paymentMethod === 'easy' ? 'active' : ''}`}
                    onClick={() => setPaymentMethod('easy')}
                  >
                    <span className="pay-icon">⚡</span>
                    <span className="pay-label">간편결제 (카카오/토스)</span>
                  </button>
                  <button
                    type="button"
                    className={`pay-method-btn ${paymentMethod === 'transfer' ? 'active' : ''}`}
                    onClick={() => setPaymentMethod('transfer')}
                  >
                    <span className="pay-icon">🏦</span>
                    <span className="pay-label">실시간 계좌이체</span>
                  </button>
                </div>
              </div>

              <div className="checkout-section-block fare-summary-block">
                <div className="fare-calc-row">
                  <span className="fare-k">기본 운임 (1인 일반실)</span>
                  <span className="fare-v">{bookingInfo.unitPrice.toLocaleString()}원</span>
                </div>
                <div className="fare-calc-row">
                  <span className="fare-k">
                    {isRoundTrip ? `가는 편 (${targetSeatCount}명)` : `예매 승객 (${targetSeatCount}명)`}
                  </span>
                  <span className="fare-v">× {outboundSeats.length}석</span>
                </div>
                {isRoundTrip && (
                  <div className="fare-calc-row">
                    <span className="fare-k">오는 편 ({targetSeatCount}명)</span>
                    <span className="fare-v">× {returnSeats.length}석</span>
                  </div>
                )}
                <div className="fare-calc-divider"></div>
                <div className="fare-calc-row total-row">
                  <span className="total-label">총 결제 금액 ({bookingInfo.tripType})</span>
                  <strong className="total-price-num">{totalPrice.toLocaleString()}원</strong>
                </div>
              </div>

              <button
                type="button"
                className={`btn-checkout-primary ${isSelectionComplete ? 'ready' : 'disabled'}`}
                disabled={!isSelectionComplete}
                onClick={handleFinalPayment}
              >
                {isSelectionComplete ? (
                  <>
                    <span>{totalPrice.toLocaleString()}원 결제 및 발권 완료</span>
                    <span className="btn-arrow">→</span>
                  </>
                ) : !isOutboundComplete ? (
                  <span>
                    {isRoundTrip ? '가는 편 ' : ''}좌석 {targetSeatCount - outboundSeats.length}석을 더 선택해 주세요
                  </span>
                ) : (
                  <span>오는 편 좌석 {targetSeatCount - returnSeats.length}석을 더 선택해 주세요</span>
                )}
              </button>

              <div className="checkout-guarantee-note">
                <span>🔒 안전 결제 암호화 시스템 적용</span>
                <span>• 발권 후 마이페이지에서 QR 승차권 확인 가능</span>
              </div>
            </div>
          </aside>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
