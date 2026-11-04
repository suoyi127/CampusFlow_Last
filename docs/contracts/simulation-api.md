# 真实硬件数据检查接口

此路径保留旧文档文件名以兼容链接；生产模拟任务、模拟控制与虚拟到访已移除。

DATA_ADMIN：

- GET /api/data/records：kind=PEOPLE/NOISE/VISIT，可选spaceId、valid、page（默认1）、pageSize（默认20，最大100）。查询实际hardware_record，按id倒序。
- GET /api/data/devices：真实设备只读状态。
- PUT /api/data/records/{kind}/{id}/validity：valid布尔值和reason（必填1—500字），仅PEOPLE/NOISE，成功204。

分页响应items,total,page,pageSize,calculatedAt,warnings，不再包含模拟运行编号。

PEOPLE含id,spaceId,deviceId,currentPeople,sampledAt,receivedAt,valid,invalidReason。NOISE含id,spaceId,deviceId,noiseDb,soundValid,sampledAt,receivedAt,valid,invalidReason。VISIT表示设备进出事件，包含id,spaceId,deviceId,eventId,eventType（CHECK_IN/CHECK_OUT）,headcount,sampledAt,receivedAt，不代表个人签到会话，不接受有效标记筛选或修改。

人数与噪声分别使用people_valid/noise_valid及各自原因字段。标记不修改原始载荷、采样时间或接收时间；无效人数、无效声音、超容量或停用设备不能显示为有效读数。典型噪声仅汇总窗口内原始声音有效且人工标记有效的遥测中位数。恢复旧记录仍按原始采样时间判断过期。

记录不存在404；原因或类型不合法400。成功操作写入RECORD_INVALIDATE/RECORD_RESTORE审计。原始设备数据协议见hardware-api.md。
