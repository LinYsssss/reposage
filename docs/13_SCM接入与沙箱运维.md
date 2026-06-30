# 13 SCM 接入与沙箱运维手册

> PR 守门 Agent Phase 3「SCM 与沙箱」。本手册覆盖：从已认证的 GitHub/GitLab 事件触发 Agent 运行，并在隔离的 runner 中执行仓库工具的接入、运维与安全验证。

## 1. 架构总览

```
GitHub/GitLab ──webhook──> backend (控制面)
                              │  验签 → 解析身份 → 选密钥 → 规范化 → 建 AgentRun(RECEIVED)
                              │  事务 Outbox ──MQ──> sandbox-runner (执行面，受信)
                              │                         │ 验签/防重放 → DockerSandboxExecutor
                              │                         └─ docker run（受限临时容器，--network none）
                              └──回写 PR/MR（GitHub Check/评论；GitLab note/status，补丁需审批）
```

- **backend 是控制面**，持有全部 SCM 凭据与 LLM 密钥；它只把「净化后的工作区归档引用 + 签名作业」交给 runner。
- **sandbox-runner 是受信执行面**，无入站 HTTP 端口，仅消费一条专用队列；它通过受限的 Docker socket 代理启动受约束的临时容器，被分析的仓库容器永远拿不到 Docker。
- 单机 Compose 仅用于**受控演示**，不是面向恶意多租户的隔离方案。

## 2. 安全边界（不可妥协）

1. 在 JSON 规范化之前，针对**原始请求字节**验签。
2. 先从载荷解析安装/项目身份，再据此选择密钥；**绝不**从载荷字段接受密钥或 provider 主机。
3. 仅允许配置内的 GitHub/GitLab API 主机与校验过的 HTTPS 归档 URL，防 SSRF。
4. SCM 凭据留在 backend；runner 只收到净化归档或临时对象引用。
5. 受信 runner 可在演示主机上访问 Docker；被分析的仓库容器**永不**获得 Docker socket。
6. 依赖准备与不可信测试是**分离**的作业；测试作业无网络。
7. GitHub 用 App 安装（App ID、加密私钥、webhook secret），不存长期 PAT；GitLab v1 用加密的项目访问令牌 + webhook secret。
8. provider 凭据支持轮换，且**绝不**被读 API 返回。

## 3. SCM 接入

### 3.1 安装（installation）

`scm_installation` 表保存一个 provider 绑定（迁移 `V6__scm_webhooks.sql`）：

| 列 | 说明 |
|---|---|
| `provider` | `GITHUB` / `GITLAB` |
| `external_installation_id` | GitHub App 安装 id 或 GitLab 项目 id（与 provider 组成唯一键） |
| `encrypted_webhook_secret` | webhook secret，AES-GCM 加密（`CryptoService`） |
| `encrypted_credential` | GitHub App 私钥或 GitLab 访问令牌，加密存储 |
| `api_base_url` | 回写用 API 主机，**仅**从此处解析 |
| `project_id` / `repository_id` | 绑定的内部 RepoSage 项目/仓库 |

密钥列均加密，且不应被任何读 API 返回；轮换 = 更新加密列。

### 3.2 Webhook 端点

| Provider | 路径 | 校验 | 事件 |
|---|---|---|---|
| GitHub | `POST /api/webhooks/scm/github` | `X-Hub-Signature-256` 常时 HMAC | `pull_request`：opened/reopened/synchronize/ready_for_review |
| GitLab | `POST /api/webhooks/scm/gitlab` | `X-Gitlab-Token` 常时比对 | `Merge Request Hook`：open/reopen/update |

两个端点都在已 `permitAll` 的 `/api/webhooks/**` 下（无 bearer，靠签名守门），并以 `202 Accepted` 异步确认。

### 3.3 幂等与去重

- 每条投递落 `scm_webhook_delivery`（唯一键 `(provider, delivery_id)`），仅存 payload 哈希 + 有界净化预览，不存原始私有载荷。
- 重复投递（同 `X-GitHub-Delivery` / 同 GitLab 事件 UUID 或确定性哈希）直接返回既有 Agent Run，不重复处理、不重复入队。

## 4. Webhook → Agent Run

`WebhookAgentRunService` 以确定性 `triggerKey = provider:install:pr:<N>:<headSha>` 为键：

- 同事件**幂等**：返回既有 run，不二次发布。
- **新 head SHA** 产生新 `RECEIVED` run，并把同 PR 的旧活跃 run 置 `CANCELED`（`failureType=SUPERSEDED`，经状态机校验）——只让最新 head 在途。
- run 创建与调度 Outbox 事件**同事务提交**；HTTP 侧据此立即回 202，仓库下载与 Agent 执行异步进行。

## 5. 签名沙箱作业协议

`SandboxJob`（job id、工作区归档引用、镜像摘要、命令 id、参数、资源限额、过期、nonce）由 `SandboxJobSigner` 以 **HMAC-SHA256 over 手写 canonical JSON**（排序键、无空格）签名——backend 与 runner 两模块逐字一致，golden 向量测试锁定跨模块兼容。验签拒绝：无效签名、已过期、重放（`SandboxReplayGuard` 按 nonce 去重）。

## 6. 容器执行策略

`ContainerPolicy` 生成的 `docker run` 强制：

```
--network none           # 无网络
--read-only              # 只读根文件系统
--user 65534:65534       # 非 root（nobody）
--cap-drop ALL --security-opt no-new-privileges
--pids-limit / --memory / --cpus / --stop-timeout   # 资源与超时
--tmpfs /work/.tmp:rw,noexec,nosuid,size=64m         # 唯一可写临时区
-v <workspace>:/work:ro  # 仓库只读挂载
```

- **命令白名单**：仅 `git.diff` / `git.file` / `code.search` 三个 id 可解析为可执行体；消息里的原始命令串一律拒绝。
- **路径围栏**：参数路径经规范化 + 符号链接解析后必须仍在工作区内。
- **清理幂等**：每次运行后及取消/超时时 kill + force-remove 容器，`execQuietly` 吞掉「容器不存在」错误。

## 7. 依赖缓存（隔离）

`DependencyPreparationPolicy` + `DependencyCacheManager`：

- Maven/Gradle/pip/npm/pnpm/yarn 锁文件 → **确定性、内容敏感**缓存键（同内容同键、按生态命名空间）。
- **依赖准备**是独立白名单作业（仅 `deps.prepare`），有大小/时间限额，**不**注入任何 SCM/LLM 密钥。
- **不可信测试作业**只读挂载缓存且保持 `--network none`。
- 缓存缺失返回 `ENVIRONMENT_INCOMPLETE`，**不**生成代码 finding（避免把缺工具链误报成代码缺陷）。

## 8. 仓库只读工具

- `SafeArchiveExtractor`：解压拒绝绝对路径、`..`、逃逸符号链接，限制总字节与条目数（防 zip bomb）。
- `RemoteResourceGuard`：归档/子模块 URL 仅允许 https 且解析到**公网**地址；环回/私网/链路本地（含 `169.254.169.254` 云元数据）一律拒绝。
- `RepositoryReadTools`：路径围栏内的有界文件读取与代码搜索，输出带 `truncated` 截断元数据。
- backend 侧 `git.diff`/`git.file`/`code.search` 为 `SANDBOXED` 工具，经 `SandboxToolGateway` 派发命令 id + 参数，自身不直接碰仓库。

## 9. 结果回写与审批

`GitHubReviewPublisher`（PR 评论 + Check Run）/ `GitLabReviewPublisher`（MR note + commit status）把中性的 `ReviewPublication`（摘要、阻断性 finding、证据链接、Agent Run URL、补丁校验状态）渲染为各 provider 载荷。**任何暴露生成补丁内容的回写都需审批**（`approved=true`），否则拒绝发送——补丁不经人就不落 provider。

## 10. 安全与集成验证

### 10.1 单元测试

```bash
mvn -f backend/pom.xml -q test          # 控制面：webhook/契约/run/协议/工具/回写
mvn -f sandbox-runner/pom.xml -q test   # 执行面：协议/容器策略/缓存/归档/SSRF/只读工具
```

本机无 Docker 时，Testcontainers 与符号链接用例**自动跳过**（不失败）。

### 10.2 Webhook → Run 集成测试（Testcontainers）

`ScmWebhookToRunIntegrationTest`：在**真实 PostgreSQL（Flyway 应用 V6）+ RabbitMQ** 上，发一条签名 GitHub 投递，断言生成 `RECEIVED` run、`PROCESSED` 投递与 Outbox 事件。`@Testcontainers(disabledWithoutDocker = true)`，无 Docker 跳过，CI 实跑。

### 10.3 容器隔离冒烟（需 Docker）

```bash
bash scripts/sandbox-smoke.sh
```

以与 runner 相同的加固参数启动一次性容器，逐项断言并期望「被阻断」：

| 检查 | 期望 |
|---|---|
| 网络出口 `wget http://example.com` | 阻断（`--network none`） |
| 云元数据 `169.254.169.254` | 阻断 |
| Docker socket `/var/run/docker.sock` | 不存在 |
| 根文件系统写 `touch /pwned` | 阻断（只读根） |
| 工作区写 `touch /work/pwned` | 阻断（只读挂载） |
| 工作区读 / `/work/.tmp` 写 | 成功（正向对照） |

全部通过则退出码 0。它证明：被分析容器无法访问 Docker socket、后端网络、云元数据地址或工作区外的主机路径。

### 10.4 Compose 级演示

```bash
cd deploy && docker compose up --build
```

- `docker-socket-proxy`（tecnativa）只暴露过滤后的 Docker API；原始 socket 不进 runner。
- `sandbox-runner` 无发布端口（无入站 HTTP），`DOCKER_HOST=tcp://docker-socket-proxy:2375`。

## 11. 密钥处理

- runner 环境**只**含 `SANDBOX_SIGNING_SECRET`（HMAC 签名密钥）；**无**任何 SCM token 或 LLM API key——这些只在 backend。
- 依赖准备作业明确禁用密钥（`DependencyPreparationPolicy.allowsSecret` 恒为 false）。
- runner 日志仅打印 `jobId` 与状态（`SandboxJobConsumer`），不打印作业密钥、签名或载荷；`ProcessContainerRuntime` 不记录命令明文密钥。
- SCM 凭据/webhook secret 加密入库，读 API 不返回。

## 12. 运维清单

| 环境变量 | 用途 | 位置 |
|---|---|---|
| `SANDBOX_SIGNING_SECRET` | 作业签名 HMAC 密钥（backend 与 runner 共享） | backend + runner |
| `SANDBOX_CACHE_ROOT` | 依赖缓存根目录（默认 `/cache`） | runner |
| `DOCKER_HOST` | 指向 socket 代理 `tcp://docker-socket-proxy:2375` | runner |
| `TOKEN_ENCRYPT_KEY` | SCM 凭据/webhook secret 加密密钥 | backend |
| `GITHUB_WEBHOOK_SECRET` | 兼容旧 `/api/webhooks/github` 单密钥路径 | backend |

| 队列/服务 | 说明 |
|---|---|
| `sandbox.job.queue` | runner 消费的专用作业队列（durable） |
| `agent_outbox_event` | 事务 Outbox，run 调度事件 |
| `sandbox_dep_cache`（卷） | 依赖缓存，准备作业写、测试作业只读挂载 |

---
_由 RepoSage Phase 3 实现。控制面/执行面分离、最小权限、默认拒绝。_
