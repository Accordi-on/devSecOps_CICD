#########################
# 1단계: build step
#########################
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY . .
RUN npm run build
# => /app/build 에 정적 파일 생성

#########################
# 2단계: runtime step (non-root nginx)
#########################
FROM nginx:alpine

# 1) 비루트 유저 생성 (uid/gid 1001)
# 2) 기본 conf 제거
# 3) static root 보장
RUN addgroup -g 1001 -S web && \
    adduser  -S -D -H -u 1001 -G web web && \
    rm /etc/nginx/conf.d/default.conf && \
    mkdir -p /usr/share/nginx/html && \
    mkdir -p /var/cache/nginx /var/log/nginx && \
    chown -R web:web /usr/share/nginx/html /var/cache/nginx /var/log/nginx

# 커스텀 nginx 설정 (8080 listen)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 정적 파일 복사
COPY --from=build /app/build /usr/share/nginx/html

# 복사된 빌드 산출물도 권한 소유자 맞춰주기
RUN chown -R web:web /usr/share/nginx/html

# 이제부터 비루트 유저로 실행
USER 1001

# nginx는 8080에서 listen (nginx.conf 기준)
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
