# Fullstack Kotlin – Budowanie backendu i aplikacji mobilnych w ramach jednego repozytorium

*Jak przestać martwić się o kontrakty API i czerpać korzyści ze współdzielenia kodu*

---

Budowanie nowoczesnych aplikacji zazwyczaj wymaga pracy oddzielnych zespołów: programistów backendowych i mobilnych. Współpraca między tymi zespołami ma kluczowe znaczenie. Gdy komunikacja nie przebiega prawidłowo, nawet najlepsi inżynierowie mogą doprowadzić do niepowodzenia projektu. W niniejszym artykule przedstawiono podejście, które pozwala wyeliminować przynajmniej jedną z głównych przeszkód: synchronizację kontraktów API.

Główna idea polega na tym, że cały kod znajduje się w jednym repozytorium napisanym w całości w języku Kotlin. Backend API działa w oparciu o bibliotekę Ktor, a aplikacje mobilne wykorzystują Compose Multiplatform dla Androida i iOS. Wszystkie moduły współdzielą te same modele domenowe i logikę biznesową.

Pokażę ci, jak to działa i dlaczego warto to wypróbować.

## **Problem: Synchronizacja kontraktów API**

Jeśli pracowałeś nad aplikacjami mobilnymi we współpracy z oddzielnym zespołem backendowym, dobrze znasz ten problem:

1. Zespół backendowy zmienia nazwę pola z `userName` na `username`
2. Wdrożenie następuje w piątek
3. Aplikacja mobilna crashuje się przez cały weekend
4. Poniedziałek rano: "Dlaczego nie zaktualizowaliście klienta mobilnego?!"

Albo jeszcze gorzej:

1. Zespół mobilny dodaje nowe wymagane pole do requestu
2. Zespół backendowy jeszcze tego nie zaimplementował
3. Wszędzie pojawiają się błędy 400 Bad Request
4. Oba zespoły obwiniają się nawzajem

**Główna przyczyna?** Backend i aplikacja mobilna komunikują się różnymi językami, znajdują się w różnych repozytoriach i komunikują się wyłącznie za pomocą plików JSON przesyłanych przez HTTP.

## **Rozwiązanie: Współdzielony kod w monorepo**

Oto radykalne podejście: co jeśli backend API i aplikacje mobilne współdzieliłyby dokładnie te same modele danych, napisane raz, skompilowane dla wszystkich platform?

```
kotlin-fullstack-template/
├── backend/          # Serwer Ktor (JVM)
├── composeApp/       # iOS + Android (Compose Multiplatform)
├── iosApp/           # Projekt iOS Xcode
├── shared/           # Magia ✨
└── gradle/           # Katalog wersji (libs.versions.toml)
```
Moduł `shared` zawiera **tylko to, czego potrzebują obie strony**:
- Modele danych (DTOs)

Ten współdzielony kod kompiluje się do JVM (backend, Android) i natywnych binarek (iOS).

**Zero kodu mapującego. Jedno repozytorium.**

## **Pokaż mi kod**

Zbudujmy proste API do zarządzania zadaniami. Oto współdzielone modele danych:

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

**Backend (Ktor) używa ich bezpośrednio:**

```kotlin
// backend/src/main/kotlin/routes/TaskRoutes.kt
fun Route.taskRoutes(repository: TaskRepository) {
    get("/tasks") {
        val tasks: List<Task> = repository.getAll()
        call.respond(tasks)  // ← Ta sama klasa Task!
    }

    post("/tasks") {
        val request = call.receive<CreateTaskRequest>()  // ← To samo DTO!
        val task = repository.create(request)
        call.respond(HttpStatusCode.Created, task)
    }
}
```

**Aplikacja mobilna używa dokładnie tych samych klas:**

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

**Co się właśnie stało?**

- Brak błędów mapowania JSON - serializacja odbywa się raz w `shared`
- Brak niezgodności nazw pól - są to dosłownie te same klasy
- Brak rozbieżności wersji - zmiana backendu natychmiast powoduje błąd kompilacji aplikacji mobilnej
- Nie jest potrzebna dokumentacja API - kod JEST dokumentacją

### **Bonus: Automatyczna optymalizacja przesyłanych danych**

Oto subtelna korzyść, którą odkryłem: serializacja w języku Kotlin z `encodeDefaults = false` (domyślnie) pomija w odpowiedzi JSON pola, które mają swoje domyślne wartości.

Ponieważ zarówno backend, jak i mobile współdzielą dokładnie ten sam model `Task` z tymi samymi wartościami domyślnymi:

```kotlin
@Serializable
data class Task(
    val completed: Boolean = false,  // Wartość domyślna
    // ...
)
```

Gdy backend zwraca zadanie z `completed = false`, JSON nie zawiera tego pola:

```json
{
  "id": "123",
  "title": "Buy milk",
  "createdAt": 1234567890
}
```

Klient mobilny otrzymuje ten JSON, a serializacja Kotlina automatycznie wypełnia `completed = false` z domyślnej wartości modelu.

**Korzyści:**
- ✅ **Mniejsze payloady** - Pola z wartościami domyślnymi są pomijane
- ✅ **Szybsza serializacja** - Mniej danych do kodowania/dekodowania
- ✅ **Brak utraty informacji** - Obie strony znają wartości domyślne

Działa to tylko dlatego, że model jest współdzielony. W tradycyjnych REST API potrzebowałbyś `encodeDefaults = true`, aby upewnić się, że klient mobilny wie, czym jest `completed`. Tutaj klient mobilny już to wie - informacja ta znajduje się we współdzielonej definicji modelu.

To mały przykład, ale w przypadku obiektów DTO zawierających wiele pól opcjonalnych lub domyślnych zmniejszenie rozmiaru przesyłanych danych ma znaczący wpływ na wydajność. 

## **Główne moduły**

Monorepo zawiera trzy moduły Kotlin, które współpracują ze sobą:

### **1. Backend (Ktor na JVM)**

```kotlin
backend/
├── routes/              # Endpointy API
├── repository/          # Dostęp do bazy danych (Exposed ORM)
└── database/            # Definicje tabel PostgreSQL
```

Serwer Ktor działający na JVM z PostgreSQL. Importuje współdzielone obiekty DTO dla odpowiedzi API. Przejrzysty, lekki, w całości napisany w języku Kotlin.

Dlaczego Ktor zamiast Spring Boot?

1. **Lekkość** - Szybki czas kompilacji, małe obrazy Docker (~50MB vs 200MB+)
2. **Natywny dla Kotlina** - Brak problemów interoperacyjności z Javą
3. **Coroutines wszędzie** - Dokładnie odpowiada wzorcom mobilnym
4. **Może importować moduł shared** - To jest kluczowa zaleta!

### **2. Shared (Kotlin Multiplatform)**

```kotlin
shared/
└── commonMain/
    └── models/          # DTOs (Task, CreateTaskRequest, etc.)
```

Kontrakt API między serwerem a aplikacją mobilną. Zawiera wyłącznie obiekty DTO z adnotacjami `@Serializable`.

**Ten szablon jest minimalistyczny** (zawiera tylko obiekty DTO), ale możesz również współdzielić:
- **Logikę walidacji** - Format emaila, zasady haseł, wymagania pól (backend i mobile odrzucają te same nieprawidłowe dane wejściowe)
- **Kalkulacje biznesowe** - Podatki, rabaty, reguły cenowe (zapewnia spójność między platformami)
- **Funkcje narzędziowe** - Formatowanie dat, konwersja walut, funkcje do obsługi ciągów znaków
- **Stałe** - Komunikaty błędów, reguły walidacji, wartości konfiguracyjne

Zacznij od samych obiektów DTO. Dodawaj współdzielony kod, gdy duplikacja staje się bolesna. Współdzielony kod dodaje powiązań, więc dziel tylko wtedy, gdy korzyści przewyższają koszty.

### **3. ComposeApp (Compose Multiplatform)**

```kotlin
composeApp/
├── commonMain/          # 90% kodu - współdzielone między iOS i Android
│   ├── data/            # Klient API, repozytoria, baza danych SQLDelight
│   ├── viewmodel/       # ViewModele z StateFlow
│   ├── ui/              # Ekrany i komponenty UI w Compose
│   └── di/              # Dependency injection z Koin
├── androidMain/         # Punkt wejścia Android (5%)
└── iosMain/             # Punkt wejścia iOS (5%)
```

Jedna baza kodu dla iOS i Androida. Wykorzystuje wspólne obiekty DTO z modułu `shared`. Architektura offline-first z wykorzystaniem biblioteki SQLDelight do lokalnego cache.

**Ogólnie: ~90% kodu jest wspólne dla systemów iOS i Android.**

## **Prawdziwe korzyści, których doświadczyłem**

**1. Bezpieczeństwo czasu kompilacji**

Wcześniej: "API się zmieniło, teraz aplikacja crashuje."

Teraz: "API się zmieniło, teraz aplikacja się nie kompiluje - napraw to przed commitem."

**Prawdziwy przykład:**
```kotlin
// Zmieniłem to w shared/models/Task.kt
data class Task(
    val id: String,
    val title: String,
    val dueDate: Long,  // NOWE POLE - było opcjonalne, teraz wymagane
    // ...
)

// Kod backendowy natychmiast się psuje:
// Error: Missing argument for parameter 'dueDate'

// Kod mobilny natychmiast się psuje:
// Error: Unresolved reference 'dueDate'
```

Brak niespodzianek w runtime. Nigdy.

**2. Atomowe zmiany**

Jeden pull request może:
- Dodać endpoint backendowy
- Zaktualizować UI aplikacji mobilnej
- Zmienić współdzielone modele
- Zaktualizować testy

Wszystko przetestowane razem, wdrożone razem, sprawdzone razem.

**3. Szybszy rozwój**

Dodanie nowej funkcji zajmowało wcześniej:
- Backend: 2 dni (endpointy, testy, wdrożenie)
- Czekanie na wdrożenie backendu
- Mobile: 3 dni (integracja API, UI, testowanie)
- **Razem: 5-6 dni** (sekwencyjnie, z opóźnieniami w przekazywaniu)

Teraz: **2-3 dni** (praca równoległa, brak przekazywania, współdzielony kod)

**4. Zero narzutu dokumentacji API**

Brak Swaggera. Brak kolekcji Postman. Brak rozmów "czy zaktualizowaliśmy dokumenty?".

Dlaczego? Ponieważ wszystko znajduje się w tym samym repozytorium, napisane w tym samym języku. Programista mobilny ma **bezpośredni dostęp do kodu backendowego**.

**Potrzebujesz wiedzieć, jakie endpointy istnieją?**
- Nie czytaj Swaggera → Otwórz `backend/routes/TaskRoutes.kt`

**Potrzebujesz wiedzieć o strukturze request/response?**
- Nie czytaj dokumentacji API → Cmd+Click na klasę DTO

**Potrzebujesz zrozumieć zachowanie endpointu?**
- Nie zgaduj z przykładów → Przeczytaj faktyczną implementację

Kod JEST dokumentacją. Jest zawsze aktualny, ponieważ jeśli zawiera błędy, kompilacja kończy się niepowodzeniem.

**5. Jeden język wszędzie**

Programista backendowy naprawia błąd mobilny? Żaden problem - wszystko jest napisane w Kotlinie.

Programista mobilny dodaje endpoint backendowy? Ta sama składnia, te same wzorce, te same narzędzia.

**Prawdziwy przykład:**
- Backend: `suspend fun getAll(): List<Task>` z coroutines
- Mobile: `suspend fun loadTasks(): Result<List<Task>>` z tymi samymi coroutines

Brak przełączania kontekstu między Java/Spring a Swift/Kotlin. Brak "czekaj, jak obsługuje się async w tym języku?"

Cały stack używa:
- Kotlin coroutines dla async
- kotlinx.serialization dla JSON
- Tych samych idiomów i wzorców

Znacznie obniża to barierę utrudniającą programistom wnoszenie wkładu na wszystkich poziomach stosu technologicznego

**7. Napisz raz, uruchom na iOS i Androidzie**

Z Compose Multiplatform kod UI jest napisany raz w `commonMain` i działa natywnie na obu platformach.

To oznacza:
- Poprawki błędów są automatycznie wprowadzane na obu platformach
- Nowe funkcje są udostępniane jednocześnie na iOS i Androidzie
- Zapewniona jest spójność interfejsu użytkownika (ten sam kod = takie samo działanie)
- Testowanie obejmuje obie platformy na raz

**Wcześniej (oddzielne bazy kodu):**
- Funkcja iOS: 3 dni (Swift + SwiftUI)
- Funkcja Android: 3 dni (Kotlin + Compose)
- Razem: 6 dni + kłopoty z synchronizacją

**Teraz (współdzielona baza kodu):**
- Obie platformy: 3 dni razem
- Zero potrzeby synchronizacji

**8. Dzielenie się wiedzą w zespole**

Gdy wszyscy piszą w Kotlinie, transfer wiedzy przebiega naturalnie.

**Prawdziwy przykład z Coroutines:**

Programista mobilny, który używa coroutines do operacji asynchronicznych na Androidzie:
```kotlin
// Mobile - znajomy teren
viewModelScope.launch {
    val tasks = repository.loadTasks()
    _uiState.update { it.copy(tasks = tasks) }
}
```

Może natychmiast zrozumieć i wnieść swój wkład do kodu backendowego:
```kotlin
// Backend - te same wzorce!
routing {
    get("/tasks") {
        val tasks = withContext(Dispatchers.IO) {
            repository.getAll()
        }
        call.respond(tasks)
    }
}
```

Te same funkcje `suspend`, te same `launch` i `withContext`, te same zasady współbieżności.

**Korzyści:**
- Programiści mobilni mogą sprawdzać PRy backendowe (i odwrotnie)
- Członkowie zespołu naturalnie stają się full-stack
- Jakość code review się poprawia (więcej osób rozumie kod)
- Onboarding jest szybszy - naucz się raz, stosuj wszędzie

## **Wyzwania i jak je rozwiązać**

**Wyzwanie 1: URL-e specyficzne dla platformy**

Emulator Androida potrzebuje `10.0.2.2` zamiast `localhost`, symulator iOS używa `127.0.0.1`.

**Rozwiązanie:** wzorzec expect/actual
```kotlin
// wspólny kontrakt
expect fun getApiBaseUrl(): String

// implementacja specyficzna dla Androida
actual fun getApiBaseUrl(): String = "http://10.0.2.2:8080"

// implementacja specyficzna dla iOS
actual fun getApiBaseUrl(): String = "http://localhost:8080"
```

**Wyzwanie 2: Różne potrzeby bazy danych**

Backend potrzebuje PostgreSQL z migracjami. Mobile potrzebuje offline SQLite.

**Rozwiązanie:** Różne ORM-y, te same DTOs. Trzymaj repozytoria osobno.

- Backend: Exposed (Kotlin SQL DSL) w `backend/repository/`
- Mobile: SQLDelight (type-safe SQL) w `composeApp/src/commonMain/kotlin/data/repository/`
- Shared: Tylko DTOs

Oba konwertują swoje modele bazy danych na to samo DTO `Task`:

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

To utrzymuje logikę bazy danych osobno, zachowując bezpieczeństwo typów.

**Wyzwanie 3: Czasy kompilacji**

Pełny clean build: ~2 minuty

**Łagodzenie:**
- Cache buildów Gradle (skraca do ~30s dla inkrementalnych)
- Cache konfiguracji włączony
- Tylko zmienione moduły się przebudowują
- Programiści pracują nad konkretnymi modułami (nie potrzebują pełnych buildów)

**Wyzwanie 4: Krzywa uczenia się iOS build (dla developerów Androida)**

Migracja do Compose Multiplatform oznaczała naukę:
- Konfiguracji projektu Xcode
- Integracji CocoaPods
- Specyfika} symulatora iOS

**Rozwiązanie:** JetBrains dostarcza doskonałą dokumentację, a społeczność jest bardzo aktywna na Slacku.

**Wyzwanie 5: Architektura backendu z wykorzystaniem języka Kotlin**

Kotlin wywodzi się ze świata mobilnego (Android), a wielu programistów backendowych ma z nim ograniczone doświadczenie. Stanowi to wyzwanie przy projektowaniu architektury backendowej zgodnej z nowoczesnymi zasadami projektowania API.

**Problem:**
- Tradycyjni programiści backendowi są przyzwyczajeni do Spring Boot i wzorców programowania w Javie
- Nowoczesny backend Kotlin (Ktor + coroutines + wzorce funkcyjne) wydaje się im obcy
- Brak ugruntowanych najlepszych praktyk w porównaniu do dojrzałego ekosystemu Spring
- Ryzyko nieprawidłowego zaprojektowania backendu z powodu braku doświadczenia w zakresie idiomów języka Kotlin

**Rozwiązanie:**
- Dokładne studiowanie przykładów kodu backendowego Kotlin i dokumentacji Ktor
- Właściwa nauka coroutines - różnią się od tradycyjnych podejść opartych na wątkach
- Zacznij prosto (operacje CRUD) przed dodaniem złożoności
- Wykorzystaj wiedzę zespołu mobilnego o Kotlinie do code review
- Używaj type-safe DSL (jak Exposed) zamiast na siłę stosować wzorce z języka Java

**Co pomaga:**
- Silna motywacja programistów mobilnych, by nauczyć się projektowania backendu
- Programiści mobilni już dobrze znają Kotlina - ich opinie na temat kodu backendowego są bezcenne
- Dokumentacja biblioteki Ktor zawiera doskonałe przykłady nowoczesnych wzorców projektowych
- Świadomość, że "podejście Springa" nie zawsze pokrywa się z "podejściem Kotlina"

## **Kiedy NIE używać tego podejścia**

Bądźmy szczerzy - to rozwiązanie nie jest dla każdego.

**Zrezygnuj z tego podejścia, jeśli:**

❌ **Backend i aplikacje mobilne są obsługiwane przez odrębne zespoły** (różne firmy, różne harmonogramy)
❌ **Zespół odmawia nauki języka Kotlin** (narzucanie technologii nigdy nie działa)
❌ **Potrzebujesz bibliotek backendowych tylko dla Javy** (chociaż większość ma alternatywy w Kotlinie)
❌ **Duży zespół** (50+ programistów) - koszty koordynacji stają się wysokie
❌ **Skomplikowany UI specyficzny dla platformy** (aplikacje z kamerą, AR, złożone integracje natywne)

**To jest idealne dla:**

✅ **Małych zespołów** (1-5 programistów) - maksymalizacja produktywności jednostki
✅ **Aplikacji ze skomplikowaną logiką biznesową** - współdzielone kalkulacje, walidacje, maszyny stanów
✅ **Projektów wymagających offline-first** - SQLDelight + wzorzec Repository działa znakomicie
✅ **Zespołów już używających Kotlina** - wykorzystaj istniejącą wiedzę
✅ **Startupów chcących szybko iOS + Android** - zbuduj raz, uruchom wszędzie
✅ **Aplikacji opartych na danych** - aplikacje CRUD, dashboardy, formularze

## **Rozpoczęcie pracy**

Stworzyłem minimalny szablon do zademonstrowania tej konfiguracji:

**[GitHub: kotlin-fullstack-template](https://github.com/yourusername/kotlin-fullstack-template)** *(Ten projekt jest celowo uproszczony dla artykułu na blogu - bez uwierzytelniania, tylko czysty CRUD do zademonstrowania współdzielonych DTOs i bezpieczeństwa czasu kompilacji)*

Zawiera:
- **Backend Ktor** (uproszczone CRUD API z PostgreSQL)
- **Compose Multiplatform mobile** (iOS + Android)
    - Konfigurację URL API specyficzną dla platformy
    - Repozytoria z logiką offline-first
    - Bazę danych SQLDelight
    - DataSources API
- **Moduł shared** z:
    - DTOs (Task, CreateTaskRequest, UpdateTaskRequest)
- **Aplikację iOS** z projektem Xcode

## **Podsumowanie**

**Dobre:**
- ✅ Bezpieczeństwo czasu kompilacji to przełomowe rozwiązanie
- ✅ Znacznie szybsze tempo rozwoju
- ✅ Reużycie kodu sięga 90% (vs 0% z oddzielnymi repozytoriami)
- ✅ Jeden język, jeden system kompilacji, jedno repozytorium
- ✅ Backend i aplikacje mobilne ewoluują razem

**Złe:**
- ❌ Wstępna konfiguracja zajmuje trochę czasu
- ❌ Nieodpowiednie dla dużych, oddzielnych zespołów
- ❌ Konfiguracja kompilacji na iOS wymaga nauki
- ❌ Trudno znaleźć specjalistów od backendu w Kotlinie

**Werdykt:**

W przypadku małych i średnich projektów, w których kontrolujesz zarówno backend, jak i aplikację mobilną, to podejście jest fenomenalne. Czas zaoszczędzony na samym debugowaniu problemów z integracją API pokrywa koszty konfiguracji.

**Czy poleciłbym to wszystkim?** Nie - ale jeśli:
- Tworzysz nowy projekt od podstaw
- Czujesz się komfortowo z Kotlinem
- Chcesz szybko wdrażać aplikacje na wielu platformach
- Cenisz bezpieczeństwo typów

Wypróbuj szablon i sprawdź, czy pasuje do Twojego przypadku użycia.

### **Zasoby**

- **[Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)** - Oficjalna dokumentacja
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)** - Framework UI
- **[Ktor Framework](https://ktor.io/)** - Framework backendowy
- **[Exposed](https://github.com/JetBrains/Exposed)** - Framework SQL Kotlin
- **[SQLDelight](https://cashapp.github.io/sqldelight/)** - Type-safe SQL
- **[Template Repository](https://github.com/miel3k/kotlin-fullstack-template)** - Projekt demo**
