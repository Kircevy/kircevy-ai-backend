package com.wgz.aikir.service;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.domain.enums.GenerationStrategyEnum;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import com.wgz.aikir.multiagent.strategy.GenerationStrategyPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppGenerationStrategyServiceTest {

    private final AppService appService = mock(AppService.class);
    private final GenerationRunService generationRunService = mock(GenerationRunService.class);
    private final MultiAgentProperties properties = enabledProperties();
    private final AppGenerationStrategyService service = new AppGenerationStrategyService(
            appService, generationRunService, new GenerationStrategyPolicy(), properties);

    @Test
    void ownerCanSelectMultiAgentBeforeGenerationStarts() {
        App app = App.builder()
                .id(1L)
                .userId(9L)
                .codeGenType(CodeGenTypeEnum.FULLSTACK.getValue())
                .generationStrategy(GenerationStrategyEnum.DIRECT.name())
                .build();
        when(appService.getById(1L)).thenReturn(app);
        when(generationRunService.hasAnyRun(1L, 9L)).thenReturn(false);
        when(appService.updateById(any(App.class))).thenReturn(true);

        App updated = service.select(1L, GenerationStrategyEnum.MULTI_AGENT.name(), user(9L));

        assertEquals(GenerationStrategyEnum.MULTI_AGENT.name(), updated.getGenerationStrategy());
        ArgumentCaptor<App> captor = ArgumentCaptor.forClass(App.class);
        verify(appService).updateById(captor.capture());
        assertEquals(GenerationStrategyEnum.MULTI_AGENT.name(), captor.getValue().getGenerationStrategy());
    }

    @Test
    void strategyCannotChangeAfterAnyGenerationRunExists() {
        App app = App.builder()
                .id(1L)
                .userId(9L)
                .codeGenType(CodeGenTypeEnum.FULLSTACK.getValue())
                .generationStrategy(GenerationStrategyEnum.DIRECT.name())
                .build();
        when(appService.getById(1L)).thenReturn(app);
        when(generationRunService.hasAnyRun(1L, 9L)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.select(1L, GenerationStrategyEnum.MULTI_AGENT.name(), user(9L)));
        verify(appService, never()).updateById(any(App.class));
    }

    @Test
    void anotherUserCannotChangeTheStrategy() {
        App app = App.builder()
                .id(1L)
                .userId(9L)
                .codeGenType(CodeGenTypeEnum.FULLSTACK.getValue())
                .build();
        when(appService.getById(1L)).thenReturn(app);

        assertThrows(BusinessException.class,
                () -> service.select(1L, GenerationStrategyEnum.DIRECT.name(), user(10L)));
    }

    private User user(long id) {
        return User.builder().id(id).build();
    }

    private MultiAgentProperties enabledProperties() {
        MultiAgentProperties result = new MultiAgentProperties();
        result.setEnabled(true);
        result.setPlanningEnabled(true);
        result.setExecutionEnabled(true);
        return result;
    }
}
