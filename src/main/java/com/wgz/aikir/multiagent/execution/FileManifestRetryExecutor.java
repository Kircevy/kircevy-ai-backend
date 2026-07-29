package com.wgz.aikir.multiagent.execution;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/** 对格式不合法的文件清单进行一次带反馈的重新生成。 */
@Component
public class FileManifestRetryExecutor {

    public List<String> generate(ManifestGenerator generator, Function<String, List<String>> parser, String prompt)
            throws IOException {
        BusinessException validationException = null;
        String currentPrompt = prompt;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String rawManifest = generator.generate(currentPrompt, attempt);
            try {
                return parser.apply(rawManifest);
            } catch (BusinessException exception) {
                validationException = exception;
                if (attempt == 1) {
                    currentPrompt = prompt + "\n\n上一次文件清单未通过格式校验，原因："
                            + exception.getMessage() + "。请仅返回合法的 JSON 文件路径清单。";
                }
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                "执行 Agent 未返回合法文件清单：" + validationException.getMessage());
    }

    @FunctionalInterface
    public interface ManifestGenerator {
        String generate(String prompt, int attempt) throws IOException;
    }
}
