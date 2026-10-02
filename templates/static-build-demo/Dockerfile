# For demos that need a build step but no server at runtime
# (Vite/React/Svelte, Kotlin/JS or Compose for Web/Wasm, Astro, ...).
# Use the same publish.yml as templates/ktor-demo; just change SERVICE.

FROM node:22-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build          # Kotlin/JS: swap for a gradle stage running jsBrowserDistribution

FROM caddy:2-alpine
COPY --from=build /src/dist /srv
# Serve on 8080; SPA fallback to index.html.
RUN printf ':8080 {\n\troot * /srv\n\ttry_files {path} /index.html\n\tfile_server\n}\n' > /etc/caddy/Caddyfile
EXPOSE 8080
