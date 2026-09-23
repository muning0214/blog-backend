/**
 * GitHub OAuth 桩服务。
 *
 * 目的：没有真实 OAuth App 凭据时，仍然能把整条链路（换令牌 → 读用户 → 读邮箱）
 * 端到端跑完。后端把三个地址做成可配置，就是为这个。
 *
 * 用 code 选择要扮演的 GitHub 用户，一个桩覆盖多种场景：
 *   同一个 id 的第二次登录、邮箱可关联、邮箱未验证、没有邮箱、已被他人绑定……
 */
const http = require('http')

/** 场景表：code → GitHub 会返回的身份 */
const SCENARIOS = {
  // 全新的 GitHub 用户，邮箱已验证
  'new-user': {
    id: 700001,
    login: 'newbie',
    name: '新来的',
    avatar_url: 'https://avatars.example/u/700001',
    emails: [{ email: 'newbie@example.com', primary: true, verified: true }],
  },
  // 同一个 GitHub 用户（id 与 new-user 相同），用于验证「二次登录不重复建号」
  'same-user': {
    id: 700001,
    login: 'newbie',
    name: '新来的',
    avatar_url: 'https://avatars.example/u/700001',
    emails: [{ email: 'newbie@example.com', primary: true, verified: true }],
  },
  // 已验证邮箱恰好是站内已有账号的邮箱 → 应自动关联，不新建账号
  'link-existing': {
    id: 700002,
    login: 'linker',
    name: '可关联',
    avatar_url: 'https://avatars.example/u/700002',
    emails: [{ email: 'admin@devlog.local', primary: true, verified: true }],
  },
  // 邮箱与已有账号相同，但 GitHub 标记为「未验证」→ 必须拒绝关联（账号顶替防护）
  'unverified-email': {
    id: 700003,
    login: 'sneaky',
    name: '未验证邮箱',
    avatar_url: 'https://avatars.example/u/700003',
    emails: [{ email: 'admin@devlog.local', primary: true, verified: false }],
  },
  // 没有开放任何邮箱 → 应走 noreply 兜底地址
  'no-email': {
    id: 700004,
    login: 'noemail',
    name: '',
    avatar_url: 'https://avatars.example/u/700004',
    emails: [],
  },
  // 用于绑定流程
  'bind-me': {
    id: 700005,
    login: 'binder',
    name: '待绑定',
    avatar_url: 'https://avatars.example/u/700005',
    emails: [],
  },
  // 已被别的账号绑定过，用于验证冲突
  'bind-occupied': {
    id: 700005,
    login: 'binder',
    name: '待绑定',
    avatar_url: 'https://avatars.example/u/700005',
    emails: [],
  },
}

/** 记录收到的请求，供断言「后端确实按规范发了参数」 */
const seen = []

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1')
  let body = ''
  req.on('data', (c) => (body += c))
  req.on('end', () => {
    const json = (code, payload) => {
      const text = JSON.stringify(payload)
      res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' })
      res.end(text)
    }

    seen.push({ method: req.method, path: url.pathname, auth: req.headers.authorization || '', body })

    // 换令牌
    if (req.method === 'POST' && url.pathname === '/login/oauth/access_token') {
      const form = new URLSearchParams(body)
      const code = form.get('code')
      if (!form.get('client_id') || !form.get('client_secret')) {
        return json(200, { error: 'invalid_client', error_description: '缺少 client 凭据' })
      }
      if (code === 'invalid-code') {
        return json(200, { error: 'bad_verification_code' })
      }
      return json(200, { access_token: `stub-token-${code}`, token_type: 'bearer', scope: 'read:user user:email' })
    }

    // 读用户 / 读邮箱：token 里带着场景名
    const m = /^Bearer stub-token-(.+)$/.exec(req.headers.authorization || '')
    const scenario = m ? SCENARIOS[m[1]] : null
    if (url.pathname === '/user' || url.pathname === '/user/emails') {
      if (!scenario) {
        return json(401, { message: 'Bad credentials' })
      }
      if (url.pathname === '/user') {
        const { emails, ...user } = scenario
        return json(200, user)
      }
      return json(200, scenario.emails || [])
    }

    // 授权页：桩里不需要真的渲染，访问到就返回 200
    if (url.pathname === '/login/oauth/authorize') {
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' })
      return res.end('stub authorize page')
    }

    // 供测试读取内部状态
    if (url.pathname === '/__seen') {
      return json(200, seen)
    }
    if (url.pathname === '/__reset') {
      seen.length = 0
      return json(200, { ok: true })
    }

    json(404, { message: 'not found: ' + url.pathname })
  })
})

const port = Number(process.env.STUB_PORT || 9099)
server.listen(port, '127.0.0.1', () => {
  console.log(`GitHub 桩服务已启动: http://127.0.0.1:${port}`)
  console.log('可用 code:', Object.keys(SCENARIOS).join(', '), '| invalid-code')
})
