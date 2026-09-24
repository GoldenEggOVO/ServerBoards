# ServerBoards 数据迁移

## 1. 备份与来源

完全停止服务器。备份整个 `plugins/ServerBoards/`，以及如需迁移旧棋局时的 `plugins/ServerGames/rooms.json`。不要在运行中复制文件，也不要把旧 ServerGames 的卡牌或麻将房间放进 Boards。

Boards 1.1.0／1.2.0 → 1.2.1：`config.yml`、`menus/*.yml`、`rooms.json` 的格式不变，只换 JAR。重启后用 `/boards status`、`/boards resume` 检查房间和桌面。插件按历史重建实体棋盘。旧版若曾向 ServerGames 注册提供者，升级后不再注册；`/sg menu` 命令转发仍可用。

旧 ServerGames → Boards：旧 `rooms.json` 可能没有 `anchorWorld/anchorX/anchorY/anchorZ`，不能推断玩家希望把桌子放到哪里。为每个需要迁移的棋类房间提供一个明确、已加载且可放置桌面的世界 UUID 和坐标。

## 2. 创建映射并生成新文件

映射文件示例 `anchors.json`：

```json
{
  "room-uuid-from-source": {
    "world": "target-world-uuid",
    "x": 100.5,
    "y": 83,
    "z": -20.5
  }
}
```

已有完整锚点的 Boards 文件使用 `{}`。脚本只允许棋类，检查房间、桌号、人数、玩家重复占座和坐标，并调用将要安装的 `server-boards-1.2.1.jar` 逐步重放所有历史动作；不可重放时拒绝生成候选文件，不改源文件，也不覆盖已有输出：

```powershell
python server-boards/tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json
python server-boards/tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --check
```

两次命令打印源与输出的 SHA-256。若 JAR 与工具不在同一安装包位置，可加 `--jar <拟安装的 ServerBoards JAR>`。`--check` 会验证输出与源数据的差别仅为显式锚点和缺失时补齐的空 `returns`。如旧文件同时含卡牌、麻将或其他不支持的房间，先从**备份副本**筛选棋类房间并保存为独立输入；保留完整原始备份。若某个棋类房间的历史动作无法重放，工具会报告房间 ID 和动作序号；不要改写原始动作或清空历史，先保留该房间原件并检查旧规则版本。

## 3. 隔离服验收后替换

把 `candidate-rooms.json` 复制到**已停止**的隔离测试服 `plugins/ServerBoards/rooms.json`；该测试服世界 UUID 必须与映射一致。启动时查看是否有“恢复棋牌房间 N 个”，用 `/boards status` 对照数量，并由玩家执行 `/boards resume` 检查座位、棋盘和历史。再次正常重启，确认恢复数量与棋盘状态一致。完成后才在目标服按相同备份、停服、复制、启动顺序操作。任一步失败时停服，保留原数据和不可读副本，恢复备份并检查日志。
