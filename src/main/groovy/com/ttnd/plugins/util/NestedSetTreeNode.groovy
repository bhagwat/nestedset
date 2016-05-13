package com.ttnd.plugins.util

import com.ttnd.plugins.NestedSet

class NestedSetTreeNode<T> {
    List<NestedSetTreeNode> children = new ArrayList<NestedSetTreeNode>()
    NestedSet nestedSet
    T object

    String renderString() {
        if (children.size() == 0) return "[" + nestedSet.id + "]"
        return "[" + nestedSet.id + ":" + children*.renderString().join(",") + "]"
    }
}
