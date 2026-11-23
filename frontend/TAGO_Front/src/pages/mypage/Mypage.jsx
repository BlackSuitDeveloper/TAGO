import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../Api/client';
import Header from '../../components/Header/Header';
import Footer from '../../components/Footer/Footer';
import QuickMenu from '../../components/QuickMenu/QuickMenu';
import useWishStore from '../../store/useWishStore';
import './MyPage.css';

// 날짜/시간 포맷 변환 헬퍼 함수
const formatDateTime = (dateStr) => {
  if (!dateStr) return { date: '', time: '' };
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) {
      const raw = String(dateStr);
      return {
        date: raw.length >= 10 ? raw.slice(0, 10) : raw,
        time: raw.length >= 16 ? raw.slice(11, 16) : '',
      };
    }
    const days = ['일', '월', '화', '수', '목', '금', '토'];
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    const day = days[d.getDay()];
    const hh = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');

    return {
      date: `${yyyy}. ${mm}. ${dd} (${day})`,
      time: `${hh}:${min}`,
    };
  } catch {
    return { date: String(dateStr), time: '' };
  }
};

// 백엔드 MyTicketResponseDTO -> 화면 표시용 데이터 변환
const parseTicket = (item) => {
  const dep = formatDateTime(item.depplandtime);
  const arr = formatDateTime(item.arrplandtime);
  const rawPrice = Number(item.totalPrice || 0);
  return {
    reservationSeq: item.reservationSeq,
    reservationSeqs: [item.reservationSeq],
    scheduleSeq: item.scheduleSeq,
    carNo: item.carNo,
    trainName: `${item.vehiclekndnm || 'KTX'} ${item.trainno || ''}`.trim(),
    trainType: item.vehiclekndnm || 'KTX',
    depDate: dep.date,
    depStation: item.depStation || '출발역',
    arrStation: item.arrStation || '도착역',
    depTime: dep.time || '00:00',
    arrTime: arr.time || '00:00',
    car: item.carNo ? `${item.carNo}호차` : '일반',
    seatNo: item.seatNo || '-',
    seat: item.seatNo || '-',
    seatGrade: item.seatGrade || '어른',
    passenger: item.seatGrade ? `${item.seatGrade} 1명` : '어른 1명',
    rawPrice,
    price: `${rawPrice.toLocaleString()}원`,
    hasRealTicketNumber: Boolean(item.ticketNumber),
    ticketNumber: item.ticketNumber || `T-${String(item.reservationSeq || '').padStart(8, '0')}`,
    status: item.status || 'CONFIRMED',
  };
};

// 동일한 스케줄·호차·상태의 승차권을 하나로 묶어 좌석 목록/인원/금액 합산
const groupTickets = (rawList) => {
  const map = new Map();

  for (const raw of rawList) {
    const t = parseTicket(raw);
    const key = `${t.scheduleSeq ?? 'none'}_${t.carNo ?? 'none'}_${t.status}`;

    if (!map.has(key)) {
      map.set(key, {
        ...t,
        seats: [t.seatNo],
      });
    } else {
      const group = map.get(key);
      group.reservationSeqs.push(t.reservationSeq);
      if (t.seatNo && t.seatNo !== '-' && !group.seats.includes(t.seatNo)) {
        group.seats.push(t.seatNo);
      }
      group.rawPrice += t.rawPrice;
      if (!group.hasRealTicketNumber && t.hasRealTicketNumber) {
        group.hasRealTicketNumber = true;
        group.ticketNumber = t.ticketNumber;
      }
    }
  }

  return Array.from(map.values()).map((group) => ({
    ...group,
    seat: group.seats.join(', '),
    passenger: `${group.seatGrade} ${group.reservationSeqs.length}명`,
    price: `${group.rawPrice.toLocaleString()}원`,
  }));
};

export default function MyPage() {
  const navigate = useNavigate();
  const [activeMenu, setActiveMenu] = useState('ticket');

  // 실제 예매 목록 및 선택된 승차권 상태
  const [ticketList, setTicketList] = useState([]);
  const [ticketFilter, setTicketFilter] = useState('ALL'); // 'ALL' | 'ACTIVE' | 'CANCELLED'
  const [selectedTicketIndex, setSelectedTicketIndex] = useState(0);
  const [isLoadingTickets, setIsLoadingTickets] = useState(false);

  // 백엔드에서 내 예매 승차권 목록 조회
  const fetchMyTickets = useCallback(async () => {
    setIsLoadingTickets(true);
    try {
      // GET /api/reservation/me
      const res = await client.get('/reservation/me');
      if (Array.isArray(res.data) && res.data.length > 0) {
        const grouped = groupTickets(res.data);
        setTicketList(grouped);
        setSelectedTicketIndex(0);
      } else {
        setTicketList([]);
      }
    } catch (err) {
      console.error('승차권 목록 조회 실패:', err);
      setTicketList([]);
    } finally {
      setIsLoadingTickets(false);
    }
  }, []);

  const filteredTickets = ticketList.filter((t) => {
    if (ticketFilter === 'ACTIVE') return t.status !== 'CANCELLED';
    if (ticketFilter === 'CANCELLED') return t.status === 'CANCELLED';
    return true;
  });

  // 현재 화면에 표시할 선택된 승차권
  const activeTicket = filteredTickets.length > 0
    ? (filteredTickets[selectedTicketIndex] || filteredTickets[0])
    : null;

  // 승차권 예매 취소
  const handleCancelTicket = async () => {
    if (!activeTicket) return;
    if (!window.confirm(`정말 [${activeTicket.trainName} / 좌석: ${activeTicket.car} ${activeTicket.seat}] 예매를 취소하시겠습니까?`)) {
      return;
    }

    try {
      const rawSeqs = activeTicket.reservationSeqs?.length ? activeTicket.reservationSeqs : [activeTicket.reservationSeq];
      const seqs = Array.from(new Set(rawSeqs.filter(Boolean)));
      await Promise.all(seqs.map((seq) => client.post(`/reservation/${seq}/cancel`)));
      alert('승차권 예매가 정상적으로 취소되었습니다.');
      await fetchMyTickets();
    } catch (error) {
      console.error('승차권 예매 취소 에러:', error);
      alert(error.response?.data?.message || '승차권 예매 취소에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  // 찜하기 중앙 스토어 연동 (메인·시각표·마이페이지 공통)
  const { wishList, fetchWishList, toggleWish } = useWishStore();
  const [favorites, setFavorites] = useState([]);

  useEffect(() => {
    fetchWishList();
  }, [fetchWishList]);

  // 백엔드에서 받아온 찜 목록 번호(wishList)에 해당하는 실제 스케줄 상세 정보를 조회해 마이페이지에 표시
  useEffect(() => {
    if (!Array.isArray(wishList) || wishList.length === 0) {
      setFavorites([]);
      return;
    }

    const calcDuration = (depStr, arrStr) => {
      if (!depStr || !arrStr) return '-';
      const diffMs = new Date(arrStr).getTime() - new Date(depStr).getTime();
      if (isNaN(diffMs) || diffMs <= 0) return '-';
      const totalMins = Math.floor(diffMs / 60000);
      const hours = Math.floor(totalMins / 60);
      const mins = totalMins % 60;
      return hours > 0 ? `${hours}시간 ${mins}분` : `${mins}분`;
    };

    const loadFavoriteSchedules = async () => {
      try {
        const results = await Promise.all(
          wishList.map((seq) =>
            client
              .get(`/schedule/${seq}`)
              .then((res) => res.data)
              .catch((err) => {
                console.error('찜한 열차 상세 조회 실패:', seq, err);
                return null;
              })
          )
        );

        const parsed = results
          .filter((item) => item && item.schedule_seq != null)
          .map((item) => {
            const dep = formatDateTime(item.depplandtime);
            const arr = formatDateTime(item.arrplandtime);
            const type = item.vehiclekndnm || 'KTX';
            return {
              id: item.schedule_seq,
              scheduleSeq: item.schedule_seq,
              name: `${type} ${item.trainno || ''}`.trim(),
              type,
              depDate: dep.date,
              depTime: dep.time || '00:00',
              arrTime: arr.time || '00:00',
              duration: calcDuration(item.depplandtime, item.arrplandtime),
              depStation: item.depStationName || item.depplacename || '출발역',
              arrStation: item.arrStationName || item.arrplacename || '도착역',
              price: item.standard_price ? Number(item.standard_price).toLocaleString() : '59,800',
            };
          });

        setFavorites(parsed);
      } catch (err) {
        console.error('찜 목록 스케줄 변환 실패:', err);
      }
    };

    loadFavoriteSchedules();
  }, [wishList]);

  // 마이페이지 찜 삭제 클릭 시 백엔드 DELETE 및 화면 즉시 갱신
  const handleRemoveFavorite = (scheduleSeq) => {
    toggleWish(scheduleSeq);
  };

  const handleBookFavoriteTrain = (fav) => {
    navigate('/reservation', {
      state: {
        scheduleSeq: fav.scheduleSeq,
        trainName: fav.name,
        trainType: fav.type || 'KTX',
        depTime: fav.depTime,
        arrTime: fav.arrTime,
        duration: fav.duration,
        depStation: fav.depStation || '서울',
        arrStation: fav.arrStation || '부산',
        depDate: fav.depDate || '',
        passenger: '어른 1명',
        seatType: '일반실',
        price: fav.price,
      }
    });
  };

  const [originalUserInfo, setOriginalUserInfo] = useState({
    userId: '',
    name: '',
    email: '',
    phone: '',
    postcode: '',
    address: '',
    detailAddress: '',
  });

  const [editUserInfo, setEditUserInfo] = useState({ ...originalUserInfo });
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    const token = sessionStorage.getItem('token') || localStorage.getItem('token');
    if (!token) {
      alert('로그인이 필요한 서비스입니다.');
      navigate('/login');
      return;
    }

    const loadData = async () => {
      try {
        // 1. 프로필 조회
        const profileRes = await client.get('/member/mypage');
        if (profileRes.data) {
          const data = profileRes.data;
          const loadedInfo = {
            userId: data.id || data.member_id || '',
            name: data.name || '',
            email: data.email || '',
            phone: data.phone || '',
            postcode: data.zipcode || '',
            address: data.address1 || '',
            detailAddress: data.address2 || '',
          };
          setOriginalUserInfo(loadedInfo);
          setEditUserInfo(loadedInfo);
        }

        // 2. 승차권 목록 조회
        const ticketRes = await client.get('/reservation/me');
        if (Array.isArray(ticketRes.data) && ticketRes.data.length > 0) {
          const grouped = groupTickets(ticketRes.data);
          setTicketList(grouped);
          setSelectedTicketIndex(0);
        } else {
          setTicketList([]);
        }
      } catch (error) {
        console.error('마이페이지 초기 데이터 로딩 실패:', error);
        if (error.response?.status === 401) {
          alert('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
          sessionStorage.removeItem('token');
          sessionStorage.removeItem('id');
          navigate('/login');
        }
      } finally {
        setIsLoadingTickets(false);
      }
    };

    loadData();
  }, [navigate]);

  const [emailAuthStep, setEmailAuthStep] = useState(1);
  const [authCodeInput, setAuthCodeInput] = useState('');
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');

  const handleUserInfoChange = (e) => {
    const { name, value } = e.target;
    setEditUserInfo((prev) => ({ ...prev, [name]: value }));
  };

  const handleStartEdit = () => {
    setEditUserInfo({ ...originalUserInfo });
    setIsEditing(true);
  };

  const handleCancelEdit = () => {
    setEditUserInfo({ ...originalUserInfo });
    setIsEditing(false);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    try {
      // PUT /api/member
      await client.put('/member', {
        name: editUserInfo.name,
        email: editUserInfo.email,
        phone: editUserInfo.phone.replace(/[^0-9]/g, ''),
        zipcode: editUserInfo.postcode,
        address1: editUserInfo.address,
        address2: editUserInfo.detailAddress,
      });

      setOriginalUserInfo({ ...editUserInfo });
      setIsEditing(false);
      alert('회원 정보가 성공적으로 수정되었습니다.');
    } catch (error) {
      console.error('회원 정보 수정 에러:', error);
      alert('회원 정보 수정에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const handleOpenKakaoPostcode = () => {
    if (!isEditing) return;

    if (window.daum && window.daum.Postcode) {
      new window.daum.Postcode({
        oncomplete: (data) => {
          const fullAddress = data.roadAddress || data.jibunAddress;
          let extraAddress = '';

          if (data.bname !== '' && /[동|로|가]$/g.test(data.bname)) {
            extraAddress += data.bname;
          }
          if (data.buildingName !== '' && data.apartment === 'Y') {
            extraAddress += extraAddress !== '' ? `, ${data.buildingName}` : data.buildingName;
          }

          const finalAddress = extraAddress !== '' ? `${fullAddress} (${extraAddress})` : fullAddress;

          setEditUserInfo((prev) => ({
            ...prev,
            postcode: data.zonecode,
            address: finalAddress,
          }));

          const detailInput = document.getElementById('editDetailAddressInput');
          if (detailInput) {
            detailInput.focus();
          }
        },
      }).open();
    } else {
      alert('주소 검색 서비스를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.');
    }
  };

  const handleSendEmailAuth = async () => {
    try {
      const res = await client.post('/member/password/send-code');
      alert(res.data?.message || `회원님의 가입 이메일(${originalUserInfo.email})로 6자리 인증번호가 발송되었습니다.`);
      setEmailAuthStep(2);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '인증번호 발송에 실패했습니다.');
    }
  };

  const handleVerifyEmailCode = async () => {
    if (!authCodeInput || authCodeInput.trim().length !== 6) {
      alert('6자리 인증번호를 정확히 입력해 주세요.');
      return;
    }
    try {
      const res = await client.post('/member/password/verify', { code: authCodeInput.trim() });
      alert(res.data?.message || '이메일 본인인증이 완료되었습니다.\n새로운 비밀번호를 설정해 주세요.');
      setIsEmailVerified(true);
      setEmailAuthStep(3);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '인증번호가 일치하지 않거나 만료되었습니다.');
    }
  };

  const handleSavePassword = async (e) => {
    e.preventDefault();
    if (!isEmailVerified) {
      alert('먼저 이메일 인증을 완료해 주세요.');
      return;
    }
    if (newPassword !== confirmNewPassword) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    if (newPassword.length < 8) {
      alert('비밀번호는 최소 8자리 이상이어야 합니다.');
      return;
    }

    try {
      const res = await client.put('/member/password', { newPassword });
      alert(res.data?.message || '비밀번호가 성공적으로 변경되었습니다. 다음 로그인부터 적용됩니다.');
      setNewPassword('');
      setConfirmNewPassword('');
      setAuthCodeInput('');
      setIsEmailVerified(false);
      setEmailAuthStep(1);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || err.response?.data || '비밀번호 변경에 실패했습니다.');
    }
  };

  return (
    <div className="mypage-wrapper">
      <Header isLoggedIn={true} showTopBar={true} />

      <main className="mypage-container">
        <div className="mypage-title-row">
          <h1>마이페이지</h1>
          <p>승차권 확인 및 회원 정보를 관리할 수 있습니다.</p>
        </div>

        <div className="mypage-grid">
          <aside className="mypage-sidebar">
            <div className="profile-card">
              <span className="user-icon">TAGO MEMBER</span>
              <strong className="member-name">{originalUserInfo.name ? `${originalUserInfo.name} 님` : '회원 님'}</strong>
              <span className="member-id">@{originalUserInfo.userId || 'user'}</span>
            </div>

            <nav className="mypage-menu">
              <button 
                type="button" 
                className={`menu-btn ${activeMenu === 'ticket' ? 'active' : ''}`}
                onClick={() => setActiveMenu('ticket')}
              >
                발권 승차권 확인
              </button>
              <button 
                type="button" 
                className={`menu-btn ${activeMenu === 'favorites' ? 'active' : ''}`}
                onClick={() => setActiveMenu('favorites')}
              >
                찜하기 목록
              </button>
              <button 
                type="button" 
                className={`menu-btn ${activeMenu === 'profile' ? 'active' : ''}`}
                onClick={() => setActiveMenu('profile')}
              >
                회원 정보 관리
              </button>
            </nav>
          </aside>

          <section className="content-section">
            {activeMenu === 'ticket' && (
              <div className="ticket-display-card">
                <div className="card-top-header">
                  <div>
                    <h2>발권 승차권 조회</h2>
                    <p className="card-subtitle" style={{ margin: '4px 0 0', fontSize: '13px', color: '#666' }}>
                      동일 열차·호차의 좌석은 하나의 승차권으로 묶어 표시됩니다. 목록에서 승차권을 선택하세요.
                    </p>
                  </div>
                  <div className="korail-filter-tabs">
                    <button
                      type="button"
                      className={`korail-tab-btn ${ticketFilter === 'ALL' ? 'active' : ''}`}
                      onClick={() => { setTicketFilter('ALL'); setSelectedTicketIndex(0); }}
                    >
                      전체 ({ticketList.length})
                    </button>
                    <button
                      type="button"
                      className={`korail-tab-btn ${ticketFilter === 'ACTIVE' ? 'active' : ''}`}
                      onClick={() => { setTicketFilter('ACTIVE'); setSelectedTicketIndex(0); }}
                    >
                      발권완료 ({ticketList.filter((t) => t.status !== 'CANCELLED').length})
                    </button>
                    <button
                      type="button"
                      className={`korail-tab-btn ${ticketFilter === 'CANCELLED' ? 'active' : ''}`}
                      onClick={() => { setTicketFilter('CANCELLED'); setSelectedTicketIndex(0); }}
                    >
                      반환/취소 ({ticketList.filter((t) => t.status === 'CANCELLED').length})
                    </button>
                  </div>
                </div>

                {isLoadingTickets ? (
                  <div className="ticket-empty-card" style={{ padding: '40px 0' }}>
                    <p>승차권 정보를 불러오는 중입니다...</p>
                  </div>
                ) : filteredTickets.length > 0 && activeTicket ? (
                  <div className="korail-ticket-layout">
                    {/* 코레일 스타일 승차권 목록 테이블 */}
                    <div className="korail-table-scroll">
                      <table className="korail-ticket-table">
                        <thead>
                          <tr>
                            <th>승차일</th>
                            <th>열차</th>
                            <th>구간 (출발 → 도착)</th>
                            <th>호차 · 좌석</th>
                            <th>매수 / 운임</th>
                            <th>상태</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredTickets.map((ticket, idx) => {
                            const isSelected = (filteredTickets[selectedTicketIndex] ? selectedTicketIndex === idx : idx === 0);
                            return (
                              <tr
                                key={`${ticket.reservationSeq}_${idx}`}
                                className={isSelected ? 'selected' : ''}
                                onClick={() => setSelectedTicketIndex(idx)}
                              >
                                <td className="td-date">{ticket.depDate}</td>
                                <td className="td-train">
                                  <span className="korail-mini-badge">{ticket.trainType}</span>
                                  <strong>{ticket.trainName.replace(`${ticket.trainType} `, '')}</strong>
                                </td>
                                <td className="td-route">
                                  <span>{ticket.depStation}({ticket.depTime})</span>
                                  <span className="route-arrow">→</span>
                                  <span>{ticket.arrStation}({ticket.arrTime})</span>
                                </td>
                                <td className="td-seat">{ticket.car} · {ticket.seat}</td>
                                <td className="td-price">{ticket.reservationSeqs.length}매 / {ticket.price}</td>
                                <td className="td-status">
                                  <span className={`korail-status-pill ${ticket.status === 'CANCELLED' ? 'cancelled' : ticket.status === 'HELD' ? 'held' : 'confirmed'}`}>
                                    {ticket.status === 'CANCELLED' ? '반환완료' : ticket.status === 'HELD' ? '결제대기' : '발권완료'}
                                  </span>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>

                    {/* 코레일톡 스타일 모바일 승차권 상세 카드 */}
                    <div className="korail-ticket-card">
                      <div className="korail-ticket-top">
                        <div className="ticket-train-meta">
                          <span className="korail-ktx-pill">{activeTicket.trainType}</span>
                          <strong className="korail-train-title">{activeTicket.trainName}</strong>
                        </div>
                        <div className="korail-top-right">
                          <span className="korail-ticket-date">{activeTicket.depDate}</span>
                          <span className={`korail-status-pill ${activeTicket.status === 'CANCELLED' ? 'cancelled' : activeTicket.status === 'HELD' ? 'held' : 'confirmed'}`}>
                            {activeTicket.status === 'CANCELLED' ? '반환완료' : activeTicket.status === 'HELD' ? '결제대기' : '발권완료'}
                          </span>
                        </div>
                      </div>

                      <div className="korail-ticket-body">
                        <div className="korail-route-row">
                          <div className="korail-station">
                            <span className="korail-station-name">{activeTicket.depStation}</span>
                            <strong className="korail-station-time">{activeTicket.depTime}</strong>
                          </div>
                          <div className="korail-route-mid">
                            <span className="korail-route-tag">직통</span>
                            <div className="korail-route-line">
                              <span className="dot" />
                              <span className="arrow">▶</span>
                            </div>
                          </div>
                          <div className="korail-station arr">
                            <span className="korail-station-name">{activeTicket.arrStation}</span>
                            <strong className="korail-station-time">{activeTicket.arrTime}</strong>
                          </div>
                        </div>

                        <div className="korail-perforation" />

                        <div className="korail-detail-grid">
                          <div className="korail-detail-cell">
                            <span className="cell-label">호차</span>
                            <strong className="cell-val blue">{activeTicket.car}</strong>
                          </div>
                          <div className="korail-detail-cell">
                            <span className="cell-label">좌석번호</span>
                            <strong className="cell-val blue">{activeTicket.seat}</strong>
                          </div>
                          <div className="korail-detail-cell">
                            <span className="cell-label">승객 / 등급</span>
                            <strong className="cell-val">{activeTicket.passenger}</strong>
                          </div>
                          <div className="korail-detail-cell">
                            <span className="cell-label">결제 운임</span>
                            <strong className="cell-val">{activeTicket.price}</strong>
                          </div>
                        </div>

                        <div className="korail-qr-footer">
                          <div className="qr-box">
                            <div className="qr-fake-visual korail-qr">
                              <span>■ ■ ■ ■ ■<br/>■ □ □ □ ■<br/>■ □ ■ □ ■<br/>■ □ □ □ ■<br/>■ ■ ■ ■ ■</span>
                            </div>
                            <div className="korail-qr-meta">
                              <span className="korail-qr-hint">개찰구 QR 리더기에 스캔하여 탑승하세요.</span>
                              <span className="korail-ticket-no">승차권 번호: {activeTicket.ticketNumber}</span>
                            </div>
                          </div>
                          <div className="ticket-actions">
                            <button 
                              type="button" 
                              className="btn-korail-outline"
                              onClick={() => alert(`[영수증 정보]\n승차권: ${activeTicket.ticketNumber}\n열차: ${activeTicket.trainName}\n구간: ${activeTicket.depStation} -> ${activeTicket.arrStation}\n좌석: ${activeTicket.car} ${activeTicket.seat}\n결제금액: ${activeTicket.price}`)}
                            >
                              영수증 조회
                            </button>
                            {activeTicket.status === 'CANCELLED' ? (
                              <button type="button" className="btn-korail-disabled" disabled>
                                반환 완료
                              </button>
                            ) : (
                              <button type="button" className="btn-korail-danger" onClick={handleCancelTicket}>
                                승차권 반환
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="ticket-empty-card">
                    <p>현재 발권된 승차권 내역이 없습니다.</p>
                    <button type="button" className="btn-go-booking" onClick={() => navigate('/')}>
                      승차권 예매하기 →
                    </button>
                  </div>
                )}
              </div>
            )}

            {activeMenu === 'favorites' && (
              <div className="favorites-display-card">
                <div className="card-top-header">
                  <div>
                    <div className="fav-header-title-row">
                      <h2>찜하기 목록</h2>
                      <span className="fav-storage-tag">관심 열차 보관함</span>
                    </div>
                    <p className="card-subtitle">
                      자주 이용하거나 찜해두신 KTX 열차를 한눈에 확인하고 빠르게 예매할 수 있습니다.
                    </p>
                  </div>
                  <span className="favorites-count-badge">총 {favorites.length}편성 등록됨</span>
                </div>

                {favorites.length > 0 ? (
                  <div className="favorites-list-container">
                    {favorites.map((train) => (
                      <div key={train.id || train.name} className="favorite-train-card">
                        <div className="fav-card-header">
                          <div className="fav-header-left">
                            <span className="badge-ktx">{train.type || 'KTX'}</span>
                            <strong className="fav-train-no">{train.name}</strong>
                            <span className="fav-seat-class">1~18호차 일반실</span>
                            <span className="fav-direct-tag">직통</span>
                          </div>
                          <div className="fav-header-right">
                            <span className="fav-heart-status">♥ 찜 등록됨</span>
                          </div>
                        </div>

                        <div className="fav-card-body">
                          <div className="fav-station-col dep">
                            <span className="station-name">{train.depStation || '서울'}</span>
                            <span className="station-time">{train.depTime}</span>
                          </div>

                          <div className="fav-timeline-col">
                            <span className="fav-duration-chip">⏱ {train.duration}</span>
                            <div className="fav-track-line">
                              <span className="track-dot start"></span>
                              <div className="track-bar"></div>
                              <span className="track-train-icon">🚆</span>
                              <div className="track-bar"></div>
                              <span className="track-dot end"></span>
                            </div>
                          </div>

                          <div className="fav-station-col arr">
                            <span className="station-name">{train.arrStation || '부산'}</span>
                            <span className="station-time">{train.arrTime}</span>
                          </div>

                          <div className="fav-fare-col">
                            <span className="fare-type">일반실 운임</span>
                            <strong className="fare-price">{train.price}원</strong>
                          </div>
                        </div>

                        <div className="fav-card-footer">
                          <span className="fav-footer-hint">실시간 잔여 좌석을 바로 확인하고 예매할 수 있습니다.</span>
                          <div className="fav-footer-actions">
                            <button 
                              type="button" 
                              className="btn-fav-remove"
                              onClick={() => handleRemoveFavorite(train.scheduleSeq || train.id)}
                              title="관심 열차 목록에서 삭제"
                            >
                              찜 삭제
                            </button>
                            <button 
                              type="button" 
                              className="btn-fav-book"
                              onClick={() => handleBookFavoriteTrain(train)}
                            >
                              좌석 선택 및 바로 예매 →
                            </button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="favorites-empty-box">
                    <div className="empty-heart-icon">♥</div>
                    <p>등록된 관심 KTX 열차가 없습니다.</p>
                    <span className="empty-sub">
                      메인페이지의 실시간 운행 현황이나 운행 시각표에서 하트(♡)를 눌러 자주 타는 열차를 찜해 보세요.
                    </span>
                    <button 
                      type="button" 
                      className="btn-go-schedule"
                      onClick={() => navigate('/schedule')}
                    >
                      KTX 열차 시각표 보러가기 →
                    </button>
                  </div>
                )}
              </div>
            )}

            {activeMenu === 'profile' && (
              <div className="profile-edit-container">
                <div className="edit-card">
                  <div className="card-top-header">
                    <div>
                      <h2>회원 기본 정보</h2>
                      <p className="card-subtitle">
                        {isEditing ? '회원 정보를 수정한 후 [수정 완료]를 눌러주세요.' : '[회원정보 수정하기]를 누르면 정보를 변경할 수 있습니다.'}
                      </p>
                    </div>
                    {!isEditing && (
                      <button type="button" className="btn-start-edit" onClick={handleStartEdit}>
                        회원정보 수정하기
                      </button>
                    )}
                  </div>

                  <form onSubmit={handleSaveProfile} className="profile-form">
                    <div className="form-group">
                      <label>아이디</label>
                      <div className="input-wrap disabled">
                        <input type="text" value={editUserInfo.userId} disabled placeholder="아이디" />
                      </div>
                      <span className="form-note">아이디는 고유 식별자로 변경할 수 없습니다.</span>
                    </div>

                    <div className="form-group">
                      <label>이름</label>
                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`}>
                        <input 
                          type="text" 
                          name="name" 
                          value={editUserInfo.name} 
                          onChange={handleUserInfoChange} 
                          disabled={!isEditing}
                          placeholder="이름"
                          required 
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <label>이메일</label>
                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`}>
                        <input 
                          type="email" 
                          name="email" 
                          value={editUserInfo.email} 
                          onChange={handleUserInfoChange} 
                          disabled={!isEditing}
                          placeholder="이메일"
                          required 
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <label>휴대전화 번호</label>
                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`}>
                        <input 
                          type="tel" 
                          name="phone" 
                          value={editUserInfo.phone} 
                          onChange={handleUserInfoChange} 
                          disabled={!isEditing}
                          placeholder="휴대전화 번호"
                          required 
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <label>주소</label>
                      <div className="postcode-row">
                        <div className={`input-wrap postcode-input-wrap ${!isEditing ? 'read-only' : ''}`}>
                          <input 
                            type="text" 
                            name="postcode" 
                            value={editUserInfo.postcode} 
                            readOnly 
                            placeholder="우편번호"
                          />
                        </div>
                        {isEditing && (
                          <button 
                            type="button" 
                            className="btn-address-search" 
                            onClick={handleOpenKakaoPostcode}
                          >
                            우편번호 찾기
                          </button>
                        )}
                      </div>

                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`} style={{ marginTop: '8px' }}>
                        <input 
                          type="text" 
                          name="address" 
                          value={editUserInfo.address} 
                          readOnly 
                          placeholder="기본 주소"
                        />
                      </div>

                      <div className={`input-wrap ${!isEditing ? 'read-only' : ''}`} style={{ marginTop: '8px' }}>
                        <input 
                          type="text" 
                          id="editDetailAddressInput"
                          name="detailAddress" 
                          value={editUserInfo.detailAddress} 
                          onChange={handleUserInfoChange} 
                          placeholder="상세 주소를 입력하세요"
                          disabled={!isEditing}
                        />
                      </div>
                    </div>

                    {isEditing && (
                      <div className="edit-action-row">
                        <button type="submit" className="btn-save-profile">
                          수정 완료
                        </button>
                        <button type="button" className="btn-cancel-profile" onClick={handleCancelEdit}>
                          취소
                        </button>
                      </div>
                    )}
                  </form>
                </div>

                <div className="edit-card" style={{ marginTop: '24px' }}>
                  <div className="card-top-header">
                    <div>
                      <h2>비밀번호 변경 (이메일 인증)</h2>
                      <p className="card-subtitle">안전한 계정 보호를 위해 등록된 이메일 본인인증 후 비밀번호를 변경할 수 있습니다.</p>
                    </div>
                  </div>

                  <form onSubmit={handleSavePassword} className="profile-form">
                    <div className="form-group">
                      <label>인증 대상 이메일</label>
                      <div className="email-auth-row">
                        <div className="input-wrap flex-1 disabled">
                          <input type="email" value={originalUserInfo.email} disabled placeholder="인증 대상 이메일" />
                        </div>
                        <button 
                          type="button" 
                          className="btn-email-auth"
                          onClick={handleSendEmailAuth}
                          disabled={isEmailVerified || !originalUserInfo.email}
                        >
                          {isEmailVerified ? '인증 완료됨' : emailAuthStep === 1 ? '인증번호 발송' : '재발송'}
                        </button>
                      </div>
                    </div>

                    {emailAuthStep >= 2 && !isEmailVerified && (
                      <div className="form-group auth-code-group">
                        <label>이메일 인증번호 6자리</label>
                        <div className="email-auth-row">
                          <div className="input-wrap flex-1">
                            <input 
                              type="text" 
                              maxLength="6"
                              placeholder="인증번호 6자리를 입력하세요" 
                              value={authCodeInput}
                              onChange={(e) => setAuthCodeInput(e.target.value)}
                            />
                          </div>
                          <button 
                            type="button" 
                            className="btn-email-verify"
                            onClick={handleVerifyEmailCode}
                          >
                            인증 확인
                          </button>
                        </div>
                        <span className="auth-hint">이메일 수신함에서 인증번호를 확인해 주세요.</span>
                      </div>
                    )}

                    {isEmailVerified && (
                      <div className="new-password-section">
                        <div className="verified-badge-row">
                          <span className="verified-icon">✓</span>
                          <span>이메일 본인인증이 정상적으로 완료되었습니다.</span>
                        </div>

                        <div className="form-group" style={{ marginTop: '14px' }}>
                          <label>새 비밀번호</label>
                          <div className="input-wrap">
                            <input 
                              type="password" 
                              placeholder="새로운 비밀번호 (8자리 이상)" 
                              value={newPassword}
                              onChange={(e) => setNewPassword(e.target.value)}
                              required 
                            />
                          </div>
                        </div>

                        <div className="form-group">
                          <label>새 비밀번호 확인</label>
                          <div className="input-wrap">
                            <input 
                              type="password" 
                              placeholder="새로운 비밀번호 재입력" 
                              value={confirmNewPassword}
                              onChange={(e) => setConfirmNewPassword(e.target.value)}
                              required 
                            />
                          </div>
                        </div>

                        <button type="submit" className="btn-save-profile">
                          비밀번호 변경 완료
                        </button>
                      </div>
                    )}
                  </form>
                </div>
              </div>
            )}
          </section>
        </div>
      </main>

      <QuickMenu />
      <Footer />
    </div>
  );
}
