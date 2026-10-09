# 评价接口约定（A04 / B03 / B04）

沿用会话登录与 CSRF：[首次集成接口](initial-api.md)。写操作携带 CSRF；角色互不继承。未登录 401、无权限 403、参数错误 400、不存在 404、版本或状态冲突 409。

## 接口

| 方法与路径 | 角色 | 用途 |
| --- | --- | --- |
| GET /api/spaces/{spaceId}/reviews | 已登录 | 当前通过评价及均分；page 默认 1，pageSize 默认 20，最大 100 |
| GET /api/user/reviews | USER | 本人评价分页，含停用空间历史 |
| GET /api/user/reviews/{spaceId} | USER | `{review: ReviewView或null, spaceEnabled: boolean}` |
| PUT /api/user/reviews/{spaceId} | USER | 提交或修改本人评价 |
| POST /api/user/reviews/{spaceId}/withdraw | USER | 撤回本人评价 |
| GET /api/data/reviews | DATA_ADMIN | 筛选评价分页 |
| POST /api/data/reviews/{id}/decision | DATA_ADMIN | 审核或撤销通过 |

提交请求：`{environmentScore: 4, facilityScore: 5, content: "说明", expectedVersion: 0}`。评分必须为 1—5 整数，拒绝小数；content 必填但可为空，最多 500 字符。首次 expectedVersion=0，后续使用服务器返回版本。

撤回请求：`{expectedVersion: 1}`。审核请求：`{action: "APPROVE", reason: "", expectedVersion: 1}`。action 支持 APPROVE、REJECT、REVOKE；后两者 reason 必须非空白，所有原因最多 500 字符。

本人及管理员分页可筛选 spaceId、status、updatedFrom、updatedTo、page、pageSize；管理员另支持 username 精确匹配。状态支持四种枚举；日期为 YYYY-MM-DD，按上海时区包含首尾整日，开始晚于结束拒绝。默认每页 20，最大 100。返回 `{items,total,page,pageSize,calculatedAt,warnings}`，按更新时间及 id 倒序。

## 状态、版本与字段

提交或重新提交 → PENDING；待审核可以 APPROVE → APPROVED 或 REJECT → REJECTED；已通过可以 REVOKE → REJECTED。本人可撤回未撤回的评价 → WITHDRAWN，并可重新提交同一记录。每次成功动作 version 加 1；写入前校验 expectedVersion，不一致返回 REVIEW_CHANGED。冲突应重新加载，不自动重试写操作。

ReviewView 包含 id、userId、username、spaceId、spaceName、spaceEnabled、environmentScore、facilityScore、content、status、reviewedBy、reviewerName、reviewedAt、reviewReason、updatedAt、version。未审核字段为 null；用户修改或撤回保留最近一次审核信息供追溯，公开资格只依据当前 status。

公开分页额外包含 `summary: {count,environmentAverage,facilityAverage}`。只纳入当前 APPROVED；count=0 时两项均分为 null。公开条目仅含 id、author（用户编号）、两项评分、content、updatedAt、reviewedAt。不公开账号名、审核人、审核原因或版本。空间卡片新增同结构 reviewSummary，均分不改变推荐得分。

停用空间禁止提交修改，公开评价查询返回 SPACE_DISABLED；本人仍可查看与撤回。写入按空间→评价顺序加锁，成功动作与审计日志同事务提交。时间返回 UTC ISO 8601，页面按上海时区显示。
