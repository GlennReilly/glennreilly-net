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
scripts/server-setup.sh   manual equivalent of cloud-init.yaml (if user data fails)
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

1. Copy `templates/ktor-demo/Dockerfile` (or `templates/static-build-demo/Dockerfile`) and `templates/ktor-demo/.github/workflows/publish.yml` into the demo repo. In `publish.yml`, set `SERVICE:` to the slug and `HEALTH_URL:` to a URL that answers 200 when the demo works (`/health` if it has one).
2. Give the demo its own deploy key, restricted on the Droplet to deploying that one service, and add the three `DEPLOY_*` secrets to the demo repo (see [Secrets](#secrets) below). Never give a demo repo this repo's key.
3. Push to `main`. The workflow builds and pushes `ghcr.io/glennreilly/<slug>:<sha>`. **It doesn't deploy:** pushes to `main` never go live.
4. Release by fast-forwarding the `production` branch: `git push origin origin/main:production`. That run also pushes `:latest`, SSHes in to run `deploy.sh <slug>`, and waits for `HEALTH_URL`. The first time, push this repo's Caddy and compose changes before the deploy runs, or re-run the deploy afterwards.
5. Once it's up, set `"status": "live"` in `sites/root/demos.json` and push this repo.

Only `production` pushes the `:latest` tag. The Droplet runs `:latest`, and every push to this repo runs `deploy.sh all`, which pulls it, so a demo's `main` must never push `:latest`. Roll back a demo with `git push --force origin <good-sha>:production` in its repo.

**Private demo repos:** GHCR packages inherit the repo's visibility, so the Droplet can't pull a private one anonymously. Either make the *package* public (GitHub → Packages → year-round → Package settings → Change visibility; the source stays private) or log the Droplet in once:

```bash
ssh deploy@glennreilly.net 'echo <PAT with read:packages> | docker login ghcr.io -u GlennReilly --password-stdin'
```

### year-round specifics

- The Dockerfile assumes a Gradle `application` module named `server`. Change `ARG SERVER_MODULE` if it differs, or set it to empty for a single-module build.
- The Ktor server must bind `0.0.0.0` and read `PORT`; see `templates/ktor-demo/Application.kt`.
- Then uncomment the `year-round` blocks in `Caddyfile` and `compose.yaml` (or delete them and run the scaffold script).

## Secrets

The `deploy` user is in the `docker` group, which makes any key it accepts **root-equivalent on the whole Droplet**. So:

- **This repo** holds the only unrestricted key. It needs it to rsync files and run `deploy.sh all`.
- **Each container-demo repo** gets its own key, which the Droplet accepts only for `deploy.sh <slug>`. A leaked demo secret can then restart that one demo and nothing else.

Never store a deploy key as a user- or organisation-level secret: every repo would get root on the Droplet.

| Secret | This repo | Each demo repo |
|---|---|---|
| `DEPLOY_HOST` | Droplet IPv4 address | same |
| `DEPLOY_KNOWN_HOSTS` | Output of `ssh-keyscan -t ed25519 <droplet-ip>` | same |
| `DEPLOY_SSH_KEY` | Private key whose public half is `__DEPLOY_PUBLIC_KEY__` in `cloud-init.yaml` | The demo's own private key (below) |

A demo's key:

```bash
ssh-keygen -t ed25519 -N '' -C <slug>-deploy -f ~/.ssh/<slug>-deploy
gh secret set DEPLOY_SSH_KEY -R GlennReilly/<repo> < ~/.ssh/<slug>-deploy
```

Add the public key to `/home/deploy/.ssh/authorized_keys` on the Droplet (append; don't replace), restricted to the one command:

```
command="bash /opt/demos/scripts/deploy.sh <slug>",restrict ssh-ed25519 AAAA… <slug>-deploy
```

Also add the same line to `cloud-init.yaml` (with a placeholder for the key), so a rebuilt Droplet accepts it. With `command=`, sshd runs that command whatever the client asks for, which is exactly what the demo workflow sends. Check a key with `ssh -i ~/.ssh/<slug>-deploy deploy@glennreilly.net true`: it should run the deploy, not `true`.

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
