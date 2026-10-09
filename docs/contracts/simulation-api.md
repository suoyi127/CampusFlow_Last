# 模拟管理与数据检查接口

C03/C05/B05 与评价、账号及动态配置模块共同运行。所有接口以 `/api` 开头，沿用真实登录会话和 CSRF；账号启停或角色变更后，旧会话失效规则同服务器管理接口。

## 服务器管理员

以下接口仅 SERVER_ADMIN，其他有效角色返回403，未登录或失效会话返回401。

| 方法 | 路径 | 请求及响应 |
| --- | --- | --- |
| GET | /system/overview | 既有概况新增 scenario、seed、targetSpaceId、eventEndsAt；后两项可为null；simulationSeconds、validitySeconds、noiseWindowSeconds继续读取RUNTIME配置 |
| POST | /system/simulation/pause、/resume | 暂停或继续；返回概况；暂停不伪造采样，到达营业结束仍清退已有到访 |
| POST | /system/simulation/reset | 清除本轮到访、人数快照和噪声读数，开启新运行，恢复NORMAL/127和设备在线，重新初始化到访；返回概况 |
| PUT | /system/simulation/scenario | `{scenario,spaceId?,seed?,durationSeconds?}`；返回概况 |
| GET | /system/devices | 全部设备数组，包含停用空间 |
| PUT | /system/devices/{spaceId} | `{online:true/false}`；返回概况；恢复不修改旧读数时间 |

scenario枚举：NORMAL正常人流；PEAK每周期增加约容量10%的到访并受营业时间和容量约束；NOISE_EVENT在目标空间叠加30dB，读数最终限制30—90dB；DEVICE_OFFLINE将目标空间检测仪立即设为离线。

NOISE_EVENT和DEVICE_OFFLINE必须指定启用空间，缺少目标返回400 SPACE_REQUIRED，目标不存在404，停用409。seed默认127，整数范围0—2147483647；durationSeconds默认60，整数范围5—3600秒，仅噪声事件使用。噪声事件到期停止叠加，窗口按持久化配置自然淘汰旧读数。

每次切换场景先恢复全部设备在线，再应用目标场景；不清除既有采样，不改变运行开关。固定种子、相同初始资料、时钟条件和操作顺序可复现人数及噪声。

重置保留账号、空间、评价、RUNTIME配置、操作日志及simulation_run历史，保持当前运行或暂停状态，不清除以前运行的历史样本。界面提供具体清除范围与确认。周期更新、场景、设备和重置在覆盖事务提交的互斥区执行，与空间维护及异常标记共用空间行锁。

## 数据管理员

以下接口仅 DATA_ADMIN；设备状态只读，设备在线离线由服务器管理员控制。

| 方法 | 路径 | 请求及响应 |
| --- | --- | --- |
| GET | /data/records | 必填kind=PEOPLE/NOISE/VISIT；可选spaceId、valid、page（默认1，1—1000000）、pageSize（默认20，1—100）；查询当前运行，按id降序 |
| GET | /data/devices | 同服务器设备数组，仅查询 |
| PUT | /data/records/{kind}/{id}/validity | `{valid:false,reason:"异常原因"}`或`{valid:true,reason:"恢复原因"}`；仅PEOPLE/NOISE；成功204；原因必填1—500字 |

记录响应为`{items,total,page,pageSize,calculatedAt,simulationRunId,warnings:[]}`。每项包含id/runId/spaceId。PEOPLE另含currentPeople/sampledAt/valid/invalidReason；NOISE另含deviceId/noiseDb/sampledAt/valid/invalidReason；VISIT另含virtualPersonId/checkedInAt/checkedOutAt，未签退时checkedOutAt为null。VISIT不接受有效标记筛选或修改。

设备项为`{id,spaceId,spaceName,enabled,deviceCode,status,baseDb,fluctuationDb}`。enabled是空间启停，status是ONLINE/OFFLINE，两者分别展示。

标记无效后，人数选取最近有效且不超过现容量的快照，不重算历史事件；噪声退出最新读数候选和中位数窗口。没有符合条件的有效候选但存在记录时返回INVALID，指标为null；设备离线优先返回OFFLINE。恢复清空当前invalidReason，恢复原因保留在审计日志，原采样时间不变，可能仍然过期。存在较早有效候选时，依其原时间和当前RUNTIME有效期判断状态。

记录不存在或已被重置清除返回404 RECORD_NOT_FOUND，非本轮记录返回409 OLD_SIMULATION_RUN。标记及恢复分别产生RECORD_INVALIDATE/RECORD_RESTORE日志；场景、重置和设备操作也写入成功审计日志，可通过已有日志页面查询。

数据库DATETIME存储UTC，原始记录使用LocalDateTime读取后明确转换UTC，防止MySQL连接时区二次偏移；客户端统一按Asia/Shanghai显示。
