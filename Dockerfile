#########################
# 1단계: build (React 빌드)
#########################
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --ignore-scripts

COPY . .
RUN npm run build
# => /app/build 에 정적 파일 생성됨

#########################
# 2단계: runtime (비루트 nginx)
#########################
FROM nginxinc/nginx-unprivileged:stable-alpine

# 위에서 "AS build" 라고 이름 붙인 스테이지의 결과물을 복사
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 8080
