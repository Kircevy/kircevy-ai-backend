# 工具调用代码内容流式输出设计

## 背景

当前 AI 调用 `writeFile` 工具写入代码文件时，代码内容不会流式输出，而是在工具执行完毕后由 `JsonMessageStreamHandler` 的 `TOOL_EXECUTED` 分支一次性拼接完整文件内容返回。用户看到的是"写完所有代码文件后才一口气输出"。

### 根因

langchain4j 的 `TokenStream` 生命周期中，`onPartialResponse` 只推送普通文本 token，工具调用参数走单独生命周期。项目 `AiCodeGeneratorFacade.processTokenStream` 未注册 `onPartialToolCall` 回调，导致工具参数（代码内容）无法流式输出；只在 `onToolExecuted` 后由 `generateToolExecutedResult` 一次性拼接完整内容。

### langchain4j 1.18.0 能力

官方 `TokenStream.onPartialToolCall(Consumer<PartialToolCall>)`（自 1.11.0 起，`@Experimental`）可逐 token 接收工具调用参数。`PartialToolCall` 含 `index`、`id`、`name`、`partialArguments`（增量片段）。OpenAI 流式实现 `OpenAiStreamingChatModel.handle()` 在每个 SSE chunk 到达时，从 `delta.toolCalls[].function.arguments` 提取增量并触发 `onPartialToolCall`，经 `AiServiceStreamingResponseHandler` 透传到用户注册的 handler。

## 目标

工具调用写入代码文件时，代码内容逐 token 流式输出到前端，而非工具执行后一次性输出。

## 设计

### 数据流

```
LLM SSE chunk
  → OpenAiStreamingChatModel.handle()
  → onPartialToolCall(PartialToolCall{index, name, partialArguments})
  → AiCodeGeneratorFacade: ToolPartialExtractor 累积 partialArguments，
    Jackson 流式解析提取 content 增量
  → 构造 ToolPartialMessage{toolName, index, partialContent, isFirst}
  → JsonMessageStreamHandler: TOOL_PARTIAL 分支透传
  → 前端逐 token 渲染代码
```

### 组件改动

#### 1. 新增 `StreamMessageTypeEnum.TOOL_PARTIAL`

值 `tool_partial`，描述"工具调用流式片段"。

#### 2. 新增 `ToolPartialMessage`（`ai/model/message/`）

字段：
- `type`：消息类型，固定 `TOOL_PARTIAL`
- `toolName`：工具名（如 `writeFile`）
- `index`：工具调用索引（区分同一轮多个工具调用）
- `partialContent`：本次新增的代码文本片段（已转义还原）
- `isFirst`：是否是该 index 的首个片段；`true` 时 `partialContent` 含文件头 + 代码开头

#### 3. 新增 `ToolPartialExtractor`（`ai/streaming/`）

职责：按 `index` 维护每个工具调用的累积缓冲，流式解析 JSON 提取 `content` 字段增量。

输入：`PartialToolCall`（含 `index`、`name`、`partialArguments`）
输出：本次新增的 `content` 文本片段（已转义还原），以及是否为首片段、文件名（若可解析）

核心逻辑：
- 维护 `Map<Integer, ToolCallBuffer>`，每个 buffer 含 `StringBuilder accumulated`（累积原始 JSON 片段）和状态机位置
- 新片段到达：追加到 `accumulated`，用 Jackson `JsonParser` 解析累积缓冲
- 跟踪 JSON 结构，定位 `content` 字段的 value 起始位置
- value 部分的原始字符（含转义）反转义后，与上次已输出位置对比，输出新增部分
- 首次输出时，尝试从已累积片段解析 `relativeFilePath`；若文件名尚未完整，文件头延迟到能解析出时输出

转义处理：手动反转义常见转义符（`\n`→换行、`\"`→`"`、`\\`→`\`、`\t`→制表符等），覆盖代码内容中可能出现的所有转义场景。

文件头格式：`[工具调用] 写入文件 {relativeFilePath}\n```{suffix}\n`，随后紧跟代码内容。

#### 4. 改造 `AiCodeGeneratorFacade.processTokenStream`

在 `tokenStream` 链上注册 `.onPartialToolCall(partial -> {...})`：
- 调用 `ToolPartialExtractor.extract(partial)`，获得新增 content 片段、是否首片段、文件名
- 构造 `ToolPartialMessage`，`JSONUtil.toJsonStr` 后 `sink.next`
- 异常时 `log.warn`，不中断主流

#### 5. 改造 `JsonMessageStreamHandler`

新增 `TOOL_PARTIAL` 分支：
- 解析 `ToolPartialMessage`
- 透传 `partialContent` 给前端
- 追加 `partialContent` 到 `chatHistoryStringBuilder`（用于持久化）

改造 `TOOL_EXECUTED` 分支：
- 不再调用 `generateToolExecutedResult` 输出完整代码
- 改为输出状态行：`[工具调用] 写入文件 {relativeFilePath} 成功`
- 状态行追加到 `chatHistoryStringBuilder`

#### 6. 改造 `FileWriteTool.generateToolExecutedResult`

改为只返回状态：`[工具调用] 写入文件 %s 成功`（不含代码内容）。

其他工具（`FileModifyTool` 等）的 `generateToolExecutedResult` 同理改造，只报状态不报内容，内容由 `TOOL_PARTIAL` 流式输出。

### 关键技术点

#### Jackson 流式解析策略

`ToolPartialExtractor` 为每个 `index` 维护累积缓冲。由于 JSON 不完整，标准 `JsonParser` 无法直接解析。实际策略：

1. 累积全部 `partialArguments` 到 `StringBuilder`
2. 用状态机跟踪 JSON 结构，识别是否进入 `content` 字段的 value 部分
3. 一旦进入 `content` value，记录已输出的字符位置
4. 新片段到达时，从上次位置开始，把 value 的原始字符（含转义）反转义后输出新增部分
5. 转义处理：遇到 `\` 开始转义序列，累积到完整转义符后反转义

状态机状态：
- `SEEK_KEY`：寻找字段名
- `IN_KEY`：在字段名引号内
- `SEEK_VALUE`：寻找 value 开始
- `IN_CONTENT_VALUE`：在 content 字段的 value 内（需处理转义）
- `IN_OTHER_VALUE`：在其他字段 value 内（跳过）

#### 首片段文件头

`isFirst=true` 时，先尝试从已累积片段解析 `relativeFilePath`。通常 JSON 字段顺序为 `relativeFilePath` 在前，文件名在前几个 token 就能解析出。若文件名尚未完整，文件头延迟到能解析出时输出，此时 `isFirst` 才置 `true`。

#### index 隔离

同一轮 AI 可能并行生成多个工具调用（如同时写 package.json 和 vite.config.js），每个工具调用有独立 `index`。`ToolPartialExtractor` 按 `index` 隔离缓冲和状态机，避免内容串台。

### 错误处理

- JSON 解析失败：`ToolPartialExtractor` 降级为不输出 `TOOL_PARTIAL`，`TOOL_EXECUTED` 仍报状态，代码内容不丢失（已写入文件）
- `onPartialToolCall` 回调异常：`log.warn`，不中断主流，`onToolExecuted` 仍正常触发
- 工具执行失败：`TOOL_EXECUTED` 报失败状态，`TOOL_PARTIAL` 已输出的代码内容保留（前端可见已生成但写入失败的代码）

### 测试

#### `ToolPartialExtractorTest`

- 模拟分片 JSON，验证 content 提取正确性
- 转义字符处理（`\n`、`\"`、`\\`、`\t`、Unicode 转义）
- 多文件 index 隔离（两个工具调用交替到达片段）
- 文件名延迟解析（`relativeFilePath` 跨多个片段）
- 不完整 JSON 不抛异常

#### `JsonMessageStreamHandlerTest`

- `TOOL_PARTIAL` 透传验证
- `TOOL_EXECUTED` 只报状态、不含代码
- `chatHistoryStringBuilder` 正确累积

## 影响范围

- 后端：`ai/model/message/`、`ai/streaming/`、`core/handler/`、`core/AiCodeGeneratorFacade.java`、`ai/tools/`
- 前端：需适配 `TOOL_PARTIAL` 消息类型，逐片段追加渲染代码
- 持久化：`chatHistoryStringBuilder` 累积 `TOOL_PARTIAL` 内容，历史记录仍含完整代码
<!-- @zbiti-ai:f:145:0dde90f1 -->
