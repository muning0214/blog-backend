# blog-backend

DevLog 个人博客的**后端**：Spring Boot 3.2 + MyBatis-Plus + MySQL。
负责鉴权、业务校验、文件存储与静态资源分发，不掺任何界面逻辑。

前端在 [blog-frontend](https://github.com/muning0214/blog-frontend)，
表结构与初始数据在 `blog-database`（数据库独立成模块）。

## 技术栈

| 用途 | 选型 |
|---|---|
| 框架 | Spring Boot 3.2 · JDK 17 |
| 持久层 | MyBatis-Plus 3.5（复杂查询写在 `resources/mapper/*.xml` 里） |
| 数据库 | MySQL 8，库名 `blog_db` |
| 令牌 | JJWT（HS256，无状态 JWT，默认 24 小时） |
| 密码 | Hutool 的 BCrypt |
| 文件 | 存本机磁盘，通过 `/uploads/**` 静态映射对外 |
| 第三方登录 | GitHub OAuth（不引 SDK，用 Spring 的 `RestClient` 手写三个调用） |

## 目录结构

```
src/main/java/com/devlog/
├── common/
│   ├── Result.java            统一响应体 { code, message, data }
│   ├── exception/             BusinessException + GlobalExceptionHandler
│   ├── jwt/                   JwtUtil / JwtInterceptor / CurrentUser
│   ├── oauth/                 GithubOauthClient · OauthStateStore
│   └── util/TextUtils         摘要截取、阅读时长估算、slug 生成
├── config/
│   ├── DevLogProperties       @ConfigurationProperties(prefix = "devlog")
│   ├── MyBatisPlusConfig
│   └── WebMvcConfig           拦截器注册 · 静态资源映射 · CORS
├── controller/                Article · Author · Auth · File · Settings · Tag
├── service/                   Article · Auth · Settings · Storage · Tag
├── mapper/                    6 个实体 Mapper（接口，继承 BaseMapper）
├── entity/                    与表一一对应的实体
├── dto/                       入参（带 jakarta.validation 注解）
└── vo/                        出参

src/main/resources/
├── application.yml            公开配置，不含任何机密
├── application-local.yml      本机机密（已被 .gitignore 忽略，需自己创建）
└── mapper/                    ArticleMapper.xml · ArticleTagMapper.xml · TagMapper.xml
```

分层的原则很简单：**Controller 只做参数校验与转发，业务规则全在 Service，
SQL 复杂到一定程度就下沉到 XML**，不在 Controller 里写任何判断。

## 快速启动

### 1. 准备数据库

```bash
mysql -u root -p < ../blog-database/01_schema.sql
mysql -u root -p < ../blog-database/02_seed.sql
```

> 上面的路径假设三个模块克隆在同一个父目录下。
> 只克隆了后端的话，从 `blog-database` 仓库取这两个文件即可 ——
> 它们只是纯 SQL，不依赖目录结构。

（若库是旧版本，还要跑一次 `04_oauth.sql` 补第三方登录需要的表与列。）

### 2. 创建本机配置

`src/main/resources/application-local.yml` **被 .gitignore 忽略，需要自己建**：

```yaml
spring:
  datasource:
    password: 你的MySQL密码
devlog:
  jwt:
    # HS256 至少 32 字节。不要用有语义的短句——泄露过一次就等于永久失效，只能换。
    secret: 至少32位的随机字符串
```

缺少 `devlog.jwt.secret` 或长度不足 32 字节时**应用会启动失败**，
这是刻意的：宁可启动不了，也不要悄悄跑在一个弱密钥上。

### 3. 启动

```bash
mvn spring-boot:run
```

访问 <http://localhost:8080/api/articles> 应能看到文章列表 JSON。

## 配置项

所有配置都有合理默认值，只有机密必须自己提供。

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `spring.datasource.password` | `DB_PASSWORD` | 无 | **必填** |
| `devlog.jwt.secret` | `JWT_SECRET` | 无 | **必填**，≥32 字节 |
| `devlog.jwt.expiration-ms` | — | 86400000 | 令牌有效期（24 小时） |
| `devlog.upload.dir` | — | `./uploads` | 文件落盘根目录 |
| `devlog.upload.max-image-bytes` | — | 6 MB | 单张图片上限 |
| `devlog.upload.max-file-bytes` | — | 20 MB | 单个附件上限 |
| `devlog.cors.allowed-origins` | `DEVLOG_CORS_ALLOWED_ORIGINS` | `http://localhost:5174,...` | 逗号分隔；**留空则不放开任何跨域** |
| `devlog.site.allow-registration` | — | `true` | 是否开放注册 |
| `devlog.github.client-id` | `GITHUB_CLIENT_ID` | 空 | 第三方登录，三项齐备才算启用 |
| `devlog.github.client-secret` | `GITHUB_CLIENT_SECRET` | 空 | 只写 local 配置 |
| `devlog.github.redirect-uri` | `GITHUB_REDIRECT_URI` | `http://localhost:5174/auth/github/callback` | 必须与 OAuth App 登记值完全一致 |
| `devlog.github.authorize-uri` / `token-uri` / `api-base` | — | GitHub 官方地址 | 可改指向本地桩服务，用于测试 |

> ⚠️ 多词属性用**环境变量**注入时必须用连写形式（`GITHUB_REDIRECTURI` 而不是 `GITHUB_REDIRECT_URI`）：
> 宽松绑定把下划线当分隔符，`REDIRECT_URI` 会被解析成 `redirect.uri` 而不是 `redirect-uri`。

## 接口一览

统一响应体 `{ "code": 200, "message": "success", "data": ... }`，
**失败时同时返回真实的 HTTP 状态码**（400/401/403/404/409/413/500/503）。

### 公开

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册，成功直接返回令牌 |
| POST | `/api/auth/login` | 邮箱密码登录 |
| GET | `/api/articles` | 文章分页：`keyword` / `tag` / `page` / `size` / `featuredOnly` |
| GET | `/api/articles/{slug}` | 详情（阅读量 +1） |
| GET | `/api/articles/{slug}/related` | 相关文章（**不**计阅读量） |
| GET | `/api/articles/{slug}/neighbours` | 上一篇 / 下一篇 |
| GET | `/api/tags` | 全部标签（含未使用的） |
| GET | `/api/tags/counts` | 标签 + 已发布文章数（数据库聚合） |
| GET | `/api/settings` | 站点配置（可能为 `null`） |
| GET | `/api/auth/github/enabled` | GitHub 登录是否可用 |
| POST | `/api/auth/github/authorize` | 发起 GitHub 登录 |
| POST | `/api/auth/github/callback` | 登录回调：`{ code, state }` → `{ token, user }` |
| GET | `/uploads/**` | 图片与附件，公开可读 |

### 需要令牌（`Authorization: Bearer <token>`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/me` | 当前用户 |
| PUT | `/api/auth/password` | 改密码；账号无密码时无需传 `old_password` |
| GET | `/api/auth/identities` | 已绑定的登录方式 |
| POST | `/api/auth/github/bind-authorize` | 发起绑定 |
| POST | `/api/auth/github/bind` | 绑定回调 |
| DELETE | `/api/auth/github/bind` | 解绑 |
| GET/POST/PUT/DELETE | `/api/author/articles[/{id}]` | 文章增删改查 |
| GET/POST/PUT/DELETE | `/api/author/tags[/{id}]` | 标签增删改查 |
| PUT | `/api/author/settings` | 保存站点配置 |
| POST / DELETE | `/api/author/files` | 上传 / 删除文件，`folder` 取 covers / images / attachments / avatars |

### ⚠️ 请求体字段必须用 snake_case

后端开了 Jackson 的 `SNAKE_CASE` 命名策略，它**同时作用于序列化和反序列化**。
请求体里写成驼峰（如 `coverPath`）**不会报错，而是被当成「这个字段没传」静默丢弃**。

多词字段目前有：`cover_path`、`old_password` / `new_password`、
`site_name` / `author_name` / `author_bio` / `about_md`。

这个坑真实发生过两次：封面上传成功却保存不上、改密码永远报「请输入新密码」。
两处都有回归用例钉住（见下方「自测」）。

## 关键设计说明

**为什么失败也要返回真实 HTTP 状态码**
早先的实现把所有异常都包成 HTTP 200，只在 `code` 字段里区分。结果是前端按
`error.response.status === 401` 判断登录失效的分支**永远不命中**——
令牌过期后用户既不跳登录页也不清凭据，只看到一堆报错，卡在后台页面上。

**身份以数据库为准，不完全信令牌**
`JwtInterceptor` 解析令牌后还会查一次库确认账号仍然有效。多一次查询换来的是：
账号被禁用或被删时立即生效，不用等令牌过期。

**列表查询不带正文**
`content` 是 `LONGTEXT`，列表页并不需要。分页查询的列在 `ArticleMapper.xml` 里显式列出，
详情接口才带正文。

**阅读量由后端原子自增**
读详情时执行 `UPDATE articles SET views = views + 1`（不是读出来加一再写回）。
`related` 与 `neighbours` 这两个派生请求不会重复计数。

**逻辑删除会占着唯一索引**
`articles.slug` 上有唯一键，而逻辑删除只是把 `deleted` 置 1——索引并不认这个标记。
所以删除时要**同时把 slug 改掉**释放它，否则「删掉旧文章、再用同样的 slug 建新的」会撞唯一键。

**`user_identities` 用物理删除**
这张表是「第三方身份 → 账号」的映射，核心约束是同一身份全局唯一。
若用逻辑删除，解绑再绑定会留下多行 deleted=1 的同一身份而直接撞唯一键。

**第三方登录的三条规则**（详见 [后端 README 的第三方登录章节](https://github.com/muning0214/blog-backend#可选接入-github-登录)）
1. 已绑定的身份直接登入对应账号
2. 未绑定，但 GitHub 返回的**已验证**邮箱命中已有账号 → 自动关联，不新建账号
3. 其余建一个**没有密码**的新账号（`users.password` 为 NULL）

第 2 条只认 `verified: true`。否则任何人在 GitHub 资料里填上你的邮箱（未验证也能填）
就能顶替你的账号——这是这类集成最常见的安全漏洞。

## 自测

```bash
# 1. OAuth 全链路（不需要真实 GitHub 凭据）
node tools/oauth/github-stub.cjs      # 另开一个终端：模拟 GitHub 的三个端点
node tools/oauth/oauth-verify.cjs     # 29 项断言，会自动启停后端两次

# 2. 表列与实体字段是否仍然一一对应（改过表结构后跑）
node ../blog-database/tools/check-entity-mapping.cjs
```

OAuth 那套工具的价值在于：**没有真实凭据也能把整条链路验证完**。
桩服务用不同的 code 扮演不同身份，一个桩覆盖了全新用户、二次登录、
邮箱可关联、邮箱未验证、没有邮箱、已被他人绑定等场景。

## 常见问题

**启动报端口被占用**
`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`

**启动报缺少 jwt secret / secret 太短**
照上面的「创建本机配置」补上。这是刻意的启动期校验。

**前端能打开但列表是空的、登录报「连不上后端服务」**
先确认 <http://localhost:8080/api/articles> 能返回 JSON。

**上传的图片 404**
确认 `uploads/` 目录存在（启动时会自动创建），且前端把 `/uploads` 代理到了 8080。

**登出之后令牌还能用**
这是无状态 JWT 的固有特性，登出只清前端凭据。要真正失效需要引入黑名单——
接口 `/api/auth/logout` 已经留好，加黑名单不用改前端。
