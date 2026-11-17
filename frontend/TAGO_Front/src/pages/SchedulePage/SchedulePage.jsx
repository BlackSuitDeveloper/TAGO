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
  const initialDate = location.state?.departureDate || '2026-11-16 (월)';
  const passenger = location.state?.passengerCount || '어른 1명';
  const targetTrainName = location.state?.targetTrainName || null;

  const [currentDate, setCurrentDate] = useState(initialDate);
  const [selectedFilter, setSelectedFilter] = useState('ALL');

  const isBaseDay = currentDate === initialDate;

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
          depStation,
          arrStation,
          price: train.generalPrice,
        };
        next = [...prev, newFav];
      }
      localStorage.setItem('tago_favorite_trains', JSON.stringify(next));
      return next;
    });
  };

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

  const handlePrevDay = () => {
    if (isBaseDay) return;
    const match = currentDate.match(/(\d{4})-(\d{2})-(\d{2})/);
    if (match) {
      const year = parseInt(match[1], 10);
      const month = parseInt(match[2], 10) - 1;
      const day = parseInt(match[3], 10);
      const prevDateObj = new Date(year, month, day - 1);
      const dayNames = ['일', '월', '화', '수', '목', '금', '토'];
      const nextY = prevDateObj.getFullYear();
      const nextM = String(prevDateObj.getMonth() + 1).padStart(2, '0');
      const nextD = String(prevDateObj.getDate()).padStart(2, '0');
      const dayName = dayNames[prevDateObj.getDay()];
      setCurrentDate(`${nextY}-${nextM}-${nextD} (${dayName})`);
    }
  };

  const handleNextDay = () => {
    const match = currentDate.match(/(\d{4})-(\d{2})-(\d{2})/);
    if (match) {
      const year = parseInt(match[1], 10);
      const month = parseInt(match[2], 10) - 1;
      const day = parseInt(match[3], 10);
      const nextDateObj = new Date(year, month, day + 1);
      const dayNames = ['일', '월', '화', '수', '목', '금', '토'];
      const nextY = nextDateObj.getFullYear();
      const nextM = String(nextDateObj.getMonth() + 1).padStart(2, '0');
      const nextD = String(nextDateObj.getDate()).padStart(2, '0');
      const dayName = dayNames[nextDateObj.getDay()];
      setCurrentDate(`${nextY}-${nextM}-${nextD} (${dayName})`);
    } else {
      setCurrentDate('2026-11-17 (화)');
    }
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
        depDate: currentDate,
        passenger,
        passengerDetail: location.state?.passengerDetail,
        seatType: '일반실',
        price: train.generalPrice,
      }
    });
  };

  return (
    <div className="schedule-page-wrapper">
      <Header showTopBar={true} />

      <main className="schedule-container">
        <div className="schedule-search-bar">
          <div className="summary-info">
            <div className="summary-route">
              <span className="station-name dep">{depStation}</span>
              <span className="route-arrow">➔</span>
              <span className="station-name arr">{arrStation}</span>
            </div>
            <div className="summary-meta">
              <span>{currentDate}</span>
              <span className="meta-divider">|</span>
              <span>{passenger}</span>
              <span className="meta-divider">|</span>
              <span className="tag-ktx">KTX 전용</span>
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

          <div className="date-changer">
            <span className="date-label">출발일</span>
            {!isBaseDay && (
              <button 
                type="button" 
                className="date-arrow-btn prev-active"
                onClick={handlePrevDay}
                title="이전날 운행 시각표 조회"
              >
                ◀ 이전일
              </button>
            )}
            <span className="current-date-text">{currentDate}</span>
            <button 
              type="button" 
              className="date-arrow-btn next-active"
              onClick={handleNextDay}
              title="다음날 운행 시각표 조회"
            >
              다음일 ▶
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
                      className={`btn-schedule-favorite ${favorites.some((f) => f.name === item.name) ? 'active' : ''}`}
                      onClick={() => handleToggleFavorite(item)}
                      title={favorites.some((f) => f.name === item.name) ? '찜 해제' : '열차 찜하기'}
                      aria-label="열차 찜하기"
                    >
                      {favorites.some((f) => f.name === item.name) ? '♥' : '♡'}
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
