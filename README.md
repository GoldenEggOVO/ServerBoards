# ServerBoards

独立棋类插件，Java 25 / Paper 26.2。主包 `dev.server.boards`，实体资源键 `boards:*`。

玩法：五子棋、中国象棋、国际象棋、飞行棋、中国跳棋、西洋跳棋、黑白棋、9/13/19 路围棋、标准 7×6 竖直四子棋。保留本地实体棋盘、房间、机器人、回合超时、断线保留、协商悔棋、再来一局及对局重放持久化。

## 构建

先将统一服务 `dev.server:server-games:2.0.0` 安装到所用 Maven 仓库，再在本项目执行 `mvn clean package`。插件只依赖 Paper 与 ServerGames，规则源码和第三方规则许可证随项目附带，没有绝对路径、系统作用域 JAR 或相邻工程源码依赖。

运行时放置最终 `server-boards-1.0.0.jar` 和 ServerGames 2.0.0。不要部署 `original-*.jar`。无需 KaMenu、ArcMenu、Python、TAB、ProtocolLib、Multiverse 或卡牌/麻将插件。菜单使用原生可点击聊天组件；原实体棋盘直接点击操作保留。第三方名称牌/半透明显示集成已移除，采用原生碰撞控制。

## 指令

`/boards` 打开棋类列表；`/boards create <kind> [人数]` 建桌；`/boards join <房间ID前缀>` 加入；`ready` 准备；`bots` 补陪练开局；`resume` 返回；`undo` 协商悔棋；`rematch` 再来一局；`leave` 确认离开。可用 `move <规则动作>` 直接操作，例如四子棋 `move drop:3`（列编号 0–6）。权限 `serverboards.use` 默认所有玩家，AuthMe 存在时必须登录。

所有建桌/加入入口先向 ServerGames 原子预占；入桌失败释放，离开、房间关闭及停用时释放。打开列表不会占座。断线保留期间仍占座。

## 数据与验证

新数据目录 `plugins/ServerBoards/`。旧 ServerGames `rooms.json` 不自动覆盖、不自动迁移：停服先备份旧目录，由管理员筛选仅本插件支持的棋类记录再放入新目录，保留原世界 UUID/锚点。无法识别或恢复失败时保留源文件并停用插件，禁止覆盖。运行期牌局、旧卡牌资源、菜单配置不属于此插件迁移范围。

测试覆盖规则、实体坐标/射线、回调身份/轮次、悔棋/重开、加入失败的占用释放。构建测试不等同于 Java/Bedrock 画面和实际点击验收；未向正式服部署。

## 来源与许可证

GPL-3.0-or-later，基于工作区既有 ServerGames 棋盘实现迁移。并非将第三方代码声明为原创；详细来源、许可证和上游版本见 `src/main/resources/META-INF/NOTICE-board-rules.txt` 与 `META-INF/licenses/`（包括棋库、象棋、五子棋、飞行棋、中国跳棋）。四子棋为本项目新增规则实现。
