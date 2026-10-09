# ESP32 签到签退与环境数据接收

当前固件项目位于 `CheckIn/hardware`，依据其 `Agent.md`、`main/network.c` 和 `main/state.c` 实现。现有固件使用 MQTT，无须修改上传字段即可接入；HTTP 接口供其他客户端和联调使用。设备只记录区域人数，没有用户身份，后端保存 CHECK_IN/CHECK_OUT 原始事件，不将匿名按钮事件配对成某个用户的到访或学习时长。

固件开发版本统一为 ESP-IDF 5.4.4。本机通过 `CheckIn/hardware/idf544.ps1` 编译、配置和烧录，产物位于 `CheckIn/hardware/build/hardware.bin`；具体命令见固件 README。

## 设备与空间绑定

已设置的本机账号使用方式：运行 `scripts/start-backend-local.ps1` 会加载已忽略的 `backend/data/checkin-local.properties`，本机后端接收账号为 admin2；设备上传账号为 admin1，Wi-Fi 与设备密码配置位于固件本地 sdkconfig，不写入公开文档。`scripts/start-mqtt-local.ps1` 使用 backend/data/checkin-broker 下的独立账号哈希和 ACL，不修改已安装 Mosquitto 服务的全局配置。密码文件与本地凭据不提交 Git。

以上脚本不替代设备绑定和 broker 地址配置。此批仅保存凭据，尚未重启后端、启动 broker 或重新烧录固件。

数据管理员登录后进入 **设备管理**（`/data/hardware-devices`），设备正常联网上报后，网页每5秒自动刷新待录入列表。点击“录入设备”，从下拉框选择真实设备 ID 和空间，再保存启停状态；设备名称选填，留空自动使用 ID 命名。绑定保存在 `hardware_device` 表并立即生效。物理 ID 仍由固件 menuconfig 预设或 MAC 自动派生，无需手抄到网页。空间由管理员在“空间管理”中录入，空间 ID 由数据库自增产生。

MQTT 连接参数仍通过环境变量配置，重启生效：

```powershell
$env:CF_CHECKIN_MQTT_ENABLED = 'true'
$env:CF_CHECKIN_MQTT_URI = 'tcp://192.168.1.100:1883'
$env:CF_CHECKIN_MQTT_CLIENT_ID = 'campusflow-checkin-receiver'
$env:CF_CHECKIN_MQTT_USERNAME = Read-Host 'MQTT接收账号'
$env:CF_CHECKIN_MQTT_PASSWORD = Read-Host 'MQTT接收密码'
```

物理设备 ID 为 1—64 个英文字母、数字、下划线或连字符，大小写必须与固件一致，不由网页自动递增生成。设备表另有内部自增 id。一个设备绑定一个空间，一个空间最多绑定一台设备（包括停用设备）。已有上传记录后禁止换绑或解绑，避免离线消息归属变化；迁移空间使用新物理设备 ID 并停用旧设备。未录入或未绑定返回 DEVICE_NOT_BOUND；停用设备返回 DEVICE_DISABLED。停用保留绑定、历史及 HARDWARE 来源，数据按有效期过期，不用模拟补齐。

V8 升级会从旧上传历史恢复可确定的一对一关系。旧 `CF_CHECKIN_DEVICE_BINDINGS` 仅在启动时补充不存在的设备，不覆盖前端修改；空间已被其他设备占用时跳过。日常维护不需要此变量，完成升级后建议删除它。

### 前端维护接口

以下接口需要 DATA_ADMIN 登录会话，写请求必须携带 CSRF；使用已有网页登录流程。

| 方法与地址 | 内容 |
| --- | --- |
| GET /api/data/hardware-devices | 设备数组，含 id、deviceId、name、spaceId、spaceName、spaceAddress、enabled、version、lastReceivedAt；从未上报时最近接收时间为 null |
| GET /api/data/hardware-devices/candidates | 已发现但未录入的设备数组，含 deviceId、firstSeenAt、lastSeenAt、online；由服务器最近有效上报时间推断在线，不等同于 broker 实时连接状态 |
| POST /api/data/hardware-devices | 录入设备，JSON 如下；返回最新设备数组 |
| PUT /api/data/hardware-devices/{id} | 更新名称、绑定和启停，必须携带最新 version；deviceId 不可修改；返回最新设备数组 |

```json
{"deviceId":"AREA_A_001","name":"学习室签到设备","spaceId":6,"enabled":true}
```

`spaceId` 可为 null，表示待绑定；更新需增加 `version`。重复设备返回 409 DEVICE_EXISTS，空间冲突返回 409 SPACE_ALREADY_BOUND，旧版本返回 409 VERSION_CONFLICT，已有历史换绑返回 409 DEVICE_HAS_HISTORY，修改物理 ID 返回 409 DEVICE_ID_IMMUTABLE。维护操作与审计日志同事务提交。

**接入顺序：开启 broker 和后端订阅 → 设备联网正常上报 → 网页下拉选择设备并绑定空间 → 开始业务签到。** 未绑定但通过完整格式校验的 MQTT/HTTP 上报会独立提交设备发现记录；错误主题、retained 消息、错误载荷、未来时间不进入候选。发现不创建绑定、不计空间人数。MQTT 未绑定消息仍保存至拒收记录，注册后不会自动补录；发现阶段不要进行正式签到。每5秒遥测即可发现，无需按按钮，但设备时钟需正常才能发送有效遥测。HTTP 发现仍需已有上传令牌；仅连接 broker 未发送有效上报不会被发现。录入后设备从候选中移除，编辑已录入设备时 ID 固定。离线候选保留并标注离线。

MQTT 账号负责 broker 登录，device_id 负责后端查找空间，两者不同。

固件 broker URI 为 `mqtt://` / `mqtts://`，Java 接收端对应 `tcp://` / `ssl://`。双方连接同一 broker。TLS 使用 JVM 信任库校验证书；私有 CA 需导入信任库。接收账号需允许订阅 `checkin/+/events` 和 `checkin/+/telemetry`；设备账号应由 broker ACL 限定为只能发布自身主题。

MQTT 默认关闭；HTTP 默认未配置令牌时返回 503 HARDWARE_HTTP_DISABLED。保持固定且独占的接收端 client-id，接收端使用持久会话。部署应先启动接收端并完成订阅，再启动设备；broker 必须启用持久存储和相应离线消息队列，才能保留接收端离线时的 QoS1 消息。

## MQTT 接收

| 主题 | 固件发送 QoS | 内容 |
| --- | --- | --- |
| `checkin/<device_id>/events` | 1 | 签到 / 签退事件 |
| `checkin/<device_id>/telemetry` | 0 | 最新人数与声音状态 |

接收端以 QoS1 订阅两种主题，实际投递等级受发布 QoS 限制，遥测仍是 QoS0。禁止 retained 消息，主题设备 ID 必须与 JSON 一致，消息最大 8192 字节。正常消息在数据库事务提交后才向 broker 确认；数据库故障不确认，重连后允许重投。永久性协议错误先保存至 hardware_rejection，再确认，避免坏消息持续堵塞；记录 error_code 和最多 8192 字符的原始内容。

固件在 broker PUBACK 后删除离线事件。接收端落库后的确认是 broker 到接收端这一段的确认，尚未扩展为设备端的应用落库确认；broker 持久化和既有固件 PUBACK 边界仍然存在。擦除设备 NVS 后事件编号会归零，必须更换设备 ID，不能继续复用旧编号。

## HTTP 接收

均使用 POST、`Content-Type: application/json`，无需 Cookie 或 CSRF。配置 `$env:CF_CHECKIN_HTTP_TOKEN` 后，请求携带 `Authorization: Bearer <配置的令牌>`；缺失或错误返回 401 INVALID_DEVICE_TOKEN。该令牌仅用于设备上传，不能用于管理员查询。网页会话的角色与 CSRF 规则保持原约定。

### 签到和签退：POST /api/hardware/events

```json
{
  "device_id": "AREA_A_001",
  "event_id": "1",
  "timestamp": 1791511200,
  "event_type": "CHECK_IN",
  "headcount_after_event": 1
}
```

| 字段 | 约束 |
| --- | --- |
| device_id | 必填，已绑定的设备标识 |
| event_id | 必填，固件单调递增的 uint64 十进制字符串，1—18446744073709551615，无前导零 |
| timestamp | 必填，DS3231 UTC Unix 秒，2000—2099 年；不得超过服务器当前时间 60 秒 |
| event_type | 必填，CHECK_IN 或 CHECK_OUT；签退使用同一接口 |
| headcount_after_event | 必填，动作后的绝对人数，整数 0—4294967295 |

成功返回 200，示例：

```json
{"recordId":12,"spaceId":6,"deviceId":"AREA_A_001","duplicate":false}
```

以 `(device_id,event_id)` 去重；相同事件重投返回原 recordId 和 duplicate=true，不再次计数。相同编号但时间、动作、人数或空间不同返回 409 EVENT_CONFLICT。后端直接使用 headcount_after_event，不重复执行 +1/-1；空人数签退可上报 0。离线缺段允许保存，不强行要求从事件 1 连续接收。容量外人数保留原始事实，当前状态标为 INVALID。

### 环境状态：POST /api/hardware/telemetry

```json
{
  "device_id": "AREA_A_001",
  "timestamp": 1791511205,
  "headcount": 1,
  "decibel": 65.4,
  "sound_valid": true
}
```

device_id、timestamp 约束同事件；headcount 为必填整数，范围同 headcount_after_event。sound_valid 必填：true 时 decibel 必须是 0—150 的有限数值；false 时 decibel 必须为 null。无效组合返回 400 INVALID_SOUND，其他格式错误返回 400 INVALID_INPUT；未来时间返回 400 FUTURE_TIMESTAMP。成功返回同样的 Receipt，duplicate=false。遥测没有事件编号，不承诺去重。

固件分贝为 MAX9814 峰峰值估算，不声明绝对声压级或 A 计权值。异常读数不替换成 0，也不沿用旧的正常读数掩盖故障。

## 管理员查询：GET /api/data/hardware-records

需要真实登录会话和 DATA_ADMIN 角色。未登录 401，其他角色 403。

```http
GET /api/data/hardware-records?spaceId=6&kind=EVENT&page=1&pageSize=20
```

可选 spaceId、deviceId、kind（EVENT / TELEMETRY）；page 默认 1，范围 1—1000000；pageSize 默认 20，范围 1—100。按接收记录 id 降序，包含全部硬件历史，不受模拟轮次影响。返回 `{items,total,page,pageSize,calculatedAt,warnings:[]}`。

每项字段：id、deviceId、spaceId、kind、eventId、eventType、headcount、decibel、soundValid、sampledAt、receivedAt。事件的声音字段为 null；遥测的事件字段为 null。sampledAt 来自 DS3231，receivedAt 来自服务器接收时间，均返回 UTC ISO 时间。CHECK_OUT 为区域签退事件，没有 checkedOutAt / virtualPersonId 等虚拟到访字段。

## 与状态、模拟的衔接

- 绑定空间的既有列表和详情状态返回 source=HARDWARE；未绑定空间继续返回 SIMULATED。没有硬件数据时返回 MISSING，不用模拟数据补齐。
- 人数取设备时间最新记录；同秒内事件优先于遥测，多个事件按数值 event_id 排序。旧补传不会倒退当前人数。人数与噪声按各自原采样时间独立判断有效期。
- 最新遥测 sound_valid=false 时噪声标为 INVALID；正常噪声继续使用配置窗口内有效遥测的中位数。设备在线状态按最近服务器接收时间和配置有效期推断，超时为 OFFLINE；固件未提供遗嘱或独立在线消息。
- 绑定空间停止新增模拟人数和噪声；容量维护按最新设备人数校验。模拟暂停、场景、重置不清除硬件记录，也不控制物理设备。设备历史上报不受空间当前营业时间、启停或容量限制，用户侧仍沿用空间启停过滤。
- 真实记录存入独立 V6 表；原 `/api/data/records?kind=VISIT` 仍只表示模拟到访，不能查询真实按钮事件。硬件记录当前提供只读查询。

接收确认实现参考 [Eclipse Paho MqttClient](https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttClient.html) 的手动确认接口，持久订阅参考 [MqttConnectOptions](https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html)。
