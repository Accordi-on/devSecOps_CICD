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

# 1. 비루트 유저/그룹 명시적으로 만들기 (uid/gid 고정해주면 k8s securityContext랑도 잘 맞음)
RUN addgroup -g 1001 -S web && \
    adduser  -S -D -H -u 1001 -G web web && \
    rm /etc/nginx/conf.d/default.conf && \
    mkdir -p /usr/share/nginx/html && \
    chown -R web:web /usr/share/nginx /var/cache/nginx /var/run /var/log/nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 3. 정적 파일 복사 (이건 root일 때 해야 함)
COPY --from=build /app/build /usr/share/nginx/html

# 4. nginx가 쓸 디렉토리들 권한을 비루트 유저에게 넘겨줌
RUN chown -R web:web /usr/share/nginx /var/cache/nginx /var/run /var/log/nginx

# 5. 이제부터는 비루트 유저로 동작
USER 1001

# 6. Nginx는 이제 8080에서 listen 하므로 8080 노출
EXPOSE 8080

# 7. 포그라운드로 nginx 실행
CMD ["nginx", "-g", "daemon off;"]