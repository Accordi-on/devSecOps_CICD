#########################
# 1단계: build step
#########################
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 설치
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

# 소스 복사
COPY . .

# 프로덕션용 정적 빌드 생성
RUN npm run build
# => /app/build 에 최종 산출물 생성됨

#########################
# 2단계: runtime step
#########################
FROM nginx:alpine
# nginx가 기본적으로 /usr/share/nginx/html 에서 정적 파일을 서빙함
COPY --from=build /app/build /usr/share/nginx/html

# 포트 80을 노출
EXPOSE 80

# nginx 포그라운드 실행
CMD ["nginx", "-g", "daemon off;"]
