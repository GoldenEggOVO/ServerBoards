# ServerBoards 1.1.0 现有功能与独立验收清单

此清单来自当前源码、命令、配置与测试，记录独立化前已有能力。`YachtGame` 仅是旧规则源码，不在游戏目录，也不得重新启用。

| 功能 | 当前入口或数据 | 验收方式 |
| --- | --- | --- |
| 中国象棋、五子棋、国际象棋、飞行棋、中国跳棋、西洋跳棋、黑白棋、9/13/19 路围棋、7×6 四子棋 | `GameFactory`、`/boards create`、目录 Dialog | 各规则测试；四子棋重力、横竖斜胜负测试 |
| 建桌、列房、加入、人数与单人单座 | `ServerBoards.create/join`、`Room`、`GameMenus` | 占座和房间测试；干净服建立房间 |
| 准备、补机器人、回合动作与超时代走 | `/boards ready/bots/move`、`tick` | 房间及规则测试；干净服命令测试 |
| 观战、回桌、退出与离线保留 | `GameMenus.observe`、`resume/leave/quit` | 菜单回调、座位测试；重启恢复 |
| 协商悔棋、再来一局、复盘历史 | `RoundActions`、`history` | `RoundActionsTest`、恢复测试 |
| 原生 Dialog、可编辑模板、身份/世界/时效/单次回调 | `BoardWindow`、`GameMenuLayouts`、`menus/*.yml` | `WindowMenuTest`、`NativeMenuTest`、干净服日志 |
| 实体棋盘、模型、射线点击、指针、桌边显示 | `GameWorld`、`TableView`、`TableLobby` | 对应测试；干净服实体生成检查；实际画面由用户验收 |
| 权限与 AuthMe 登录门禁 | `serverboards.use`、`allowed` | 权限测试；干净服命令测试 |
| 配置与房间持久化 | `config.yml`、`menus/*.yml`、`rooms.json` | 数据格式测试；重启前后文件与状态检查 |
| ServerMenu 入口、ServerGames `/sg menu` | `serverboards:boards menu` 命令转发 | 无依赖启动；组合安装入口测试 |

## 本次验收结果

- Maven：120 项测试通过，失败、错误、跳过均为 0；包含各玩法规则、四子棋、房间、座位、回调、实体坐标与重放校验。
- Python 迁移测试：4 项通过。
- 本地 Purpur 26.2 五次启动：独立建桌与四子棋落子、独立重启恢复、ServerGames 2.0.3 不接管 Boards 房间且 `/sg menu` 可打开 Boards、ServerMenu 0.7.1 棋牌入口、旧数据迁移恢复均通过。探针通过模拟玩家触发原生 Dialog 构建与回调；真实客户端画面和手感由服主验收。
- 旧 ServerGames 测试样本中 4 间规则可重放房间恢复成功，源历史 21 条保持为前缀。另一间飞行棋房间在历史第 4 个动作处与当前规则不符，迁移工具拒绝输出，原文件保持不变。
