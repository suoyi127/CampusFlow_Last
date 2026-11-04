# 真实服务器管理接口

沿用 Cookie 会话和 CSRF，仅 SERVER_ADMIN 可访问；未登录或旧会话失效401，其他角色403。成功业务写入与审计同事务提交。

## 账号

GET /api/system/accounts：username（包含匹配）、role、enabled、page、pageSize；分页返回 items,total,page,pageSize,calculatedAt,warnings。
GET /api/system/accounts/{id}：获取当前 AccountView，编辑冲突重载使用。
POST /api/system/accounts：username,password,role,enabled。用户名3—64位字母数字下划线，密码至少8字符且最多72个UTF-8字节，角色USER/DATA_ADMIN/SERVER_ADMIN。密码使用BCrypt且不返回。
PUT /api/system/accounts/{id}：role,enabled,expectedVersion。角色或启停变化递增版本，使旧会话失效；不能停用或降权最后一个启用服务器管理员。

首次真实管理员由显式 CF_ADMIN_USERNAME/CF_ADMIN_PASSWORD 在空管理员库引导创建，不覆盖已有账号。不再创建固定演示账号；仍使用已公开演示口令且角色未改变的历史样例账号会被停用。

## 配置

GET /api/system/config：version,validitySeconds,noiseWindowSeconds,distanceWeight,quietWeight,freeWeight,facilityWeight。
PUT 同路径：全部六项参数加 expectedVersion。有效期5—300秒，噪声窗口5—600秒且不小于有效期；四项权重均为有限0—1数值且和为1。无效400，版本冲突409并保留客户端草稿。

默认有效期30秒、窗口60秒、权重.30/.30/.25/.15。配置保存到MySQL的RUNTIME行；不包含模拟采样周期，不控制硬件采样。修改窗口或有效期不会伪造新采样时间。

## 概况与设备

GET /api/system/overview：version,startedAt,accountCount,spaceCount,running,pollSeconds,dataSource,deviceCount,onlineDeviceCount,validitySeconds,noiseWindowSeconds。dataSource固定HARDWARE，统计来自实际数据库，pollSeconds=5。

GET /api/system/devices：实际hardware_device数组，每项id,spaceId（可空）,spaceName（可空）,deviceId,deviceCode,name,enabled,status,lastReceivedAt（可空）。status根据启用状态、实际接收时间和有效期计算，不允许人为设为在线。

原 /api/system/simulation/* 及 PUT /api/system/devices/{id} 已移除。

## 日志

GET /api/system/logs：actor、action（精确匹配）、updatedFrom/updatedTo（YYYY-MM-DD，上海日期首尾整日）、page、pageSize。条目id,actor,action,target,result,reason,occurredAt；只读、密码不写入日志。保留历史审计事实，不把历史模拟动作作为当前运行数据。
