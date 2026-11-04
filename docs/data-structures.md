# 当前业务数据结构

新建真实库结构以 database/real-migrations 为准，Flyway自动迁移。新库不创建模拟轮次、模拟到访、模拟快照或模拟噪声表，不插入示例账号、空间或设备数据。database/migrations仅保留历史与测试资源，不打包到生产程序。

| 结构 | 关键关系与约束 |
| --- | --- |
| sys_user / AccountView | username唯一、BCrypt摘要不返回；USER/DATA_ADMIN/SERVER_ADMIN互不继承；角色或启停变化递增version，旧会话失效 |
| account_management_lock | 固定行锁串行化账号管理，保护最后一个启用服务器管理员 |
| study_space | 实际空间资料；坐标需明确GCJ02；容量、开放时段、设施经后端校验；data_origin用于排除可准确识别的旧样例 |
| hardware_discovery | 记录实际物理ID首次和最近发现时间；未录入设备可供管理员选择 |
| hardware_device | device_id唯一；space_id可空且唯一，停用设备仍占用绑定；version防覆盖；有上传历史后禁止换绑或解绑 |
| hardware_record | 原始EVENT/TELEMETRY；设备ID、空间、事件编号、数值序列、人数、分贝、声音有效性、采样与接收时间；物理ID与事件ID去重 |
| people_valid / noise_valid | hardware_record中独立人工有效标记及原因，不改写原始载荷或时间 |
| hardware_rejection | 保存不符合协议的消息及原因，永久协议错误隔离后确认；数据库故障不确认，允许broker重投 |
| SpaceStatus | source=HARDWARE；人数和噪声各自时间/状态；缺失、停用、离线、过期或异常显示null，不回退模拟源 |
| SpaceQuery / SpaceCard | 实际GCJ02起点必填，聚合真实空间、读数、直线距离和推荐理由；不默认使用上海坐标 |
| space_review | 一账号一空间一条当前评价；两项1—5分、内容至多500字；PENDING/APPROVED/REJECTED/WITHDRAWN；version保护写入 |
| PublicReview / ReviewSummary | 仅APPROVED公开并参与两项均分，数量0时均分null |
| system_config / RuntimeConfig | RUNTIME保存有效期、噪声窗口和四维权重，整体version控制；不包含模拟周期 |
| audit_log | 实际业务操作只读审计，不记录密码；历史记录不作为实时指标 |

DATETIME存UTC，接口ISO8601 UTC，客户端按Asia/Shanghai显示。在线状态根据设备启用和真实最近接收时间计算；读数有效性另按采样时间判断。噪声窗口不小于有效期，典型噪声为有效分贝中位数。

推荐权重默认为距离.30、安静.30、空闲.25、设施.15，均有限且总和1。缺失实时维度记0，不重分配权重；评价均分独立展示。评价修改退出公开集合，需重新审核；版本冲突保留草稿并要求重新读取。
