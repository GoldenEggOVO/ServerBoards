# ServerBoards 1.2.1

独立棋类插件，Java 25 / Paper 26.2。主包 `dev.server.boards`，实体资源键 `boards:*`。

玩法：五子棋、中国象棋、国际象棋、飞行棋、中国跳棋、西洋跳棋、黑白棋、9/13/19 路围棋、标准 7×6 竖直四子棋。保留本地实体棋盘、房间、机器人、回合超时、断线保留、协商悔棋、再来一局及对局重放持久化。

## 构建

在工作区根目录用 Java 25 和自带 Maven 执行：

```powershell
$mvn = '.\.tools\apache-maven-3.9.11\bin\mvn.cmd'
$repo = "-Dmaven.repo.local=$PWD\.tools\m2"
& $mvn -o $repo -f server-boards/pom.xml package
```

构建不依赖 ServerGames、ServerMenu、ServerCasino 或 KaMenu。规则源码和第三方许可证随模块附带。

单独安装只需 `server-boards-1.2.1.jar`。停服后替换旧 JAR，同一服只保留一个 ServerBoards 版本；不要部署 `original-*.jar`。无需 KaMenu、ArcMenu、Python、TAB、ProtocolLib、Multiverse 或卡牌/麻将插件。菜单使用 Paper 原生 Dialog；实体棋盘仍可直接点击。源码和交付包保持私有。

## 指令

`/boards` 打开棋类列表；`/boards create <kind> [人数]` 建桌；`/boards join <房间ID前缀>` 加入；`ready` 准备；`bots` 补陪练开局；`resume` 返回；`undo` 协商悔棋；`rematch` 再来一局；`leave` 确认离开。可用 `move <规则动作>` 直接操作，例如四子棋 `move drop:3`（列编号 0–6）。权限 `serverboards.use` 默认所有玩家，AuthMe 存在时必须登录。

`kind` 可取 `xiangqi`、`gomoku`、`chess`、`aeroplane`、`checkers`、`draughts`、`reversi`、`go`、`go9`、`go13`、`connectfour`。`serverboards.use` 默认给予玩家，控制菜单和所有棋类操作；若装有 AuthMe，还需完成登录。`/boards status` 可从控制台查看房间数。

Boards 自己管理房间与座位；入桌失败释放，离开、房间关闭及停用时释放。打开列表不会占座。断线保留期间仍占座。运行时不读取 ServerGames 服务，不注册其游戏提供者，也不需要 `servergames.use`。若两款插件同时安装，各自管理自己的占座；Boards 内仍保证一名玩家只能占一间 Boards 房间。ServerGames 2.0.3 的 `/sg menu` 是直接转发到 `serverboards:boards menu`，因此该入口仍可打开 Boards；其 `/sg list/open/create` 不列出或操作 Boards 房间。

## 数据与验证

数据目录为 `plugins/ServerBoards/`。`config.yml` 包含 `max-rooms`、`reconnect-seconds`、`idle-room-minutes`、`turn-seconds`；`menus/*.yml` 是可编辑的 Dialog 外观模板。房间、座位、实体桌面世界 UUID 与坐标、随机种子和动作历史保存在 schema 1 `rooms.json`。从 Boards 1.1.0 升级无需转换配置或数据，先停服备份整个目录。旧 ServerGames 数据若缺世界锚点，按 [MIGRATION.md](MIGRATION.md) 显式迁移；恢复失败时保留源文件并停用插件。

现有功能与逐项验收见 [FEATURES.md](FEATURES.md)。构建测试覆盖规则、实体坐标/射线、回调身份/轮次、悔棋/重开、加入失败的占用释放。`python server-boards/probe/run_standalone.py` 在本地 Purpur 26.2 测试服依次验证独立安装、重启恢复、ServerGames 的 `/sg menu` 入口、ServerMenu 入口，以及旧数据迁移。第一、二、五次启动的插件目录仅含 Boards 与测试探针。构建和探针不等同于 Java/Bedrock 画面与实际点击验收；服主负责视觉与体验验收。

## 来源与许可证

GPL-3.0-or-later，基于工作区既有 ServerGames 棋盘实现迁移。并非将第三方代码声明为原创；详细来源、许可证和上游版本见 `src/main/resources/META-INF/NOTICE-board-rules.txt` 与 `META-INF/licenses/`（包括棋库、象棋、五子棋、飞行棋、中国跳棋）。四子棋为本项目新增规则实现。

## 独立菜单

`/boards` 打开 Paper 原生窗口，无需 KaMenu。菜单模板位于 `plugins/ServerBoards/menus/`，服务器绑定和校验按钮回调，模板中的任意命令不会被执行。安装 ServerMenu 0.7.1 及其自身要求的 Essentials 后可从 Shift+F 的棋牌入口进入；ServerGames 2.0.3 的 `/sg menu` 也转入 Boards。都不安装时仍可完整使用 `/boards`。安装或移除可选插件后请正常重启。
