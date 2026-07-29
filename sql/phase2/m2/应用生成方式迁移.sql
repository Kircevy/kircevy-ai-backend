alter table app
    add column generationStrategy varchar(32) default 'DIRECT' not null
        comment '生成方式：DIRECT/MULTI_AGENT' after codeGenType;
