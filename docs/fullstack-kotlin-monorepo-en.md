# Fullstack Kotlin – Building Backend and Mobile apps using the monorepo approach

*How to stop worrying about API contracts and taking advantage of shared code*

---

Building modern applications typically means separate teams: backend developers and mobile developers. Coordination between these teams is critical. When communication breaks down, even the best engineers can derail a project. This article presents an approach that eliminates at least one major obstacle: API contract synchronization.

The core idea is that everything lives in one repository, written entirely in Kotlin. The backend API runs on Ktor and the mobile apps use Compose Multiplatform for Android and iOS. They all share the same domain models and business logic.

Let me show you how this works and why you might want to try it.

## **The Problem: The API Contract Nightmare**

If you've worked on mobile apps with a separate backend team, you know the pain:

1. Backend team changes a field name from `userName` to `username`
2. Deploy happens on Friday
3. Mobile app crashes all weekend
4. Monday morning: "Why didn't you update the mobile client?!"

Or worse:

1. Mobile team adds a new required field to the request
2. Backend hasn't implemented it yet
3. 400 Bad Request errors everywhere
4. Both teams blame each other

**The root cause?** Backend and mobile speak different languages, live in different repos, and share nothing but JSON over HTTP.

## **The Solution: Shared Code in a Monorepo**

Here's the radical idea: what if your backend API and mobile apps shared the exact same data models, written once, compiled for all platforms?

```
kotlin-fullstack-template/
├── backend/          # Ktor server (JVM)
├── composeApp/       # iOS + Android (Compose Multiplatform)
├── iosApp/           # iOS Xcode project
├── shared/           # The magic ✨
└── gradle/           # Version catalog (libs.versions.toml)
```
The `shared` module contains **only what both sides need**:
- Data models (DTOs)

This shared code compiles to JVM (backend, Android) and native binaries (iOS).

**Zero mapping code. One repository.**

## **Show Me the Code**

Let's build a simple task management API. Here are shared data models:

```kotlin
// shared/src/commonMain/kotlin/models/Task.kt
@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val createdAt: Long,
)

// shared/src/commonMain/kotlin/models/CreateTaskRequest.kt
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String?,
)
```

**Backend (Ktor) uses it directly:**

```kotlin
// backend/src/main/kotlin/routes/TaskRoutes.kt
fun Route.taskRoutes(repository: TaskRepository) {
    get("/tasks") {
        val tasks: List<Task> = repository.getAll()
        call.respond(tasks)  // ← Same Task class!
    }

    post("/tasks") {
        val request = call.receive<CreateTaskRequest>()  // ← Same DTO!
        val task = repository.create(request)
        call.respond(HttpStatusCode.Created, task)
    }
}
```

**Mobile app uses the exact same classes:**

```kotlin
// composeApp/src/commonMain/kotlin/data/TaskDataSource.kt
class TaskDataSource(private val httpClient: HttpClient) {
    suspend fun loadTasks(): Result<List<Task>> {
        return try {
            val tasks = httpClient.get("tasks").body<List<Task>>()
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(request: CreateTaskRequest): Result<Task> {
        return try {
            val task = httpClient.post("tasks") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<Task>()
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**What just happened?**

- No JSON mapping errors - the serialization happens once in `shared`
- No field name mismatches - they're literally the same class
- No version drift - backend change breaks mobile build immediately
- No API documentation needed - the code IS the documentation

### **Bonus: Automatic Payload Optimization**

Here's a subtle benefit I discovered: Kotlin serialization with `encodeDefaults = false` (the default) skips fields that have their default values in the JSON response.

Since both backend and mobile share the exact same `Task` model with the same defaults:

```kotlin
@Serializable
data class Task(
    val completed: Boolean = false,  // Default value
    // ...
)
```

When the backend returns a task with `completed = false`, the JSON doesn't include that field:

```json
{
  "id": "123",
  "title": "Buy milk",
  "createdAt": 1234567890
}
```

The mobile client receives this JSON, and Kotlin serialization automatically fills in `completed = false` from the model's default value.

**Benefits:**
- ✅ **Smaller payloads** - Fields with default values are omitted
- ✅ **Faster serialization** - Less data to encode/decode
- ✅ **No information loss** - Both sides know the defaults

This only works because the model is shared. In traditional REST APIs, you'd need `encodeDefaults = true` to ensure the mobile client knows what `completed` is. Here, the mobile client already knows - it's in the shared model definition.

This is a small example, but in DTOs with many optional/default fields, the payload size reduction adds up.

## **The Main Modules**

The monorepo contains three Kotlin modules that work together:

### **1. Backend (Ktor on JVM)**

```kotlin
backend/
├── routes/              # API endpoints
├── repository/          # Database access (Exposed ORM)
└── database/            # PostgreSQL table definitions
```

Ktor server running on JVM with PostgreSQL. Imports shared DTOs for API responses. Clean, lightweight, fully in Kotlin.

Why Ktor instead of Spring Boot?

1. **Lightweight** - Fast builds, small Docker images (~50MB vs 200MB+)
2. **Kotlin native** - No Java interop weirdness
3. **Coroutines everywhere** - Matches mobile patterns exactly
4. **Can import shared module** - This is the key!

### **2. Shared (Kotlin Multiplatform)**

```kotlin
shared/
└── commonMain/
    └── models/          # DTOs (Task, CreateTaskRequest, etc.)
```

The API contract between backend and mobile. Contains only DTOs with `@Serializable` annotations.

**This template keeps it minimal** (just DTOs), but you could also share:
- **Validation logic** - Email format, password rules, field requirements (backend and mobile reject the same invalid inputs)
- **Business calculations** - Tax, discounts, pricing rules (ensures consistency across platforms)
- **Utility functions** - Date formatting, currency conversion, string helpers
- **Constants** - Error messages, validation rules, configuration values

Start with DTOs only. Add shared code when duplication becomes painful. Shared code adds coupling, so only share when the benefits outweigh the cost.

### **3. ComposeApp (Compose Multiplatform)**

```kotlin
composeApp/
├── commonMain/          # 90% of code - shared between iOS & Android
│   ├── data/            # API client, repositories, SQLDelight database
│   ├── viewmodel/       # ViewModels with StateFlow
│   ├── ui/              # Compose UI screens and components
│   └── di/              # Koin dependency injection
├── androidMain/         # Android entry point (5%)
└── iosMain/             # iOS entry point (5%)
```

Single codebase for iOS and Android. Uses shared DTOs from the `shared` module. Offline-first architecture with SQLDelight for local caching.

**Overall: ~90% code reuse between iOS and Android.**

## **Real Benefits I've Experienced**

**1. Compile-Time Safety**

Before: "The API changed, now the app crashes."

After: "The API changed, now the app won't compile - fix it before commit."

**Real example:**
```kotlin
// I changed this in shared/models/Task.kt
data class Task(
    val id: String,
    val title: String,
    val dueDate: Long,  // NEW FIELD - was optional, now required
    // ...
)

// Backend code breaks immediately:
// Error: Missing argument for parameter 'dueDate'

// Mobile code breaks immediately:
// Error: Unresolved reference 'dueDate'
```

No runtime surprises. Ever.

**2. Atomic Changes**

One pull request can:
- Add backend endpoint
- Update mobile UI
- Change shared models
- Update tests

All tested together, deployed together, reviewed together.

**3. Faster Development**

Adding a new feature used to take:
- Backend: 2 days (endpoints, tests, deploy)
- Wait for backend deploy
- Mobile: 3 days (API integration, UI, testing)
- **Total: 5-6 days** (sequential, with handoff delays)

Now: **2-3 days** (parallel work, no handoff, shared code)

**4. Zero API Documentation Overhead**

No Swagger. No Postman collections. No "did we update the docs?" conversations.

Why? Because everything lives in the same repository, written in the same language. The mobile developer has **direct access to the backend code**.

**Need to know what endpoints exist?**
- Don't read Swagger → Open `backend/routes/TaskRoutes.kt`

**Need to know request/response structure?**
- Don't read API docs → Cmd+Click on the DTO class

**Need to understand endpoint behavior?**
- Don't guess from examples → Read the actual implementation

The code IS the documentation. It's always up-to-date because if it's wrong, the build fails.

**5. One Language Everywhere**

Backend developer fixing a mobile bug? No problem - it's all Kotlin.

Mobile developer adding a backend endpoint? Same syntax, same patterns, same tooling.

**Real example:**
- Backend: `suspend fun getAll(): List<Task>` with coroutines
- Mobile: `suspend fun loadTasks(): Result<List<Task>>` with the same coroutines

No context switching between Java/Spring and Swift/Kotlin. No "wait, how do you handle async in this language again?"

The entire stack uses:
- Kotlin coroutines for async
- kotlinx.serialization for JSON
- Same idioms and patterns

This dramatically lowers the barrier for developers to contribute across the stack.

**7. Write Once, Run on iOS and Android**

With Compose Multiplatform, the UI code is written once in `commonMain` and it runs natively on both platforms.

This means:
- Bug fixes apply to both platforms automatically
- New features ship to iOS and Android simultaneously
- UI consistency is guaranteed (same code = same behavior)
- Testing covers both platforms at once

**Before (separate codebases):**
- iOS feature: 3 days (Swift + SwiftUI)
- Android feature: 3 days (Kotlin + Compose)
- Total: 6 days + synchronization headaches

**After (shared codebase):**
- Both platforms: 3 days total
- Zero synchronization needed

**8. Team Knowledge Sharing**

When everyone writes Kotlin, knowledge transfer happens naturally.

**Real example with Coroutines:**

A mobile developer who's been using coroutines for async operations on Android:
```kotlin
// Mobile - familiar territory
viewModelScope.launch {
    val tasks = repository.loadTasks()
    _uiState.update { it.copy(tasks = tasks) }
}
```

Can immediately understand and contribute to backend code:
```kotlin
// Backend - same patterns!
routing {
    get("/tasks") {
        val tasks = withContext(Dispatchers.IO) {
            repository.getAll()
        }
        call.respond(tasks)
    }
}
```

Same `suspend` functions, same `launch` and `withContext`, same structured concurrency principles.

**Benefits:**
- Mobile developers can review backend PRs (and vice versa)
- Team members naturally become full-stack
- Code review quality improves (more eyes understand the code)
- Onboarding is faster - learn once, apply everywhere

## **Challenges & How to solve Them**

**Challenge 1: Platform-Specific URLs**

Android emulator needs `10.0.2.2` instead of `localhost`, iOS simulator uses `127.0.0.1`.

**Solution:** expect actual pattern
```kotlin
// common contract
expect fun getApiBaseUrl(): String

// Android specific implementation
actual fun getApiBaseUrl(): String = "http://10.0.2.2:8080"

// iOS specific implementation
actual fun getApiBaseUrl(): String = "http://localhost:8080"
```

**Challenge 2: Different Database Needs**

Backend needs PostgreSQL with migrations. Mobile needs offline SQLite.

**Solution:** Different ORMs, same DTOs. Keep repositories separate.

- Backend: Exposed (Kotlin SQL DSL) in `backend/repository/`
- Mobile: SQLDelight (type-safe SQL) in `composeApp/src/commonMain/kotlin/data/repository/`
- Shared: Only DTOs

Both convert their database models to the same `Task` DTO:

```kotlin
// Backend: Exposed → Task
fun ResultRow.toTask() = Task(
    id = this[TaskTable.id],
    title = this[TaskTable.title],
    // ...
)

// Mobile: SQLDelight → Task
fun TaskEntity.toTask() = Task(
    id = this.id,
    title = this.title,
    // ...
)
```

This keeps database logic separate while maintaining type safety.

**Challenge 3: Build Times**

Full clean build: ~2 minutes

**Mitigation:**
- Gradle build cache (cuts to ~30s for incremental)
- Configuration cache enabled
- Only changed modules rebuild
- Developers work on specific modules (don't need full builds)

**Challenge 4: The iOS Build Learning Curve (for Android devs)**

Migrating to Compose Multiplatform meant learning:
- Xcode project setup
- CocoaPods integration
- iOS simulator quirks

**Solution:** JetBrains provides excellent docs, and the community is very active on Slack.

**Challenge 5: Backend Architecture with Kotlin**

Kotlin originated from the mobile world (Android), and many backend developers have limited experience with it. This creates a challenge when designing backend architecture complaint with the modern API design principles.

**The problem:**
- Traditional backend developers are used to Spring Boot with Java patterns
- Modern Kotlin backend (Ktor + coroutines + functional patterns) feels unfamiliar
- Lack of established best practices compared to mature Spring ecosystem
- Risk of designing the backend incorrectly due to inexperience with Kotlin idioms

**Solution:**
- Study Kotlin backend examples and Ktor documentation thoroughly
- Learn coroutines properly - they're different from traditional thread-based approaches
- Start simple (CRUD operations) before adding complexity
- Leverage the mobile team's Kotlin knowledge for code reviews
- Use type-safe DSLs (like Exposed) instead of forcing Java patterns

**What helps:**
- Strong desire of mobile developers to learn backend design
- The mobile developers already know Kotlin well - their feedback on backend code is invaluable
- Ktor's documentation has excellent examples of modern patterns
- Accepting that "the Spring way" isn't always "the Kotlin way"

## **When NOT to Use This**

Be honest - this isn't for everyone.

**Skip this approach if:**

❌ **Separate teams own backend and mobile** (different companies, different timelines)
❌ **Team refuses to learn Kotlin** (forcing technology never works)
❌ **You need Java-only backend libraries** (though most have Kotlin alternatives)
❌ **Large team** (50+ developers) - coordination overhead gets high
❌ **Heavy platform-specific UI** (camera apps, AR, complex native integrations)

**This is perfect for:**

✅ **Small teams** (1-5 developers) - maximize individual productivity
✅ **Apps with heavy business logic** - shared calculations, validations, state machines
✅ **Projects needing offline-first** - SQLDelight + Repository pattern works beautifully
✅ **Teams already using Kotlin** - leverage existing knowledge
✅ **Startups wanting iOS + Android fast** - ship once, run everywhere
✅ **Data-driven apps** - CRUD apps, dashboards, forms

## **Getting Started**

I've created a minimal template to demonstrate this setup:

**[GitHub: kotlin-fullstack-template](https://github.com/yourusername/kotlin-fullstack-template)** *(This project is intentionally simplified for the blog article - no authentication, just pure CRUD to demonstrate shared DTOs and compile-time safety)*

It includes:
- **Ktor backend** (simplified CRUD API with PostgreSQL)
- **Compose Multiplatform mobile** (iOS + Android)
    - Platform-specific API URL configuration
    - Repositories with offline-first logic
    - SQLDelight database
    - API DataSources
- **Shared module** with:
    - DTOs (Task, CreateTaskRequest, UpdateTaskRequest)
- **iOS app** with Xcode project

## **Conclusion**

**The Good:**
- ✅ Compile-time safety is a game-changer
- ✅ Development velocity noticeably faster
- ✅ Code reuse reaches 90% (vs 0% with separate repos)
- ✅ One language, one build system, one repository
- ✅ Backend and mobile evolve together

**The Bad:**
- ❌ Initial setup takes time
- ❌ Not suitable for large, separated teams
- ❌ iOS build setup has learning curve
- ❌ Kotlin backend expertise is hard to find

**The Verdict:**

For small-to-medium projects where you control both backend and mobile, this approach is phenomenal. The time saved on debugging API integration issues alone pays for the setup cost.

**Would I recommend it to everyone?** No - but if you're:
- Building a new project from scratch
- Comfortable with Kotlin
- Want to ship fast across platforms
- Value type safety

Then try the template and see if it fits your use case.

### **Resources**

- **[Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)** - Official documentation
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)** - UI framework
- **[Ktor Framework](https://ktor.io/)** - Backend framework
- **[Exposed](https://github.com/JetBrains/Exposed)** - Kotlin SQL framework
- **[SQLDelight](https://cashapp.github.io/sqldelight/)** - Type-safe SQL
- **[Template Repository](https://github.com/miel3k/kotlin-fullstack-template)** - Demo project**
