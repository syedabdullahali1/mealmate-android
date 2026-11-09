# MealMate

MealMate is a Kotlin/Jetpack Compose Android meal diary and calorie tracker with a FastAPI backend, PostgreSQL database, and offline Python nutrition insights powered by Chaquopy.

## Features

- Account registration and sign-in; API-issued bearer tokens are stored on-device.
- Dashboard with today's calorie and macro totals.
- Meal diary to add and delete breakfast, lunch, dinner, and snack entries.
- Calorie and macro tracker with local, rule-based Python insights.
- Per-user API data access, password hashing, and PostgreSQL persistence.

## Start the backend

Install Docker Compose, then run from the project root:

```bash
# Set production-quality secrets before deployment.
export POSTGRES_PASSWORD='choose-a-strong-password'
export JWT_SECRET='choose-a-long-random-secret'
docker compose up --build
```

The API is at `http://localhost:8000`; interactive API docs are at `http://localhost:8000/docs`. A lightweight local alternative is to install `backend/requirements.txt` and run `uvicorn main:app --app-dir backend --reload` from the project root (uses a local SQLite database).

## Build and run Android

Open this repository in Android Studio (JDK 17; Android SDK 34), allow Gradle sync, start an emulator, and run the `app` configuration. The default API URL in `app/build.gradle` is `http://10.0.2.2:8000/`, which routes from the standard Android emulator to the host machine. For a physical device, change `API_BASE_URL` to your computer's reachable LAN address and ensure the device can reach port 8000. The sample enables cleartext HTTP for local development; use HTTPS and disable cleartext traffic before release.

Chaquopy embeds Python 3.11 and loads `app/src/main/python/nutrition.py` for offline nutrition suggestions. No Python server is needed on the phone.

## API overview

- `POST /auth/register`, `POST /auth/login`
- `GET /meals`, `POST /meals`, `DELETE /meals/{id}`
- `GET /summary`, `GET /health`

Meal endpoints and summaries require `Authorization: Bearer <access_token>`. Configure strong secrets and HTTPS for production. The calorie reference and nutrition insights are general wellness information, not medical advice.
