# 官方路线与四语名称来源

`app/src/main/assets/official_routes.json` 记录 MarioWiki《Mario Kart World》修订号5544799已发布列表的8个大奖赛杯赛、12条 Knockout Tour/Rally 路线。每个 stop 保存明确 ordinal，`geometry=ordered_stops_only` 只表达官方经过顺序，不是道路折线或自定义合法连接数据库。主快照来源 URL、SHA256 与逐路线资料页 URL 一同保留。

`localized_names.json` 保存30个赛道与20条官方路线的四语名称（en / zh-Hans / zh-Hant / ja），来源于各自资料页的 Names in other languages 表；每条含 URL 和原始 wikitext 的 SHA256。源表没有多义语言值，生成时确认50条记录的四语字段齐备且路线名称与路线草稿一致。

原始抓取与核对文件：`/workspace/mkw-planning/official-route-sources/`。快照哈希用于复查抓取版本，不构成道路方向、Jukebox 候选池或音乐事件证据。

特别杯保持实际 entitlement 门禁；其他官方拉力赛经过 BigTree、SFCMarioCircuit 和 Central，不能因为这些赛道同时属于特别杯而隐藏它们。当前唯一仅出现在特别杯的赛道为 Rainbow。

## MKW v1.8.0 游戏内提取数据对齐说明（2026-11-28）

现已在 `app/src/main/assets/stage_connections.json` 与 `app/src/main/assets/route_music_catalog.json` 中接入完整的 **MKW v1.8.0 游戏内提取连接拓扑与道路配乐表**：
- **202 条合法有向连接赛道**：严格对齐 `InterStageEx1`（137 条）+ `InterStageEx2`（65 条），移除了早期外部 CSV 中混入的 12 条 `LongRally` 伪边；构成 **96 对双向边 + 10 条严格单向边**（`Central → Rainbow` 唯一汇点、`Coaster/DesertHills/StormClouds → KoopaTroopaBeach`、`DonkeyKong → Central/DesertHills/FirstCircuit`、`IceBuildings → LagoonCity/RaftingRiver`、`SFCMarioCircuit → Central`）。
- **4 条 `OverrideJukeBoxEndRatio` 手工覆写边**：`Cave → Cinema`（`0.80`）、`SFCMarioCircuit → Central`（`0.80`）、`DKPass → SnowMountain`（`0.75`）、`ShyGuyBazaar → WarioStadium`（`0.75`）；其余遵循默认公式 $\max((s_9 \cdot L - 650.0) / L,\; 0.75 \cdot s_9)$，衔接采用 `3.0 s` 淡出 + `StageConnect_IntervalToStage = 2.4 s` 启动 `_Pre` + `StageConnect_StopByArrivalTime = 20.0 s` 防半截新歌。
- **8 大奖赛杯（24 条连接路段）**：其中 23 条配备 `PrimaryCourse` 专属策展曲池（共 79 曲），第 24 条 `Central → Rainbow` 固定跳过点唱机直接起播 `BGM_Rainbow_Area1_Pre`。
- **12 条生存赛拉力（`GP09–GP20`）**：直连模式（`Outro_ToNextPre → Next Pre`），游戏内领先车手默认提前 `200 m` 触发预测切出，下一站为 `StormClouds`（？神殿）时特判放宽为 `500 m`；12 条生存赛开场动画音轨 `Dm_SV01_OP..Dm_SV12_OP` 经全量审计逐字节完全一致。
