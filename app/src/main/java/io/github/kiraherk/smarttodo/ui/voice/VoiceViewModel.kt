package io.github.kiraherk.smarttodo.ui.voice

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.ActionType
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.ai.AiException
import io.github.kiraherk.smarttodo.data.db.InboxEntity
import io.github.kiraherk.smarttodo.domain.CategoryKey
import io.github.kiraherk.smarttodo.domain.NoteSource
import io.github.kiraherk.smarttodo.domain.TaskStatus
import io.github.kiraherk.smarttodo.ui.actionName
import io.github.kiraherk.smarttodo.ui.categoryName
import io.github.kiraherk.smarttodo.ui.recurrenceText
import io.github.kiraherk.smarttodo.ui.statusName
import io.github.kiraherk.smarttodo.util.Dates
import io.github.kiraherk.smarttodo.voice.SpeechCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The whole voice flow, shared by every screen:
 * listen -> understand (Gemini) -> show + read back -> he confirms -> save.
 * Anything that can't be understood right now is saved to the Inbox, never lost.
 */
class VoiceViewModel(private val c: AppContainer) : ViewModel() {

    enum class Mode { COMMAND, REVIEW, NOTE, SEARCH }

    data class Request(val mode: Mode = Mode.COMMAND, val taskId: Long? = null, val inboxId: Long? = null)

    data class Preview(val type: String, val summary: String, val detail: String)

    sealed interface UiState {
        data object Idle : UiState
        data class Listening(val text: String, val level: Float) : UiState
        /** [slow] = it is taking long (Google busy), so the screen says so and offers Cancel. */
        data class Thinking(val transcript: String, val slow: Boolean = false) : UiState
        data class Confirm(val transcript: String, val previews: List<Preview>) : UiState
        data class Answer(val question: String, val answer: String) : UiState
        data class Message(val text: String, val error: Boolean = false, val canRetry: Boolean = false) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _searchText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val searchText: SharedFlow<String> = _searchText.asSharedFlow()

    private var pendingActions: List<AiAction> = emptyList()
    private var pendingTranscript = ""
    private var pendingInboxId: Long? = null
    private var lastRequest = Request()
    private var listenJob: Job? = null
    /** The current "understand / answer" work, so Cancel can stop it. */
    private var workJob: Job? = null
    private var popupRequest: Request? = null

    // A channel, not a SharedFlow: the request must not be lost if the screen isn't collecting yet.
    private val _popupRequests = Channel<String>(Channel.CONFLATED)
    /** Language tags for which AppRoot should open Google's listening popup. */
    val popupRequests: Flow<String> = _popupRequests.receiveAsFlow()

    /** Starts listening. The caller must already hold the microphone permission. */
    fun start(request: Request) {
        lastRequest = request
        pendingActions = emptyList()
        c.speaker.stop()
        viewModelScope.launch {
            val s = c.settings.current()
            c.speaker.enabled = s.readAloud
            // Phones where the in-app listener can't work use Google's standard listening popup.
            if (s.voicePopup || !c.speech.isAvailable()) {
                openPopup(request, s.dialect)
                return@launch
            }
            listenJob?.cancel()
            listenJob = viewModelScope.launch {
                c.speech.state.collect { st -> if (st.listening) _state.value = UiState.Listening(st.display, st.level) }
            }
            c.speech.start(s.dialect) { text, error ->
                listenJob?.cancel()
                onTranscript(request, text, error, s.dialect)
            }
        }
    }

    private fun openPopup(request: Request, language: String) {
        popupRequest = request
        _state.value = UiState.Idle
        _popupRequests.trySend(language)
    }

    /** Text from Google's listening popup (null/blank if he said nothing or closed it). */
    fun onPopupResult(text: String?) {
        val request = popupRequest ?: return
        popupRequest = null
        launchWork {
            if (text.isNullOrBlank()) {
                _state.value = UiState.Message(str(R.string.voice_nothing_heard), error = true, canRetry = true)
            } else {
                handle(text, request)
            }
        }
    }

    /** The phone has no speech popup at all (no Google app). */
    fun onPopupUnavailable() {
        popupRequest = null
        viewModelScope.launch { c.settings.setVoicePopup(false) }
        _state.value = UiState.Message(str(R.string.voice_not_available), error = true)
    }

    fun finishListening() = c.speech.finish()

    fun retry() = start(lastRequest)

    /** Closes the voice window. Also stops a request that is still waiting for Google. */
    fun dismiss() {
        workJob?.cancel()
        workJob = null
        c.speech.cancel()
        c.speaker.stop()
        listenJob?.cancel()
        pendingActions = emptyList()
        _state.value = UiState.Idle
    }

    /** For text that didn't come from the microphone: inbox items, suggestion chips, typed text. */
    fun processText(text: String, request: Request = Request()) {
        lastRequest = request
        launchWork {
            c.speaker.enabled = c.settings.current().readAloud
            handle(text, request)
        }
    }

    /** A question straight to the assistant (one AI request instead of two). */
    fun ask(question: String) {
        launchWork {
            c.speaker.enabled = c.settings.current().readAloud
            answer(question, null)
        }
    }

    fun confirm() {
        val actions = pendingActions
        if (actions.isEmpty()) return
        pendingActions = emptyList()
        viewModelScope.launch {
            val result = c.executor.apply(actions, pendingTranscript)
            pendingInboxId?.let { c.db.inbox().delete(it) }
            pendingInboxId = null
            val msg = if (result.failed == 0) str(R.string.saved_ok) else str(R.string.saved_partly, result.applied, result.applied + result.failed)
            say(UiState.Message(msg, error = result.applied == 0))
        }
    }

    fun reject() {
        pendingActions = emptyList()
        c.speaker.stop()
        _state.value = UiState.Message(str(R.string.not_saved), canRetry = true)
    }

    fun speakAgain() {
        when (val s = _state.value) {
            is UiState.Answer -> c.speaker.speakNow(s.answer)
            is UiState.Confirm -> c.speaker.speakNow(s.previews.joinToString(" ") { it.summary })
            is UiState.Message -> c.speaker.speakNow(s.text)
            else -> Unit
        }
    }

    private fun onTranscript(request: Request, text: String, error: Int?, language: String) {
        launchWork {
            if (text.isBlank()) {
                when {
                    error == null -> _state.value = UiState.Message(str(R.string.voice_nothing_heard), error = true, canRetry = true)
                    error in SpeechCapture.NETWORK_ERRORS ->
                        _state.value = UiState.Message(str(R.string.voice_error_network), error = true, canRetry = true)
                    else -> {
                        // None of the phone's speech services work from inside the app: switch to
                        // Google's listening popup now and from now on (can be changed in Settings).
                        c.settings.setVoicePopup(true)
                        openPopup(request, language)
                    }
                }
                return@launchWork
            }
            handle(text, request)
        }
    }

    private suspend fun handle(text: String, request: Request) {
        when (request.mode) {
            Mode.NOTE -> {
                val id = request.taskId ?: return
                c.repo.addNote(id, text, NoteSource.VOICE)
                say(UiState.Message(str(R.string.note_saved) + "\n\n" + text))
            }
            Mode.SEARCH -> {
                _searchText.emit(text)
                _state.value = UiState.Idle
            }
            Mode.COMMAND, Mode.REVIEW -> interpret(text, request)
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        workJob?.cancel()
        workJob = viewModelScope.launch { block() }
    }

    /** Shows "Thinking", and after a while tells him Google is slow (he can press Cancel). */
    private fun thinking(text: String) {
        val shown = UiState.Thinking(text)
        _state.value = shown
        viewModelScope.launch {
            delay(SLOW_AFTER_MS)
            if (_state.value === shown) _state.value = shown.copy(slow = true)
        }
    }

    private suspend fun interpret(text: String, request: Request) {
        thinking(text)
        if (!c.secrets.hasApiKey()) {
            saveToInbox(text, request, "no key")
            say(UiState.Message(str(R.string.saved_inbox_no_key), error = true))
            return
        }
        try {
            val actions = c.interpreter.interpret(text, request.taskId, review = request.mode == Mode.REVIEW)
            val changes = actions.filter { it.type in ActionType.CHANGES }
            val question = actions.firstOrNull { it.type == ActionType.ASK }
            when {
                changes.isNotEmpty() -> {
                    pendingActions = changes
                    pendingTranscript = text
                    pendingInboxId = request.inboxId
                    val previews = changes.map { preview(it) }
                    _state.value = UiState.Confirm(text, previews)
                    val auto = c.settings.current().autoConfirm && changes.all { it.type == ActionType.CREATE || it.type == ActionType.ADD_NOTE }
                    if (auto) confirm() else speak(previews.joinToString(" ") { it.summary } + " " + str(R.string.confirm_spoken))
                }
                question != null -> answer(question.question?.takeIf { it.isNotBlank() } ?: text, request.inboxId)
                else -> say(UiState.Message(actions.firstOrNull()?.spokenSummary?.takeIf { it.isNotBlank() } ?: str(R.string.voice_unclear), canRetry = true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveToInbox(text, request, e.message)
            say(UiState.Message(errorMessage(e) + "\n\n" + str(R.string.saved_inbox_later), error = true))
        }
    }

    private suspend fun answer(question: String, inboxId: Long?) {
        thinking(question)
        if (!c.secrets.hasApiKey()) {
            say(UiState.Message(str(R.string.err_no_key), error = true))
            return
        }
        try {
            val a = c.assistant.answer(question)
            inboxId?.let { c.db.inbox().delete(it) }
            _state.value = UiState.Answer(question, a)
            speak(a)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            say(UiState.Message(errorMessage(e), error = true, canRetry = true))
        }
    }

    private suspend fun saveToInbox(text: String, request: Request, error: String?) {
        val existing = request.inboxId?.let { c.db.inbox().get(it) }
        if (existing != null) {
            c.db.inbox().update(existing.copy(attempts = existing.attempts + 1, error = error, state = "FAILED"))
        } else {
            c.db.inbox().insert(InboxEntity(text = text, createdAt = System.currentTimeMillis(), error = error))
        }
    }

    private fun errorMessage(e: Exception): String = str(
        when (e) {
            is AiException.NoKey -> R.string.err_no_key
            is AiException.InvalidKey -> R.string.err_invalid_key
            is AiException.Quota -> R.string.err_quota
            is AiException.Network -> R.string.err_network
            is AiException.Timeout, is AiException.Server -> R.string.err_slow
            else -> R.string.err_ai_generic
        }
    )

    private suspend fun preview(a: AiAction): Preview {
        val ctx = c.localized()
        val parts = mutableListOf<String>()
        a.taskId?.let { id -> c.repo.task(id)?.let { parts += "«${it.title}»" } }
        if (a.type == ActionType.CREATE) a.title?.takeIf { it.isNotBlank() }?.let { parts += it }
        CategoryKey.parse(a.category)?.let { parts += categoryName(ctx, it) }
        Dates.parseDate(a.date)?.let { parts += Dates.friendlyDate(ctx, it) }
        Dates.parseTime(a.time)?.let { parts += Dates.friendlyTime(ctx, it) }
        a.recurrence?.toRule()?.let { parts += recurrenceText(ctx, it) }
        if (a.removeRecurrence == true) parts += str(R.string.no_repeat)
        if (a.reminder == true) parts += "🔔"
        TaskStatus.parse(a.status)?.let { parts += statusName(ctx, it) + (a.progress?.takeIf { p -> p in 1..99 }?.let { p -> " $p%" } ?: "") }
        a.note?.takeIf { it.isNotBlank() }?.let { parts += "📝 $it" }
        return Preview(a.type, a.spokenSummary.ifBlank { actionName(ctx, a.type) }, parts.joinToString(" · "))
    }

    private fun say(message: UiState.Message) {
        _state.value = message
        speak(message.text)
    }

    private fun speak(text: String) = c.speaker.speak(text)

    private fun str(@StringRes id: Int, vararg args: Any): String = c.localized().getString(id, *args)

    companion object {
        const val SLOW_AFTER_MS = 12_000L
    }
}
