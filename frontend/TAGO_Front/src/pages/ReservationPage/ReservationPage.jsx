import { useState, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import './ReservationPage.css';

const KTX_CARS = [
  { carNo: 1, seats: 56, rows: 14, status: '여유' },
  { carNo: 2, seats: 56, rows: 14, status: '여유' },
  { carNo: 3, seats: 56, rows: 14, status: '여유' },
  { carNo: 4, seats: 56, rows: 14, status: '여유' },
  { carNo: 5, seats: 56, rows: 14, status: '여유' },
  { carNo: 6, seats: 56, rows: 14, status: '여유' },
  { carNo: 7, seats: 60, rows: 15, status: '여유' },
  { carNo: 8, seats: 56, rows: 14, status: '여유' },
  { carNo: 9, seats: 60, rows: 15, status: '여유' },
  { carNo: 10, seats: 60, rows: 15, status: '여유' },
  { carNo: 11, seats: 56, rows: 14, status: '여유' },
  { carNo: 12, seats: 60, rows: 15, status: '여유' },
  { carNo: 13, seats: 56, rows: 14, status: '여유' },
  { carNo: 14, seats: 60, rows: 15, status: '여유' },
  { carNo: 15, seats: 56, rows: 14, status: '여유' },
  { carNo: 16, seats: 60, rows: 15, status: '여유' },
  { carNo: 17, seats: 56, rows: 14, status: '여유' },
  { carNo: 18, seats: 56, rows: 14, status: '여유' },
];

export default function ReservationPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const bookingInfo = useMemo(() => {
    const state = location.state || {};
    return {
      trainName: state.trainName || state.train?.name || 'KTX 001',
      trainType: 'KTX',
      depTime: state.depTime || state.train?.depTime || '05:15',
      arrTime: state.arrTime || state.train?.arrTime || '07:42',
      duration: state.duration || state.train?.duration || '2시간 27분',
      depStation: state.depStation || '서울',
      arrStation: state.arrStation || '부산',
      depDate: state.depDate || '2026-11-16 (월)',
      passenger: state.passenger || state.passengerCount || '어른 1명',
      unitPrice: 59800,
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

  const [selectedSeats, setSelectedSeats] = useState([]);
  const [paymentMethod, setPaymentMethod] = useState('card');

  const [occupiedSeatsByCar, setOccupiedSeatsByCar] = useState({
    1: ['1A', '1B'],
  });

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

    const existingIndex = selectedSeats.findIndex(
      (s) => s.carNo === selectedCarNo && s.seatId === seatId
    );

    if (existingIndex > -1) {
      setSelectedSeats(selectedSeats.filter((_, idx) => idx !== existingIndex));
    } else {
      if (selectedSeats.length >= targetSeatCount) {
        alert(`선택하신 승객 인원(${targetSeatCount}명)에 맞춰 최대 ${targetSeatCount}석까지만 선택할 수 있습니다.`);
        return;
      }
      setSelectedSeats([...selectedSeats, { carNo: selectedCarNo, seatId }]);
    }
  };

  const handleRemoveSeat = (carNo, seatId) => {
    setSelectedSeats(selectedSeats.filter((s) => !(s.carNo === carNo && s.seatId === seatId)));
  };

  const isSelectionComplete = selectedSeats.length === targetSeatCount;
  const totalPrice = selectedSeats.length * bookingInfo.unitPrice;

  const handleFinalPayment = () => {
    if (!isSelectionComplete) {
      alert(`좌석을 모두 선택해 주세요. (현재 ${selectedSeats.length}/${targetSeatCount}석 선택됨)`);
      return;
    }

    const seatString = selectedSeats.map((s) => `${s.carNo}호차 ${s.seatId}`).join(', ');
    const paymentName =
      paymentMethod === 'card'
        ? '신용/체크카드'
        : paymentMethod === 'easy'
        ? '간편결제 (카카오/네이버/토스)'
        : '실시간 계좌이체';

    alert(
      `[KTX 승차권 결제 및 발권 완료]\n\n` +
      `• 열차: ${bookingInfo.trainName} (KTX 직통)\n` +
      `• 구간: ${bookingInfo.depStation} ➔ ${bookingInfo.arrStation}\n` +
      `• 일시: ${bookingInfo.depDate} | 출발 ${bookingInfo.depTime} ➔ 도착 ${bookingInfo.arrTime}\n` +
      `• 승객: ${bookingInfo.passenger} (총 ${targetSeatCount}명)\n` +
      `• 지정 좌석: ${seatString}\n` +
      `• 결제 수단: ${paymentName}\n` +
      `• 총 결제금액: ${totalPrice.toLocaleString()}원\n\n` +
      `승차권 발권이 정상 처리되었습니다.\n마이페이지(승차권 확인)로 이동합니다.`
    );

    navigate('/mypage');
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
                <span className="meta-k">좌석 등급</span>
                <strong className="meta-v">일반실 (59,800원)</strong>
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
            <div className="formation-panel">
              <div className="formation-header">
                <div className="formation-title-group">
                  <h3 className="section-title">좌석 선택</h3>
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
                  <span>열차 진행 방향 (부산 방면)</span>
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
                        {leftColumns.map((col) => {
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
                        {rightColumns.map((col) => {
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
                  <span className="block-name">지정 좌석 ({targetSeatCount}석)</span>
                  <span className={`status-tag ${isSelectionComplete ? 'complete' : 'pending'}`}>
                    {isSelectionComplete
                      ? '선택 완료'
                      : `${selectedSeats.length}/${targetSeatCount}석 선택 중`}
                  </span>
                </div>

                <div className="selected-seat-chips-box">
                  {selectedSeats.length > 0 ? (
                    selectedSeats.map((s) => (
                      <div key={`${s.carNo}-${s.seatId}`} className="seat-ticket-chip">
                        <span className="chip-car">{s.carNo}호차</span>
                        <strong className="chip-seat">{s.seatId}석</strong>
                        <button
                          type="button"
                          className="chip-remove-btn"
                          onClick={() => handleRemoveSeat(s.carNo, s.seatId)}
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
                  <span className="fare-k">예매 승객 ({targetSeatCount}명)</span>
                  <span className="fare-v">× {selectedSeats.length}석</span>
                </div>
                <div className="fare-calc-divider"></div>
                <div className="fare-calc-row total-row">
                  <span className="total-label">총 결제 금액</span>
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
                ) : (
                  <span>좌석 {targetSeatCount - selectedSeats.length}석을 더 선택해 주세요</span>
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
