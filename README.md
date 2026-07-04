# Portfolio — Buddhaprakash Patil

Personal site for backend-engineering work: case studies, projects, skills. The site itself is
a portfolio piece — a Spring Boot API with a static frontend, built the way production services
are built.

## Architecture

```
frontend/   → GitHub Pages (static, works standalone)
backend/    → Spring Boot 3 API on Render free tier (Docker)
              /api/profile    profile + case-study JSON
              /api/contact    rate-limited contact form
              /api/visitors   visit counter
              /swagger-ui     live OpenAPI docs
              /actuator/health
```

The frontend bakes all content in and treats the API as progressive enhancement — Render's
free tier cold-starts (~1 min), and the site must never look broken while that happens.

## Run locally

```bash
# backend
cd backend && mvn spring-boot:run     # → localhost:8080/swagger-ui

# frontend — any static server
cd frontend && python -m http.server 5500
```

## Deploy

1. Push to GitHub → `deploy-pages.yml` publishes `frontend/` to GitHub Pages.
2. Connect the repo on [render.com](https://render.com) → `render.yaml` builds `backend/Dockerfile`.
3. Update `API` constant in `frontend/index.html` with the Render URL.
4. Set `ALLOWED_ORIGINS` env var on Render to the GitHub Pages origin.
