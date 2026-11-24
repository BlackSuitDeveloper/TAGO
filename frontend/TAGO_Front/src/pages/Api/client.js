import axios from "axios"

const client = axios.create({
    baseURL: "http://localhost:80/api/v1",
});

client.interceptors.request.use((config) => {
    const token = sessionStorage.getItem("token") || localStorage.getItem("token");
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config;
})

// 서버 응답은 { success, data, message?, totalCount? } 로 감싸져 온다 (백엔드 ResWrapper).
// 여기서 한 번 풀어서, 호출하는 쪽은 res.data 를 본문으로 쓴다.
//   res.data       : 본문 (객체 / 배열 / true·false, 본문이 없는 응답은 undefined)
//   res.totalCount : 목록 응답의 전체 건수
//   res.message    : 안내 문구
// 에러는 error.response.data 가 { success:false, status, message } 로 온다.
client.interceptors.response.use((res) => {
    const body = res.data;
    if (body && typeof body === "object" && typeof body.success === "boolean") {
        res.message = body.message;
        res.totalCount = body.totalCount;
        res.data = body.data;
    }
    return res;
});

export default client;
