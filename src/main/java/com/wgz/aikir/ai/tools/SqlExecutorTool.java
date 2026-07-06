package com.wgz.aikir.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 执行器工具
 * 支持 AI 通过工具调用的方式执行 SQL 语句，验证数据库 Schema 是否正确
 */
@Slf4j
@Component
public class SqlExecutorTool extends BaseTool {

    @Tool("执行SQL语句（DML/DDL），验证数据库Schema是否正确，返回执行结果或查询数据")
    public String executeSql(
            @P("要执行的SQL语句（如 CREATE TABLE, SELECT, INSERT 等）") String sql,
            @P("数据库JDBC URL，格式：jdbc:mysql://localhost:3306/demo_db") String jdbcUrl,
            @P("数据库用户名") String username,
            @P("数据库密码") String password
    ) {
        log.info("执行 SQL: {}", sql);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {

            String trimmedSql = sql.trim().toUpperCase();

            if (trimmedSql.startsWith("SELECT") || trimmedSql.startsWith("SHOW") || trimmedSql.startsWith("DESC") || trimmedSql.startsWith("EXPLAIN")) {
                // 查询类语句，返回结果集
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    StringBuilder result = new StringBuilder();
                    // 输出列名
                    List<String> columnNames = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columnNames.add(metaData.getColumnName(i));
                    }
                    result.append("列: ").append(String.join(" | ", columnNames)).append("\n");
                    result.append("---\n");

                    // 输出数据行
                    int rowCount = 0;
                    while (rs.next() && rowCount < 50) {
                        List<String> rowValues = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String value = rs.getString(i);
                            rowValues.add(value != null ? value : "NULL");
                        }
                        result.append(String.join(" | ", rowValues)).append("\n");
                        rowCount++;
                    }
                    result.append("共 ").append(rowCount).append(" 行记录");
                    return result.toString();
                }
            } else {
                // 非查询类语句（DDL/DML），执行并返回影响行数
                int affectedRows = stmt.executeUpdate(sql);
                return "SQL 执行成功，影响行数: " + affectedRows;
            }
        } catch (Exception e) {
            String errorMsg = "SQL 执行失败: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        }
    }

    @Override
    public String getToolName() {
        return "executeSql";
    }

    @Override
    public String getDisplayName() {
        return "执行 SQL";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String sql = arguments.getStr("sql");
        return String.format("[工具调用] 执行 SQL:\n```sql\n%s\n```", sql);
    }
}
