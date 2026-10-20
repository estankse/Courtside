# 羽序 Courtside

羽毛球赛事管理与裁判协同系统。两个独立的原生 Android 应用使用 Kotlin、Jetpack Compose、ViewModel 和 Room，通过 Spring Boot 云服务协作。

## 快速运行

1. 用 Android Studio 打开本目录，Gradle JDK 选择 **JDK 21**（JDK 17 也可），安装 Android SDK 35 和 Build Tools 36.0.0。`local.properties` 中的 `sdk.dir` 指向本机 SDK。
2. Windows 下执行 `./scripts/setup.ps1` 可下载并校验 JDK 21 与 Gradle 8.11.1，工具链保存在项目同级 `tmp/toolchain`，不会修改系统 Java。
3. 执行 `./scripts/build.ps1`，完成两个 APK、服务端包和自动测试。最终文件复制到 `dist`。只构建服务端可加 `-ServerOnly`。
4. 执行 `./scripts/start-server.ps1`，或 `./scripts/start-server.ps1 -Background` 在后台启动。健康检查为 [本机服务](http://localhost:8080/api/health)。前台用 Ctrl+C 停止，后台可执行 `./scripts/stop-server.ps1`。
5. 分别安装 `dist/courtside-organizer.apk` 与 `dist/courtside-referee.apk`。Android 模拟器默认连接 `http://10.0.2.2:8080/api`。真机与电脑连接同一局域网，在登录页“服务连接设置”填写 `http://电脑的局域网IP:8080/api`。

调试 APK 允许 HTTP 局域网联调；正式 release 配置只允许 HTTPS。部署到公网时应配置 HTTPS 反向代理，并替换演示账号及数据库凭证。没有云账号时，服务端可先在本机运行，两个客户端仍然通过真实 HTTP 请求交换数据。

首次启动会创建一项示例赛事、8 名参赛者、2 块场地、2 名裁判和4场已发布比赛。不会在后续启动时重置数据。

| 身份 | 演示账号 | 演示密码 |
| --- | --- | --- |
| 赛事组 | organizer | Courtside2026! |
| 裁判一 | referee | Courtside2026! |
| 裁判二 | referee2 | Courtside2026! |

登录页预填的是开发演示账号。可以修改账号和服务地址。`SEED_DEMO=false` 可关闭首次初始化；演示账号密码由 `DemoData` 统一管理，正式使用应替换账号初始化方案。

在全新数据库关闭演示初始化时，可用 BOOTSTRAP_USERNAME 与 BOOTSTRAP_PASSWORD 环境变量创建唯一的初始赛事管理员。赛事组可在“管理资料 → 裁判”中创建新的裁判账号并加入赛事。

## 三个模块

| 目录 | 职责 |
| --- | --- |
| `organizer` | 赛事组独立 APK 入口，应用标识 cn.courtside.organizer |
| `referee` | 裁判独立 APK 入口，应用标识 cn.courtside.referee |
| `client` | 两端共用的 Compose 设计系统、页面、ViewModel、网络与 Room 数据层 |
| `shared` | Java 业务模型与纯计分规则，客户端和服务端使用同一份规则 |
| `server` | Java 17、Spring Boot 3.4.6、认证、权限、事务、成绩与持久化 |
| `scripts` | 工具链准备、构建、启动脚本 |
| `docs` | 接口、设计与运行说明 |

Gradle 缓存、模块编译目录、测试报告和调试日志均可放在同级 `tmp`；`scripts/build.ps1` 已按此配置。两个 APK 与服务端包是独立产物，不需要把 tmp 交给最终使用者。

## 已实现的业务

赛事组可以创建及编辑赛事和计分规则，维护参赛者、场地及赛事裁判，手工编排对阵与时间，保存草稿、发布、调整或取消未开始的比赛。云端检查场地、裁判和参赛者的时间重叠。已接受的赛程发生调整后重新进入待接受状态；开始后锁定安排。

裁判可以登录、查看本人任务与日期分组赛程，接受任务，赛前随机决定优先选择方，再根据现场选择设置首先发球者。正式开始与原设备绑定，其他设备不能接手覆盖计分。计分页提供左右蓝红触控区、发球方与左右发球区、局点和赛点提示、加时提示、撤销、交换显示、弃权处理、长亮屏幕及明暗模式。

中场间歇与局间休息使用持久化的结束时刻计时，重建界面后不会从头计时。局末可以撤销误触最后一分，确认后封存本局。正常完赛后提供每局逐分轨迹和双方现场确认，再提交云端。弃权使用独立结果与原因，不伪造正常比分。

成绩依次经历本机保存、云端接收、赛事组核验。赛事组可以确认或附原因退回；退回后保留原始提交，裁判在原设备进入更正批次，修改局分时必须填写原因，原始逐分轨迹继续保留并注明更正。更正后重新确认再提交。全部比赛完成或取消后可归档赛事。

## 计分与离线保障

- 共享规则默认三局两胜、目标21分、领先2分、30分封顶，赛事创建时可配置。发布后规则锁定。
- 加分和撤销通过互斥队列串行执行，Room 成功保存后才更新画面。没有等待网络的加分操作。
- 正式开始需要联网；之后可以断网继续计分、完成本局及本场。退回成绩的重新开启需要联网获取新批次。
- 每场本地记录与账号、服务地址、比赛编号绑定。退出登录不删除记录，原账号重新登录后可继续。
- 成绩进入 Room 待提交队列时固定提交编号和内容。重试不生成新编号；收到云端回执后才移除待提交项。
- 前台每10秒刷新任务并同步，提供手动刷新。应用后台或关闭时没有即时推送承诺；重新打开后恢复并重试。
- 服务端使用赛事行锁序列化变更、安排版本校验、执裁设备会话、更正批次与递增操作序号阻止旧数据覆盖。

应用卸载、清除数据或设备损坏后，尚未上传的本地记录无法保证恢复。首版没有离线换机交接。客户端的账号状态保存在应用私有存储，安装包关闭系统备份，服务端凭证为有期限的随机令牌。

## 数据库与部署

默认开发数据库是 H2 文件库，位于同级 `tmp/runtime/courtside`。该库提供跨服务重启持久化，方便一条命令演示，不要求预先安装 MySQL。

生产数据库支持 MySQL，可设置：

```text
DATABASE_URL=jdbc:mysql://mysql:3306/courtside?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
DATABASE_USER=courtside
DATABASE_PASSWORD=自行设置
SEED_DEMO=false
PORT=8080
```

存储层为用户、令牌、赛事、成员、参赛者、场地、比赛、提交和审计分别建表，按业务编号和赛事范围查询，聚合内容以 JSON 文本持久化。事务和唯一主键承担并发与幂等约束。此实现便于保持两端共享模型，不是把运行数据只保存在进程内存中。

`docker compose up --build` 可使用提供的 MySQL 与服务端容器配置。先复制 `.env.example` 为 `.env`，替换密码。示例服务暴露8080端口，公网部署应由 HTTPS 反向代理访问。MySQL 容器数据使用命名卷保存。

备份 H2 时先停止服务，再复制数据库文件；MySQL 使用数据库自己的备份工具。不要在比赛中重置或覆盖数据。

## 测试与界面验证

```powershell
./scripts/build.ps1
# 已启动服务并连接模拟器后，执行真正的跨端界面流程
./gradlew.bat --project-cache-dir ../tmp/gradle-project-cache :client:connectedDebugAndroidTest
# 模拟器或USB设备的宿主网络异常时，可用ADB转发，连接的仍是真实服务
./scripts/test-ui.ps1 -Serial emulator-5556 -UseAdbReverse
```

`ScoringTest` 验证平分、封顶、撤销与发球恢复、局点、整场结束、非法追加得分和更正记录。`WorkflowTest` 启动随机端口的真实 Spring 服务，验证登录、赛程冲突、版本过期、权限、设备绑定、结果重试、退回、更正和确认。

`WorkflowUiTest` 在 Android 设备上连接本机8080服务，创建独立的3分演示赛事，操作实际 Compose 页面完成裁判登录、接受、开赛、计分、间歇、双方确认、提交，然后切换赛事组身份核验。3分仅用于缩短自动化流程；正常赛事默认仍为21分。测试截图写入测试应用的外部私有目录，再提取到同级 tmp 检查。

本次验收的 6 项计分规则测试、3 项服务端流程测试和 1 项真实界面流程测试均通过；两个 APK 构建成功并通过签名校验。Android Lint 为 0 错误，公共客户端有 4 项非阻断建议。具体环境、操作步骤与验证边界见 [验收记录](docs/TESTING.md)。

更多接口与设计细节见 [API](docs/API.md) 和 [界面与交互设计](docs/DESIGN.md)。
