# 服务器管理接口（C04 / A05）

沿用会话 Cookie 和 CSRF。以下接口仅 SERVER_ADMIN；其他角色 403，未登录或旧会话失效 401。所有成功写入与审计记录同事务提交。场景选择、重置、设备控制及异常记录现已接入，见[模拟管理与数据检查接口](simulation-api.md)。

## 账号

GET `/api/system/accounts`：可选 username（包含匹配）、role、enabled、page、pageSize。默认 1/20，最大每页 100。返回 `{items,total,page,pageSize,calculatedAt,warnings}`，按 id 升序。

GET `/api/system/accounts/{id}`：按 id 精确返回 AccountView，编辑冲突重载使用此接口，避免包含查询和分页遗漏目标；不存在返回 404。

POST `/api/system/accounts`：`{username,password,role,enabled}`。username 为 3—64 个字母、数字或下划线；密码至少 8 字符，最多 72 UTF-8 字节；角色 USER/DATA_ADMIN/SERVER_ADMIN。使用 BCrypt，响应及审计均不输出密码。重复用户名 409。

PUT `/api/system/accounts/{id}`：`{role,enabled,expectedVersion}`。用户名不可更改。返回 AccountView：`{id,username,role,enabled,createdAt,version}`。首次 version=1；每次有效角色或启停变化递增。无变化保存不产生新版本或日志。不存在 404；旧版本 409；禁止停用或降权最后一位启用 SERVER_ADMIN，返回 LAST_ADMINISTRATOR。

登录时捕获账号版本；后续 API 请求先比对数据库版本及启用状态，再进行 CSRF 与角色判断。不匹配时清除会话、返回 SESSION_EXPIRED 401。重新启用仍不能恢复旧会话，须重新登录。管理写入先锁固定管理行，再核对操作者和目标版本，最后管理员统计使用锁定读。

## 配置

GET `/api/system/config`：返回 `{version,simulationSeconds,validitySeconds,noiseWindowSeconds,distanceWeight,quietWeight,freeWeight,facilityWeight}`。

PUT 同路径：传入全部七项参数与 expectedVersion，不传 version。模拟周期 2—60 秒；有效期 5—300 秒且至少为模拟周期的两倍；噪声窗口 5—600 秒且不小于有效期。时间参数必须整数；权重为 0—1 有限数值，总和为 1（浮点误差容限 1e-9）。参数不合法 400；版本不一致 CONFIG_CHANGED 409。

默认 5/30/60 与 .30/.30/.25/.15。配置整体保存到 RUNTIME 行，每次保存 version 加 1；模拟、状态与推荐在后续计算读取持久化配置。调度每秒检查当前周期，因此实际采样间隔包含调度与计算耗时；前端轮询固定 5 秒。修改有效期/窗口可能使已有样本重新有效或退出窗口，这属于新配置下的重新计算，不伪造新采样。

概况 GET `/api/system/overview` 新增 simulationSeconds；validitySeconds、noiseWindowSeconds 使用配置，pollSeconds=5。已有暂停/继续接口不变。

## 日志

GET `/api/system/logs`：可选 actor、action（精确匹配）、updatedFrom、updatedTo（YYYY-MM-DD，上海时区包含首尾整日）、page、pageSize。开始晚于结束拒绝 400。默认 1/20，每页最多 100，返回统一分页，按 occurredAt 及 id 倒序。

条目为 `{id,actor,action,target,result,reason,occurredAt}`。时间为 UTC ISO 8601。动作新增 ACCOUNT_CREATE、ACCOUNT_UPDATE、CONFIG_UPDATE；保留空间、模拟、评价既有记录。只读查询，无删除接口；当前记录成功业务动作，不宣称涵盖所有登录失败或异常请求。
