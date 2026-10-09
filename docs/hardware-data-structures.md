# 硬件接收业务数据结构

V6 新增两张表，与模拟的 simulation_run / sim_visit 分开，模拟重置不会删除硬件数据。

## hardware_device（V8）

| 字段 | 类型与作用 |
| --- | --- |
| id | BIGINT 自增，设备管理内部编号 |
| device_id | VARCHAR(64) 唯一，来自固件的物理标识，创建后不可修改 |
| name | VARCHAR(100)，管理员填写的显示名称 |
| space_id | 可空的学习空间外键，唯一约束保证每空间最多一设备 |
| enabled | 接收开关，停用拒绝新上传，保留绑定和历史 |
| version | 乐观锁版本，每次维护递增，防止多人覆盖 |
| created_at / updated_at | UTC DATETIME(6)，录入及修改时间 |

V8 从旧硬件历史恢复无歧义的一对一绑定；旧环境变量仅启动时补充缺失且空间未占用的设备。维护先按空间 ID 顺序锁定空间，再锁设备；接收先锁绑定空间，再核对设备最新绑定和启停状态。存在上传历史后禁止换绑/解绑，因为既有离线协议没有绑定版本。没有物理删除接口，历史接收记录保留原始空间归属。最近接收时间由 hardware_record 聚合，不把网页录入误认为设备已联网。

## hardware_discovery（V9）

device_id 为 VARCHAR(64) 主键；first_seen、last_seen 为服务器首次/最近收到有效上报的 UTC DATETIME(6)。尚未绑定的上报在完整验证后进入此表，使用独立事务保留发现结果，外层业务上传仍拒绝。重复上报更新最近时间，不重复建候选；管理员录入后通过 NOT EXISTS 查询将其排除，离线候选仍保留。此表不包含空间或人数，也不替代硬件历史记录。online 按服务器当前有效期推断，不冒充 broker 的实时连接状态。

## hardware_record

| 字段 | 类型与作用 |
| --- | --- |
| id | BIGINT 自增主键，接收记录编号 |
| device_id / space_id | 固件设备标识 / 接收时绑定的学习空间外键 |
| kind | EVENT 或 TELEMETRY |
| event_id / event_sequence | 事件序号原字符串 / DECIMAL(20,0) 数值；遥测为 null |
| event_type | CHECK_IN / CHECK_OUT，遥测为 null |
| headcount | BIGINT，0—4294967295，设备持久化的动作后或当前绝对人数 |
| decibel / sound_valid | 环境估算分贝及有效标记；事件均为 null |
| sampled_at | UTC DATETIME(6)，源自设备 Unix 秒 |
| received_at | UTC DATETIME(6)，源自服务器接收时钟 |

UNIQUE(device_id,event_id) 防止 QoS1 重投重复保存。遥测 event_id 为 null，可保存多条。space_id + sampled_at + id 索引用于最新数据和声音窗口，space_id + received_at 索引用于在线推断。

接收先获取 study_space 行锁，事件内容比对、去重和写入同一事务完成，并与容量维护序列化。相同编号内容冲突拒绝写入；原数据不可覆盖。人数是区域总量，无个人身份，不推导个人进出配对。

## hardware_rejection

id 为自增主键；topic 最大 200 字符，payload 最多 8192 字符，error_code 最大 64 字符，received_at 为 UTC DATETIME(6)。保存 MQTT 永久协议错误，正常数据库提交后才确认坏消息；数据库失败继续保留 broker 的重投机会。

查询接口、配置与状态选择规则见 [硬件接收接口](contracts/hardware-api.md)。
