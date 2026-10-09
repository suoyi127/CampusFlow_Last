# 用户注册接口

`POST /api/auth/register` 允许匿名访问，但须先 `GET /api/auth/csrf` 获取令牌并携带同一会话 Cookie 和返回的令牌请求头。

JSON 输入：`{"username":"new_student","password":"自选密码"}`。用户名 3—64 位字母、数字、下划线；密码至少 8 字符且最多 72 UTF-8 字节。确认密码仅在前端比较，不传入服务器。

成功返回 201 与 AccountView，角色固定 USER、enabled=true；不返回密码或摘要，也不自动登录。客户端传入 role/enabled 不改变创建结果。用户名已存在返回 409 USERNAME_EXISTS，非法输入返回 400，无有效 CSRF 返回 403。

账号与 ACCOUNT_REGISTER 日志同事务提交，重名依赖唯一索引处理并发竞争。管理员账号继续通过现有服务器管理员接口创建。本次不增加数据库迁移。
