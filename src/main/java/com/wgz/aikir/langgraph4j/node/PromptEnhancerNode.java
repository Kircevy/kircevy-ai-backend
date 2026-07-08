package com.wgz.aikir.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.wgz.aikir.langgraph4j.model.ImageResource;
import com.wgz.aikir.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.*;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class PromptEnhancerNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();
            StringBuilder enhancerPrompt = new StringBuilder();
            enhancerPrompt.append(originalPrompt);
            if (CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)){
                enhancerPrompt.append("\n\n## 图片\n");
                if (CollUtil.isNotEmpty(imageList)){
                    // 按类别分组，避免重复类别名
                    Map<String, List<ImageResource>> grouped = imageList.stream()
                            .collect(Collectors.groupingBy(
                                    img -> img.getCategory() != null ? img.getCategory().getText() : "其他",
                                    LinkedHashMap::new,
                                    Collectors.toList()
                            ));
                    for (Map.Entry<String, List<ImageResource>> entry : grouped.entrySet()) {
                        enhancerPrompt.append("[").append(entry.getKey()).append("]\n");
                        for (ImageResource image : entry.getValue()) {
                            // 紧凑格式：描述|URL
                            String desc = StrUtil.maxLength(image.getDescription(), 40);
                            enhancerPrompt.append("  ").append(desc)
                                    .append("|").append(image.getUrl()).append("\n");
                        }
                    }
                } else {
                    // imageListStr 已是外部传入的文本，直接追加
                    enhancerPrompt.append(imageListStr);
                }
            }
            String enhancedPrompt = enhancerPrompt.toString();
            // 更新状态
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度字符：{}", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}
