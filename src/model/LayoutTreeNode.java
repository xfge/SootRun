package model;

import java.util.ArrayList;
import java.util.List;

public class LayoutTreeNode {
    private String id;
    private String className;
    private String type;
    private boolean clickable;

    public boolean isClickable() {
        return clickable;
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    private List<String> ancestors;
    private List<LayoutTreeNode> children;

    public LayoutTreeNode() {
        children = new ArrayList<>();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<String> getAncestors() {
        return ancestors;
    }

    public void setAncestors(List<String> ancestors) {
        this.ancestors = ancestors;
    }

    public List<LayoutTreeNode> getChildren() {
        return children;
    }

    public void addChild(LayoutTreeNode child) {
        children.add(child);
    }
}
