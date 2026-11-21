package cn.courtside.client

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MatchEntranceState(val id: String, val resumed: Boolean, val startedAtMillis: Long) {
    val durationMillis: Long
        get() = if (resumed) 1400L else 3400L
}

data class UiState(
    val login: Login? = null,
    val busy: Boolean = false,
    val online: Boolean = true,
    val tournaments: List<Tournament> = emptyList(),
    val workspace: Workspace? = null,
    val feed: Feed = Feed(),
    val referees: List<User> = emptyList(),
    val selected: Match? = null,
    val score: ScoreSession? = null,
    val route: String = "home",
    val entrance: MatchEntranceState? = null,
    val message: String? = null,
    val issue: String? = null,
    val lastSync: String = "尚未同步",
    val organizerRefreshedAt: String = "",
    val pending: Int = 0,
    val organizerLoading: Boolean = false,
    val organizerLoaded: Boolean = false,
    val organizerLoadError: String? = null,
    val organizerError: String? = null,
    val pendingRefereeJoins: Map<String, User> = emptyMap(),
    val invalidReviewIds: Set<String> = emptySet(),
)

class CourtsideViewModel(application: Application) : AndroidViewModel(application) {
    val repo = Repository(application)
    private val _ui = MutableStateFlow(UiState(login = repo.login))
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val scoreLock = Mutex()
    private val networkLock = Mutex()

    init {
        viewModelScope.launch {
            if (repo.login != null) {
                val feed = repo.cached("feed", Feed::class.java)
                if (feed != null) {
                    // The start response may have reached this device just before connectivity was
                    // lost.
                    // Restore that durable session even if the last task-list cache predates the
                    // start.
                    for (m in feed.matches) {
                        val local = repo.session(m.id)
                        if (
                            local != null &&
                                local.sessionId.isNotBlank() &&
                                m.status in listOf("PUBLISHED", "ACCEPTED")
                        ) {
                            m.status = "IN_PROGRESS"
                            m.sessionId = local.sessionId
                            m.deviceId = local.deviceId
                            m.correctionRound = local.correctionRound
                            m.progress = local
                        }
                    }
                }
                val tours =
                    repo.cached("tournaments", Array<Tournament>::class.java)?.toList().orEmpty()
                val workspace = repo.cached("workspace", Workspace::class.java)
                _ui.update {
                    it.copy(feed = feed ?: Feed(), tournaments = tours, workspace = workspace)
                }
                refresh(false)
            }
            while (isActive) {
                delay(10000)
                if (repo.login != null) refresh(false)
            }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun message(value: String) {
        _ui.update { it.copy(message = value) }
    }

    fun clearOrganizerError() {
        _ui.update { it.copy(organizerError = null) }
    }

    // Only organizer mutations use this completion path. A successful write must not be
    // retried merely because the following list refresh failed.
    private fun organizerJob(
        success: String,
        onSaved: () -> Unit,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        val login = repo.login?.takeIf { it.user.role == "ORGANIZER" } ?: return@launch
        if (_ui.value.busy) return@launch
        _ui.update { it.copy(busy = true, organizerError = null) }
        try {
            try {
                networkLock.withLock {
                    if (repo.login !== login) return@launch
                    block()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (repo.login !== login) return@launch
                failure(e)
                if (repo.login !== login) return@launch
                val detail = e.message ?: "连接失败，请检查网络"
                _ui.update {
                    it.copy(organizerError = if (e is ApiFailure || e is IllegalArgumentException)
                        detail else "未收到操作回执：$detail。请先刷新确认是否已保存，再决定是否重试。")
                }
                if (e is ApiFailure && e.code in setOf("STALE_SCHEDULE", "STATE_CONFLICT")) {
                    refresh(false, waitForLock = true)
                }
                return@launch
            }
            if (repo.login !== login) return@launch
            onSaved()
            val refreshed = refresh(false, waitForLock = true)
            if (repo.login === login) {
                message(if (refreshed) success else "$success；列表暂未刷新，请点击刷新重试")
            }
        } finally {
            if (repo.login === login) _ui.update { it.copy(busy = false) }
        }
    }

    private fun failure(e: Exception, visible: Boolean = true) {
        if (repo.login?.user?.role == "ORGANIZER" && e is ApiFailure &&
            e.status == 401 && e.code in setOf("TOKEN_EXPIRED", "UNAUTHENTICATED", "HTTP_401")
        ) {
            repo.updateLogin(null)
            _ui.value = UiState(message = "登录已失效，请重新登录")
            return
        }
        val online = e is ApiFailure || e is IllegalArgumentException
        _ui.update {
            it.copy(
                online = online,
                issue = e.message,
                message = if (visible) (e.message ?: "连接失败，请检查服务地址和网络") else it.message,
            )
        }
    }

    private fun job(block: suspend () -> Unit) =
        viewModelScope.launch {
            if (_ui.value.busy) return@launch
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failure(e)
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }

    fun signIn(url: String, username: String, password: String, organizer: Boolean) = job {
        repo.setUrl(url)
        val login =
            repo.request(
                "POST",
                "/auth/login",
                Login::class.java,
                mapOf("username" to username.trim(), "password" to password),
            )
        require((login.user.role == "ORGANIZER") == organizer) {
            "请使用${if (organizer) "赛事组" else "裁判"}账号登录本客户端"
        }
        repo.updateLogin(login)
        _ui.value = UiState(
            login = login,
            busy = true,
            pendingRefereeJoins = if (organizer) repo.pendingRefereeJoins() else emptyMap(),
        )
        refresh(true, waitForLock = true)
    }

    fun logout() {
        repo.updateLogin(null)
        _ui.value = UiState()
    }

    fun navigate(route: String) {
        _ui.update { it.copy(route = route, entrance = null) }
    }

    fun finishEntrance(id: String) {
        _ui.update {
            if (it.route == "entrance" && it.entrance?.id == id)
                it.copy(route = "score", entrance = null)
            else it
        }
    }

    fun refreshNow() = viewModelScope.launch { refresh(true) }

    private suspend fun refresh(visible: Boolean, waitForLock: Boolean = false): Boolean {
        val login = repo.login ?: return false
        if (waitForLock) networkLock.lock() else if (!networkLock.tryLock()) return false
        if (repo.login !== login) {
            networkLock.unlock()
            return false
        }
        val organizer = login.user.role == "ORGANIZER"
        if (organizer) _ui.update { it.copy(organizerLoading = true) }
        try {
            if (login.user.role == "ORGANIZER") {
                val tours =
                    repo.request("GET", "/tournaments", Array<Tournament>::class.java).toList()
                if (repo.login !== login) return false
                val refs = repo.request("GET", "/users/referees", Array<User>::class.java).toList()
                if (repo.login !== login) return false
                val selectedId =
                    _ui.value.workspace?.tournament?.id?.takeIf { id -> tours.any { it.id == id } }
                        ?: tours.firstOrNull()?.id
                val work =
                    selectedId?.let {
                        repo.request("GET", "/tournaments/$it/workspace", Workspace::class.java)
                    }
                if (repo.login !== login) return false
                repo.cache("tournaments", tours)
                if (repo.login !== login) return false
                if (work != null) repo.cache("workspace", work)
                if (repo.login !== login) return false
                _ui.update { old ->
                    updateOrganizerWorkspace(old, work).copy(
                        tournaments = tours,
                        referees = refs,
                    )
                }
            } else {
                val feed = repo.request("GET", "/me/matches", Feed::class.java)
                if (repo.login !== login) return false
                repo.cache("feed", feed)
                if (repo.login !== login) return false
                _ui.update { old ->
                    old.copy(
                        feed = feed,
                        tournaments = feed.tournaments,
                        selected = feed.matches.find { it.id == old.selected?.id } ?: old.selected,
                    )
                }
                flushPending(login)
                if (repo.login !== login) return false
                val current = _ui.value
                val score = current.score
                if (
                    score != null &&
                        !score.submitted &&
                        current.selected?.status == "IN_PROGRESS" &&
                        score.correctionRound == current.selected.correctionRound
                ) {
                    repo.request(
                        "PUT",
                        "/matches/${score.matchId}/progress",
                        Match::class.java,
                        score,
                    )
                }
            }
            if (repo.login !== login) return false
            val pending = repo.dao.list(repo.owner, "PENDING").size
            if (repo.login !== login) return false
            _ui.update {
                it.copy(
                    online = true,
                    pending = pending,
                    lastSync = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                )
            }
            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (repo.login === login) {
                failure(e, visible)
                if (organizer && repo.login === login) _ui.update {
                    it.copy(organizerLoaded = true, organizerLoadError = e.message ?: "加载失败，请重试")
                }
            }
            return false
        } finally {
            if (organizer && repo.login === login) _ui.update { it.copy(organizerLoading = false) }
            networkLock.unlock()
        }
    }

    fun chooseTournament(t: Tournament) = job {
        val login = repo.login ?: return@job
        networkLock.withLock {
            if (repo.login !== login) return@withLock
            val w = repo.request("GET", "/tournaments/${t.id}/workspace", Workspace::class.java)
            if (repo.login !== login) return@withLock
            repo.cache("workspace", w)
            if (repo.login !== login) return@withLock
            _ui.update {
                updateOrganizerWorkspace(it, w).copy(online = true, issue = null)
            }
        }
    }

    private fun updateOrganizerWorkspace(state: UiState, workspace: Workspace?): UiState {
        val selected = workspace?.matches?.find { it.id == state.selected?.id }
        val changedTournament = state.workspace?.tournament?.id != workspace?.tournament?.id
        return state.copy(
            workspace = workspace,
            selected = selected,
            route = if (changedTournament || state.route == "detail" && selected == null) "home" else state.route,
            organizerLoaded = true,
            organizerLoadError = null,
            organizerRefreshedAt = Instant.now().toString(),
        )
    }

    fun createTournament(body: Map<String, Any>, onSaved: () -> Unit = {}) =
        organizerJob("赛事已创建，接下来添加参赛者、场地和裁判", onSaved) {
            val t = repo.request("POST", "/tournaments", Tournament::class.java, body)
            // The POST receipt is enough to close the form, even if the next GET fails.
            _ui.update { old ->
                updateOrganizerWorkspace(old, Workspace().apply { tournament = t })
                    .copy(organizerRefreshedAt = "")
            }
        }

    fun editTournament(body: Map<String, Any>, onSaved: () -> Unit = {}) =
        organizerJob(if (body["status"] == "ARCHIVED") "赛事已归档" else "赛事资料已保存", onSaved) {
            val id = _ui.value.workspace!!.tournament.id
            val t = repo.request("PUT", "/tournaments/$id", Tournament::class.java, body)
            _ui.update { old ->
                old.copy(workspace = old.workspace?.let { current ->
                    Workspace().apply {
                        tournament = t
                        entrants = current.entrants
                        courts = current.courts
                        referees = current.referees
                        matches = current.matches
                        submissions = current.submissions
                        audit = current.audit
                    }
                })
            }
        }

    fun resource(kind: String, body: Map<String, String>, id: String? = null, onSaved: () -> Unit = {}) =
        organizerJob("已保存", onSaved) {
            val tid = _ui.value.workspace!!.tournament.id
            repo.request(
                if (id == null) "POST" else "PUT",
                "/tournaments/$tid/$kind" + (id?.let { "/$it" } ?: ""),
                Any::class.java,
                body,
            )
        }

    fun createReferee(username: String, name: String, password: String, onSaved: () -> Unit = {}) =
        organizerJob("裁判账号已创建并加入赛事", onSaved) {
            val tid = checkNotNull(_ui.value.workspace).tournament.id
            check(tid !in _ui.value.pendingRefereeJoins) { "请先处理已创建的裁判账号" }
            val ref =
                repo.request(
                    "POST",
                    "/users/referees",
                    User::class.java,
                    mapOf("username" to username, "name" to name, "password" to password),
                )
            updatePendingRefereeJoins(_ui.value.pendingRefereeJoins + (tid to ref))
            joinCreatedReferee(tid, ref)
        }

    fun retryRefereeJoin(tournamentId: String, onSaved: () -> Unit) =
        organizerJob("裁判已加入赛事", onSaved) {
            check(_ui.value.workspace?.tournament?.id == tournamentId) { "请回到原赛事重试加入" }
            val ref = checkNotNull(_ui.value.pendingRefereeJoins[tournamentId]) { "没有待加入的裁判" }
            joinCreatedReferee(tournamentId, ref)
        }

    private suspend fun joinCreatedReferee(tournamentId: String, ref: User) {
        repo.request(
            "POST",
            "/tournaments/$tournamentId/members",
            User::class.java,
            mapOf("userId" to ref.id),
        )
        updatePendingRefereeJoins(_ui.value.pendingRefereeJoins - tournamentId)
    }

    private fun updatePendingRefereeJoins(pending: Map<String, User>) {
        repo.savePendingRefereeJoins(pending)
        _ui.update { it.copy(pendingRefereeJoins = pending) }
    }

    fun deleteDraft(match: Match, onSaved: () -> Unit = {}) = organizerJob("草稿已删除", onSaved) {
        repo.request("DELETE", "/matches/${match.id}", Any::class.java)
        _ui.update { it.copy(route = "home", selected = null, score = null) }
    }

    fun saveMatch(body: Map<String, Any>, id: String? = null, onSaved: () -> Unit = {}) =
        organizerJob(if (id == null) "比赛已保存为草稿，确认后即可发布" else "比赛安排已保存", onSaved) {
            val tid = _ui.value.workspace!!.tournament.id
            repo.request(
                if (id == null) "POST" else "PUT",
                if (id == null) "/tournaments/$tid/matches" else "/matches/$id",
                Match::class.java,
                body,
            )
        }

    fun matchAction(match: Match, action: String, reason: String = "", onSaved: () -> Unit = {}) =
        if (repo.login?.user?.role == "ORGANIZER") {
            organizerJob(if (action == "publish") "赛程已发布至裁判端" else "比赛已取消", onSaved) {
                val m = repo.request("POST", "/matches/${match.id}/$action", Match::class.java,
                    mapOf("scheduleVersion" to match.scheduleVersion, "reason" to reason))
                _ui.update { it.copy(selected = m) }
            }
        } else job {
        val m =
            repo.request(
                "POST",
                "/matches/${match.id}/$action",
                Match::class.java,
                mapOf("scheduleVersion" to match.scheduleVersion, "reason" to reason),
            )
        _ui.update { it.copy(selected = m) }
        refresh(true)
        message(
            when (action) {
                "accept" -> "任务已接受，请按时到场"
                "publish" -> "赛程已发布至裁判端"
                else -> "操作完成"
            }
        )
    }

    fun review(submission: Submission, confirm: Boolean, reason: String = "", onSaved: () -> Unit = {}) =
        organizerJob(if (confirm) "成绩已确认为正式结果" else "已退回裁判更正", onSaved) {
            val workspace = _ui.value.workspace
            val match = workspace?.matches?.find { it.id == submission.matchId }
            val current = workspace?.submissions?.find { it.id == submission.id }
            if (submission.id in _ui.value.invalidReviewIds || match?.status != "PENDING_REVIEW" || current == null ||
                !isCurrentOrganizerSubmission(current, match)
            ) throw ApiFailure(409, "STATE_CONFLICT", "该提交已核验或被更新，请查看最新成绩")
            val receipt = try {
                repo.request(
                    "POST",
                    "/submissions/${submission.id}/${if (confirm) "confirm" else "return"}",
                    Submission::class.java,
                    mapOf("reason" to reason),
                )
            } catch (e: ApiFailure) {
                if (e.code == "STATE_CONFLICT") {
                    _ui.update { it.copy(invalidReviewIds = it.invalidReviewIds + submission.id) }
                }
                throw e
            }
            // Update the open review immediately even if the next workspace refresh fails.
            val reviewedMatch = repo.gson.fromJson(repo.gson.toJson(match), Match::class.java).apply {
                if (confirm) {
                    status = "FINISHED"
                } else {
                    status = "IN_PROGRESS"
                    correctionRound++
                    returnReason = receipt.reviewReason
                }
            }
            _ui.update { state ->
                val updated = Workspace().apply {
                    tournament = workspace.tournament
                    entrants = workspace.entrants
                    courts = workspace.courts
                    referees = workspace.referees
                    matches = workspace.matches.map { if (it.id == match.id) reviewedMatch else it }
                    submissions = workspace.submissions.map { if (it.id == receipt.id) receipt else it }
                    audit = workspace.audit
                }
                state.copy(workspace = updated, selected = if (state.selected?.id == match.id) reviewedMatch else state.selected)
            }
        }

    fun openMatch(match: Match) =
        viewModelScope.launch {
            if (repo.login?.user?.role == "ORGANIZER") {
                val latest = _ui.value.workspace?.matches?.find { it.id == match.id } ?: return@launch
                _ui.update { it.copy(selected = latest, score = null, route = "detail") }
                return@launch
            }
            val score = repo.session(match.id)
            _ui.update {
                it.copy(selected = match, score = score, route = "detail", entrance = null)
            }
        }

    fun start(match: Match, initialServer: Int) = job {
        val m =
            repo.request(
                "POST",
                "/matches/${match.id}/start",
                Match::class.java,
                mapOf("scheduleVersion" to match.scheduleVersion, "deviceId" to repo.deviceId),
            )
        val s =
            ScoreSession().apply {
                matchId = m.id
                ownerId = repo.login!!.user.id
                sessionId = m.sessionId
                deviceId = repo.deviceId
                this.initialServer = initialServer
                updatedAt = System.currentTimeMillis()
            }
        repo.saveSession(s)
        val feed = repo.gson.fromJson(repo.gson.toJson(_ui.value.feed), Feed::class.java)
        feed.matches = feed.matches.map { if (it.id == m.id) m else it }
        repo.cache("feed", feed)
        _ui.update {
            it.copy(
                selected = m,
                score = s,
                route = "entrance",
                feed = feed,
                entrance =
                    MatchEntranceState(
                        UUID.randomUUID().toString(),
                        false,
                        SystemClock.elapsedRealtime(),
                    ),
            )
        }
        refresh(false)
    }

    fun resume() = job {
        val m = _ui.value.selected ?: return@job
        require(m.deviceId == repo.deviceId) { "请使用开始这场比赛的原设备继续" }
        var s =
            repo.session(m.id)
                ?: m.progress?.let(repo::copy)
                ?: ScoreSession().apply {
                    matchId = m.id
                    ownerId = repo.login!!.user.id
                    sessionId = m.sessionId
                    deviceId = repo.deviceId
                }
        if (s.correctionRound < m.correctionRound) {
            require(_ui.value.online) { "请联网获取退回原因后再更正" }
            s =
                repo.copy(s).apply {
                    correctionRound = m.correctionRound
                    submissionId = ""
                    submitted = false
                    aConfirmed = false
                    bConfirmed = false
                    breakUntil = 0
                    localSeq++
                }
            repo.saveSession(s)
        }
        val complete = Scoring.winner(s, m.rules) >= 0
        _ui.update {
            it.copy(
                score = s,
                route = if (complete) "result" else "entrance",
                entrance =
                    if (complete) null
                    else
                        MatchEntranceState(
                            UUID.randomUUID().toString(),
                            true,
                            SystemClock.elapsedRealtime(),
                        ),
            )
        }
    }

    fun scoreAction(action: (ScoreSession, Match) -> Unit) =
        viewModelScope.launch {
            scoreLock.withLock {
                try {
                    val m = _ui.value.selected ?: return@withLock
                    val source = _ui.value.score ?: return@withLock
                    require(m.status == "IN_PROGRESS" && !source.submitted) { "当前比赛已提交，请先同步状态" }
                    val s = repo.copy(source)
                    action(s, m)
                    s.updatedAt = System.currentTimeMillis()
                    repo.saveSession(s)
                    _ui.update { it.copy(score = s, issue = null) }
                } catch (e: Exception) {
                    failure(e)
                }
            }
        }

    fun add(side: Int) = scoreAction { s, m ->
        Scoring.add(s, m.rules, side)
        val threshold = (m.rules.target + 1) / 2
        if (
            maxOf(Scoring.points(s.rallies, 0), Scoring.points(s.rallies, 1)) == threshold &&
                s.intervalGame != s.games.size
        ) {
            s.intervalGame = s.games.size
            s.breakUntil = System.currentTimeMillis() + 60000
            if (
                Scoring.wins(s, 0) == m.rules.gamesToWin - 1 &&
                    Scoring.wins(s, 1) == m.rules.gamesToWin - 1
            )
                s.swapped = !s.swapped
        }
    }

    fun undo() = scoreAction { s, _ -> Scoring.undo(s) }

    fun finishGame() = scoreAction { s, m ->
        Scoring.confirmGame(s, m.rules)
        if (Scoring.winner(s, m.rules) < 0) {
            s.breakUntil = System.currentTimeMillis() + 120000
            s.swapped = !s.swapped
        } else _ui.update { it.copy(route = "result") }
    }

    fun endBreak() = scoreAction { s, _ -> s.breakUntil = 0 }

    fun swap() = scoreAction { s, _ -> s.swapped = !s.swapped }

    fun retire(winner: Int, reason: String) = scoreAction { s, _ ->
        require(reason.isNotBlank()) { "请填写弃权原因" }
        s.retirementWinner = winner
        s.retirementReason = reason
        s.breakUntil = 0
        _ui.update { it.copy(route = "result") }
    }

    fun sign(side: Int) = scoreAction { s, _ ->
        if (side == 0) s.aConfirmed = !s.aConfirmed else s.bConfirmed = !s.bConfirmed
    }

    fun correctGame(index: Int, a: Int, b: Int, note: String) = scoreAction { s, _ ->
        require(s.correctionRound > 0 && note.isNotBlank()) { "更正需填写原因" }
        val g = s.games[index]
        g.a = a
        g.b = b
        g.corrected = true
        g.correctionNote = note
        s.aConfirmed = false
        s.bConfirmed = false
        s.localSeq++
    }

    fun submit() =
        viewModelScope.launch {
            scoreLock.withLock {
                try {
                    val m = _ui.value.selected ?: return@withLock
                    val s = repo.copy(_ui.value.score ?: return@withLock)
                    require(!s.submitted) { "该结果已进入同步队列" }
                    Scoring.validateResult(s, m.rules)
                    s.submissionId = UUID.randomUUID().toString()
                    s.submitted = true
                    s.updatedAt = System.currentTimeMillis()
                    repo.queue(s)
                    _ui.update { it.copy(score = s, pending = it.pending + 1, issue = null) }
                    message("结果已保存在本机，正在提交云端")
                } catch (e: Exception) {
                    failure(e)
                    return@launch
                }
            }
            refresh(true)
        }

    private suspend fun flushPending(login: Login) {
        for (row in repo.dao.list(repo.owner, "PENDING")) {
            if (repo.login !== login) return
            val s = repo.gson.fromJson(row.payload, ScoreSession::class.java)
            try {
                val receipt =
                    repo.request(
                        "POST",
                        "/matches/${s.matchId}/submissions",
                        Submission::class.java,
                        s,
                    )
                repo.dao.delete(row.id)
                if (repo.login !== login) return
                _ui.update { old ->
                    old.copy(
                        selected =
                            old.selected?.let {
                                if (it.id == s.matchId && receipt.status != "RETURNED")
                                    repo.gson
                                        .fromJson(repo.gson.toJson(it), Match::class.java)
                                        .apply {
                                            status =
                                                if (receipt.status == "CONFIRMED") "FINISHED"
                                                else "PENDING_REVIEW"
                                            currentSubmissionId = s.submissionId
                                        }
                                else it
                            }
                    )
                }
            } catch (e: ApiFailure) {
                if (repo.login !== login) return
                // Preserve rejected payloads for inspection; never discard a locally completed
                // match.
                message("待同步成绩：${e.message}")
                _ui.update { it.copy(issue = e.message) }
            }
        }
    }
}
