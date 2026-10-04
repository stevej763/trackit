# syntax=docker/dockerfile:1

# Stage 1: build the static bundle.
FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .

# No VITE_* build args: the app calls /api on its own origin, so the same bundle
# works on localhost, a LAN address or a domain with no rebuild.
RUN npm run build

# Stage 2: serve it, and proxy /api to the api service.
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
COPY security-headers.conf /etc/nginx/security-headers.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
