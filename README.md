# BekalBu

BekalBu is a Kotlin/Jetpack Compose Android meal diary and calorie tracker with a FastAPI backend, PostgreSQL database, and offline Python nutrition insights powered by Chaquopy.

## Features

- Account registration and sign-in; API-issued bearer tokens are stored on-device.
- Dashboard with today's calorie and macro totals.
- Meal diary to add and delete meal entries.
- Calorie and macro tracker with local Python nutrition insights.
- Per-user API data access, password hashing, and PostgreSQL persistence.

## Start the backend

Install Docker Compose, then run from the project root:

```bash
export POSTGRES_PASSWORD='choose-a-strong-password'
export JWT_SECRET='choose-a-long-random-secret'
docker compose up --build
```

The API is at `http://localhost:8000`; interactive API docs are at `http://localhost:8000/docs`. For a local alternative, install `backend/requirements.txt` and run `uvicorn main:app --app-dir backend --reload` from the project root.

## Build and run Android

Open this repository in Android Studio with JDK 17 and Android SDK 34, allow Gradle sync, start an emulator, and run the `app` configuration. The Android namespace and application ID are both `com.bekalbu.app`. Kotlin sources are in `app/src/main/java/com/bekalbu/app/` and the installed app label is BekalBu.

The default API URL in `app/build.gradle` is `http://10.0.2.2:8000/`, which routes from the standard Android emulator to the host machine. For a physical device, change `API_BASE_URL` to your computer's reachable LAN address and ensure the device can reach port 8000. Cleartext HTTP is enabled for local development; use HTTPS and disable cleartext traffic before release.

To build a debug APK from a machine with Gradle installed, run `gradle --no-daemon :app:assembleDebug`. The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

Chaquopy embeds Python 3.11 and loads `app/src/main/python/nutrition.py` for offline nutrition suggestions. No Python server is needed on the phone.

## API overview

- `POST /auth/register`, `POST /auth/login`
- `GET /meals`, `POST /meals`, `DELETE /meals/{id}`
- `GET /summary`, `GET /health`

Meal endpoints and summaries require `Authorization: Bearer <access_token>`. Configure strong secrets and HTTPS for production. Nutrition insights are general wellness information, not medical advice.
