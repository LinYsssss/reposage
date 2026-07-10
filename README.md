# RepoSage 智能代码审查平台

RepoSage 以 Git Commit / Pull Request 的 Diff 为输入，提供**内置规则审查、可选轻量模型风险信号和项目知识库（RAG）上下文**，通过 Mock 或 OpenAI 兼容模型生成结构化审查报告：风险等级、问题定位、证据来源和修复建议。

当前已经可以端到端运行的是**经典 Commit 审查与手工 PR 工作流**；在此之上，项目正在演进为可恢复的 PR 守门 Agent。GitHub/GitLab webhook、Agent 状态机与预算、Outbox 数据结构、独立 sandbox-runner 和结果发布器等基础组件已经落地，但 Agent 步骤执行、Outbox 派发、后端到沙箱的真实调用以及新 SCM 流程的自动回写仍在接线中。

它适合作为「AI 应用 + 后端工程实践」的写实示例项目——既能接真实大模型，也能在**无 Key、无 PostgreSQL/RabbitMQ 的本地环境中演示经典审查流程**。

> 想快速理解整个系统，先读 [`docs/00_项目详细介绍.md`](docs/00_项目详细介绍.md)（端到端全景）。

---

## 它能做什么

- 绑定一个 Git 仓库，针对某次 commit 的改动自动审查。
- 上传项目知识库（业务规则、接口规范、数据库结构、历史 Bug、安全规范），让审查结合**项目自身的上下文**，而不是泛泛而谈。
- 输出带证据的 JSON 报告：每个问题标注严重程度、类别、文件、建议，并尽量引用知识库中的依据。
- Mock 模式用内置关键词/模式规则直接生成演示报告；真实模型模式可加入 FastAPI 轻量分类结果作为辅助风险信号，模型服务不可用时自动跳过。
- 使用**手工 PR 工作流**登记 PR、基于 head/base Diff 发起审查，并提交「通过 / 打回修改 / 风险豁免」等人工动作；兼容的旧 GitHub webhook 路径可在审查完成后回写 PR 评论。
- 接入 GitHub / GitLab 新 webhook 控制面，完成验签、delivery 去重、事件规范化和 AgentRun 创建；Agent 自动执行、沙箱派发与结果发布仍在集成。

### 当前成熟度

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 经典 Commit 审查 | **可用** | Git CLI → Diff 分片 → Mock 规则审查，或轻量模型/RAG 增强的真实模型审查 → 报告 |
| inline / RabbitMQ 审查 | **可用** | 开发环境可同步执行，生产可异步消费 |
| 手工 PR 工作流 | **可用** | 登记 PR、触发审查、关联报告与人工动作 |
| 旧 GitHub webhook 评论 | **可用** | 仅支持 GitHub PR comment |
| GitHub/GitLab 新 webhook | **入口已实现** | 验签、去重、规范化、创建 AgentRun |
| Agent 状态机 / 预算 / Outbox | **基础已实现** | Outbox 当前完成事务内写入，尚缺 dispatcher |
| Agent 步骤执行 / 启动恢复 | **待贯通** | `AgentStepConsumer`、`AgentRecoveryService` 仍是执行骨架 |
| sandbox-runner | **模块已实现** | runner 可独立验证；后端默认仍使用 `NoopSandboxToolGateway` |
| GitHub/GitLab publisher | **组件已实现** | 尚未接入新 SCM Agent 的自动完成链路 |
| Agent SSE | **通道骨架** | 当前以持久化 Timeline 查询为主要事实来源 |

---

## 核心特性

### 审查引擎
- **真实大模型审查**：通过 OpenAI 兼容接口接入大模型（已验证小米 MiMo），也可切换到任何 OpenAI 兼容服务。用 `RestClient` 直连，不依赖 Spring AI。
- **多路径审查**：Mock 模式用内置关键词/模式规则直接产出报告；真实模型模式会把可选轻量模型风险信号与 RAG 项目上下文拼入 Prompt，再结合原始 Diff 生成结论。它不是 AST/CFG 级 SAST 引擎。
- **全量上下文注入**：知识库不大或模型没有 embedding API 时，可显式启用 `RAG_FULL_CONTEXT=true`，直接把项目文档拼进 Prompt，**无需 embedding / 向量数据库**。应用默认仍是检索模式（`false`）。
- **大 Diff 分片审查**：改动很大时按文件拆分、分批调用大模型再合并问题，避免整体 Diff 被静默截断丢失代码。
- **可选向量检索**：知识库很大时，可切换到内存余弦检索或 PostgreSQL + pgvector 向量检索（需要 embedding API）。
- **Mock 规则审查**：纯 Java 关键词/模式规则直接生成本地演示报告，识别常见高危模式，不依赖外部模型服务。
- **轻量分类模型**：FastAPI + scikit-learn（TF-IDF + 逻辑回归）独立服务，对 Diff 做风险预判，结果作为信号注入审查上下文；模型加载失败自动回退规则匹配，服务不可用时后端优雅降级。

### 工程护栏
- **异步任务**：RabbitMQ 承载经典审查任务。消费失败后由应用把消息发到 TTL delay queue，TTL 到期通过 DLX 回投工作队列；达到最大重试次数后，再由应用主动发布到最终 dead queue。开发环境可用 inline 模式免装 MQ。
- **调用弹性**：大模型调用接入 Resilience4j 重试 + 熔断，仅对网络超时 / 5xx / 429 等瞬时故障重试，连续失败时快速失败避免雪崩。
- **安全**：登录 Token / Cookie 鉴权；Git accessToken、SCM 凭据、webhook secret 均用 AES-GCM 加密存储，接口不返回明文。
- **可观测**：`ai_call_log` 记录每次大模型 / embedding / 模型调用的类型、模型、耗时、输入输出长度、**Token 用量**、状态和错误；并通过 Micrometer 把审查时延 / 吞吐 / Token 成本暴露到 `/actuator/prometheus`。
- **工程护栏**：每请求 `X-Trace-Id` 贯穿日志（MDC）；按用户/IP 的**单实例内存限流**（超限返回 429 + `Retry-After`）；`/actuator/health` 深度探活（AI provider / 模型服务 / DB / 磁盘，细节仅鉴权可见）。
- **迁移可控**：生产 schema 由 Flyway 迁移管理（`V1`→`V6`），启动自动应用、`ddl-auto=validate` 校验；开发/测试走 H2 + Hibernate。

### PR 守门 Agent（基础组件已实现，自动闭环待贯通）
- **SCM 控制面已实现**：GitHub / GitLab 双 webhook（`/api/webhooks/scm/{github,gitlab}`），使用 constant-time HMAC / token 校验，按 installation 查找密钥，持久化 delivery 去重，并规范化为统一事件后创建 AgentRun。
- **Agent 基础已实现**：`agent_run` / `agent_step` 持久化、状态转换校验、时间/工具/模型/Token/成本预算、计划校验、事务内 Outbox 写入、Timeline 查询与指标。
- **独立 runner 已实现**：`sandbox-runner` 无入站 HTTP，包含 HMAC-SHA256 作业验签、防重放、命令白名单、路径围栏和受限 Docker 容器策略（`--network none`、只读根、非 root、`cap-drop ALL`、资源限额）。
- **结果发布器已实现**：提供 GitHub Check / PR comment 与 GitLab note / commit status 封装；暴露生成补丁内容时要求人工批准。
- **当前断点**：尚无 Outbox dispatcher；`AgentStepConsumer` 与启动恢复逻辑未执行真实流程；后端默认 `NoopSandboxToolGateway`；新 webhook 流程尚未自动推进到 sandbox 和 publisher。

> ⚠️ 单机 Docker Compose 仅用于**受控演示**，不是面向恶意多租户的强隔离方案。安全边界详见 [`docs/13_SCM接入与沙箱运维.md`](docs/13_SCM接入与沙箱运维.md)。

---

## 架构概览

```text
                          ┌─────────────── 控制面（持有全部密钥）───────────────┐
浏览器                     │                                                     │
  │                       │   Spring Boot 后端 :8080                            │
  ▼                       │     ├─ 审查引擎（规则 + 模型 + RAG + 大模型）        │
Nginx :80 ──► Vue 前端 ───┼──►  ├─ Agent 基础（状态机 / 预算 / Outbox 数据）       │
                          │     ├─ SCM 控制面（验签 / 去重 / 规范化 / publisher）   │
GitHub / GitLab ─webhook──┼──►  └─ 依赖：                                       │
                          │          ├─ PostgreSQL（业务数据，生产可启用 pgvector）
                          │          ├─ RabbitMQ（经典异步审查）
                          │          ├─ FastAPI 模型服务 :8000（轻量风险分类，可选）
                          │          ├─ 大模型 API（OpenAI 兼容，如 MiMo，可选）
                          │          └─ Git 仓库 / 本地演示仓库（git CLI clone & diff）
                          └───────────────────────┬─────────────────────────────┘
                                                  ┊ [待贯通] Outbox / Agent step /
                                                  ┊          SandboxToolGateway
                          ┌───────────────────────▼─────────────── 执行面（受信）──┐
                          │   sandbox-runner（模块已实现，无入站 HTTP）             │
                          │     └─ DockerSandboxExecutor ──► docker-socket-proxy   │
                          │             └─ docker run（--network none 受限临时容器）│
                          └───────────────────────────────────────────────────────┘
```

> 注：本项目**不使用 Redis**；Git 操作走 **git CLI**（非 JGit）；大模型走 **OpenAI 兼容 RestClient 直连**（非 Spring AI）。

---

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring Security、Spring Data JPA、Flyway、Resilience4j、Micrometer/Prometheus |
| 沙箱执行面 | 独立 Maven 模块 `sandbox-runner`（Spring Boot、RabbitMQ 消费者、Docker CLI，无 Web 端口） |
| 前端 | Vue 3、Vite（单页应用，捕获 `X-Trace-Id` 拼进错误提示） |
| 数据库 | H2（开发）、PostgreSQL 16（生产，可选 pgvector 扩展） |
| 消息队列 | RabbitMQ（经典审查任务已接入；Agent / 沙箱队列仍在贯通） |
| 大模型接口 | OpenAI 兼容 Chat API（MiMo 等），或内置 Mock |
| 检索 | 应用默认 memory 检索；小知识库可启用全量注入；pgvector 可选 |
| 轻量模型 | FastAPI、scikit-learn、joblib（TF-IDF + 逻辑回归） |
| 测试 | JUnit + Mockito、Testcontainers（PostgreSQL + RabbitMQ，无 Docker 自动跳过） |
| 部署 | Docker Compose、Nginx、Tecnativa docker-socket-proxy |

---

## 快速开始（本地，最少外部依赖）

开发环境默认使用 H2 内存库、Mock AI、inline 审查，**不需要安装 PostgreSQL / RabbitMQ，也不需要任何大模型 Key**。项目没有公开注册接口，因此需先显式配置一组仅用于本地开发的种子账号。

### 1. 启动后端

PowerShell：

```powershell
$env:SEED_ADMIN_USERNAME = 'admin'
$env:SEED_ADMIN_PASSWORD = 'change-me-local'
cd backend
mvn -s .mvn/settings.xml spring-boot:run
```

Bash：

```bash
cd backend
SEED_ADMIN_USERNAME=admin SEED_ADMIN_PASSWORD=change-me-local \
  mvn -s .mvn/settings.xml spring-boot:run
```

后端启动在 `http://localhost:8080`。只有同时配置 `SEED_ADMIN_USERNAME` 和 `SEED_ADMIN_PASSWORD` 时，`AuthSeedRunner` 才会创建种子账号；应用默认值均为空。`deploy/.env.example` 与冒烟脚本中的账号只是示例/脚本参数，不是应用默认账号。

### 2. 启动前端

```text
cd frontend
npm install --cache .npm-cache
npm run dev
```

打开 `http://localhost:5173`，使用你显式配置的种子账号登录。

### 3.（可选）训练并启动模型服务

```text
cd model-service
python scripts/train_model.py --version local-demo-v1
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

模型状态：`GET http://localhost:8000/model/status`。模型服务未启动时，后端会自动跳过模型预判，不影响审查。

---

## 接入真实大模型（以 MiMo 为例）

默认 `AI_PROVIDER=mock`（规则引擎模拟审查）。要用真实大模型，配置环境变量后重启后端即可，**不需要改任何代码**。

### 1. 确认你的模型名

```text
curl https://token-plan-cn.xiaomimimo.com/v1/models -H "Authorization: Bearer <你的KEY>"
```

选一个对话模型，例如 `mimo-v2.5-pro`。

### 2. 配置环境变量

```text
AI_PROVIDER=openai-compatible
LLM_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1
LLM_API_KEY=<你的KEY>
LLM_CHAT_MODEL=mimo-v2.5-pro
EMBEDDING_PROVIDER=mock
RAG_MODE=memory
RAG_FULL_CONTEXT=true
```

这样：对话审查走真实 MiMo；检索走**全量注入**（把项目文档直接拼进 Prompt，不依赖 embedding）；上传时仍会生成本地 mock 向量，但 full-context 审查不会使用这些向量做召回。

> **MiMo 没有 embedding 接口**，所以这里必须用全量注入而不是向量检索。这正是 `RAG_FULL_CONTEXT=true` 的用途。

---

## 配置项说明

所有配置通过环境变量注入，默认值见 `backend/src/main/resources/application.yml`，部署模板见 `deploy/.env.example`。

### 审查与检索

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `AI_PROVIDER` | `mock` | 审查提供方：`mock`（规则模拟）/ `openai-compatible`（真实大模型） |
| `LLM_BASE_URL` | 空 | 大模型 OpenAI 兼容地址，以 `/v1` 结尾 |
| `LLM_API_KEY` | 空 | 大模型 API Key |
| `LLM_CHAT_MODEL` | `mock-reviewer` | 对话模型名，如 `mimo-v2.5-pro` |
| `RAG_FULL_CONTEXT` | `false` | **`true` 时启用全量注入**，把项目全部知识库文档拼进 Prompt，不走向量检索 |
| `RAG_MAX_CONTEXT_CHARS` | `6000` | 全量注入时上下文最大字符数，超出截断 |
| `RAG_MODE` | `memory` | 向量存储/检索模式：`memory`（内存余弦）/ `pgvector`（数据库向量） |
| `RAG_TOP_K` | `5` | 向量检索返回的片段数（全量注入模式下不生效） |
| `EMBEDDING_PROVIDER` | `mock` | 向量化提供方：`mock`（本地确定性 token-hash 向量，适合演示）/ `openai-compatible`（真实 embedding API） |
| `REVIEW_INLINE` | `true` | `true` 同步审查（免 MQ）；`false` 走 RabbitMQ 异步 |
| `REVIEW_MAX_DIFF_CHARS` | `20000` | 单个 AI 调用的 Diff 字符预算（分片审查按此打包文件） |
| `REVIEW_MAX_TOTAL_DIFF_CHARS` | `200000` | 单任务存储的 Diff 总上限（安全边界） |
| `REVIEW_MAX_FILES` | `40` | 单任务最多审查的文件数，超出跳过并在报告中说明 |
| `MODEL_SERVICE_ENABLED` | `false` | 是否启用 FastAPI 轻量模型预判 |
| `MODEL_SERVICE_URL` | 空 | 模型服务地址，如 `http://model-service:8000` |

### 护栏与安全

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `SEED_ADMIN_USERNAME` / `SEED_ADMIN_PASSWORD` | 空 / 空 | 可选的首个管理员种子账号；两项均配置后才创建（无公开注册） |
| `JWT_SECRET` | `dev-secret-change-me` | 本地开发占位值；生产必须替换 |
| `TOKEN_ENCRYPT_KEY` | `dev-token-encryption-key-change-me` | AES-GCM 加密密钥；生产必须替换，用于 Git token / SCM 凭据 / webhook secret |
| `RATE_LIMIT_ENABLED` | `true` | 是否启用 `/api/**` 接口限流 |
| `RATE_LIMIT_REQUESTS` | `120` | 单时间窗内每用户/IP 允许的请求数，超出返回 429 |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | 限流时间窗长度（秒） |

### SCM 与沙箱

| 变量 | 默认 | 作用 | 位置 |
| --- | --- | --- | --- |
| `WEBHOOK_ENABLED` | `true` | 是否启用旧版 GitHub webhook 入口 | backend |
| `GITHUB_WEBHOOK_SECRET` | 空 | 兼容旧 `/api/webhooks/github` 单密钥路径；为空跳过校验（仅本地/演示） | backend |
| `WEBHOOK_POST_COMMENTS` | `true` | 审查完成后是否回写 PR 评论（需仓库存有 token） | backend |
| `GITHUB_API_BASE` | `https://api.github.com` | GitHub API 地址（可指向 Enterprise） | backend |
| `SANDBOX_SIGNING_SECRET` | 空 | 沙箱作业签名 HMAC 密钥（backend 与 runner 共享） | backend + runner |
| `SANDBOX_CACHE_ROOT` | `/cache` | 依赖缓存根目录 | runner |
| `DOCKER_HOST` | 空 | 指向 socket 代理 `tcp://docker-socket-proxy:2375` | runner |

> 新的 SCM 端点（`/api/webhooks/scm/*`）的安装级密钥（GitHub App 私钥 + webhook secret / GitLab 访问令牌 + token）加密存在 `scm_installation` 表，不走单一环境变量；`GITHUB_WEBHOOK_SECRET` 只服务于兼容用的旧单密钥路径。

---

## 检索模式怎么选

审查时需要把「项目上下文」喂给大模型。两种方式：

| 模式 | 配置 | 适合 | 是否需要 embedding API |
| --- | --- | --- | --- |
| **全量注入（小知识库推荐）** | `RAG_FULL_CONTEXT=true` | 知识库总量不大，或模型没有 embedding API | **不需要** |
| 向量检索（应用默认） | `RAG_FULL_CONTEXT=false` + `RAG_MODE=memory`/`pgvector` | 知识库较大，需要先筛选相关片段 | mock 可本地演示；更可靠的语义召回需真实 embedding |

**建议**：应用默认使用 `RAG_FULL_CONTEXT=false`；对小型知识库或仅有对话 API 的模型，可显式改成全量注入。它不依赖 embedding，但仍受 `RAG_MAX_CONTEXT_CHARS`（默认 6000 字符）限制；上下文持续增长后再切换真实 embedding + memory/pgvector 检索。

> 知识库太大怎么办？优先**精炼文档**（写成可校验的规则条目，而非长篇叙述），按 `docType` 分类、按项目隔离、删除过时内容。信息密度比体量更重要。

---

## 使用流程

1. 显式配置种子管理员并登录（无公开注册）。
2. 创建项目。
3. 绑定 Git 仓库（演示可用 `demo-repos/mall-order-service`）。
4. 上传知识库文档（Markdown / TXT），如 `security-policy.md`、`order-flow.md`。
5. 查询 Commit，选择要审查的提交。
6. 触发审查任务。
7. 查看审查报告：风险等级、问题列表、证据来源、修复建议。
8. 对问题提交人工反馈（确认 / 误报 / 备注）。
9. 在 PR 工作流中登记 Pull Request，基于 PR 触发审查，并按报告执行「通过 / 打回修改 / 风险豁免」。
10. 查看 AI 调用日志、MQ 日志与 Agent Run Timeline。

**验证大模型与全量注入是否生效**：报告中的问题应能引用到你上传文档里的具体内容（例如发货问题引用 `order-flow.md` 的支付校验规则），说明完整项目上下文确实进入了 Prompt。

---

## API 速查

统一前缀 `/api`。除下方「公开」标注外，均需请求头 `Authorization: Bearer <token>`（或登录 Cookie）。

| 模块 | 方法与路径 |
| --- | --- |
| 认证 | `POST /api/auth/login`（公开）、`GET /api/auth/me`、`POST /api/auth/logout` |
| 项目 | `POST /api/projects`、`GET /api/projects`、`GET/PUT/DELETE /api/projects/{projectId}` |
| 仓库 | `POST/GET/DELETE /api/projects/{projectId}/repository`、`GET .../commits`、`GET .../commits/{commitId}/diff` |
| PR 工作流 | `POST/GET/PUT /api/projects/{projectId}/pull-requests`、`POST .../pull-requests/{prId}/review-task`、`POST/GET .../pull-requests/{prId}/actions` |
| 知识库 | `POST/GET /api/projects/{projectId}/knowledge/documents`、`DELETE .../documents/{documentId}`、`POST .../knowledge/search` |
| 审查 | `POST/GET /api/projects/{projectId}/reviews/tasks`、`GET .../tasks/{taskId}`、`GET .../reviews/reports`、`GET .../reports/{reportId}` |
| 反馈 | `POST/GET /api/review-issues/{issueId}/feedback` |
| Agent Run | `GET /api/agent-runs/{runId}`（持久化 timeline）、`GET /api/agent-runs/{runId}/events`（SSE 通道骨架，尚未形成完整广播/重放） |
| MQ 日志 | `GET /api/mq/logs` |
| AI 日志 | `GET /api/ai/logs` |
| Webhook（公开，签名守门） | `POST /api/webhooks/github`（旧单密钥）、`POST /api/webhooks/scm/github`、`POST /api/webhooks/scm/gitlab` |

接口字段细节见 `docs/03_接口设计文档.md`。

---

## Docker 部署

```text
cd deploy
cp .env.example .env
# 编辑 .env：填入真实 LLM_API_KEY，改掉 DB_PASSWORD / JWT_SECRET / TOKEN_ENCRYPT_KEY / SANDBOX_SIGNING_SECRET 等默认值
docker compose up -d --build
```

如果是从 GitHub 新 clone 到服务器，先初始化演示仓库（让后端能 clone & diff）：

```text
bash scripts/init-demo-repo.sh
```

启动的 8 个服务：

| 服务 | 说明 |
| --- | --- |
| `postgres` | PostgreSQL 16 + pgvector |
| `rabbitmq` | RabbitMQ（含管理台） |
| `backend` | Spring Boot 控制面 |
| `model-service` | FastAPI 轻量模型服务 |
| `sandbox-runner` | 隔离执行面（**无对外端口**） |
| `docker-socket-proxy` | 过滤后的 Docker API，原始 socket 不进 runner |
| `frontend` | Vue 构建产物 |
| `nginx` | 反向代理入口 |

对外入口：前端 `http://服务器IP/`，健康检查 `http://服务器IP/actuator/health`，RabbitMQ 管理台 `http://服务器IP:15672`。

生产数据库结构由 **Flyway 迁移**管理（`backend/src/main/resources/db/migration/V*.sql`），后端启动自动应用尚未执行的迁移，并以 `ddl-auto=validate` 校验实体结构。`baseline-on-migrate` 允许对已有非空旧库平滑升级。详见 `docs/12_服务器部署与演示手册.md`、`docs/13_SCM接入与沙箱运维.md`。

---

## 构建与测试

### 兼容基线

| 组件 | 支持基线 |
| --- | --- |
| Java | 17 |
| Node | 20 LTS |
| Maven | 3.9+ |
| PostgreSQL | 16 + pgvector |
| RabbitMQ | 3.13 |
| Docker Compose | v2 |

### 演进状态

RepoSage 已经完成经典审查链路的工程化，并正在把 SCM、Agent 与沙箱组件连接成自动 PR 守门流程：

| 阶段 / 能力 | 状态 | 当前实现 |
| --- | --- | --- |
| Phase 1 生产化 | **已可用** | Micrometer/Prometheus、Resilience4j、Token 计量、Flyway、Testcontainers |
| Phase 2 分片审查 | **已可用** | `DiffSplitter` 按文件打包，`ReviewProcessor` 逐批审查并合并 |
| Phase 3 基础护栏 | **已可用** | `X-Trace-Id`、单实例内存限流、深度健康探活 |
| Agent 数据模型 / 状态机 / 预算 | **已实现组件** | run/step 持久化、转换校验、工具与模型预算、计划校验、指标 |
| Outbox 数据模型与事务内写入 | **已实现组件** | 可与 AgentRun 创建同事务提交；尚缺实际 dispatcher |
| Agent 步骤执行 / 启动恢复 | **待贯通** | Consumer 与 recovery 保留结构，但尚未推进真实任务 |
| GitHub/GitLab webhook 控制面 | **已实现入口** | 验签、delivery 去重、事件规范化、AgentRun 创建与旧 head 取代 |
| sandbox-runner | **独立模块已实现** | 协议、防重放、容器策略、只读工具；后端派发待接入 |
| GitHub/GitLab publisher | **已实现组件** | comment/check/note/status 封装；自动发布链路待接入 |
| SCM → Agent → sandbox → publisher | **待贯通** | 当前不能描述为端到端自动审查闭环 |

### 测试命令

后端（控制面：webhook / 契约 / run / 协议 / 工具 / 回写）：

```text
cd backend
mvn -s .mvn/settings.xml test
```

沙箱 runner（执行面：协议 / 容器策略 / 缓存 / 归档 / SSRF / 只读工具）：

```text
cd sandbox-runner
mvn -s ../backend/.mvn/settings.xml test
```

> 本机无 Docker 时，部分 Testcontainers / 平台相关用例会按测试条件跳过。当前 GitHub Actions 只执行 backend 与 frontend，尚未把 sandbox-runner 纳入 CI。

前端测试与构建：

```text
cd frontend
npm test
npm run build
```

一键本地验收（后端测试 + 前端测试 + 前端构建 + 模型服务检查 + 后端冒烟 + Docker 可用性检查）：

```text
.\scripts\verify-local.ps1
```

> 当前 CI（`.github/workflows/ci.yml`）在 push / PR 上执行 backend 的 `mvn verify`（生成 JaCoCo 报告）以及 frontend 的 `npm ci/test/build`。sandbox-runner 与 model-service 暂未进入这条工作流；前端现有测试属于轻量 smoke 保障，不是完整 UI/E2E 覆盖。

**数据库迁移规则**：生产 schema 变更必须新增不可变的 `V<N>__描述.sql`；已发布的迁移文件**绝不能再修改**（Flyway 以校验和锁定历史），只能追加新版本。

后端冒烟（跑通登录→建项目→绑仓库→传知识库→审查→报告→反馈全链路）：

```text
.\scripts\smoke-backend.ps1
```

---

## 常见问题

**没有 embedding API 怎么办？**
用全量注入（`RAG_FULL_CONTEXT=true`），完全不需要 embedding。MiMo 等只提供对话接口的厂商正适用这种方式。

**知识库太大、超过上下文怎么办？**
先精炼文档（规则条目化、按类型分、删冗余）。仍然过大时切换到向量检索（`RAG_FULL_CONTEXT=false` + `RAG_MODE=pgvector`），但这需要 embedding API。

**API Key 会不会被提交到 Git？**
不会。`.env` 和 `deploy/.env` 已在 `.gitignore` 中；仓库只跟踪不含真实 Key 的 `deploy/.env.example`。请把真实 Key 填进 `deploy/.env`，切勿写进 `.env.example`。

**RabbitMQ 日志为空？**
开发环境默认 `REVIEW_INLINE=true`，审查同步执行、不经过 MQ，所以 MQ 日志为空。设 `REVIEW_INLINE=false` 后才走 RabbitMQ。

**没有公开注册接口？**
是的。显式配置 `SEED_ADMIN_USERNAME` 与 `SEED_ADMIN_PASSWORD` 后，首个用户由 `AuthSeedRunner` 创建；两项默认均为空。这样可避免公网任意注册。

---

## 文档索引

| 文档 | 说明 |
| --- | --- |
| [`docs/00_项目详细介绍.md`](docs/00_项目详细介绍.md) | **当前实现全景**：定位、架构、审查引擎、成熟度与工程边界（建议先读） |
| [`docs/14_当前功能与实现状态说明.md`](docs/14_当前功能与实现状态说明.md) | **功能说明**：逐项解释当前能做什么、怎样运行、哪些 Agent 能力尚未贯通 |
| [`docs/01_系统架构设计说明书.md`](docs/01_系统架构设计说明书.md) | 系统架构设计；含部分早期口径，以当前代码和 `00` 文档为准 |
| [`docs/02_数据库设计说明书.md`](docs/02_数据库设计说明书.md) | 表结构与数据关系 |
| [`docs/03_接口设计文档.md`](docs/03_接口设计文档.md) | API 字段与约定 |
| [`docs/04_MQ与异步任务设计.md`](docs/04_MQ与异步任务设计.md) | RabbitMQ、重试、死信、幂等 |
| [`docs/05_RAG与AI审查设计.md`](docs/05_RAG与AI审查设计.md) | RAG/AI 设计；部分流程描述仍是历史方案 |
| [`docs/08_部署环境与配置清单.md`](docs/08_部署环境与配置清单.md) | 环境变量与配置 |
| [`docs/11_本地开发与联调手册.md`](docs/11_本地开发与联调手册.md) | 本地启动与联调 |
| [`docs/12_服务器部署与演示手册.md`](docs/12_服务器部署与演示手册.md) | 服务器部署与演示 |
| [`docs/13_SCM接入与沙箱运维.md`](docs/13_SCM接入与沙箱运维.md) | SCM 接入、签名沙箱、容器隔离与安全验证 |
| [`代码仓库智能审查平台_需求规格说明书.md`](代码仓库智能审查平台_需求规格说明书.md) | 需求规格说明 |

完整的文档状态与阅读顺序见 [`docs/README.md`](docs/README.md)。
