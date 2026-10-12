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
# Serve on 8080. Pages fall back to index.html (SPA), but a missing file under /assets/ is a plain 404:
# after a redeploy, an open tab asking for an old hashed bundle must fail cleanly, not get HTML back.
# (try_files takes no matcher, so this is its expanded form.)
RUN printf ':8080 {\n\troot * /srv\n\t@page {\n\t\tnot path /assets/*\n\t\tfile {path} /index.html\n\t}\n\trewrite @page {file_match.relative}\n\tfile_server\n}\n' > /etc/caddy/Caddyfile
EXPOSE 8080
