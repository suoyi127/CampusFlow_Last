# 硬件端与后端环境、配置和启动说明

适用日期：2026年10月9日。硬件工程：`D:\collectionOfCode\program\campus\CheckIn\hardware`；后端工程：`D:\collectionOfCode\program\campus\CampusFlow_Last\backend`。硬件使用 ESP-IDF **5.4.4**。

## 1. 系统组成

```text
ESP32-S3（按键、RTC、声音传感器）
    │ Wi-Fi + MQTT，设备账号 admin1
    ▼
MQTT broker（Mosquitto，1883）
    │ MQTT 订阅，后端账号 admin2
    ▼
Spring Boot 后端（18080） → H2 或 MySQL 数据库
    ▲
    │ 前端 /api 代理
Vue 前端（5173） → 浏览器
```

MQTT broker 是设备和后端之间的消息服务器，需要独立运行。后端不是 broker；前端也不直接连接 broker。当前 ESP32 固件使用 MQTT 上传，HTTP 上传令牌属于另一种接入方式，完整运行当前固件无需该令牌。

## 2. 已完成与待完成的配置

| 项目 | 当前情况 | 下一步 |
| --- | --- | --- |
| ESP-IDF | hardware 工程已使用 5.4.4 全量构建成功 | 接线后烧录 |
| Wi-Fi | 本地 sdkconfig 已保存网络名称 gam 和用户提供的密码 | 确认设备能连接此网络 |
| 设备 MQTT 账号 | admin1，密码已保存到本地 sdkconfig；broker 中已有密码哈希 | 烧录后连接项目 broker |
| 后端 MQTT 账号 | admin2，密码保存在 backend/data/checkin-local.properties；broker 中已有密码哈希 | 用启动脚本加载 |
| broker 地址 | 固件现为 mqtt://192.168.1.100:1883，尚未核实该地址是否为运行电脑 | 替换为实际电脑局域网 IPv4 |
| 设备 ID | 当前为空，由完整 MAC 自动派生 AREA_ 开头的标识 | 正常联网上报后网页自动发现，下拉选择即可 |
| 设备与空间绑定 | 由数据库保存 | 数据管理员在前端“设备管理”录入并选择空间 |
| 后端 MQTT 开关 | 本机已保存为 true，并成功订阅1883服务；独立部署默认 false | 本机脚本加载本地配置，其他环境需开启 |
| 数据库 | local 模式使用已有持久化 H2 | 本地联调可直接使用；MySQL 需另配账号 |
| HTTP 上传令牌 | 尚未配置 | 当前 MQTT 方案无需配置 |
| 高德地图凭据 | 远端地图功能已整合，尚未配置 Web JS API Key 和安全密钥 | 地图功能按 docs/amap-setup.md 配置；不影响 MQTT 上传 |
| broker 持久化 | 配置已开启；此前保存检查遇到 Windows 文件权限错误 | 正式联调前确认能创建、更新 mosquitto.db，问题尚未确认解决 |

本文件不重复保存明文密码。密码位于已忽略的本地配置文件；文件不随 Git 克隆分发，换电脑时需重新填写。编译成功不表示已烧录、联网或完成真实硬件验收。

## 3. 所需环境与硬件

| 位置 | 必需环境或部件 | 说明 |
| --- | --- | --- |
| 固件开发电脑 | ESP-IDF 5.4.4、对应 Python / 编译器 / CMake / Ninja | 本机已安装，由 idf544.ps1 激活 |
| 固件开发电脑 | VS Code ESP-IDF 扩展、USB 数据线及可识别的设备接口 | 用于配置、编译、烧录和串口监视 |
| 设备 | ESP32-S3、DS3231、两个 KY-004 按键、MAX9814 | 提供人数事件、UTC 时间和声音估算 |
| 设备网络 | 可用的 2.4GHz Wi-Fi；能够连接电脑的 broker | 设备与电脑通常在同一局域网，避免访客网络隔离 |
| 后端电脑 | JDK 21、Maven 3.9 | Maven 用于源码构建；运行已有 JAR 只需 Java |
| broker 电脑 | Mosquitto | 本机安装在 C:\Program Files\mosquitto |
| 数据库 | H2 或 MySQL 8.0 | H2 随后端依赖提供，无需另外安装服务 |
| 前端电脑 | Node.js 22.12 以上或兼容的 24、npm | 运行现有 Vue/Vite 前端 |

电脑、broker 可以是同一台机器。ESP32 中的 localhost 指设备自己，不能作为电脑的 broker 地址。首次初始化 RTC 时设备需能访问所配置的 NTP 服务；电脑时间也应正确。

## 4. 硬件端配置

在硬件工程目录执行：

```powershell
cd D:\collectionOfCode\program\campus\CheckIn\hardware
.\idf544.ps1 menuconfig
```

进入 `Area check-in hardware configuration`：

| 设置 | 用途 | 配置要求 |
| --- | --- | --- |
| Wi-Fi SSID / password | 联网 | 已保存 gam 和密码，网络变化时重新填写 |
| MQTT broker URI | 连接消息服务器 | mqtt://实际电脑IP:1883；TLS 使用 mqtts:// |
| MQTT username / password | 设备认证 | admin1 和已设置的密码 |
| Device ID | 标识物理区域 | 固定标识或留空使用 MAC；必须与后端绑定一致 |
| NTP server | 校准 DS3231 | 使用设备能访问的服务器；默认 pool.ntp.org |
| SDA / SCL | RTC 引脚 | 默认 GPIO8 / GPIO9，按实际接线调整 |
| Check-in / Check-out GPIO | 签到、签退按键 | 默认 GPIO4 / GPIO5 |
| ADC1 channel | MAX9814 声音输入 | 默认 0，对应 GPIO1 |
| 94 dB reference millivolts | 声音估算参考 | 默认 1000，实际应用需参考声源校准 |

接线要求：各模块共地；信号电平 3.3V；RTC 配置备用电池，SDA/SCL 使用约 4.7kΩ 外部上拉。按键低电平有效。引脚不能与板上 USB、Flash、PSRAM 或其他外设冲突。

编译和烧录：

```powershell
.\idf544.ps1 build
.\idf544.ps1 -p COM3 flash monitor
```

COM3 换成实际串口。镜像位于 `hardware/build/hardware.bin`，需要配套 bootloader 和分区表；使用上述 flash 命令统一烧录。不要为了版本切换重复执行 set-target 导致本地配置被重置，也不要擦除已有 NVS 来解决普通连接问题：离线事件和人数保存在 NVS。

VS Code 切换方式：Ctrl+Shift+P → `ESP-IDF: Select Flash Method`；通过 COM 口烧录选择 UART，再用 `ESP-IDF: Select Port to Use` 选择串口；通过 OpenOCD 烧录选择 JTAG。选择应与接线、驱动和实际接口相匹配。idf544.ps1 中的 flash 命令使用 esptool 串口下载，不读取 VS Code 的 idf.flashType。

默认每5秒发送环境状态；签到签退即时产生事件。修改周期、队列容量等代码参数时编辑 `hardware/main/config.h`，重新编译和烧录。更改 menuconfig 也需要重新构建、烧录才会作用于设备。

## 5. MQTT broker 配置

本机项目配置在：

```text
CampusFlow_Last/backend/data/checkin-broker/
  mosquitto.conf  监听、认证、权限和持久化设置
  passwd          admin1/admin2 的密码哈希
  acl             发布、订阅权限
  mosquitto.db    broker 运行时保存的持久化消息和会话数据
```

当前权限：admin1 可发布 checkin/+/events 和 checkin/+/telemetry；admin2 可订阅这两个主题。两个账号仅用于 MQTT，与网页登录、数据库账号无关。当前设备账号覆盖该主题命名空间，若增加多个互不信任的设备，应给每台设备单独账号并限制到自身设备主题。

项目配置监听 1883、禁止匿名连接、启用持久化。启动前确认1883没有被另一个 Mosquitto 服务占用。已有安装目录中的全局 mosquitto.conf 不会被项目脚本自动修改；必须启动使用项目配置的 broker，项目账号才会生效。

Windows 防火墙允许专用网络访问1883。普通 mqtt:// / tcp:// 用于可信局域网联调；公开网络需配置 TLS、证书和对应信任库，设备用 mqtts://，后端用 ssl://。

曾出现的持久化权限错误涉及 broker 数据文件，账号文件当前用户读写权限已修复，但持久化写入尚未确认正常。运行 broker 后须确认日志没有 Error saving in-memory database / Permission denied，并在正常退出后确认 mosquitto.db 已保存。仅显示 Configuration file is OK 不等于持久化正常。

## 6. 后端配置

2026年10月9日设备自动发现更新后，本机忽略的 `backend/data/checkin-local.properties` 已保存 `campusflow.checkin.mqtt.enabled=true` 和 `campusflow.checkin.mqtt.uri=tcp://127.0.0.1:1883`，接收账号凭据保留。本次复用当前1883服务并确认订阅成功；后续运行本地后端脚本会加载该开关。独立部署仍默认关闭，需要按下表开启并配置自己的 broker。

### MQTT 必需配置

| 配置项 | 示例 | 用途 |
| --- | --- | --- |
| CF_CHECKIN_MQTT_ENABLED | true | 开启接收，默认 false |
| CF_CHECKIN_MQTT_URI | tcp://127.0.0.1:1883 | broker 与后端同机时可用；不同机填写 broker IP |
| CF_CHECKIN_MQTT_CLIENT_ID | campusflow-checkin-receiver | 固定且独占的接收端客户端标识 |
| 设备与空间绑定 | 前端“设备管理” | 保存到数据库立即生效，无需环境变量 |
| MQTT username / password | admin2 / 本地保存的密码 | 由项目启动脚本加载凭据文件 |

没有本地凭据文件时，配置 CF_CHECKIN_MQTT_USERNAME / CF_CHECKIN_MQTT_PASSWORD 即可供 application.yml 使用；本机脚本另外加载的 properties 文件直接指定了 admin2 凭据，修改账号时需同步修改该文件。

数据管理员先在“空间管理”录入真实空间，启动 broker 和后端订阅，让设备联网并正常上报，再在“设备管理 → 录入设备”下拉选择自动发现的设备和学习空间。物理设备 ID 可以在固件 menuconfig 预设，也可留空使用 MAC 派生标识，无须抄写到网页；名称选填，留空自动命名。尚未产生业务上传记录可调整绑定，已有历史后不能换绑或解绑，可修改名称和启停。旧 CF_CHECKIN_DEVICE_BINDINGS 仅补充缺失设备，不再用于日常维护。

发现依赖有效遥测或签到消息，单纯连上 Wi-Fi/broker 不够；现有固件每5秒发送遥测，需设备时钟正常。网页每5秒刷新，离线候选保留并标注离线。未绑定阶段只发现设备，不计空间人数；请完成绑定后再开始正式签到。格式错误、未来时间、MQTT 主题不一致或 retained 消息不会产生候选。

### 数据库两种方式

本地演示：`local` profile 自动使用 `backend/data/campusflow` 中的 H2；用户名 sa、空密码，无需启动额外数据库服务。Flyway 自动执行迁移，真实记录使用 V6 表，设备管理使用 V8 表，自动发现使用 V9 表。

MySQL：先创建数据库和专用账号，然后配置：

```powershell
$env:CF_DB_URL = 'jdbc:mysql://localhost:3306/campusflow?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:CF_DB_USER = '你的数据库账号'
$env:CF_DB_PASSWORD = Read-Host '数据库密码'
java.exe -jar target/campusflow-0.1.0.jar --server.port=18080 --spring.config.additional-location=file:./data/checkin-local.properties
```

在 backend 目录执行；MySQL 模式不要带 local profile，也不要使用 start-backend-local.ps1，否则仍会使用 H2。独立环境需自行创建业务账号；仅课程演示需要初始化演示账号时才设置 CF_DEMO_ENABLED=true。

### HTTP 上传令牌（可选）

CF_CHECKIN_HTTP_TOKEN 是后端校验 HTTP 上传请求的共享密钥，不是网页密码或 MQTT 密码。仅测试或使用 POST /api/hardware/events、POST /api/hardware/telemetry 时需要。配置后重启后端，请求携带 `Authorization: Bearer <同一令牌>`；未配置时 HTTP 上传返回503。现有 ESP32 固件无需填写此令牌。

## 7. scripts 目录是做什么的

实际目录名是 **scripts**，不是 script：`D:\collectionOfCode\program\campus\CampusFlow_Last\scripts`。其中的 .ps1 是 PowerShell 启动脚本，帮你执行已有程序，不是上传接口或固件业务代码。

| 脚本 | 实际操作 | 不会自动完成的事 |
| --- | --- | --- |
| start-mqtt-local.ps1 | 调用 C:\Program Files\mosquitto\mosquitto.exe，加载项目 broker 配置并显示日志 | 不安装 broker，不修改全局服务配置，不自动解决端口与防火墙问题 |
| start-backend-local.ps1 | 进入 backend，加载 data/checkin-local.properties，运行 JAR；固定 local profile 和18080端口 | 不编译 JAR，不开启 MQTT，不填写设备绑定，不改用 MySQL |
| hardware/idf544.ps1（位于硬件目录） | 激活本机5.4.4，校验版本，提供无空格 SDK 入口，转发 menuconfig/build/flash/monitor 命令 | 不填写真实 broker IP，不选择接线，不自动连接板卡 |

这些脚本运行期间占用当前终端，Ctrl+C 停止对应进程。不要同时重复启动同一端口的服务。后台安装的 Mosquitto Windows 服务是否运行，要另外确认。

脚本不是必须的，但本机 MQTT 凭据写在独立 properties 文件中。如果直接运行 JAR 而没有 additional-location 或等价账号配置，后端不会自动读取该文件。这里提供的脚本避免漏掉这一步。

## 8. 完整启动顺序

以下为本地 H2 方案；电脑 IP、设备 ID、空间 ID 必须先按实际情况配置。修改后端源码后先停止占用 JAR 的旧后端，并在 backend 中执行 `mvn.cmd -DskipTests package`，否则脚本运行的仍是旧构建。

**终端1：启动项目 broker。**

```powershell
cd D:\collectionOfCode\program\campus\CampusFlow_Last
.\scripts\start-mqtt-local.ps1
```

**终端2：设置接收开关和地址，再启动后端。**

```powershell
cd D:\collectionOfCode\program\campus\CampusFlow_Last
$env:CF_CHECKIN_MQTT_ENABLED = 'true'
$env:CF_CHECKIN_MQTT_URI = 'tcp://127.0.0.1:1883'
$env:CF_CHECKIN_MQTT_CLIENT_ID = 'campusflow-checkin-receiver'
.\scripts\start-backend-local.ps1
```

看到“签到MQTT订阅已就绪”之后，让设备联网正常上报，在前端选择已发现的设备并绑定空间，再开始正式签到。未绑定的消息会产生候选并进入拒收记录，绑定后不会自动补录过去消息。环境变量仅对当前终端及其启动的进程生效；MQTT 配置改动需要重启后端，网页绑定保存后立即生效。

**终端3：启动前端。**

```powershell
cd D:\collectionOfCode\program\campus\CampusFlow_Last\frontend
$env:CF_API_TARGET = 'http://127.0.0.1:18080'
npm.cmd run dev
```

首次未安装依赖时先执行 npm.cmd ci。电脑访问 http://localhost:5173；手机访问运行前端电脑的局域网IP:5173，需要允许专用网络访问5173。

**终端4或VS Code：烧录并监视 ESP32。**

```powershell
cd D:\collectionOfCode\program\campus\CheckIn\hardware
.\idf544.ps1 -p COM3 flash monitor
```

电脑IP不要直接照抄192.168.1.100。用 ipconfig 查看已连接网络的 IPv4，再填入固件 MQTT URI。如果路由器重新分配IP，需调整固件配置或给电脑设置稳定地址。

## 9. 最低联调检查

1. 串口显示正确 device_id，RTC 首次校准成功时出现 DS3231 synchronized (UTC)。
2. broker 接收设备和后端两个客户端连接；后端订阅成功，没有持续认证失败或落库异常。
3. 登录网页查看绑定空间，来源显示设备实测，按签到人数增加、按签退人数减少，最低为0。
4. 声音约每5秒更新；ADC 异常时显示无效，不显示伪造的0dB。
5. 数据管理员通过 GET /api/data/hardware-records?spaceId=6&kind=EVENT 查询真实事件。现有网页“数据检查”主要展示模拟记录，真实事件历史尚未增加独立页面。
6. 短时断开设备网络，产生事件后恢复，核对原时间和事件去重；同时确认 broker 持久化保存正常。

local 模式已有演示网页账号 student、data_admin、server_admin，密码统一 Demo@123456。它们分别用于用户、数据管理员和服务器管理员页面，不代替 MQTT 账号。

## 10. 常见问题与调整位置

| 现象 | 优先核对 |
| --- | --- |
| 设备连不上 Wi-Fi | 名称、密码、2.4GHz网络、信号和供电 |
| 设备连不上 broker | 实际局域网IP、1883监听、防火墙、设备 admin1 凭据 |
| 后端连不上 broker | MQTT开关、URI、admin2凭据、是否运行了使用项目配置的 broker |
| DEVICE_NOT_BOUND | 串口 device_id 与前端设备 ID 大小写是否一致、是否已选择空间 |
| DEVICE_DISABLED | 前端设备管理是否停用了该设备 |
| FUTURE_TIMESTAMP | DS3231 UTC 时间与电脑时间；后端拒绝超前超过60秒的设备时间 |
| 网页人数未知或过期 | 绑定空间不再用模拟数据补齐；检查设备上报和原采样时间 |
| 人数 INVALID | 上报人数超过空间容量，需核对设备人数和空间设置 |
| HTTP返回503 | 未配上传令牌；不影响 MQTT |
| broker保存 Permission denied | 检查数据文件的 Windows权限及启动用户，尤其 mosquitto.db / 临时文件 |
| 端口占用或JAR无法打包 | 先停止对应旧服务，避免重复启动与文件占用 |

业务字段、去重和返回格式见 [硬件接收接口](contracts/hardware-api.md)；硬件接线与固件模块细节见 [hardware README](../../CheckIn/hardware/README.md)。
