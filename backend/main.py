from __future__ import annotations

import hashlib
import os
import secrets
from datetime import date, datetime, timedelta, timezone
from typing import Generator

import jwt
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, EmailStr, Field
from sqlalchemy import DateTime, ForeignKey, Integer, String, create_engine, func, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, relationship, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./mealmate.db")
JWT_SECRET = os.getenv("JWT_SECRET", "dev-only-change-this-secret")
JWT_ALGORITHM = "HS256"
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {})
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(80))
    password_hash: Mapped[str] = mapped_column(String(256))
    meals: Mapped[list["Meal"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class Meal(Base):
    __tablename__ = "meals"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    calories: Mapped[int] = mapped_column(Integer)
    protein: Mapped[int] = mapped_column(Integer, default=0)
    carbs: Mapped[int] = mapped_column(Integer, default=0)
    fat: Mapped[int] = mapped_column(Integer, default=0)
    meal_type: Mapped[str] = mapped_column(String(20), default="Snack")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)
    user: Mapped[User] = relationship(back_populates="meals")


class Credentials(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str | None = Field(default=None, max_length=80)


class AuthResult(BaseModel):
    access_token: str
    token_type: str = "bearer"
    display_name: str


class MealInput(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    calories: int = Field(ge=0, le=10000)
    protein: int = Field(default=0, ge=0, le=1000)
    carbs: int = Field(default=0, ge=0, le=1000)
    fat: int = Field(default=0, ge=0, le=1000)
    meal_type: str = Field(default="Snack", pattern="^(Breakfast|Lunch|Dinner|Snack)$")


class MealResult(MealInput):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: datetime


class Summary(BaseModel):
    date: date
    calories: int
    protein: int
    carbs: int
    fat: int
    meal_count: int


security = HTTPBearer(auto_error=False)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def password_hash(password: str, salt: bytes | None = None) -> str:
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 310_000)
    return f"{salt.hex()}:{digest.hex()}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        salt_hex, digest_hex = encoded.split(":", 1)
        return secrets.compare_digest(password_hash(password, bytes.fromhex(salt_hex)), f"{salt_hex}:{digest_hex}")
    except (ValueError, TypeError):
        return False


def issue_token(user: User) -> str:
    return jwt.encode({"sub": str(user.id), "exp": datetime.now(timezone.utc) + timedelta(days=14)}, JWT_SECRET, algorithm=JWT_ALGORITHM)


def current_user(credentials: HTTPAuthorizationCredentials | None = Depends(security), db: Session = Depends(get_db)) -> User:
    if credentials is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Sign in required")
    try:
        user_id = int(jwt.decode(credentials.credentials, JWT_SECRET, algorithms=[JWT_ALGORITHM])["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token")
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Account not found")
    return user


app = FastAPI(title="BekalBu API", version="1.0.0", description="Meal diary and nutrition tracking API")


@app.on_event("startup")
def startup() -> None:
    Base.metadata.create_all(bind=engine)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/auth/register", response_model=AuthResult, status_code=201)
def register(data: Credentials, db: Session = Depends(get_db)) -> AuthResult:
    email = data.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status_code=409, detail="An account with this email already exists")
    user = User(email=email, display_name=(data.display_name or email.split("@")[0])[:80], password_hash=password_hash(data.password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return AuthResult(access_token=issue_token(user), display_name=user.display_name)


@app.post("/auth/login", response_model=AuthResult)
def login(data: Credentials, db: Session = Depends(get_db)) -> AuthResult:
    user = db.scalar(select(User).where(User.email == data.email.lower()))
    if user is None or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Email or password is incorrect")
    return AuthResult(access_token=issue_token(user), display_name=user.display_name)


@app.get("/meals", response_model=list[MealResult])
def list_meals(user: User = Depends(current_user), db: Session = Depends(get_db)) -> list[Meal]:
    today = date.today()
    return list(db.scalars(select(Meal).where(Meal.user_id == user.id, func.date(Meal.created_at) == today.isoformat()).order_by(Meal.created_at.desc())))


@app.post("/meals", response_model=MealResult, status_code=201)
def create_meal(data: MealInput, user: User = Depends(current_user), db: Session = Depends(get_db)) -> Meal:
    meal = Meal(user_id=user.id, **data.model_dump())
    db.add(meal)
    db.commit()
    db.refresh(meal)
    return meal


@app.delete("/meals/{meal_id}", status_code=204)
def delete_meal(meal_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)) -> None:
    meal = db.scalar(select(Meal).where(Meal.id == meal_id, Meal.user_id == user.id))
    if meal is None:
        raise HTTPException(status_code=404, detail="Meal not found")
    db.delete(meal)
    db.commit()


@app.get("/summary", response_model=Summary)
def daily_summary(user: User = Depends(current_user), db: Session = Depends(get_db)) -> Summary:
    today = date.today()
    values = db.execute(select(
        func.coalesce(func.sum(Meal.calories), 0),
        func.coalesce(func.sum(Meal.protein), 0),
        func.coalesce(func.sum(Meal.carbs), 0),
        func.coalesce(func.sum(Meal.fat), 0),
        func.count(Meal.id),
    ).where(Meal.user_id == user.id, func.date(Meal.created_at) == today.isoformat())).one()
    return Summary(date=today, calories=values[0], protein=values[1], carbs=values[2], fat=values[3], meal_count=values[4])
