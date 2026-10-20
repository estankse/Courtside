# HTTP API

根路径 `/api`。除 `/health` 与 `/auth/login` 外，所有接口要求 `Authorization: Bearer <token>`。时间使用 UTC ISO 8601，界面按 Asia/Shanghai 显示。错误结构为 `{ "code": "业务码", "message": "可读原因" }`。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | /health | 服务健康 |
| POST | /auth/login | username、password 换取 token 和 user |
| GET | /me | 当前用户 |
| GET | /users/referees | 赛事组可查询的裁判账号 |
| POST | /users/referees | 赛事组创建裁判 username、name、password |
| GET POST | /tournaments | 赛事列表或创建 |
| PUT | /tournaments/{id} | 修改信息、草稿规则或归档 |
| GET | /tournaments/{id}/workspace | 本赛事参赛者、场地、裁判、赛程、提交、审计 |
| POST | /tournaments/{id}/entrants | 添加参赛者 name、unit |
| PUT | /tournaments/{id}/entrants/{id} | 编辑参赛者 |
| POST PUT | /tournaments/{id}/courts[/{id}] | 添加或编辑场地 name |
| POST | /tournaments/{id}/members | 按 userId 添加裁判 |
| POST | /tournaments/{id}/matches | 新建比赛草稿 |
| PUT | /matches/{id} | 调整未开始比赛，携带 scheduleVersion |
| DELETE | /matches/{id} | 仅删除未发布草稿 |
| GET | /me/matches | 当前裁判的 Feed |
| GET | /matches/{id} | 可见比赛详情 |
| POST | /matches/{id}/publish | 发布草稿，检查时间冲突 |
| POST | /matches/{id}/cancel | 未开始比赛取消，必填 reason |
| POST | /matches/{id}/accept | 接受任务，必填 scheduleVersion |
| POST | /matches/{id}/start | 在线开始，必填 scheduleVersion、deviceId |
| PUT | /matches/{id}/progress | ScoreSession 比分快照 |
| POST | /matches/{id}/submissions | ScoreSession 正式成绩 |
| GET | /submissions/{submissionId} | 查询接收或核验回执 |
| POST | /submissions/{id}/confirm | 赛事组确认当前提交 |
| POST | /submissions/{id}/return | 退回更正，必填 reason |

创建比赛使用 sideAId、sideBId、courtId、refereeId、startsAt、endsAt、round。服务端从本赛事资料中解析显示姓名，不接受客户端任意指定正式姓名或越赛事引用。

ScoreSession 与 Game 的完整字段见 shared/Models.java。逐分 rallies 中 0 为蓝方、1 为红方；games 为已确认局；localSeq 是递增操作序号，撤销也增加序号。sessionId、deviceId、ownerId、correctionRound 与云端匹配才允许同步。

正式提交的 submissionId 在首次排队时生成；同一编号同一内容返回原回执，不重复改变状态。相同编号不同内容返回409。退回增加 correctionRound，重新提交使用新编号；迟到的原提交重试可返回原回执，但不会重新生效。

状态路径：DRAFT → PUBLISHED → ACCEPTED → IN_PROGRESS → PENDING_REVIEW → FINISHED。未开始场次可 CANCELLED；PENDING_REVIEW 退回到 IN_PROGRESS，并保留旧提交。IN_PROGRESS 后安排锁定。已确认成绩不能直接覆盖。

主要错误码：UNAUTHENTICATED、TOKEN_EXPIRED、BAD_CREDENTIALS、FORBIDDEN、NOT_FOUND、VALIDATION、INVALID_TIME、STALE_SCHEDULE、STATE_CONFLICT、SESSION_CONFLICT、STALE_ROUND、IDEMPOTENCY_CONFLICT、COURT_CONFLICT、REFEREE_CONFLICT、ENTRANT_CONFLICT、ARCHIVED。
