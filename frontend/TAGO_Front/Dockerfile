# ==========================================
# Stage 1: Vite Production Build
# ==========================================
FROM node:22.23.5-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 운영 환경에서는 같은 오리진(/api/v1)으로 요청 -> Nginx가 백엔드로 프록시
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ==========================================
# Stage 2: Nginx Static Server + Reverse Proxy
# ==========================================
FROM nginx:1.30.8-alpine

RUN rm -rf /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
