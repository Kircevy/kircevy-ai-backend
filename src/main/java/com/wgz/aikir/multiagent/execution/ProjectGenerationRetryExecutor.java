package com.wgz.aikir.multiagent.execution;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 以完整项目校验结果驱动一次受控重新生成。 */
@Component
public class ProjectGenerationRetryExecutor {

    public <T> T execute(ProjectGenerator<T> generator) throws IOException {
        String previousFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            AttemptResult<T> result = generator.generate(attempt, previousFailure);
            if (result.success()) {
                return result.value();
            }
            previousFailure = result.failure();
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目生成失败：" + previousFailure);
    }

    @FunctionalInterface
    public interface ProjectGenerator<T> {
        AttemptResult<T> generate(int attempt, String previousFailure) throws IOException;
    }

    public record AttemptResult<T>(boolean success, T value, String failure) {
        public static <T> AttemptResult<T> succeeded(T value) {
            return new AttemptResult<>(true, value, "");
        }

        public static <T> AttemptResult<T> failed(String failure) {
            return new AttemptResult<>(false, null, failure);
        }
    }
}
