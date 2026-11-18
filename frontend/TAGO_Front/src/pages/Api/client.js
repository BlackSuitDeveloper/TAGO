import axios from "axios"

const client = axios.create({
    baseURL: "http://localhost:80/api",
});

client.interceptors.request.use((config) => {
    const token = sessionStorage.getItem("token") || localStorage.getItem("token");
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config;
})

export default client;
