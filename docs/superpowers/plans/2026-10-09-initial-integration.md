# 首次集成实施计划

目标：完成开发计划 D1—D5 的可运行首次集成。

架构：frontend、backend、database、docs 单仓库；功能模块通过 Service 公开方法协作。保留正式 MySQL 与本地 H2 两种启动方式。

技术栈：Vue 3、TypeScript、Vite、Element Plus；Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、MySQL、Flyway。

- [x] 基础：backend/pom.xml、application.yml、local/test 配置；frontend/package.json、vite.config.ts、tsconfig；database 的版本化 SQL 作为 Flyway 唯一来源。
- [x] 先编写 backend/src/test/java/com/campusflow/InitialIntegrationTest.java，验证真实 HTTP 会话登录、未登录拒绝、角色互斥、十空间初始化和更新。执行 `mvn test`，观察缺少接口导致的失败。
- [x] 实现 common 错误、auth 会话与 CSRF、space 的实体 Mapper Service Controller。所有管理写操作在服务器校验，容量降低不能低于真实在场人数。
- [x] 增补最小模拟业务测试，先观察失败，再实现 simulation 到访与采样、status 的有效性与中位数、recommendation 的聚合查询。用固定时间测试过期，不用等待 30 秒。
- [x] 实现 frontend/src/api.ts、types.ts、router.ts、各页面和样式。入口按角色显示；服务端仍执行权限限制。轮询统一管理，失效指标按服务器时间与本地经过时间显示未知。
- [x] 编写 docs/contracts/initial-api.md、docs/data-structures.md、README.md、docs/progress.md，说明已完成任务、演示密码、启动及未实现范围。
- [x] 执行 `mvn test package`、`npm run build`，使用真实进程验证登录与查询。将结果写入进度文档。

本次在新建功能分支执行；遵循用户“仅项目整体走向需要询问”的约定，不增加重复审批。
