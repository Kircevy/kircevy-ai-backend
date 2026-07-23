package com.wgz.aikir.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Generated project tree node exposed to the application workspace.
 */
@Data
public class CodeFileTreeNode {

    private String title;

    private String key;

    private boolean leaf;

    private List<CodeFileTreeNode> children;
}
