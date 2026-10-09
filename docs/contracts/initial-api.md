# 首次集成接口约定

所有路径以 `/api` 开头。使用服务器会话 Cookie，登录以外的业务接口要求登录，未登录 401、角色不匹配 403。数据管理只接受 DATA_ADMIN，系统管理只接受 SERVER_ADMIN，角色互不继承。错误格式：

```json
{"code":"CAPACITY_TOO_SMALL","message":"容量不能低于当前在场人数 10","timestamp":"2026-10-09T01:00:00Z"}
```

时间返回 ISO 8601 UTC，客户端以 Asia/Shanghai 显示。数据库 DATETIME 存储 UTC。距离为米，拥挤率为 0—1，缺失指标为 null，不能使用 0 代替。

## 会话与安全令牌

| 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- |
| GET | /auth/csrf | 无 | `{headerName,token}`，允许匿名 |
| POST | /auth/login | 表单编码 username、password | `{username}`，创建或轮换会话 |
| GET | /auth/me | 无 | `{id,username,role,enabled,createdAt}`，不含密码摘要 |
| POST | /auth/logout | 无 | 204，销毁会话 |

全部 POST/PUT 请求携带 `/auth/csrf` 返回的 headerName 和 token。登录、退出后令牌轮换，重新获取；不要在失效时自动重发业务修改。登录失败返回 LOGIN_FAILED。

## 用户空间查询

`GET /spaces` 接受以下可选参数；设施可重复传值，如 `facilities=POWER&facilities=WIFI`。

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| name、type | 空 | 名称包含匹配、类型精确匹配 |
| latitude、longitude | 31.2304、121.4737 | 预置出发点 |
| maxDistance | 3000 | 最大直线距离，单位米 |
| minQuiet | 空 | 1—5，缺少有效噪声时不能满足 |
| maxOccupancy | 空 | 0—1，缺少有效人数时不能满足 |
| facilities | 空 | AC、SEAT、POWER、WIFI，要求同时满足 |
| openOnly | true | 仅当前开放；指定预计区间时始终检查该区间 |
| startAt、durationMinutes | 当前、0 | ISO 8601 时刻、0—1440 分钟 |
| sort | SCORE | SCORE、DISTANCE、QUIET、OCCUPANCY、FACILITY |
| page、pageSize | 1、20 | 页码、每页 1—100 条 |

响应为 `{items,total,page,pageSize,calculatedAt,warnings}`。停用空间始终隐藏。item 为 `{space,status,distanceMeters,score,openNow,reasons}`。预计时间在未来时明确提示实时数据仍代表当前。无结果不放宽用户条件。

`GET /spaces/{id}`：可选 latitude、longitude，返回相同的单个 item。停用空间返回 409 SPACE_DISABLED，不存在返回 404 SPACE_NOT_FOUND。

## 状态字段

```json
{
  "spaceId": 6,
  "simulationRunId": 1,
  "snapshotVersion": 42,
  "currentPeople": 5,
  "capacity": 16,
  "occupancyRate": 0.3125,
  "noiseDb": 49.5,
  "typicalNoiseDb": 50.0,
  "quietLevel": 4,
  "peopleState": "VALID",
  "noiseState": "VALID",
  "deviceState": "ONLINE",
  "peopleUpdatedAt": "2026-10-09T01:00:00Z",
  "noiseUpdatedAt": "2026-10-09T01:00:00Z",
  "calculatedAt": "2026-10-09T01:00:02Z",
  "validitySeconds": 30,
  "source": "SIMULATED"
}
```

peopleState 为 VALID/MISSING/EXPIRED，noiseState 另可为 OFFLINE；deviceState 为 ONLINE/OFFLINE。过期和离线指标返回 null，但保留上次有效记录时间。典型噪声只有在最新有效读数未过期时计算，使用最近 60 秒有效读数中位数。安静等级阈值为 45/55/65/75 dB。同分按距离和空间编号稳定排序；单指标缺失排最后。

## 空间维护

| 方法 | 路径 | 角色 | 响应 |
| --- | --- | --- | --- |
| GET | /data/spaces | DATA_ADMIN | 所有空间数组，含停用空间 |
| POST | /data/spaces | DATA_ADMIN | 新建后的 space |
| PUT | /data/spaces/{id} | DATA_ADMIN | 更新后的 space |

space 的查询字段 openDays/facilities 使用逗号字符串；写入请求使用数组，避免将界面展示格式当作写入模型：

```json
{
  "name":"新自习室","type":"STUDY_ROOM","address":"教学楼一层",
  "latitude":31.2304,"longitude":121.4737,"capacity":30,
  "openTime":"08:00","closeTime":"22:00","openDays":[1,2,3,4,5],
  "allDay":false,"facilities":["SEAT","POWER"],
  "description":"安静学习区域","enabled":true
}
```

容量 1—10000、合法坐标、设施枚举、开放日 1—7、HH:mm 时段；非全天时 closeTime 必须晚于 openTime。空间维护与模拟使用同一空间行锁；降低容量到当前在场人数以下返回 409。新空间同时创建一个模拟设备。无硬删除接口，停用仍保留历史。

## 系统概况与基础控制

`GET /system/overview` 返回 `{version,startedAt,accountCount,spaceCount,simulationRunId,running,pollSeconds,validitySeconds,noiseWindowSeconds}`。

`POST /system/simulation/pause` 和 `/resume` 暂停、继续本轮运行并返回概况。仅 SERVER_ADMIN，写入审计日志。暂停不清空记录，按时间过期。reset、场景切换和配置接口尚未实现。
