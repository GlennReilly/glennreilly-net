# glennreilly.net

Hosting for a collection of experiments, each on its own subdomain and in whatever tech suits it.

```
                       ┌──────────── DigitalOcean Droplet (Ubuntu 24.04, Docker) ────────────┐
 glennreilly.net ─┐    │                                                                     │
 hello.… ─────────┼──► │  Caddy :443  ── auto HTTPS (Let's Encrypt) per hostname              │
 year-round.… ────┘    │    ├─ glennreilly.net      → /srv/root      (landing page)          │
  (DO DNS: @ and *     │    ├─ hello.…              → /srv/hello     (static files)          │
   → Droplet IP)       │    └─ year-round.…         → year-round:8080 (container from GHCR)  │
                       └─────────────────────────────────────────────────────────────────────┘
```

There are two kinds of demo:

| Kind | Lives in | Good for |
|---|---|---|
| **static** | `sites/<slug>/` in this repo | Hand-written HTML/JS, tiny experiments |
| **container** | Its own repo, which publishes `ghcr.io/glennreilly/<slug>` | Anything with a build or a server: Ktor, Spring, Node, Python, Vite/React, Kotlin/Wasm… |

## Layout

```
Caddyfile                 hostname → files or container
compose.yaml              Caddy + one service per container demo
sites/root/               landing page; demos.json drives the cards
sites/<slug>/             static demos
scripts/deploy.sh         runs on the Droplet: pull, up, reload Caddy
cloud-init.yaml           first-boot setup for the Droplet
.github/workflows/        deploys this repo on push to main
templates/ktor-demo/      Dockerfile + publish workflow + Ktor notes for a demo repo
templates/static-build-demo/  Dockerfile for build-then-static demos
tools/NewDemo.main.kts    scaffolds a new demo (Kotlin script, no dependencies)
```

## Adding a demo

```bash
# static
kotlin tools/NewDemo.main.kts tide-clock static "Tide Clock" "Tides as a clock face" "HTML,SVG"

# container (a separate repo)
kotlin tools/NewDemo.main.kts my-app container "My App" "What it does" "Kotlin,Ktor" 8080
```

The script adds the Caddy block, the compose service (container demos), the `sites/<slug>/` folder (static demos) and the landing-page card. Commit and push, and the infra workflow deploys it. Caddy fetches the new certificate on the first request; the wildcard DNS record already covers the hostname.

For a **container** demo, also:

1. Copy `templates/ktor-demo/Dockerfile` (or `templates/static-build-demo/Dockerfile`) and `templates/ktor-demo/.github/workflows/publish.yml` into the demo repo. Set `SERVICE:` in `publish.yml` to the slug.
2. Add the three `DEPLOY_*` secrets to the demo repo (below).
3. Push. The workflow builds and pushes `ghcr.io/glennreilly/<slug>`, then SSHes in and runs `deploy.sh <slug>`.
4. Once the first image exists, set `"status": "live"` in `sites/root/demos.json` and push this repo.

**Private demo repos:** GHCR packages inherit the repo's visibility, so the Droplet can't pull a private one anonymously. Either make the *package* public (GitHub → Packages → year-round → Package settings → Change visibility; the source stays private) or log the Droplet in once:

```bash
ssh deploy@glennreilly.net 'echo <PAT with read:packages> | docker login ghcr.io -u GlennReilly --password-stdin'
```

### year-round specifics

- The Dockerfile assumes a Gradle `application` module named `server`. Change `ARG SERVER_MODULE` if it differs, or set it to empty for a single-module build.
- The Ktor server must bind `0.0.0.0` and read `PORT`; see `templates/ktor-demo/Application.kt`.
- Then uncomment the `year-round` blocks in `Caddyfile` and `compose.yaml` (or delete them and run the scaffold script).

## Secrets (this repo and every container-demo repo)

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | Droplet IPv4 address |
| `DEPLOY_SSH_KEY` | Private key whose public half is in `cloud-init.yaml` for the `deploy` user |
| `DEPLOY_KNOWN_HOSTS` | Output of `ssh-keyscan -t ed25519 <droplet-ip>` |

Tip: set them once as **organisation/user-level** secrets if you move the repos into an org. Otherwise `gh secret set DEPLOY_HOST -R GlennReilly/<repo>` makes it quick.

## Day-2 operations

```bash
ssh root@glennreilly.net                        # your own key, added at Droplet creation
cd /opt/demos && docker compose ps              # what's running
docker compose logs -f caddy                    # cert issuance, access errors
docker compose logs -f year-round
bash scripts/deploy.sh all                      # redeploy everything
```

- Security updates install automatically (unattended-upgrades). Reboot occasionally for kernel updates: `sudo reboot`. Everything restarts on boot (`restart: unless-stopped`).
- The firewall (ufw) allows only 22, 80 and 443.
- Running short on memory? Each JVM demo uses roughly 150–350 MB. Resize the Droplet in the DO console (CPU/RAM only, so you can scale back down) or lower `mem_limit`.
- Enable DigitalOcean **Backups** (+20%) or take a snapshot before big changes. Everything except `.env` and the Caddy cert volume is rebuilt from git anyway.
