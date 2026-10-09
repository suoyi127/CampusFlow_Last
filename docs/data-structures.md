# 业务逻辑数据结构

本说明仅覆盖参与业务逻辑设计的数据结构。数据库定义以 `database/migrations` 为准，变更通过新增版本化 SQL；模块通过公开 Service 交互，不依赖其他模块 Mapper。

| 结构 | 归属 | 关键关系与约束 |
| --- | --- | --- |
| sys_user / Account | auth | username 唯一；BCrypt 密码摘要不出现在接口；USER、DATA_ADMIN、SERVER_ADMIN 互不继承 |
| study_space / StudySpace | space | 正容量、合法坐标；设施为四项枚举；启用状态代替删除；开放日 1—7 使用同一日间时段或全天 |
| SpaceInput | space | 写入边界使用开放日和设施数组，经服务器校验后存储；不能将容量降至在场人数以下 |
| simulation_run | simulation | 每次启动建立新的模拟轮次，记录起点、场景名与随机种子；本批仅 NORMAL 场景 |
| sim_visit | simulation | 虚拟人员与真实账号无关，唯一编号关联同一次签到签退；checked_out_at 为空表示在场；不重新累加历史签到 |
| space_snapshot | simulation/status | 记录轮次、空间、人数、采样时间、有效标记；取本轮最近有效快照；id 作为 snapshotVersion |
| noise_device | simulation | 一空间一设备，ONLINE/OFFLINE 独立于读数有效性；维护基础分贝与波动范围 |
| noise_sample | simulation/status | 30—90 dB，服务器生成采样时间；近期典型噪声为 60 秒有效样本中位数 |
| SpaceStatus | status | 分别包含人数与噪声时间、状态；过期时值为 null；源固定 SIMULATED；同轮次快照用于两端核对 |
| SpaceQuery、SpaceCard | recommendation | Query 表达全部筛选约束；Card 聚合空间、动态状态、直线距离、固定基准得分和实际理由 |
| space_review / ReviewView | review | 一账号一空间一条当前评价；环境和设施 1—5 整数、说明最多 500 字符；PENDING、APPROVED、REJECTED、WITHDRAWN；version 每次成功修改或审核递增；保留最近一次审核信息 |
| ReviewInput、ReviewDecision | review | expectedVersion 防止旧表单覆盖；首次提交为 0；驳回及撤销通过必须填写原因 |
| PublicReview、ReviewSummary | review | 只输出当前 APPROVED；公开作者用用户编号，不输出审核原因或审核人；分别计算两项均分；数量为 0 时均分为 null |
| system_config | system（预留） | 周期、有效期、窗口和四维权重；当前运行仍使用既定默认值，尚无可修改配置接口 |
| audit_log | system | 保存操作者、动作、目标、结果、原因、时间；记录空间维护、模拟控制和评价提交、修改、撤回、审核，不提供删除接口 |

人数状态从持久化快照读取，不从前端随机生成。签到签退更新、快照及分贝采样在服务器事务内执行；空间维护与模拟共享空间行锁，避免容量校验和新增签到相互穿插。

综合评分为距离 30%、安静 30%、空闲 25%、设施 15%。距离按固定 3000 米线性归一化，等级按 `(quietLevel-1)/4`，空闲按 `1-occupancyRate`，四设施各占 25%。缺失实时维度记 0，不重分配权重。评价均分独立展示，不参与推荐分。

评价写入统一先锁空间、再锁评价，随后核对版本。修改已通过评价立即退出公开集合；撤回后仍保留本人历史，并可重新提交。空间停用后禁止提交或修改，但允许本人查询与撤回。管理员审核旧版本返回 409，需重新读取当前内容。

DATETIME 保存 UTC，无时区偏移；接口返回 ISO 8601 UTC，页面按 Asia/Shanghai 显示。数据有效期边界为不超过 30 秒有效，超过即过期；设备离线即时失效，保留最后更新时间。
