FROM node:22-alpine

WORKDIR /app

COPY package*.json ./

RUN npm ci --ignore-scripts

COPY . .

# npm build
RUN npm run build

# prod environment
FROM nginxinc/nginx-unprivileged:stable-alpine
ARG DOCROOT=/usr/share/nginx/html
COPY . ${DOCROOT}
RUN find ${DOCROOT} -type d -print0 | xargs -0 chmod 755 && \
    find ${DOCROOT} -type f -print0 | xargs -0 chmod 644 && \
    chmod 755 ${DOCROOT}

USER nginx

EXPOSE 8080

# nginx 서버를 실행하고 백그라운드로 동작하도록 한다.
CMD ["nginx", "-g", "daemon off;"]