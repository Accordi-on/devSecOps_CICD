FROM node:22-alpine

WORKDIR /app

COPY package*.json ./

RUN npm ci --ignore-scripts

COPY . .

# npm build
RUN npm run build

# prod environment
FROM nginxinc/nginx-unprivileged:stable-alpine

COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 8080