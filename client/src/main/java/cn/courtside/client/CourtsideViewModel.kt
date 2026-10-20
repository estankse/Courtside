package cn.courtside.client

import android.app.Application
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
    val message: String? = null,
    val issue: String? = null,
    val lastSync: String = "尚未同步",
    val pending: Int = 0,
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

    private fun failure(e: Exception, visible: Boolean = true) {
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
        repo.persistLogin(login)
        _ui.value = UiState(login = login)
        refresh(true)
    }

    fun logout() {
        repo.persistLogin(null)
        _ui.value = UiState()
    }

    fun navigate(route: String) {
        _ui.update { it.copy(route = route) }
    }

    fun refreshNow() = viewModelScope.launch { refresh(true) }

    private suspend fun refresh(visible: Boolean) {
        if (!networkLock.tryLock()) return
        val accountKey = repo.owner
        try {
            val login = repo.login ?: return
            if (login.user.role == "ORGANIZER") {
                val tours =
                    repo.request("GET", "/tournaments", Array<Tournament>::class.java).toList()
                val refs = repo.request("GET", "/users/referees", Array<User>::class.java).toList()
                val selectedId =
                    _ui.value.workspace?.tournament?.id?.takeIf { id -> tours.any { it.id == id } }
                        ?: tours.firstOrNull()?.id
                val work =
                    selectedId?.let {
                        repo.request("GET", "/tournaments/$it/workspace", Workspace::class.java)
                    }
                if (repo.owner != accountKey) return
                repo.cache("tournaments", tours)
                if (work != null) repo.cache("workspace", work)
                _ui.update { old ->
                    old.copy(
                        tournaments = tours,
                        referees = refs,
                        workspace = work,
                        selected = work?.matches?.find { it.id == old.selected?.id } ?: old.selected,
                    )
                }
            } else {
                val feed = repo.request("GET", "/me/matches", Feed::class.java)
                if (repo.owner != accountKey) return
                repo.cache("feed", feed)
                _ui.update { old ->
                    old.copy(
                        feed = feed,
                        tournaments = feed.tournaments,
                        selected = feed.matches.find { it.id == old.selected?.id } ?: old.selected,
                    )
                }
                flushPending()
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
            val pending = repo.dao.list(repo.owner, "PENDING").size
            _ui.update {
                it.copy(
                    online = true,
                    pending = pending,
                    lastSync = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (repo.owner == accountKey) failure(e, visible)
        } finally {
            networkLock.unlock()
        }
    }

    fun chooseTournament(t: Tournament) = job {
        val w = repo.request("GET", "/tournaments/${t.id}/workspace", Workspace::class.java)
        repo.cache("workspace", w)
        _ui.update { it.copy(workspace = w) }
    }

    fun createTournament(body: Map<String, Any>) = job {
        val t = repo.request("POST", "/tournaments", Tournament::class.java, body)
        val w = repo.request("GET", "/tournaments/${t.id}/workspace", Workspace::class.java)
        _ui.update { it.copy(workspace = w) }
        refresh(true)
        message("赛事已创建，接下来添加参赛者、场地和裁判")
    }

    fun editTournament(body: Map<String, Any>) = job {
        val id = _ui.value.workspace!!.tournament.id
        repo.request("PUT", "/tournaments/$id", Tournament::class.java, body)
        refresh(true)
    }

    fun resource(kind: String, body: Map<String, String>, id: String? = null) = job {
        val tid = _ui.value.workspace!!.tournament.id
        repo.request(
            if (id == null) "POST" else "PUT",
            "/tournaments/$tid/$kind" + (id?.let { "/$it" } ?: ""),
            Any::class.java,
            body,
        )
        refresh(true)
        message("已保存")
    }

    fun createReferee(username: String, name: String, password: String) = job {
        val ref =
            repo.request(
                "POST",
                "/users/referees",
                User::class.java,
                mapOf("username" to username, "name" to name, "password" to password),
            )
        val tid = _ui.value.workspace!!.tournament.id
        repo.request(
            "POST",
            "/tournaments/$tid/members",
            User::class.java,
            mapOf("userId" to ref.id),
        )
        refresh(true)
        message("裁判账号已创建并加入赛事")
    }

    fun deleteDraft(match: Match) = job {
        repo.request("DELETE", "/matches/${match.id}", Any::class.java)
        _ui.update { it.copy(route = "home", selected = null, score = null) }
        refresh(true)
        message("草稿已删除")
    }

    fun saveMatch(body: Map<String, Any>, id: String? = null) = job {
        val tid = _ui.value.workspace!!.tournament.id
        repo.request(
            if (id == null) "POST" else "PUT",
            if (id == null) "/tournaments/$tid/matches" else "/matches/$id",
            Match::class.java,
            body,
        )
        refresh(true)
        message(if (id == null) "比赛已保存为草稿，确认后即可发布" else "赛程已更新，裁判需重新接受")
    }

    fun matchAction(match: Match, action: String, reason: String = "") = job {
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

    fun review(submission: Submission, confirm: Boolean, reason: String = "") = job {
        repo.request(
            "POST",
            "/submissions/${submission.id}/${if (confirm) "confirm" else "return"}",
            Submission::class.java,
            mapOf("reason" to reason),
        )
        refresh(true)
        message(if (confirm) "成绩已确认归档" else "已退回裁判更正")
    }

    fun openMatch(match: Match) =
        viewModelScope.launch {
            val score = repo.session(match.id)
            _ui.update { it.copy(selected = match, score = score, route = "detail") }
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
        _ui.update { it.copy(selected = m, score = s, route = "score", feed = feed) }
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
        _ui.update {
            it.copy(score = s, route = if (Scoring.winner(s, m.rules) >= 0) "result" else "score")
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

    private suspend fun flushPending() {
        for (row in repo.dao.list(repo.owner, "PENDING")) {
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
                // Preserve rejected payloads for inspection; never discard a locally completed
                // match.
                message("待同步成绩：${e.message}")
                _ui.update { it.copy(issue = e.message) }
            }
        }
    }
}
