package io.github.kiraherk.smarttodo.ui.voice

import androidx.annotation.StringRes
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kiraherk.smarttodo.AppContainer
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ai.ActionType
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.ai.AiException
import io.github.kiraherk.smarttodo.data.settings.LISTEN_AUTO
import io.github.kiraherk.smarttodo.data.settings.LISTEN_POPUP
import io.github.kiraherk.smarttodo.data.settings.LISTEN_RECORD
import io.github.kiraherk.smarttodo.data.db.ChatMessageEntity
import io.github.kiraherk.smarttodo.data.db.InboxEntity
import io.github.kiraherk.smarttodo.domain.ChatRole
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

    /** DICTATE = only write down what he said (into the chat's text box, so he can correct it). */
    enum class Mode { COMMAND, REVIEW, NOTE, SEARCH, DICTATE }

    /** [chat] = sent from the chat: questions and results are answered in the chat, like a conversation. */
    data class Request(val mode: Mode = Mode.COMMAND, val taskId: Long? = null, val inboxId: Long? = null, val chat: Boolean = false)

    data class Preview(val type: String, val summary: String, val detail: String)

    sealed interface UiState {
        data object Idle : UiState
        /** [recording] = recording with no time limit (he presses Done); [elapsedMs] = how long so far. */
        data class Listening(val text: String, val level: Float, val recording: Boolean = false, val elapsedMs: Long = 0) : UiState
        /** [slow] = it is taking long (Google busy), so the screen says so and offers Cancel. */
        data class Thinking(val transcript: String, val slow: Boolean = false) : UiState
        /** [actions] = what will be saved; new tasks can still be changed on the card. */
        data class Confirm(val transcript: String, val previews: List<Preview>, val actions: List<AiAction> = emptyList()) : UiState
        data class Answer(val question: String, val answer: String) : UiState
        data class Message(val text: String, val error: Boolean = false, val canRetry: Boolean = false) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _searchText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val searchText: SharedFlow<String> = _searchText.asSharedFlow()

    private val _dictation = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** What he said into the chat's microphone, for the text box. */
    val dictation: SharedFlow<String> = _dictation.asSharedFlow()

    private val _chatTyping = MutableStateFlow(false)
    /** The assistant is working on a chat message ("typing..." in the chat). */
    val chatTyping: StateFlow<Boolean> = _chatTyping.asStateFlow()

    /** The changes waiting for his OK came from the chat (the result is then written in the chat). */
    private var pendingFromChat = false

    private var pendingActions: List<AiAction> = emptyList()
    private var pendingTranscript = ""
    private var pendingInboxId: Long? = null
    private var lastRequest = Request()
    private var listenJob: Job? = null
    /** The current "understand / answer" work, so Cancel can stop it. */
    private var workJob: Job? = null
    private var popupRequest: Request? = null
    /** Set while recording (the request the recording is for). */
    private var recordRequest: Request? = null
    /** Failed in-app listening attempts in a row. */
    private var inAppFailures = 0

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
            val hasKey = c.secrets.hasApiKey()
            when {
                s.listenMode == LISTEN_POPUP -> {
                    openPopup(request, s.dialect)
                    return@launch
                }
                s.listenMode == LISTEN_RECORD && hasKey -> {
                    startRecording(request)
                    return@launch
                }
                // No speech recognition on this phone: record (or, without an AI key, Google's window).
                !c.speech.isAvailable() -> {
                    if (hasKey) startRecording(request) else openPopup(request, s.dialect)
                    return@launch
                }
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

    /**
     * Records with no time limit: pauses are fine, it only ends when he presses Done (or after two
     * minutes). The AI then writes down what he said.
     */
    private fun startRecording(request: Request) {
        recordRequest = request
        c.recorder.onLimitReached = { viewModelScope.launch { finishListening() } }
        if (!c.recorder.start()) {
            recordRequest = null
            _state.value = UiState.Message(str(R.string.voice_mic_busy), error = true, canRetry = true)
            return
        }
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            c.recorder.state.collect { st ->
                if (st.recording) _state.value = UiState.Listening("", st.level, recording = true, elapsedMs = st.elapsedMs)
            }
        }
    }

    private fun finishRecording() {
        val request = recordRequest ?: return
        recordRequest = null
        launchWork {
            val wav = c.recorder.stop()
            listenJob?.cancel()
            if (wav == null) {
                _state.value = UiState.Message(str(R.string.voice_nothing_heard), error = true, canRetry = true)
                return@launchWork
            }
            thinking(str(R.string.voice_writing))
            val text = try {
                val dialect = c.settings.current().dialect
                c.ai.transcribe(wav, if (dialect.startsWith("en")) "English" else "Arabic")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(UiState.Message(errorMessage(e), error = true, canRetry = true))
                return@launchWork
            }
            if (text.isBlank()) {
                _state.value = UiState.Message(str(R.string.voice_nothing_heard), error = true, canRetry = true)
            } else {
                handle(text, request)
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
        viewModelScope.launch { c.settings.setListenMode(LISTEN_AUTO) }
        _state.value = UiState.Message(str(R.string.voice_not_available), error = true)
    }

    /** He pressed "Done". */
    fun finishListening() {
        if (recordRequest != null) finishRecording() else c.speech.finish()
    }

    fun retry() = start(lastRequest)

    /** Closes the voice window. Also stops a request that is still waiting for Google. */
    fun dismiss() {
        workJob?.cancel()
        workJob = null
        recordRequest = null
        c.recorder.cancel()
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

    /**
     * A message sent in the chat (typed, dictated or a ready-made question). It shows up in the
     * chat straight away, the assistant "types" while it works, then answers there. Changes to
     * his tasks still get the confirmation card first.
     */
    fun chat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        c.speaker.stop()
        lastRequest = Request(chat = true)
        launchWork {
            c.speaker.enabled = c.settings.current().readAloud
            c.db.chat().insert(ChatMessageEntity(role = ChatRole.USER.name, text = clean, at = System.currentTimeMillis()))
            _chatTyping.value = true
            try {
                interpret(clean, Request(chat = true))
            } finally {
                _chatTyping.value = false
            }
        }
    }

    /** A ready-made question in the chat: straight to the assistant (one AI request), answered in the chat. */
    fun chatAsk(question: String) {
        c.speaker.stop()
        launchWork {
            c.speaker.enabled = c.settings.current().readAloud
            c.db.chat().insert(ChatMessageEntity(role = ChatRole.USER.name, text = question, at = System.currentTimeMillis()))
            if (!c.secrets.hasApiKey()) {
                chatReply(str(R.string.err_no_key))
                return@launchWork
            }
            _chatTyping.value = true
            try {
                val a = c.assistant.answer(question, alreadyInChat = true)
                c.speaker.speak(a, key = "chat-${c.db.chat().recent(1).lastOrNull()?.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chatReply(errorMessage(e))
            } finally {
                _chatTyping.value = false
            }
        }
    }

    /** A reply from the app in the chat (results, "I didn't understand", errors). */
    private suspend fun chatReply(text: String) {
        val id = c.db.chat().insert(ChatMessageEntity(role = ChatRole.ASSISTANT.name, text = text, at = System.currentTimeMillis()))
        c.speaker.speak(text, key = "chat-$id")
    }

    fun confirm() {
        val actions = pendingActions
        if (actions.isEmpty()) return
        pendingActions = emptyList()
        val fromChat = pendingFromChat
        pendingFromChat = false
        val shown = (_state.value as? UiState.Confirm)?.previews.orEmpty()
        viewModelScope.launch {
            val result = c.executor.apply(actions, pendingTranscript)
            pendingInboxId?.let { c.db.inbox().delete(it) }
            pendingInboxId = null
            if (fromChat) {
                val done = shown.joinToString("\n") { p -> "• " + p.detail.ifBlank { p.summary } }
                val head = if (result.failed == 0) str(R.string.saved_ok) else str(R.string.saved_partly, result.applied, result.applied + result.failed)
                c.db.chat().insert(ChatMessageEntity(role = ChatRole.ASSISTANT.name, text = head + "\n" + done, at = System.currentTimeMillis()))
            }
            if (result.failed == 0) {
                // Saved: close the window straight away (no extra "Saved" screen to dismiss).
                c.speaker.stop()
                _state.value = UiState.Idle
                Toast.makeText(c.localized(), str(R.string.saved_ok), Toast.LENGTH_SHORT).show()
            } else {
                say(UiState.Message(str(R.string.saved_partly, result.applied, result.applied + result.failed), error = result.applied == 0))
            }
        }
    }

    /** He corrected what the phone heard: understand the corrected sentence instead. */
    fun reprocess(text: String) {
        val request = if (pendingFromChat) Request(chat = true) else lastRequest
        pendingActions = emptyList()
        pendingFromChat = false
        c.speaker.stop()
        if (request.chat) {
            launchWork {
                c.db.chat().insert(ChatMessageEntity(role = ChatRole.USER.name, text = text, at = System.currentTimeMillis()))
                _state.value = UiState.Idle
                _chatTyping.value = true
                try { interpret(text, request) } finally { _chatTyping.value = false }
            }
        } else {
            processText(text, request)
        }
    }

    /** He changed a detail of a new task on the confirmation card. */
    fun editPending(index: Int, edited: AiAction) {
        val current = _state.value as? UiState.Confirm ?: return
        if (index !in pendingActions.indices) return
        c.speaker.stop()
        val fixed = if (edited.type == ActionType.CREATE) {
            edited.copy(spokenSummary = str(R.string.confirm_add_summary, edited.title.orEmpty()))
        } else {
            edited
        }
        pendingActions = pendingActions.toMutableList().also { it[index] = fixed }
        viewModelScope.launch {
            _state.value = current.copy(previews = pendingActions.map { preview(it) }, actions = pendingActions)
        }
    }

    fun reject() {
        pendingActions = emptyList()
        c.speaker.stop()
        if (pendingFromChat) {
            pendingFromChat = false
            _state.value = UiState.Idle
            viewModelScope.launch { chatReply(str(R.string.not_saved)) }
            return
        }
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
                        // The phone's speech recognition failed inside the app. Carry on listening
                        // by recording (no time limit, he presses Done). If it keeps failing on
                        // this phone, record straight away from now on (Settings can change it).
                        inAppFailures++
                        if (c.secrets.hasApiKey()) {
                            if (inAppFailures >= SWITCH_AFTER_FAILURES) c.settings.setListenMode(LISTEN_RECORD)
                            startRecording(request)
                        } else {
                            openPopup(request, language)
                        }
                    }
                }
                return@launchWork
            }
            inAppFailures = 0
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
            Mode.DICTATE -> {
                // Into the chat's text box: he checks it, corrects it if needed, then sends it.
                _dictation.emit(text)
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
        if (request.chat) {
            interpretInChat(text, request)
            return
        }
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
                    _state.value = UiState.Confirm(text, previews, changes)
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

    /** Same as [interpret], but the conversation happens in the chat instead of the voice window. */
    private suspend fun interpretInChat(text: String, request: Request) {
        if (!c.secrets.hasApiKey()) {
            saveToInbox(text, request, "no key")
            chatReply(str(R.string.saved_inbox_no_key))
            return
        }
        try {
            val actions = c.interpreter.interpret(text, request.taskId)
            val changes = actions.filter { it.type in ActionType.CHANGES }
            val question = actions.firstOrNull { it.type == ActionType.ASK }
            when {
                changes.isNotEmpty() -> {
                    pendingActions = changes
                    pendingTranscript = text
                    pendingInboxId = null
                    pendingFromChat = true
                    val previews = changes.map { preview(it) }
                    _state.value = UiState.Confirm(text, previews, changes)
                    val auto = c.settings.current().autoConfirm && changes.all { it.type == ActionType.CREATE || it.type == ActionType.ADD_NOTE }
                    if (auto) confirm() else speak(previews.joinToString(" ") { it.summary } + " " + str(R.string.confirm_spoken))
                }
                question != null -> {
                    val a = c.assistant.answer(question.question?.takeIf { it.isNotBlank() } ?: text, alreadyInChat = true)
                    val last = c.db.chat().recent(1).lastOrNull()
                    c.speaker.speak(a, key = "chat-${last?.id}")
                }
                else -> chatReply(actions.firstOrNull()?.spokenSummary?.takeIf { it.isNotBlank() } ?: str(R.string.voice_unclear))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveToInbox(text, request, e.message)
            chatReply(errorMessage(e) + "\n\n" + str(R.string.saved_inbox_later))
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
        // No button to press: it is added by itself once the connection (or the AI) is back.
        io.github.kiraherk.smarttodo.inbox.OfflineQueue.scheduleOnConnection(c.app)
        io.github.kiraherk.smarttodo.inbox.OfflineQueue.scheduleLater(c.app, io.github.kiraherk.smarttodo.inbox.OfflineQueue.NETWORK_RETRY_MS)
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
        if (a.type == ActionType.CLEAR_HISTORY && a.taskId == null) parts += str(R.string.whole_history)
        if (a.type == ActionType.CREATE) a.title?.takeIf { it.isNotBlank() }?.let { parts += it }
        a.category?.takeIf { it.isNotBlank() }?.let { parts += io.github.kiraherk.smarttodo.ui.categoryLabel(ctx, c.categories.all(), it) }
        Dates.parseDate(a.date)?.let { parts += Dates.friendlyDate(ctx, it) }
        Dates.parseTime(a.time)?.let { parts += Dates.friendlyTime(ctx, it) }
        a.recurrence?.toRule()?.let { parts += recurrenceText(ctx, it) }
        if (a.removeRecurrence == true) parts += str(R.string.no_repeat)
        if (a.removeDate == true && a.date.isNullOrBlank()) parts += str(R.string.no_date)
        if (a.removeTime == true && a.time.isNullOrBlank()) parts += str(R.string.no_time)
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
        const val SWITCH_AFTER_FAILURES = 2
    }
}
