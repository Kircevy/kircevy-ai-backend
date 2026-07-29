package com.wgz.aikir.multiagent.strategy;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.domain.enums.GenerationStrategyEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationStrategyPolicyTest {

    private final GenerationStrategyPolicy policy = new GenerationStrategyPolicy();

    @Test
    void nonFullstackApplicationsCanOnlyUseDirectGeneration() {
        MultiAgentProperties properties = enabledProperties();

        assertEquals(GenerationStrategyEnum.DIRECT,
                policy.validateSelection(CodeGenTypeEnum.VUE_PROJECT, GenerationStrategyEnum.DIRECT.name(), properties));
        assertThrows(BusinessException.class,
                () -> policy.validateSelection(CodeGenTypeEnum.VUE_PROJECT,
                        GenerationStrategyEnum.MULTI_AGENT.name(), properties));
    }

    @Test
    void fullstackApplicationsCanUseMultiAgentWhenEveryRequiredSwitchIsEnabled() {
        assertEquals(GenerationStrategyEnum.MULTI_AGENT,
                policy.validateSelection(CodeGenTypeEnum.FULLSTACK,
                        GenerationStrategyEnum.MULTI_AGENT.name(), enabledProperties()));
    }

    @Test
    void multiAgentSelectionFailsWhenExecutionIsDisabled() {
        MultiAgentProperties properties = enabledProperties();
        properties.setExecutionEnabled(false);

        assertThrows(BusinessException.class,
                () -> policy.validateSelection(CodeGenTypeEnum.FULLSTACK,
                        GenerationStrategyEnum.MULTI_AGENT.name(), properties));
    }

    @Test
    void executionUsesThePersistedApplicationStrategy() {
        App multiAgentApp = App.builder()
                .codeGenType(CodeGenTypeEnum.FULLSTACK.getValue())
                .generationStrategy(GenerationStrategyEnum.MULTI_AGENT.name())
                .build();
        App directApp = App.builder()
                .codeGenType(CodeGenTypeEnum.FULLSTACK.getValue())
                .generationStrategy(GenerationStrategyEnum.DIRECT.name())
                .build();

        assertTrue(policy.shouldStartMultiAgent(multiAgentApp, enabledProperties()));
        assertFalse(policy.shouldStartMultiAgent(directApp, enabledProperties()));
        assertThrows(BusinessException.class, () -> policy.requireMultiAgentSelected(directApp));
        policy.requireMultiAgentSelected(multiAgentApp);
    }

    private MultiAgentProperties enabledProperties() {
        MultiAgentProperties properties = new MultiAgentProperties();
        properties.setEnabled(true);
        properties.setPlanningEnabled(true);
        properties.setExecutionEnabled(true);
        return properties;
    }
}
