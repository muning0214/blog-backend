/**
 * GitHub OAuth 端到端验证。
 *
 * 阶段 A：后端带 GitHub 配置（指向本地桩服务）→ 跑完整登录/绑定/解绑链路
 * 阶段 B：后端不带 GitHub 配置 → 验证优雅降级（enabled=false、接口 503）
 *
 * 前置：github-stub.cjs 已在 9099 端口运行。
 */
const fs = require('fs')
const path = require('path')
const { execSync, spawn } = require('child_process')

const API = 'http://127.0.0.1:8080'
const STUB = 'http://127.0.0.1:9099'
const BACKEND = path.resolve(__dirname, '../..')
const MYSQL = process.env.MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'

const results = []
const pass = (n, e) => results.push(`PASS  ${n}${e ? '  ::  ' + e : ''}`)
const fail = (n, m) => results.push(`FAIL  ${n}  ::  ${m}`)
const assert = (c, m) => {
  if (!c) throw new Error(m)
}

async function call(method, p, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(API + p, { method, headers, body: payload })
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
  }
  return { status: res.status, json }
}

function runSql(sql) {
  const tmp = path.join(require('os').tmpdir(), 'oauth-verify.sql')
  fs.writeFileSync(tmp, sql, 'utf8')
  const out = execSync(`"${MYSQL}" -uroot -N --default-character-set=utf8mb4 < "${tmp}"`, {
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PWD || '' },
  })
  fs.rmSync(tmp, { force: true })
  return out.trim()
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms))

/**
 * 清掉上一轮测试留下的数据，保证脚本可重复执行。
 * 只删测试造出来的账号（noreply 兜底地址、样例邮箱）与全部第三方身份记录，
 * 种子账号 admin 本身保留 —— 只是把它身上的测试绑定解掉。
 */
function cleanup() {
  runSql(`
DELETE FROM blog_db.user_identities;
DELETE FROM blog_db.users
 WHERE email = 'newbie@example.com'
    OR email = 'no-oauth@example.com'
    OR email LIKE '%@users.noreply.github.com';
`)
  const left = runSql('SELECT COUNT(*) FROM blog_db.users')
  return left
}

const MVN =
  'C:/Users/陈聪/.m2/wrapper/dists/apache-maven-3.9.10-bin/53h08a94dg6djh6umvruv7q564/apache-maven-3.9.10/bin/mvn.cmd'

/**
 * 启动后端。
 *
 * 配置通过**环境变量**注入，而且多词属性必须用连写形式：
 *   DEVLOG_GITHUB_AUTHORIZEURI  -> devlog.github.authorize-uri   ✓
 *   DEVLOG_GITHUB_AUTHORIZE_URI -> devlog.github.authorize.uri   ✗（下划线会被当成点）
 * 宽松绑定把名字规范化后（去掉短横线、转小写）再比对，所以连写能被正确匹配。
 *
 * 不用命令行参数的原因：spawn 走 shell:true 时是 cmd.exe /s /c，嵌在里面的引号
 * 会被 cmd 的引号规则吃掉，`--devlog.github.*` 传不进去（踩过一次，表现为「参数没生效」）。
 *
 * SERVER__PORT 置空：那是本机环境注入的变量，Spring 会当成 server.port，
 * 不处理的话会去监听一个被占用的随机端口。
 */
function startBackend(extraEnv = {}, logFile = '.run.log') {
  const cmd = `"${MVN}" -B spring-boot:run -Dspring-boot.run.arguments=--server.port=8080 > ${logFile} 2>&1`
  return spawn(cmd, {
    cwd: BACKEND,
    env: { ...process.env, JAVA_HOME: 'E:\\jdk\\jdk_17', SERVER__PORT: '', ...extraEnv },
    shell: true,
    stdio: 'ignore',
    windowsHide: true,
  })
}

async function waitReady(child) {
  for (let i = 0; i < 40; i++) {
    await wait(2000)
    try {
      const r = await fetch(`${API}/api/articles`)
      if (r.status === 200) return child
    } catch {
      /* 还没起来 */
    }
  }
  throw new Error('后端未能在 80 秒内启动')
}

async function stopBackend(child) {
  try {
    execSync(`taskkill /F /T /PID ${child.pid}`, { stdio: 'ignore', shell: 'cmd.exe' })
  } catch {
    /* 已经没了 */
  }
  // 端口上可能还有 java 子进程
  try {
    const out = execSync('netstat -ano | findstr :8080 | findstr LISTENING', { encoding: 'utf8', shell: 'cmd.exe' })
    for (const pid of [...new Set(out.split(/\r?\n/).map((l) => l.trim().split(/\s+/).pop()).filter((p) => /^\d+$/.test(p)))]) {
      try {
        execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore', shell: 'cmd.exe' })
      } catch {
        /* ignore */
      }
    }
  } catch {
    /* 端口已释放 */
  }
  await wait(2500)
}

const step = async (name, fn) => {
  try {
    pass(name, await fn())
  } catch (e) {
    fail(name, (e && e.message) || String(e))
  }
}

async function main() {
  // 清空桩服务上一轮记录的请求，避免读到历史数据
  await fetch(STUB + '/__reset')
  const remaining = cleanup()
  console.log(`测试前清理完成，库中剩余用户数：${remaining}\n`)

  /* ================= 阶段 A ================= */
  const childA = await waitReady(
    startBackend(
      {
        DEVLOG_GITHUB_CLIENTID: 'stub-client-id',
        DEVLOG_GITHUB_CLIENTSECRET: 'stub-client-secret',
        DEVLOG_GITHUB_REDIRECTURI: 'http://localhost:5173/auth/github/callback',
        DEVLOG_GITHUB_AUTHORIZEURI: `${STUB}/login/oauth/authorize`,
        DEVLOG_GITHUB_TOKENURI: `${STUB}/login/oauth/access_token`,
        DEVLOG_GITHUB_APIBASE: STUB,
      },
      '.run-A.log',
    ),
  )

  // 先确认配置真的进去了，避免后面一堆断言跑在错误的前提上
  {
    const probe = await call('GET', '/api/auth/github/enabled')
    if (probe.json?.data !== true) {
      console.log('配置未生效，阶段 A 中止。后端日志尾部：')
      const log = path.join(BACKEND, '.run-A.log')
      if (fs.existsSync(log)) {
        console.log(
          fs
            .readFileSync(log, 'utf8')
            .replace(/\x1b\[[0-9;]*m/g, '')
            .split(/\r?\n/)
            .slice(-15)
            .join('\n'),
        )
      }
      await stopBackend(childA)
      process.exit(1)
    }
  }
  console.log('阶段 A：后端已启动，GitHub 配置已生效（指向本地桩）\n')

  let token = null
  let loginToken = null

  await step('配置 / enabled 为 true', async () => {
    const r = await call('GET', '/api/auth/github/enabled')
    assert(r.status === 200 && r.json.data === true, `HTTP ${r.status} data=${r.json?.data}`)
    return 'enabled=true'
  })

  await step('发起登录 / 返回授权地址且带上 client_id 与 state', async () => {
    const r = await call('POST', '/api/auth/github/authorize')
    assert(r.status === 200, `HTTP ${r.status}`)
    const { authorize_url: url, state } = r.json.data
    assert(url && url.startsWith(`${STUB}/login/oauth/authorize`), `地址不对：${url}`)
    // 解析后比较，而不是比编码后的字符串：空格可能被编成 %20 或 +
    const parsed = new URL(url)
    assert(parsed.searchParams.get('client_id') === 'stub-client-id', '缺少 client_id')
    assert(parsed.searchParams.get('state') === state, 'state 未拼进 URL')
    assert(parsed.searchParams.get('scope') === 'read:user user:email', `scope=${parsed.searchParams.get('scope')}`)
    assert(parsed.searchParams.get('redirect_uri') === 'http://localhost:5173/auth/github/callback', 'redirect_uri 不对')
    assert(!url.includes('stub-client-secret'), '授权地址里绝不能出现 client_secret')
    assert(state && state.length >= 32, `state 太短：${state}`)
    loginToken = state
    return `state 长度 ${state.length}，参数齐备且 secret 未出现`
  })

  await step('登录回调 / 全新 GitHub 用户建号并签发令牌', async () => {
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: loginToken },
    })
    assert(r.status === 200, `HTTP ${r.status}：${r.json?.message}`)
    token = r.json.data.token
    assert(token && token.split('.').length === 3, '返回的不是 JWT')
    assert(r.json.data.user.nickname === '新来的', `昵称应取自 GitHub：${r.json.data.user.nickname}`)
    assert(r.json.data.user.email === 'newbie@example.com', `邮箱：${r.json.data.user.email}`)
    assert(r.json.data.user.has_password === false, '第三方登录账号不应有密码')
    return `昵称=${r.json.data.user.nickname} 邮箱=${r.json.data.user.email} has_password=false`
  })

  await step('数据落库 / users 与 user_identities 各一条', async () => {
    const user = runSql("SELECT id, email, password IS NULL, nickname FROM blog_db.users WHERE email='newbie@example.com'")
    assert(user, 'users 里没有新账号')
    const [id, email, pwdNull, nickname] = user.split('\t')
    assert(pwdNull === '1', `password 应为 NULL，实为 ${pwdNull}`)
    const identity = runSql(`SELECT provider, provider_user_id, provider_username FROM blog_db.user_identities WHERE user_id=${id}`)
    assert(identity, 'user_identities 里没有记录')
    assert(identity.startsWith('github\t700001'), `身份记录不对：${identity}`)
    return `user_id=${id} password IS NULL · identity=700001`
  })

  await step('桩服务收到的换令牌请求符合规范', async () => {
    const seen = await (await fetch(`${STUB}/__seen`)).json()
    const tokenReq = seen.find((s) => s.path === '/login/oauth/access_token')
    assert(tokenReq, '桩服务没有收到换令牌请求')
    const form = new URLSearchParams(tokenReq.body)
    assert(form.get('client_id') === 'stub-client-id', 'client_id 不对')
    assert(form.get('client_secret') === 'stub-client-secret', 'client_secret 没发出来')
    assert(form.get('code') === 'new-user', 'code 没传')
    assert(form.get('redirect_uri') === 'http://localhost:5173/auth/github/callback', 'redirect_uri 没传')
    const userReq = seen.find((s) => s.path === '/user')
    assert(userReq && userReq.auth === 'Bearer stub-token-new-user', '读用户时没带 Bearer 令牌')
    return '四个必填参数齐备，读用户带了 Bearer 头'
  })

  await step('二次登录 / 同一个 GitHub 用户不重复建号', async () => {
    const before = runSql('SELECT COUNT(*) FROM blog_db.users')
    const a = await call('POST', '/api/auth/github/authorize')
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'same-user', state: a.json.data.state },
    })
    assert(r.status === 200, `HTTP ${r.status}`)
    const after = runSql('SELECT COUNT(*) FROM blog_db.users')
    assert(before === after, `用户数从 ${before} 变成 ${after}，说明重复建号了`)
    const first = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString()).sub
    const second = JSON.parse(Buffer.from(r.json.data.token.split('.')[1], 'base64').toString()).sub
    assert(first === second, `两次登录拿到的 user id 不同：${first} vs ${second}`)
    return `user_id 仍是 ${first}，用户总数未变（${after}）`
  })

  await step('账号关联 / 已验证邮箱命中已有账号时直接关联', async () => {
    const before = runSql('SELECT COUNT(*) FROM blog_db.users')
    const a = await call('POST', '/api/auth/github/authorize')
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'link-existing', state: a.json.data.state },
    })
    assert(r.status === 200, `HTTP ${r.status}`)
    assert(r.json.data.user.email === 'admin@devlog.local', `应登录到已有账号：${r.json.data.user.email}`)
    const after = runSql('SELECT COUNT(*) FROM blog_db.users')
    assert(before === after, `不应新建账号：${before} → ${after}`)
    // 管理员账号原本有密码，关联后仍然有
    assert(r.json.data.user.has_password === true, '原账号的密码不应被清掉')
    const linked = runSql("SELECT COUNT(*) FROM blog_db.user_identities WHERE provider='github' AND provider_user_id='700002'")
    assert(linked === '1', '身份没有绑定到已有账号')
    return `关联到 admin，用户数未变，原密码保留`
  })

  await step('【安全】未验证邮箱不得顶替已有账号', async () => {
    const before = runSql('SELECT COUNT(*) FROM blog_db.users')
    const a = await call('POST', '/api/auth/github/authorize')
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'unverified-email', state: a.json.data.state },
    })
    assert(r.status === 200, `HTTP ${r.status}`)
    assert(
      r.json.data.user.email !== 'admin@devlog.local',
      '未验证邮箱竟然关联上了已有账号 —— 这是账号顶替漏洞',
    )
    const after = runSql('SELECT COUNT(*) FROM blog_db.users')
    assert(Number(after) === Number(before) + 1, '应当新建独立账号')
    const linked = runSql("SELECT COUNT(*) FROM blog_db.user_identities WHERE provider='github' AND provider_user_id='700003'")
    assert(linked === '1', '新账号没有绑定身份')
    return `未关联 admin，另建了新账号（${r.json.data.user.email}）`
  })

  await step('邮箱兜底 / 没开放邮箱时用 GitHub noreply 地址', async () => {
    const a = await call('POST', '/api/auth/github/authorize')
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'no-email', state: a.json.data.state },
    })
    assert(r.status === 200, `HTTP ${r.status}`)
    assert(
      r.json.data.user.email === '700004+noemail@users.noreply.github.com',
      `兜底邮箱不对：${r.json.data.user.email}`,
    )
    assert(r.json.data.user.nickname === 'noemail', `name 为空时应回退到 login：${r.json.data.user.nickname}`)
    return r.json.data.user.email
  })

  await step('state 校验 / 无效 state 被拒 400', async () => {
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: 'not-a-real-state' },
    })
    assert(r.status === 400, `期望 400，实得 ${r.status}`)
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('state 校验 / 同一个 state 只能用一次', async () => {
    const a = await call('POST', '/api/auth/github/authorize')
    const first = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: a.json.data.state },
    })
    const second = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: a.json.data.state },
    })
    assert(first.status === 200, `第一次应成功，实得 ${first.status}`)
    assert(second.status === 400, `重放应被拒，实得 ${second.status}`)
    return '第一次 200，重放 400'
  })

  await step('state 校验 / 登录的 state 不能用于绑定', async () => {
    const after = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: loginToken },
    })
    assert(after.status === 400, `登录 state 被复用到绑定场景，实得 ${after.status}`)
    return '跨用途使用被拒 400'
  })

  await step('错误 code / 换取令牌失败返回 401', async () => {
    const a = await call('POST', '/api/auth/github/authorize')
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'invalid-code', state: a.json.data.state },
    })
    assert(r.status === 401, `期望 401，实得 ${r.status}`)
    assert(!JSON.stringify(r.json).includes('bad_verification_code'), '不应把 GitHub 的原始错误透出去')
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('参数校验 / 缺 code 或 state 返回 400', async () => {
    const a = await call('POST', '/api/auth/github/callback', { body: { state: 'x' } })
    const b = await call('POST', '/api/auth/github/callback', { body: { code: 'x' } })
    assert(a.status === 400 && b.status === 400, `应都是 400：${a.status}/${b.status}`)
    return '缺 code、缺 state 均为 400'
  })

  await step('无密码账号 / 邮箱密码登录给出与密码错误完全相同的提示', async () => {
    const r = await call('POST', '/api/auth/login', {
      body: { email: 'newbie@example.com', password: 'whatever-123' },
    })
    const wrong = await call('POST', '/api/auth/login', {
      body: { email: 'nobody@devlog.local', password: 'whatever-123' },
    })
    assert(r.status === 401, `期望 401，实得 ${r.status}`)
    assert(r.json.message === wrong.json.message, `提示不应有差异：${r.json.message} vs ${wrong.json.message}`)
    return `统一提示「${r.json.message}」`
  })

  await step('注册冲突 / 用第三方账号的邮箱注册时给出可行动提示', async () => {
    const r = await call('POST', '/api/auth/register', {
      body: { email: 'newbie@example.com', password: 'some-password-123' },
    })
    assert(r.status === 409, `期望 409，实得 ${r.status}`)
    assert(r.json.message.includes('GitHub'), `提示应引导去 GitHub 登录：${r.json.message}`)
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('身份列表 / 需要登录（匿名 401）', async () => {
    const r = await call('GET', '/api/auth/identities')
    assert(r.status === 401, `期望 401，实得 ${r.status}`)
    return `HTTP ${r.status}`
  })

  await step('身份列表 / 登录后能看到已绑定的 GitHub', async () => {
    const r = await call('GET', '/api/auth/identities', { token })
    assert(r.status === 200, `HTTP ${r.status}`)
    const github = r.json.data.find((i) => i.provider === 'github')
    assert(github, '列表里没有 github 身份')
    assert(github.provider_username === 'newbie', `用户名不对：${github.provider_username}`)
    return `provider=${github.provider} @${github.provider_username}`
  })

  await step('【回归】SNAKE_CASE 的坑：多词字段必须能落库（封面）', async () => {
    // 后端开了 Jackson 的 SNAKE_CASE 策略，反序列化也按 snake_case 匹配。
    // 前端若把 cover_path 写成 coverPath，字段会被静默丢弃 ——
    // 表现是「封面上传成功了，保存后却没了」。这条用例把它钉住。
    const created = await call('POST', '/api/author/articles', {
      token,
      body: {
        title: 'SNAKE_CASE 回归验证',
        content: '## 回归\n\n正文',
        cover_path: 'covers/regression-check.png',
        summary: '回归用例',
        tags: [],
        status: 'draft',
      },
    })
    assert(created.status === 200, `HTTP ${created.status}：${created.json?.message}`)
    const id = created.json.data.id
    assert(
      created.json.data.cover_path === 'covers/regression-check.png',
      `cover_path 没落库（实际 ${created.json.data.cover_path}）—— 请求体字段名大小写可能有问题`,
    )
    const reread = await call('GET', `/api/author/articles/${id}`, { token })
    assert(reread.json.data.cover_path === 'covers/regression-check.png', '重新读取时封面丢失')
    await call('DELETE', `/api/author/articles/${id}`, { token })
    return 'cover_path 写入并读回一致，测试文章已清理'
  })

  await step('绑定 / 已登录账号可绑定另一个 GitHub 身份', async () => {
    const a = await call('POST', '/api/auth/github/bind-authorize', { token })
    assert(a.status === 200, `bind-authorize HTTP ${a.status}`)
    const r = await call('POST', '/api/auth/github/bind', {
      token,
      body: { code: 'bind-me', state: a.json.data.state },
    })
    assert(r.status === 200, `HTTP ${r.status}：${r.json?.message}`)
    const cnt = runSql("SELECT COUNT(*) FROM blog_db.user_identities WHERE provider_user_id='700005'")
    assert(cnt === '1', '身份没有落库')
    return '已绑定 700005'
  })

  await step('绑定 / 已被他人绑定的 GitHub 身份返回 409', async () => {
    // 换一个账号来试绑 700005
    const adminLogin = await call('POST', '/api/auth/login', {
      body: { email: 'admin@devlog.local', password: 'admin123' },
    })
    const adminToken = adminLogin.json.data.token
    const a = await call('POST', '/api/auth/github/bind-authorize', { token: adminToken })
    const r = await call('POST', '/api/auth/github/bind', {
      token: adminToken,
      body: { code: 'bind-occupied', state: a.json.data.state },
    })
    assert(r.status === 409, `期望 409，实得 ${r.status}`)
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('解绑 / 唯一登录方式时被拒 400', async () => {
    // no-email 那个账号既没有密码，也只有一个身份 → 不允许解绑
    const a = await call('POST', '/api/auth/github/authorize')
    const login = await call('POST', '/api/auth/github/callback', {
      body: { code: 'no-email', state: a.json.data.state },
    })
    const t = login.json.data.token
    const r = await call('DELETE', '/api/auth/github/bind', { token: t })
    assert(r.status === 400, `期望 400，实得 ${r.status}`)
    assert(r.json.message.includes('密码'), `提示应给出解法：${r.json.message}`)
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('解绑 / 设置密码后即可解绑（且解绑后还能用密码登录）', async () => {
    const a = await call('POST', '/api/auth/github/authorize')
    const login = await call('POST', '/api/auth/github/callback', {
      body: { code: 'no-email', state: a.json.data.state },
    })
    const t = login.json.data.token
    const pwd = await call('PUT', '/api/auth/password', {
      token: t,
      body: { old_password: '', new_password: 'brand-new-pass-123' },
    })
    assert(pwd.status === 200, `设置密码失败 HTTP ${pwd.status}：${pwd.json?.message}`)
    const un = await call('DELETE', '/api/auth/github/bind', { token: t })
    assert(un.status === 200, `解绑失败 HTTP ${un.status}：${un.json?.message}`)
    const relogin = await call('POST', '/api/auth/login', {
      body: { email: '700004+noemail@users.noreply.github.com', password: 'brand-new-pass-123' },
    })
    assert(relogin.status === 200, `解绑后应能用密码登录，实得 ${relogin.status}`)
    return '设置密码 → 解绑成功 → 密码仍可登录'
  })

  await step('解绑 / newbie 解绑后只剩一种登录方式，第二次被守卫拦下', async () => {
    // 用 newbie：它有 GitHub 身份、但没有密码
    const a = await call('POST', '/api/auth/github/authorize')
    const login = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: a.json.data.state },
    })
    const t = login.json.data.token

    // 第一次：此时还绑着 700001 与 700005 两个身份 → 允许解绑
    const first = await call('DELETE', '/api/auth/github/bind', { token: t })
    assert(first.status === 200, `第一次解绑应成功，实得 ${first.status}：${first.json?.message}`)

    // 第二次：只剩一个身份且没有密码 → 应当被守卫拦下并告知解法。
    // 这里返回 400 比 404 更有信息量：账号确实还绑着一个身份，
    // 问题在于解绑后它会变成谁也进不去的孤儿。
    const second = await call('DELETE', '/api/auth/github/bind', { token: t })
    assert(second.status === 400, `第二次应被守卫拦下 400，实得 ${second.status}：${second.json?.message}`)
    assert(second.json.message.includes('密码'), `提示应告知解法：${second.json.message}`)

    // 解绑只解除映射，账号本身还在。再用同一个 GitHub 登录会被重新关联 ——
    // 这是期望行为：否则这个账号就永久失去了 GitHub 登录能力。
    const again = await call('POST', '/api/auth/github/authorize')
    const relogin = await call('POST', '/api/auth/github/callback', {
      body: { code: 'new-user', state: again.json.data.state },
    })
    assert(relogin.status === 200, `重新登录失败 HTTP ${relogin.status}`)
    const cnt = runSql("SELECT COUNT(*) FROM blog_db.user_identities WHERE provider_user_id='700001'")
    assert(cnt === '1', '再次登录时身份没有自动重新关联')
    return '第一次 200；第二次被守卫拦下并提示先设密码；再登录自动写回身份'
  })

  await step('解绑 / 完全没有绑定第三方时返回 404', async () => {
    // 用一个全新注册（没有任何第三方绑定）的账号，才能走到 404 分支
    const reg = await call('POST', '/api/auth/register', {
      body: { email: 'no-oauth@example.com', password: 'some-pass-123', nickname: '无绑定' },
    })
    assert(reg.status === 200, `注册应成功，实得 ${reg.status}：${reg.json?.message}`)
    const r = await call('DELETE', '/api/auth/github/bind', { token: reg.json.data.token })
    assert(r.status === 404, `期望 404，实得 ${r.status}：${r.json?.message}`)
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('绑定后 / 新身份也能用于登录同一账号', async () => {
    const a = await call('POST', '/api/auth/github/authorize')
    const r = await call('POST', '/api/auth/github/callback', {
      body: { code: 'bind-me', state: a.json.data.state },
    })
    assert(r.status === 200, `HTTP ${r.status}`)
    const sub = JSON.parse(Buffer.from(r.json.data.token.split('.')[1], 'base64').toString()).sub
    const originalSub = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString()).sub
    assert(sub === originalSub, `应登录到同一账号：${sub} vs ${originalSub}`)
    return `用绑定过的身份登录，仍回到 user_id=${sub}`
  })

  await stopBackend(childA)

  /* ================= 阶段 B ================= */
  // 不传任何 github.* 变量 → 走 application.yml 的空默认值 = 未配置
  const childB = await waitReady(startBackend({}, '.run-B.log'))
  console.log('阶段 B：后端已重启（未配置 GitHub）\n')

  await step('未配置 / enabled 返回 false', async () => {
    const r = await call('GET', '/api/auth/github/enabled')
    assert(r.status === 200, `HTTP ${r.status}`)
    assert(r.json.data === false, `应返回 false，实得 ${r.json.data}`)
    return 'enabled=false（前端据此隐藏按钮）'
  })

  await step('未配置 / 发起登录返回 503 而不是 500', async () => {
    const r = await call('POST', '/api/auth/github/authorize')
    assert(r.status === 503, `期望 503，实得 ${r.status}`)
    return `HTTP ${r.status} + "${r.json.message}"`
  })

  await step('未配置 / 原有邮箱密码登录不受影响', async () => {
    const r = await call('POST', '/api/auth/login', {
      body: { email: 'admin@devlog.local', password: 'admin123' },
    })
    assert(r.status === 200, `HTTP ${r.status}`)
    return '邮箱密码登录正常'
  })

  await stopBackend(childB)

  cleanup()
  console.log('\n测试后已清理测试数据')

  console.log(results.join('\n'))
  const fails = results.filter((r) => r.startsWith('FAIL')).length
  console.log(`\n共 ${results.length} 项，失败 ${fails} 项`)
  console.log(`RESULT = ${fails === 0 ? 'ALL_PASS' : 'HAS_FAILURES'}`)
  fs.writeFileSync(path.join(__dirname, 'oauth-report.txt'), results.join('\n'), 'utf8')
  process.exit(fails === 0 ? 0 : 1)
}

main().catch(async (e) => {
  console.error('FATAL', e)
  process.exit(1)
})
