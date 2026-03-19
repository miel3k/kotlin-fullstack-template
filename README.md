# Kotlin Full-Stack Template

A complete monorepo template for building full-stack applications with Kotlin. Includes Ktor
backend, Compose Multiplatform frontend (Android + iOS), and shared business logic.

## 🏗️ Architecture

```
kotlin-fullstack-template/
├── shared/              # Shared DTOs only
│   └── models/          # Task (used by backend + frontend)
├── backend/             # Ktor server (JVM)
│   ├── routes/          # API endpoints
│   ├── repository/      # PostgreSQL with Exposed
│   └── auth/            # JWT authentication
└── composeApp/          # Compose Multiplatform (Android + iOS)
    ├── data/
    │   ├── datasource/  # API client
    │   ├── repository/  # Offline-first with SQLDelight
    │   └── database/    # SQLDelight schemas
    ├── viewmodel/       # ViewModels (StateFlow)
    └── ui/              # Compose UI
```

### What's Shared vs Platform-Specific

**Shared Module (`shared/`):**

- ✅ DTOs (Task, CreateTaskRequest, etc.)
- ✅ Serialization annotations
- ✅ 100% shared API contracts

**ComposeApp Module (`composeApp/`):**

- ✅ API DataSources (uses shared DTOs)
- ✅ Repositories with offline-first logic (SQLDelight)
- ✅ ViewModels (MVVM pattern)
- ✅ UI (Compose Multiplatform - 95% shared)
- Platform-specific: Entry points (MainActivity, MainViewController)

**Backend Module (`backend/`):**

- ✅ Ktor routes
- ✅ Repository with PostgreSQL (Exposed ORM)
- ✅ JWT authentication
- ✅ Uses shared DTOs for API responses

## 🚀 Quick Start

### Prerequisites

- **JDK 17+**
- **Android Studio** (for Android/iOS development)
- **Xcode** (for iOS, macOS only)
- **PostgreSQL** (for backend database)

### 1. Clone and Setup

```bash
git clone https://github.com/yourusername/kotlin-fullstack-template.git
cd kotlin-fullstack-template
```

### 2. Setup Database

```bash
# Install PostgreSQL (macOS)
brew install postgresql@17
brew services start postgresql@17

# Create database
createdb taskapp

# Set environment variables for backend
export DATABASE_URL="jdbc:postgresql://localhost:5432/taskapp"
export DATABASE_USER="your_username"
export DATABASE_PASSWORD="your_password"
```

### 3. Run Backend

```bash
# Run backend locally
./gradlew backend:run

# Backend runs at: http://localhost:8080
```

### 4. Run ComposeApp

**Android:**

```bash
./gradlew composeApp:assembleDebug
# Or open in Android Studio and run
```

**iOS:**

```bash
# Open in Android Studio
# Select iOS simulator and run
```

## 📱 Features

### Backend (Ktor)

- ✅ **RESTful API** with CRUD operations
- ✅ **PostgreSQL** with Exposed ORM
- ✅ **CORS** configured

### ComposeApp (Android + iOS)

- ✅ **Compose Multiplatform** UI (95% code sharing)
- ✅ **Offline-first** architecture with SQLDelight
- ✅ **MVVM** pattern with StateFlow
- ✅ **Koin** dependency injection
- ✅ **Material 3** design

### Shared

- ✅ **Type-safe API contracts** (shared DTOs)
- ✅ **Kotlinx.serialization** for JSON

## 🛠️ API Endpoints

### Tasks

```
GET    /tasks          # List all tasks
GET    /tasks/{id}     # Get task by ID
POST   /tasks          # Create task
PUT    /tasks/{id}     # Update task
DELETE /tasks/{id}     # Delete task
```

### Example Requests

**Create Task:**

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Buy groceries",
    "description": "Milk, eggs, bread"
  }'
```

## 🏗️ Project Structure Details

### Shared DTOs

All DTOs are defined once in `shared/src/commonMain/kotlin/models/`:

```kotlin
@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val createdAt: Long
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String?
)
```

These compile to:

- **JVM bytecode** for backend
- **Native code** for iOS (ARM64)
- **Dalvik bytecode** for Android

### Offline-First Repository Pattern

```kotlin
// composeApp/data/repository/TaskRepository.kt
override suspend fun getTasks(): Result<List<Task>> {
    // Try API first
    return when (val result = api.getTasks()) {
        is Result.Success -> {
            // Cache to SQLDelight database
            result.data.forEach { database.insert(it) }
            result
        }
        is Result.Error -> {
            // Fallback to cached data
            Result.Success(database.getAll())
        }
    }
}
```

### Backend Repository

```kotlin
// backend/repository/TaskRepository.kt
fun getAll(userId: String): List<Task> = transaction {
    TaskTable
        .selectAll()
        .where { TaskTable.userId eq userId }
        .map { it.toTask() } // Same Task DTO!
}
```

**Key insight:** Backend and frontend use the **same `Task` class**, so API changes break the build
immediately.

## 🔧 Development

### Build Commands

```bash
# Build everything
./gradlew build

# Build backend JAR
./gradlew backend:build

# Build Android APK
./gradlew composeApp:assembleDebug

# Run backend locally
./gradlew backend:run

# Run tests
./gradlew test
```

### Database Configuration

Edit `backend/src/main/resources/application.yaml`:

```hocon
ktor:
  deployment:
    host: "0.0.0.0"
    port: 8080
  application:
    modules:
      - ApplicationKt.module

database:
  url: "jdbc:postgresql://localhost:5432/taskapp"
  user: "your_username"
  password: "your_password"
```

Or use environment variables (recommended for production):

- `DATABASE_URL`
- `DATABASE_USER`
- `DATABASE_PASSWORD`

## 📦 Building for Production

### Backend

```bash
# Build JAR
./gradlew backend:build

# Run JAR
java -jar backend/build/libs/backend-all.jar
```

### Android

```bash
./gradlew composeApp:assembleRelease
# APK at: composeApp/build/outputs/apk/release/
```

### iOS

```bash
# Open in Xcode via Android Studio
# Product → Archive → Distribute
```

## 🎯 Key Benefits

1. **Compile-Time Safety**
    - Backend changes that break mobile contracts fail at compile time
    - No runtime JSON mapping errors

2. **Code Reuse**
    - 100% of DTOs shared
    - 95% of UI code shared (Android + iOS)
    - 100% of business logic shared

3. **Atomic Changes**
    - One PR can update backend + mobile simultaneously
    - No version drift between services

4. **Developer Experience**
    - One repository to clone
    - One language (Kotlin) across stack
    - Familiar patterns (MVVM, Repository, DI)

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Backend tests only
./gradlew backend:test

# ComposeApp tests
./gradlew composeApp:testDebugUnitTest
```

## 🐛 Troubleshooting

### Backend won't start

```bash
# Check PostgreSQL is running
brew services list

# Check database connection
psql -d taskapp -U your_username
```

### Android emulator can't connect to localhost

Use `10.0.2.2:8080` instead of `localhost:8080` in the API URL configuration.

### iOS build fails

```bash
# Clean and rebuild
./gradlew clean
./gradlew composeApp:build
```

### SQLDelight errors

```bash
# Regenerate database code
./gradlew composeApp:generateCommonMainTaskDatabaseInterface
```

## 📚 Learn More

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor Framework](https://ktor.io/)
- [SQLDelight](https://cashapp.github.io/sqldelight/)
- [Exposed ORM](https://github.com/JetBrains/Exposed)

## 📝 License

MIT License - feel free to use this template for your projects!

## 🤝 Contributing

Contributions welcome! Please open an issue or PR.

---

**Built with ❤️ using Kotlin**
