# 1. Base image
FROM node:22-alpine AS build

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 의존성 정의 파일만 먼저 복사 (캐시 포인트)
COPY package*.json ./

# 4. npm 캐시 폴더 지정 (optional, 빌드 속도 + 저장 공간 개선)
ENV NPM_CONFIG_CACHE=/tmp/.npm-cache

# 5. 의존성 설치 (여기까지는 변경이 거의 없음 → 캐시 유지)
RUN npm ci --only=production

# 6. 나머지 소스 코드 복사 (변경 가능 부분)
COPY . .

# 7. 포트 노출
EXPOSE 80

# 8. 실행 명령
CMD ["npm", "start"]