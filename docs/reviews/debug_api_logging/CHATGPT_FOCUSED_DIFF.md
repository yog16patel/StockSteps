# Focused diff — DEBUG-only API logging (security, concurrency)

Excerpts only; full files in the working tree. Each block names the decision, its risk and the tests that cover it.

## 1. Observe and rethrow; read only the saved body (`core/.../network/ApiLogging.kt`)

```kotlin
on(Send) { request ->
    val settings = settingsOf() ?: return@on proceed(request)       // off: one null check
    val id = ApiLogIds.next()
    request.attributes.put(ApiLogIdKey, id)
    …                                                                // snapshot method, URL, headers, serialized body
    settings.writer.emit(ApiLogLevel.INFO) { "REQ id=$id env=$environment … body=${ApiLogRedaction.body(…)}" }
    try {
        val call = proceed(request)
        …
        val saved = savedBody(response, responseType)
        settings.writer.emit(if (status >= 400) ApiLogLevel.WARN else ApiLogLevel.INFO) { "RES id=$id … body=${…}" }
        call
    } catch (cause: Throwable) {
        val (kind, source) = ApiLogErrors.classify(cause, settings.errorKind)
        if (kind == ApiLogErrors.CANCELLED) settings.writer.emit(INFO) { "CANCEL id=$id …" }
        else settings.writer.emit(WARN) { "ERR id=$id … kind=$kind type=${typeName(source)} … message=${safeMessage(source)}" }
        throw cause                                                  // unchanged: error mapping and cancellation stay as before
    }
}

private suspend fun savedBody(response: HttpResponse, contentType: ContentType?): SavedBody {
    if (!response.isSaved) return SavedBody(null, declared)          // streaming: never touched → [NOT AVAILABLE]
    val channel = response.rawContent                                // fresh channel over Ktor's saved bytes
    if (ApiLogRedaction.isBinary(contentType)) return SavedBody(null, declared ?: channel.discard())
    val prefix = channel.readRemaining(PARSE_LIMIT_BYTES + 1L).readByteArray()
    SavedBody(prefix, prefix.size + channel.discard())
}
```

- **Risk:**
  - `rawContent` is `@InternalAPI` (the same use as Ktor's own `ResponseObserver`);
  - a Ktor upgrade could change `SaveBody`.
- **Mitigation:** tests fail if the body is no longer readable or logged.
- **Tests (`ApiLoggingTest`):**
  - `requestAndResponseShareOneId…` (body still read in full);
  - `binaryBodiesAreOmittedAndStillReadable`;
  - `largeBodiesAreTruncatedAndHugeOnesNotInspected`;
  - `connectionFailuresAreClassifiedAndRethrownUnchanged`;
  - `errorResponsesKeepTheirCodeAndAreWarnings` (`StockStepsApiException` code unchanged);
  - `signedInPayloadsAreMetadataOnlyAndAuthIsUnchanged` (MockEngine sees `Bearer <token>` unchanged).

## 2. Deserialization failures without the input (`ApiLogging.kt`)

```kotlin
client.responsePipeline.intercept(HttpResponsePipeline.Receive) {     // before ContentNegotiation's Transform
    try { proceed() } catch (cause: Throwable) {
        if (settings != null && cause !is CancellationException) emit(WARN) {
            "ERR id=$id … kind=SERIALIZATION type=… target=${subject.expectedType.type.simpleName} detail=${serializationDetail(cause)}"
        }
        throw cause
    }
}
```

- **Detail:** only `missing=<field>`, `unknownKey=<key>` and `jsonPath=$…`. kotlinx messages quote the JSON input, so a message is never
  printed.
- **Tests:** `deserializationFailuresGiveTypeAndFieldsOnly`, `serializationDetailNamesFieldsAndPathOnly`.

## 3. Default-deny redaction (`core/.../network/ApiLogRedaction.kt`)

```kotlin
fun headers(headers) = … "$name=" + if (name.lowercase() in SAFE_HEADERS) text(value) else REDACTED
fun isSensitiveKey(name) = key in SENSITIVE_NAMES || SENSITIVE_FRAGMENTS.any { it in key } || SENSITIVE_SUFFIXES.any { key.endsWith(it) }
fun path(p) = /* under /api/v1/me/ every non-lower-case-word segment */ REDACTED
fun json(e) = /* sensitive key → REDACTED whatever the value; strings → text() */
fun shape(e) = /* keys, types, array sizes; id-like keys hidden */
fun body(…, userData, …) = when { parsed != null && userData -> "[METADATA] " + shape(parsed); parsed != null -> json(parsed).toString(); … }
```

- **Risk:**
  - over-redaction of market fields: `session` was dropped as a fragment so `sessionDate` and `marketSession` stay readable;
  - under-redaction of an unknown secret header or key: headers are allow-listed, and keys also match by value shape (JWT, opaque 32+,
    e-mail, phone, card, `mock-user:`).
- **Tests (`ApiLogRedactionTest`):**
  - `sensitiveKeysAreMatchedWithoutHidingMarketFields`;
  - `urlsKeepRouteAndSafeQueryButDropCredentials`;
  - `signedInPathsHideIdsButKeepRouteWords`;
  - `headersAreDefaultDeny`;
  - `textRedactsTokenShapes…`;
  - `jsonIsRedactedAtEveryDepth`;
  - `shapeShowsStructureWithoutValuesOrIdKeys`;
  - `bodiesFollowTypeSizeAndUserDataRules`.
- **Device check:** a grep over every captured line, with 0 hits.

## 4. Concurrency (`ApiLogging.kt`)

```kotlin
class ApiLogWriter(sink, dispatcher = Dispatchers.Default) {
    private val queue = Channel<Pair<ApiLogLevel, () -> String>>(512, onBufferOverflow = DROP_OLDEST)
    init { if (dispatcher != null) CoroutineScope(SupervisorJob() + dispatcher).launch { for ((l, b) in queue) write(l, b) } }
    fun emit(level, build) { if (dispatcher == null) write(level, build) else queue.trySend(level to build) }   // never suspends
    private fun write(level, build) { try { sink.write(level, build()) } catch (_: Throwable) {} }
}
internal object ApiLogIds { … AtomicInt … "$prefix-" + counter.incrementAndFetch().toString().padStart(4, '0') }
private object EnvironmentTracker { @Volatile var last: String? = null }   // process-wide (the app has two clients)
```

- **Risk:**
  - a lost line under a burst (accepted; debug only);
  - the ENV tracker is a benign race: two clients may both log one switch.
- **Tests:**
  - `concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter` (25 parallel requests, distinct ids, each RES carries its own request's
    data);
  - `environmentLabelsComeFromTheExplicitChoiceAndSwitchesAreLogged`.

## 5. Build gating (Android `AndroidApiLogging.kt`, `MainActivity.kt`; iOS `IosApiLogging.kt`, `iOSApp.swift`)

```kotlin
// Android: called inside `if (BuildConfig.DEBUG)` in MainActivity
internal fun allowed(debugBuild: Boolean, applicationFlags: Int) = debugBuild && (applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
// iOS: Swift call compiled only under #if DEBUG
fun enable(environment: () -> String) = ApiLogging.enable(kotlin.native.Platform.isDebugBinary, ApiLogSettings(environment, ApiLogWriter(sink), errorKind = ::darwinErrorKind))
// core
fun enable(debugBuild: Boolean, settings: ApiLogSettings): Boolean { this.settings = if (debugBuild) settings else null; return this.settings != null }
```

- **Risk:** someone calls `enable(true, …)` from release code. **Mitigation:** both platform wrappers add a runtime build check that the
  caller can't override.
- **Tests:**
  - `releaseBuildsCannotEnableLogging`;
  - `AndroidApiLoggingTest.onlyADebugBuildOfADebuggableAppMayLog` and `appClientIsSilentWhileLoggingIsOff`;
  - `IosApiLoggingSinkTest.enableUsesTheDebugBinaryFlag`.

## 6. iOS sink (fixed during device verification)

```kotlin
// was: NSLog("%@", "[StockSteps-API] $text")  → EXC_BAD_ACCESS: a Kotlin String through C varargs isn't an NSString
NSLog("[${ApiLogging.TAG}] $text".replace("%", "%%"))
```

- **Test:** `IosApiLoggingSinkTest.nsLogSinkWritesLinesWithPercentSignsAndLongLines` drives the real sink. It was not re-run against the old
  code.
