package com.wgz.aikir.multiagent.execution;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 汇总并行任务的失败原因，避免后一个失败被前一个失败覆盖。 */
@Component
public class TaskFailureSummary {

    public String summarize(String frontendFailure, String backendFailure) {
        List<String> failures = new ArrayList<>();
        if (frontendFailure != null && !frontendFailure.isBlank()) {
            failures.add("前端 Agent：" + frontendFailure);
        }
        if (backendFailure != null && !backendFailure.isBlank()) {
            failures.add("后端 Agent：" + backendFailure);
        }
        return String.join("；", failures);
    }
}
