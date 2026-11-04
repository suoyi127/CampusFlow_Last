# CampusFlow 校园学习空间

Android 与 Spring Boot 后端现仅使用真实 MySQL 数据库及硬件上报。生产程序不初始化演示账号或空间，不生成模拟人数、噪声或虚拟到访，不提供模拟控制接口。

## 本机启动

环境：JDK 21、Maven 3.9、MySQL、MQTT broker。Android 工程位于同级 App 目录。

首次建库：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/initialize-real-database.ps1
```

脚本在本机提示输入 MySQL 管理员凭据，创建 campusflow 数据库及 campusflow_app 专用账号，随机应用数据库口令仅保存到被忽略的 backend/data/mysql-local.properties。不导入样例业务数据。

构建和启动：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-backend-local.ps1
powershell -ExecutionPolicy Bypass -File scripts/start-backend-local.ps1
```

后端监听 8080，local 与正式配置均使用真实 MySQL。脚本加载 mysql-local.properties；local 配置同时加载 backend/data/checkin-local.properties 连接实际 MQTT。首次启动按提示创建服务器管理员，已有管理员直接回车，不重置其密码。

2026-10-26 已初始化真实数据库并创建 admin。随机登录密码保存在本机 backend/data/admin-login.properties，不提交到版本控制。正在运行的后端日志位于 backend/data/server-real.out.log 与 server-real.err.log。

## Android 手机访问

手机通过 USB 调试授权后执行：

```powershell
D:\develop\sdk\android\platform-tools\adb.exe -s 手机序列号 reverse tcp:8080 tcp:8080
```

App 默认访问 http://127.0.0.1:8080，由 ADB 转发到电脑后端；拔线或重连后需要重新建立转发。脱离 USB 时使用手机可达的局域网地址，发布环境使用 HTTPS。Android 没有演示模式。地图 SDK 与自动定位尚未实现，附近搜索需要手动设置实际 GCJ-02 起点。

## 数据与权限

公开注册创建 USER；SERVER_ADMIN 创建真实 DATA_ADMIN 账号，后者录入真实空间、发现设备并绑定。三角色互不继承。初始没有空间和设备时界面显示空状态，不能用空列表证明硬件联调完成。

硬件经 MQTT checkin/+/events 与 checkin/+/telemetry 上传，后端在事务提交后确认。设备上线后进入发现列表，绑定后接收事实记录。无设备、离线、过期或无效读数返回未知；不回退到旧模拟表。服务器概况和设备在线状态均只读。

新库使用 database/real-migrations，由 Flyway 自动建表，不含示例业务记录或模拟表。database/migrations 保留作历史迁移与隔离测试资源，不打包到生产 JAR；已有旧库迁移需单独规划，不能直接切换 Flyway 路径。

## 测试和接口

本次正式后端 mvn clean verify 的 48 项测试通过，其中真实建库结构、注册、权限、版本冲突、评价与硬件处理在隔离数据库验证。实际 MySQL 已验证 admin 登录、会话与真实概况。Android 构建、23 项单元测试、3 项登录界面测试以及 3 项真实服务器管理员接口测试通过。实际设备指标尚需设备上线后验收。

- [认证与空间接口](docs/contracts/initial-api.md)
- [评价接口](docs/contracts/reviews-api.md)
- [真实服务器管理接口](docs/contracts/system-admin-api.md)
- [真实数据检查接口](docs/contracts/simulation-api.md)
- [硬件接收协议](docs/contracts/hardware-api.md)
- [当前业务数据结构](docs/data-structures.md)

Vue Web 源码仍保留部分原模拟控制界面；后端相应接口已移除。Web 界面适配不属于本次 Android 与后端改造验收。
