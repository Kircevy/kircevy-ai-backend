package com.wgz.aikir.multiagent.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证 M1 固定规划产物的结构门禁。 */
class StructuredPlanningValidatorTest {

    private final StructuredPlanningValidator validator = new StructuredPlanningValidator(new ObjectMapper());

    @Test
    void 应接受完整的产品规格和架构规划() {
        String productSpec = """
                {
                  "version":"1.0",
                  "summary":"博客系统",
                  "userRoles":["访客","管理员"],
                  "pages":[{"name":"首页","path":"/","features":["文章列表"]}],
                  "functionalRequirements":["文章管理"],
                  "acceptanceCriteria":["可创建文章"]
                }
                """;
        String architectureBundle = """
                {
                  "architecture": {
                    "version":"1.0",
                    "technologyStack":{"frontend":"Vue3","backend":"Spring Boot","database":"MySQL"},
                    "modules":[{"name":"文章模块","responsibility":"管理文章","dependsOn":[]}],
                    "dataModels":[{"name":"Article","fields":[{"name":"id","type":"Long","required":true}]}]
                  },
                  "apiContractYaml":"openapi: 3.0.3\\ninfo:\\n  title: 博客接口\\n  version: 1.0.0\\npaths:\\n  /articles:\\n    get:\\n      responses:\\n        '200':\\n          description: 成功",
                  "taskManifest": {
                    "version":"1.0",
                    "tasks":[
                      {"taskKey":"frontend_generation","role":"FRONTEND_AGENT","dependsOn":[],"inputArtifacts":["product-spec.json"],"outputArtifacts":["frontend/**"],"writeScopes":["frontend/**"]},
                      {"taskKey":"backend_generation","role":"BACKEND_AGENT","dependsOn":[],"inputArtifacts":["api-contract.yaml"],"outputArtifacts":["backend/**"],"writeScopes":["backend/**"]}
                    ]
                  }
                }
                """;

        assertEquals("博客系统", validator.validateProductSpec(productSpec).get("summary").asText());
        assertEquals("Vue3", validator.validateArchitectureBundle(architectureBundle)
                .architecture().get("technologyStack").get("frontend").asText());
    }

    @Test
    void 应拒绝越权写入范围的任务清单() {
        String invalidBundle = """
                {
                  "architecture":{"version":"1.0","technologyStack":{"frontend":"Vue3","backend":"Spring Boot","database":"MySQL"},"modules":[{"name":"模块","responsibility":"职责","dependsOn":[]}],"dataModels":[]},
                  "apiContractYaml":"openapi: 3.0.3\\ninfo:\\n  title: 示例\\npaths:\\n  /items:\\n    get:\\n      responses:\\n        '200':\\n          description: 成功",
                  "taskManifest":{"version":"1.0","tasks":[
                    {"taskKey":"frontend_generation","role":"FRONTEND_AGENT","dependsOn":[],"inputArtifacts":[],"outputArtifacts":[],"writeScopes":["/**"]},
                    {"taskKey":"backend_generation","role":"BACKEND_AGENT","dependsOn":[],"inputArtifacts":[],"outputArtifacts":[],"writeScopes":["backend/**"]}
                  ]}
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> validator.validateArchitectureBundle(invalidBundle));
    }
}
