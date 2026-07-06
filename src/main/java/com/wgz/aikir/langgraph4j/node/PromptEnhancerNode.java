package com.wgz.aikir.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.wgz.aikir.langgraph4j.model.ImageResource;
import com.wgz.aikir.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

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
                enhancerPrompt.append("\n\n## 可用的图片资源");
                enhancerPrompt.append("请在生成网站使用一下图片资源，将这些图片合理地嵌入到网赚的相应为值中。\n");
                if (CollUtil.isNotEmpty(imageList)){
                    for (ImageResource image : imageList) {
                        enhancerPrompt.append(image.getCategory().getText())
                                .append("：")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                } else {
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
