import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import './ReservationPage.css';

export default function ReservationPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedTrain = location.state?.train || {
    name: 'KTX 011',
    type: 'KTX',
    depTime: '05:15',
    arrTime: '07:42',
    depStation: '서울',
    arrStation: '부산',
    generalPrice: '59,800',
    selectedSeatType: '일반실',
  };

  const [selectedCar, setSelectedCar] = useState(3);
  const [selectedSeats, setSelectedSeats] = useState(['4B']);
  const occupiedSeats = ['1A', '1B', '2C', '3D', '7A', '7B', '8C', '11D', '12A'];

  // 1부터 13행 (13행 × 4열 = 총 52석)
  const rows = Array.from({ length: 13 }, (_, i) => i + 1);
  const leftSeats = ['A', 'B'];
  const rightSeats = ['C', 'D'];

  const handleSeatClick = (seatId) => {
    if (occupiedSeats.includes(seatId)) return;

    if (selectedSeats.includes(seatId)) {
      setSelectedSeats(selectedSeats.filter((s) => s !== seatId));
    } else {
      if (selectedSeats.length >= 4) {
        alert('최대 4석까지 선택 가능합니다.');
        return;
      }
      setSelectedSeats([...selectedSeats, seatId]);
    }
  };

  const pricePerSeat = parseInt((selectedTrain.generalPrice || '59800').replace(/[^0-9]/g, ''), 10);
  const totalPrice = selectedSeats.length * pricePerSeat;

  const handleCompleteBooking = () => {
    if (selectedSeats.length === 0) {
      alert('예매할 좌석을 선택해 주세요.');
      return;
    }
    alert(`[KTX 예매 완료]\n열차: ${selectedTrain.name}\n좌석: ${selectedCar}호차 ${selectedSeats.join(', ')}\n결제 금액: ${totalPrice.toLocaleString()}원\n\n마이페이지로 이동합니다.`);
    navigate('/mypage');
  };

  return (
    <div className="reservation-page-wrapper">
      <Header showTopBar={true} />

      <main className="reservation-container">
        {/* Navigation & Title */}
        <div className="reservation-header-bar">
          <button 
            type="button" 
            className="back-btn"
            onClick={() => navigate(-1)}
          >
            ← 운행 시각표
          </button>
          <h2>KTX 좌석 선택</h2>
        </div>

        {/* Selected Train Info Card */}
        <div className="train-summary-card">
          <div className="train-meta">
            <span className="badge-ktx">{selectedTrain.type || 'KTX'}</span>
            <strong className="train-title-main">{selectedTrain.name}</strong>
            <span className="class-pill">{selectedTrain.selectedSeatType || '일반실'}</span>
          </div>

          <div className="train-time-display">
            <div className="time-item">
              <span className="station">{selectedTrain.depStation || '서울'}</span>
              <strong className="time">{selectedTrain.depTime}</strong>
            </div>
            <div className="route-arrow-box">
              <span>➔</span>
            </div>
            <div className="time-item">
              <span className="station">{selectedTrain.arrStation || '부산'}</span>
              <strong className="time">{selectedTrain.arrTime}</strong>
            </div>
          </div>
        </div>

        {/* Car Selection */}
        <div className="car-selection-wrapper">
          <span className="car-label">호차 선택</span>
          <div className="car-tabs">
            {[1, 2, 3, 4, 5, 6, 7, 8].map((carNum) => (
              <button
                key={carNum}
                type="button"
                className={`car-tab-btn ${selectedCar === carNum ? 'active' : ''}`}
                onClick={() => setSelectedCar(carNum)}
              >
                {carNum}호차
                <span className="car-subtext">{carNum <= 2 ? '특실' : '일반실'}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Legend */}
        <div className="seat-legend-bar">
          <div className="legend-item">
            <span className="legend-box available"></span>
            <span>선택 가능</span>
          </div>
          <div className="legend-item">
            <span className="legend-box selected"></span>
            <span>선택 좌석</span>
          </div>
          <div className="legend-item">
            <span className="legend-box occupied"></span>
            <span>예약 완료</span>
          </div>
        </div>

        {/* Train Coach Layout */}
        <div className="train-coach-container">
          <div className="coach-direction-bar">
            <span>[ 전면 - 서울 방면 순방향 ]</span>
          </div>

          <div className="coach-body">
            <div className="coach-amenity top">
              <span>출입문 / 수화물 보관소</span>
              <span>화장실</span>
            </div>

            <div className="seats-grid-area">
              <div className="column-headers">
                <span className="col-char">A</span>
                <span className="col-char">B</span>
                <span className="aisle-spacer">통로</span>
                <span className="col-char">C</span>
                <span className="col-char">D</span>
              </div>

              {rows.map((row) => (
                <div key={row} className="seat-row">
                  {/* Left A, B */}
                  <div className="seat-pair left">
                    {leftSeats.map((col) => {
                      const seatId = `${row}${col}`;
                      const isOccupied = occupiedSeats.includes(seatId);
                      const isSelected = selectedSeats.includes(seatId);

                      return (
                        <button
                          key={seatId}
                          type="button"
                          className={`seat-unit ${isOccupied ? 'occupied' : ''} ${isSelected ? 'selected' : ''}`}
                          disabled={isOccupied}
                          onClick={() => handleSeatClick(seatId)}
                          title={`${seatId} 좌석`}
                        >
                          <span className="seat-label">{seatId}</span>
                        </button>
                      );
                    })}
                  </div>

                  {/* Aisle */}
                  <div className="aisle">
                    <span className="row-number">{row}</span>
                  </div>

                  {/* Right C, D */}
                  <div className="seat-pair right">
                    {rightSeats.map((col) => {
                      const seatId = `${row}${col}`;
                      const isOccupied = occupiedSeats.includes(seatId);
                      const isSelected = selectedSeats.includes(seatId);

                      return (
                        <button
                          key={seatId}
                          type="button"
                          className={`seat-unit ${isOccupied ? 'occupied' : ''} ${isSelected ? 'selected' : ''}`}
                          disabled={isOccupied}
                          onClick={() => handleSeatClick(seatId)}
                          title={`${seatId} 좌석`}
                        >
                          <span className="seat-label">{seatId}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>

            <div className="coach-amenity bottom">
              <span>출입문 / 휴대폰 충전기</span>
            </div>
          </div>
        </div>

        {/* Bottom Booking Summary Floating Bar */}
        <div className="booking-summary-bottom-bar">
          <div className="summary-left">
            <div className="selection-info">
              <span className="label">선택 좌석</span>
              <strong className="seat-values">
                {selectedSeats.length > 0 ? (
                  selectedSeats.map((s) => `${selectedCar}호차 ${s}`).join(', ')
                ) : (
                  '좌석을 선택해 주세요'
                )}
              </strong>
            </div>
            <div className="price-info">
              <span className="label">총 결제 금액</span>
              <strong className="price-value">{totalPrice.toLocaleString()}원</strong>
            </div>
          </div>

          <button 
            type="button" 
            className="complete-book-btn"
            disabled={selectedSeats.length === 0}
            onClick={handleCompleteBooking}
          >
            승차권 예매 ({selectedSeats.length}석)
          </button>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
