# AgentScope Java 学习清单

当前 Demo 已覆盖：ReActAgent（同步调用）、Mock Tool（`get_app_status`）、DeepSeek 模型、Spring Boot 集成。

## 阶段 1：工具系统深入

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 1.1 | Tool 参数类型覆盖 | 给工具增加 `String`、`Enum`、`List<String>`、`boolean` 参数，观察 JSON Schema 生成 | pending |
| 1.2 | ToolResultConverter 自定义 | 写 `AppStatusConverter` 把原始 JSON 转成中文结构化文本后交给 LLM | pending |
| 1.3 | 多工具注册 | 新增 `build_project`、`deploy_project` 两个 Mock Tool，让 Agent 自主决定调用哪个 | pending |
| 1.4 | Tool Emitter 流式输出 | 用 `ToolEmitter` 实现工具执行过程流式推送 | pending |
| 1.5 | 真实 Service 接入 | 把 `AppMockTool` 替换为调用 `AppService` 的 `AppTool`，连接 MySQL 查询真实数据 | pending |

## 阶段 2：模型配置深入

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 2.1 | 切换模型 Provider | `QwenDashScopeChatModel` 替代 `OpenAIChatModel + DeepSeekFormatter` | pending |
| 2.2 | GenerateOptions 调参 | 实验 `temperature`、`topP`、`maxTokens` 对工具调用准确率的影响 | pending |
| 2.3 | 流式响应 | `stream(true)` + SSE Controller 推流给前端 | pending |
| 2.4 | 多模型路由 | 简单任务用 `qwen-turbo`，复杂推理用 `deepseek-reasoner` | pending |

## 阶段 3：Memory 系统

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 3.1 | InMemoryMemory 多轮对话 | 同一 Agent 实例连续调用 3 次，验证上下文保持 | pending |
| 3.2 | 自定义 Redis Memory | 使用项目已有 Redis 实现多会话隔离 | pending |
| 3.3 | LongTermMemory | 启用 LongTermMemory 存储用户偏好 | pending |

## 阶段 4：Hook 与可观测性

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 4.1 | Pre/PostReasoning Hook | 记录每次 LLM 推理的输入输出日志 | pending |
| 4.2 | Pre/PostActing Hook | 记录工具调用耗时，写入 Prometheus 指标 | pending |
| 4.3 | 限流 / 中断 | 实验 `stopAgent()` 在 Hook 中中断超长推理 | pending |

## 阶段 5：Structured Output

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 5.1 | 强制 JSON 输出 | 让 Agent 输出结构化 `AppStatusResult` 对象而非自由文本 | pending |
| 5.2 | Schema 校验 | 对返回结果做 JSON Schema 校验，不合格则重试 | pending |

## 阶段 6：多 Agent 与 Skill

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 6.1 | 双 Agent 协作 | `CodeAnalysisAgent` + `DeployAgent` 通过 SubAgent 互相调用 | pending |
| 6.2 | Skill 注册 | 封装可复用 Skill（如 `CodeGenSkill`） | pending |
| 6.3 | 与 LangGraph4j 对比 | 相同任务分别用 AgentScope ReAct + LangGraph4j Graph 实现，对比差异 | pending |

## 阶段 7：生产化

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 7.1 | 异步非阻塞 | `agent.call().subscribe()` + `SseEmitter` 流式推送 | pending |
| 7.2 | 异常处理标准化 | Agent 异常 → BusinessException → GlobalExceptionHandler 闭环 | pending |
| 7.3 | 连接池与超时 | HttpClient 连接池配置、超时重试策略 | pending |
| 7.4 | 成本统计 | token 消耗、工具调用次数、总耗时写入监控 | pending |

## 阶段 8：单元测试

| 序号 | 学习目标 | 具体做法 | 状态 |
|------|---------|---------|------|
| 8.1 | Agent 单测 | Mock Model 返回固定推理结果，验证工具调用逻辑 | pending |
| 8.2 | Tool 单测 | 验证 `@Tool` 方法 JSON Schema 生成正确性 | pending |
