-- ============================================================
-- Kircevy AI · 增量迁移脚本
-- 作用：为已存在的 app 表新增 dockerDeployUrl 字段
-- 场景：一阶段补全部署闭环，双模式部署（Docker 一键部署模式记录访问地址）
-- 日期：2026-07-21
-- ============================================================

USE kircevy_ai;

-- app 表新增 dockerDeployUrl 字段（Docker 部署访问地址，仅 docker_compose 模式下有值）
ALTER TABLE `app`
    ADD COLUMN `dockerDeployUrl` varchar(512) NULL COMMENT 'Docker 部署访问地址（仅 docker_compose 模式）'
    AFTER `deployedTime`;
