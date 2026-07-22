-- ============================================================
-- Kircevy AI 增量迁移脚本
-- 作用：允许保存完整的 AI 生成结果与工具执行记录，避免 TEXT（64 KiB）截断。
-- 日期：2026-07-22
-- ============================================================

USE kircevy_ai;

ALTER TABLE `chat_history`
    MODIFY COLUMN `message` MEDIUMTEXT NOT NULL COMMENT '消息';
