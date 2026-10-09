# CampusFlow 学习空间推荐系统

按三人开发计划推进的课程项目。当前版本包含三角色登录、空间维护、后端统一模拟人数与噪声、空间筛选排序、评价提交与审核、账号与运行配置管理、日志查询、固定种子场景、设备离线、模拟重置及异常记录检查。

## 开发环境

- JDK 21 或兼容版本，项目编译目标为 Java 21；Maven 3.9。
- Node.js 22.12 以上或 Node.js 24，npm。
- 正式数据库 MySQL 8.0；无需安装数据库的本地演示可使用 local 配置的 H2。

技术栈：Vue 3、TypeScript、Vite、Element Plus；Spring Boot、Spring Security 会话认证、MyBatis-Plus、Flyway。

## 本地快速启动

在 PowerShell 的两个终端分别执行。

后端：

```powershell
cd CampusFlow_Last/backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

前端：

```powershell
cd CampusFlow_Last/frontend
npm.cmd ci
npm.cmd run dev
```

访问 `http://localhost:5173`。前端代理 `/api` 到 `http://localhost:8080`，通过同源 Cookie 保持会话。手机与 PC 演示时，手机访问运行前端电脑的局域网 IP 和 5173 端口；两端共用该电脑的后端。允许本机防火墙对演示网络开放前端端口，手机定位不可用时选择预置点。

如果 8080 已被其他服务占用，分别在启动后端和前端的终端设置 `$env:CF_PORT = '18080'` 和 `$env:CF_API_TARGET = 'http://localhost:18080'`，然后执行上述启动命令。本次联调使用 18080，避免影响机器上的既有服务。

local 数据库保存在 `backend/data`，已排除 Git。每次启动开启新的模拟运行，保留账号、空间和已有业务数据，结束上次仍在场的模拟到访。当前运行暂停后，采样自然过期。

演示初始化仅在 local/test 或显式 `CF_DEMO_ENABLED=true` 时创建账号，不重置已有账号密码：

| 账号 | 角色 | 测试密码 |
| --- | --- | --- |
| student | 用户 | Demo@123456 |
| data_admin | 数据管理员 | Demo@123456 |
| server_admin | 服务器管理员 | Demo@123456 |

以上是公开测试账号，不用于真实部署。服务器管理员不继承数据管理员权限。

## 使用 MySQL

先创建空数据库 `campusflow` 和具有该库建表、读写权限的专用账号。密码通过环境变量传入，不写进版本库。

```powershell
cd CampusFlow_Last/backend
$env:CF_DB_URL = 'jdbc:mysql://localhost:3306/campusflow?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:CF_DB_USER = 'campusflow'
$env:CF_DB_PASSWORD = Read-Host '数据库密码'
$env:CF_DEMO_ENABLED = 'true'
mvn spring-boot:run
```

Flyway 自动从 `database/migrations` 执行版本化迁移；该目录由 Maven 打包到 JAR，不能同时手动执行同一套脚本。V2 的十个空间和坐标是课程演示数据，不代表真实校园；账号由启用演示初始化的后端生成 BCrypt 摘要。后续数据库变更新增迁移文件，不改写已应用版本。

## 验证与打包

```powershell
cd CampusFlow_Last/backend
mvn test package
cd ../frontend
npm.cmd run build
npm.cmd test
```

后端输出 `backend/target/campusflow-0.1.0.jar`，前端输出 `frontend/dist`。JAR 提供 API；开发演示页面由 Vite 提供。正式静态部署需让前端与 `/api` 共用同一域名，并将 API 反向代理到后端，不要直接用文件方式打开 `dist/index.html`。

```powershell
cd CampusFlow_Last/backend
java -jar target/campusflow-0.1.0.jar --spring.profiles.active=local
```

## 首次集成演示

1. 用户登录，比较空间；选择插座、网络，切换排序，进入详情查看人数和噪声各自更新时间。
2. 数据管理员登录，编辑空间、创建空间或停用空间；用户列表隐藏停用空间。容量不能低于当前在场人数。
3. 服务器管理员登录，暂停模拟；状态超过配置有效期（默认 30 秒）后显示未知。恢复后观察状态更新。
4. 两个浏览器查询同一空间，比较同一快照编号。服务器按配置周期生成数据（默认 5 秒），前端每 5 秒查询，不独立产生随机状态。
5. 用户在详情提交环境与设施评分，在“我的评价”查看状态、修改或撤回；数据管理员在“评价审核”筛选并通过或驳回。仅当前已通过评价公开并参与均分，修改会重新待审核，撤销通过需要原因。
6. 服务器管理员在“账号管理”创建账号、启停及分配角色；变更后旧会话在下一次 API 请求失效。系统保护最后一个启用的服务器管理员。密码至少 8 字符且最多 72 UTF-8 字节，创建后不返回明文。
7. 在“运行配置”修改模拟周期、有效期、噪声窗口和推荐权重；保存后后续计算生效。在“操作日志”按操作者、动作及日期检查成功业务操作。前端轮询固定 5 秒，运行参数以数据库 RUNTIME 配置为准。

## 文档与后续范围

- [接口约定](docs/contracts/initial-api.md)
- [评价接口约定](docs/contracts/reviews-api.md)
- [服务器管理接口](docs/contracts/system-admin-api.md)
- [模拟管理与数据检查接口](docs/contracts/simulation-api.md)
- [业务数据结构](docs/data-structures.md)
- [开发进度与验证记录](docs/progress.md)
- [原开发计划](docs/reference/development-plan.md)与 [需求文档](docs/reference/requirements.docx)

当前营业时间采用选中的开放日共用一个时段，支持全天开放，不支持跨午夜营业时段；每周每日分别配置时段仍待完成。A05 的账号、配置、日志、场景和模拟重置已接入。

## 模拟管理与数据检查演示

1. server_admin在服务器概况选择人流高峰，观察人数逐周期增加且不超过容量；应用噪声事件并选择目标空间和持续秒数，观察窗口中位数及推荐变化。
2. 将检测仪设为离线，噪声立即显示未知；恢复后按原采样时间判断有效性，下一模拟周期才产生新读数。运行周期、有效期和噪声窗口继续由运行配置页面管理。
3. 暂停模拟后，data_admin在数据检查按空间、记录类型和有效标记查询。填写原因标记最新快照或噪声读数无效，汇总回退或重新计算；恢复不刷新采样时间。虚拟到访仅供查询，不标记无效。
4. 重置前阅读确认框：本轮到访、快照和读数清除；账号、空间、评价、运行配置和日志保留，运行开关保持。重置后运行编号变化，旧记录写操作被拒绝。
5. 在操作日志检查SIMULATION_SCENARIO、SIMULATION_RESET、DEVICE_STATUS、RECORD_INVALIDATE或RECORD_RESTORE动作。
