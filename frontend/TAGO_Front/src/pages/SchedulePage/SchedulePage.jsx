import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import './SchedulePage.css';

export default function SchedulePage() {
  const navigate = useNavigate();
  const location = useLocation();

  const depStation = location.state?.departureStation || '서울';
  const arrStation = location.state?.arrivalStation || '부산';
  const depDate = location.state?.departureDate || '2026-11-16 (월)';
  const passenger = location.state?.passengerCount || '어른 1명';

  const [selectedFilter, setSelectedFilter] = useState('ALL');

  // KTX 전용 열차 목록
  const scheduleList = [
    {
      id: 1,
      trainNo: '011',
      name: 'KTX 011',
      type: 'KTX',
      depTime: '05:15',
      arrTime: '07:42',
      duration: '2시간 27분',
      generalPrice: '59,800',
      firstPrice: '83,700',
      generalStatus: '예매가능',
      firstStatus: '매진',
    },
    {
      id: 2,
      trainNo: '002',
      name: 'KTX-청룡 002',
      type: 'KTX-청룡',
      depTime: '06:00',
      arrTime: '08:15',
      duration: '2시간 15분',
      generalPrice: '59,800',
      firstPrice: '83,700',
      generalStatus: '예매가능',
      firstStatus: '예매가능',
    },
    {
      id: 3,
      trainNo: '402',
      name: 'KTX-산천 402',
      type: 'KTX-산천',
      depTime: '06:30',
      arrTime: '08:58',
      duration: '2시간 28분',
      generalPrice: '59,800',
      firstPrice: '83,700',
      generalStatus: '매진',
      firstStatus: '예매가능',
    },
    {
      id: 4,
      trainNo: '015',
      name: 'KTX 015',
      type: 'KTX',
      depTime: '07:00',
      arrTime: '09:25',
      duration: '2시간 25분',
      generalPrice: '59,800',
      firstPrice: '83,700',
      generalStatus: '예매가능',
      firstStatus: '예매가능',
    },
    {
      id: 5,
      trainNo: '701',
      name: 'KTX-이음 701',
      type: 'KTX-이음',
      depTime: '08:00',
      arrTime: '10:18',
      duration: '2시간 18분',
      generalPrice: '59,800',
      firstPrice: '83,700',
      generalStatus: '예매가능',
      firstStatus: '예매가능',
    },
    {
      id: 6,
      trainNo: '019',
      name: 'KTX 019',
      type: 'KTX',
      depTime: '08:45',
      arrTime: '11:10',
      duration: '2시간 25분',
      generalPrice: '59,800',
      firstPrice: '83,700',
      generalStatus: '매진',
      firstStatus: '매진',
    },
  ];

  const filteredList = scheduleList.filter((item) => {
    if (selectedFilter === 'ALL') return true;
    return item.type === selectedFilter;
  });

  const handleSelectTrain = (train, seatType) => {
    const price = seatType === '특실' ? train.firstPrice : train.generalPrice;
    alert(`[KTX 승차권 예매 완료]\n\n• 열차: ${train.name} (${seatType})\n• 구간: ${depStation} ➔ ${arrStation}\n• 출발일시: ${depDate} ${train.depTime}\n• 결제운임: ${price}원\n• 좌석: 3호차 4B (자동 배정)\n\n발권이 완료되었습니다. 마이페이지에서 승차권을 확인하세요.`);
    navigate('/mypage');
  };

  return (
    <div className="schedule-page-wrapper">
      <Header showTopBar={true} />

      <main className="schedule-container">
        {/* Top Summary Bar */}
        <div className="schedule-search-bar">
          <div className="summary-info">
            <div className="summary-route">
              <span className="station-name dep">{depStation}</span>
              <span className="route-arrow">➔</span>
              <span className="station-name arr">{arrStation}</span>
            </div>
            <div className="summary-meta">
              <span>{depDate}</span>
              <span className="meta-divider">|</span>
              <span>{passenger}</span>
              <span className="meta-divider">|</span>
              <span className="tag-ktx">KTX 직통</span>
            </div>
          </div>
          <button 
            type="button" 
            className="modify-search-btn"
            onClick={() => navigate('/')}
          >
            여정 변경
          </button>
        </div>

        {/* Filter Navigation */}
        <div className="schedule-filter-row">
          <div className="filter-buttons">
            <button 
              type="button" 
              className={`filter-btn ${selectedFilter === 'ALL' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('ALL')}
            >
              전체 KTX ({scheduleList.length})
            </button>
            <button 
              type="button" 
              className={`filter-btn ${selectedFilter === 'KTX' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('KTX')}
            >
              KTX
            </button>
            <button 
              type="button" 
              className={`filter-btn ${selectedFilter === 'KTX-청룡' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('KTX-청룡')}
            >
              KTX-청룡 (320km/h)
            </button>
            <button 
              type="button" 
              className={`filter-btn ${selectedFilter === 'KTX-산천' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('KTX-산천')}
            >
              KTX-산천
            </button>
            <button 
              type="button" 
              className={`filter-btn ${selectedFilter === 'KTX-이음' ? 'active' : ''}`}
              onClick={() => setSelectedFilter('KTX-이음')}
            >
              KTX-이음
            </button>
          </div>

          <div className="date-changer">
            <button type="button" className="date-arrow-btn">◀ 이전일</button>
            <span className="current-date-text">{depDate}</span>
            <button type="button" className="date-arrow-btn">다음일 ▶</button>
          </div>
        </div>

        {/* Schedule Table Header */}
        <div className="schedule-table-head">
          <span className="col-train">열차 구분</span>
          <span className="col-time">출발 / 도착</span>
          <span className="col-duration">소요시간</span>
          <span className="col-class">일반실 (Standard)</span>
          <span className="col-class">특실 / 우등 (First)</span>
        </div>

        {/* Schedule List */}
        <div className="schedule-list">
          {filteredList.map((item) => {
            const isGeneralSoldout = item.generalStatus === '매진';
            const isFirstSoldout = item.firstStatus === '매진';

            return (
              <div key={item.id} className="schedule-row-card">
                {/* Train Info */}
                <div className="col-train">
                  <span className={`badge-ktx`}>{item.type}</span>
                  <strong className="train-title">{item.name}</strong>
                </div>

                {/* Times */}
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

                {/* Duration */}
                <div className="col-duration">
                  <span className="duration-pill">{item.duration}</span>
                </div>

                {/* General Class Seat Button */}
                <div className="col-class">
                  <button 
                    type="button" 
                    className={`seat-select-btn ${isGeneralSoldout ? 'soldout' : 'available'}`}
                    disabled={isGeneralSoldout}
                    onClick={() => handleSelectTrain(item, '일반실')}
                  >
                    <span className="price-text">{item.generalPrice}원</span>
                    <span className="status-sub">{item.generalStatus}</span>
                  </button>
                </div>

                {/* First Class Seat Button */}
                <div className="col-class">
                  <button 
                    type="button" 
                    className={`seat-select-btn ${isFirstSoldout ? 'soldout' : 'available'}`}
                    disabled={isFirstSoldout}
                    onClick={() => handleSelectTrain(item, '특실')}
                  >
                    <span className="price-text">{item.firstPrice}원</span>
                    <span className="status-sub">{item.firstStatus}</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Notice Info Box */}
        <div className="schedule-notice-box">
          <h4>KTX 승차권 예매 및 이용 안내</h4>
          <ul>
            <li>원하시는 좌석 등급(일반실/특실) 버튼을 클릭하시면 승차권이 자동 배정되어 즉시 발권됩니다.</li>
            <li>발권된 승차권은 마이페이지에서 QR 코드로 확인하실 수 있으며 스마트폰으로 개찰구를 통과하실 수 있습니다.</li>
            <li>열차 출발 전까지 마이페이지에서 위약금 없이 반환(취소) 및 영수증 출력이 가능합니다.</li>
          </ul>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
