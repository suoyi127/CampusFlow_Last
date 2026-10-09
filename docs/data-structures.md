# 业务逻辑数据结构

本说明仅覆盖参与业务逻辑设计的数据结构。数据库定义以 `database/migrations` 为准，变更通过新增版本化 SQL；模块通过公开 Service 交互，不依赖其他模块 Mapper。

地图业务统一使用 GCJ02。`StudySpace.coordinateSystem` 为 GCJ02 或 UNKNOWN；未知坐标空间不参与推荐，管理员必须显式确认后才可恢复推荐。`MapLocation` 包含经纬度、固定 GCJ02 标识及可选地址/定位精度，选点草稿在确认前不写入业务表单。演示坐标为合成数据。

| 结构 | 归属 | 关键关系与约束 |
| --- | --- | --- |
| sys_user / Account、AccountView | auth | username 唯一且不可改名；BCrypt 摘要不出接口；三角色互不继承；启停或角色改变时 version 递增，登录会话绑定认证时版本 |
| account_management_lock | auth | 固定单行锁，序列化账号管理写入；锁定读检查至少保留一个启用的 SERVER_ADMIN |
| study_space / StudySpace | space | 正容量、合法坐标；设施为四项枚举；启用状态代替删除；开放日 1—7 使用同一日间时段或全天 |
| SpaceInput | space | 写入边界使用开放日和设施数组，经服务器校验后存储；不能将容量降至在场人数以下 |
| simulation_run | simulation | 启动或重置建立新轮次；记录NORMAL/PEAK/NOISE_EVENT/DEVICE_OFFLINE场景和固定种子；查询按当前数据库快照确定轮次 |
| sim_visit | simulation | 虚拟人员与真实账号无关，唯一编号关联同一次签到签退；checked_out_at 为空表示在场；不重新累加历史签到 |
| space_snapshot | simulation/status | 记录轮次、空间、人数、采样时间、有效标记；取本轮最近有效快照；id 作为 snapshotVersion |
| noise_device | simulation | 一空间一设备，ONLINE/OFFLINE 独立于读数有效性；维护基础分贝与波动范围 |
| noise_sample | simulation/status | 30—90 dB，服务器生成采样时间；近期典型噪声为配置窗口内有效样本中位数，默认 60 秒 |
| SpaceStatus | status | 分别包含人数与噪声时间、状态；过期时值为 null；源固定 SIMULATED；同轮次快照用于两端核对 |
| SpaceQuery、SpaceCard | recommendation | Query 表达全部筛选约束；Card 聚合空间、动态状态、直线距离、固定基准得分和实际理由 |
| space_review / ReviewView | review | 一账号一空间一条当前评价；环境和设施 1—5 整数、说明最多 500 字符；PENDING、APPROVED、REJECTED、WITHDRAWN；version 每次成功修改或审核递增；保留最近一次审核信息 |
| ReviewInput、ReviewDecision | review | expectedVersion 防止旧表单覆盖；首次提交为 0；驳回及撤销通过必须填写原因 |
| PublicReview、ReviewSummary | review | 只输出当前 APPROVED；公开作者用用户编号，不输出审核原因或审核人；分别计算两项均分；数量为 0 时均分为 null |
| system_config / RuntimeConfig | system | RUNTIME 单行完整保存模拟周期、有效期、窗口和四维权重；version 防旧表单覆盖；持久化后后续计算读取，无需重启 |
| audit_log / LogView | system | 保存操作者、动作、目标、结果、原因、时间；记录空间、模拟、评价及账号/配置成功操作，密码不写入；服务器管理员可分页筛选，无删除接口 |

人数状态从持久化快照读取，不从前端随机生成。签到签退更新、快照及分贝采样在服务器事务内执行；空间维护与模拟共享空间行锁，避免容量校验和新增签到相互穿插。

综合评分默认权重为距离 30%、安静 30%、空闲 25%、设施 15%，服务器管理员可修改，四项均为有限 0—1 数值且和为 1。距离按固定 3000 米线性归一化，等级按 `(quietLevel-1)/4`，空闲按 `1-occupancyRate`，四设施各占 25%。缺失实时维度记 0，不重分配权重。评价均分独立展示，不参与推荐分。

评价写入统一先锁空间、再锁评价，随后核对版本。修改已通过评价立即退出公开集合；撤回后仍保留本人历史，并可重新提交。空间停用后禁止提交或修改，但允许本人查询与撤回。管理员审核旧版本返回 409，需重新读取当前内容。

DATETIME 保存 UTC，无时区偏移；接口返回 ISO 8601 UTC，页面按 Asia/Shanghai 显示。默认不超过 30 秒有效，超过即过期；设备离线即时失效，保留最后更新时间。有效期可配置，但至少是模拟周期两倍，噪声窗口不小于有效期。

模拟和检查复用既有表字段，没有新增数据库迁移。快照与噪声valid/invalid_reason用于无效标记和恢复；操作原因写入audit_log。人数回退排除超过现容量的快照，噪声仅汇总有效窗口读数；恢复不修改采样时间，没有有效候选但有记录时状态为INVALID。

周期更新、场景、设备及重置通过覆盖提交的互斥事务执行，空间维护和异常标记共用空间行锁。重置仅清除当前轮次的到访、快照和噪声，保留账号、空间、评价、RUNTIME配置、日志及轮次历史。虚拟人员用运行编号和递增序号标识，不关联真实账号。
