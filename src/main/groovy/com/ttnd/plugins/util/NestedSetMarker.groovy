package com.ttnd.plugins.util

import com.ttnd.plugins.NestedSet
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

trait NestedSetMarker<T> implements Identifier{
    static Log log = LogFactory.getLog(NestedSetMarker.class)

    abstract Long getId()

    static String getDefaultSetId() { 'DEFAULT' }

    String nestedSetToString() {
        this.class.name + ':' + this.id
    }

    static List<T> nestedSetGetRoots(Map params) {
        String setId = getDefaultSetId()
        NestedSet.getChildren(setId, this, params?.offset, params?.max)
    }

    void nestedSetMakeRoot() { nestedSetMakeRoot([:]) }

    void nestedSetMakeRoot(Map params) {
        String setId = getDefaultSetId()
        Integer position = params?.position
        log.trace "nestedSetMakeRoot ${nestedSetToString()}, position: ${position}"
        if (!this.hasProperty('nestedSetCheckMembershipBeforeAdding')) {
            def node = NestedSet.findNode(setId, this)
            if (node != null) {
                throw new RuntimeException("Object already in the nestedset: ${nestedSetToString()}")
            }
        }
        String className = this.class.name
        Long lft
        if (position == 0) {
            lft = 1
        } else if (position != null) {
            def nodes = NestedSet.getChildren(setId, this, position - 1, 1, true)
            if (nodes) lft = nodes[0].rgt + 1
        }
        if (lft == null) {
            lft = (executeQuery("select max(rgt) from NestedSet where objectClass='${className}' and setId=?", [setId])[0] ?: 0) + 1
        }
        Long rgt = lft + 1
        NestedSet.evictAll()
        NestedSet.executeUpdate("update versioned NestedSet set lft=lft+2, rgt=rgt+2 where objectClass='${className}' and setId=? and lft>=?", [setId, lft])
        def node = new NestedSet(setId: setId, lft: lft, rgt: rgt, objectClass: className, objectId: id).save(flush: true)
        if (node.hasErrors()) {
            throw new RuntimeException("NestedSet:" + node.errors)
        }
    }

    void nestedSetOnDelete(Map params) {
        if (hasProperty('nestedSetOnDeleteCleanup')) {
            log.trace "nestedSetOnDeleteCleanup in progress. Skipping deleting request"
            return
        }
        log.trace "nestedSetOnDelete ${nestedSetToString()}"
        String className = this.class.name
        List<NestedSet> nodes = NestedSet.executeQuery("select n from NestedSet n where n.objectClass='${className}' and n.objectId=?", [id])
        nodes.each { nestedSetRemove(setId: it.setId, _nestedNode: it) }
    }

    void nestedSetRemove(Map params) {
        log.trace "nestedSetRemove ${nestedSetToString()}"
        String setId = getDefaultSetId()
        String className = this.class.name
        def node = params?._nestedNode ?: NestedSet.findNode(setId, this)
        if (node == null) {
            throw new RuntimeException("Object not in the nestedset: ${nestedSetToString()}")
        }
        NestedSet.evictAll()
        NestedSet.executeUpdate("update NestedSet set parent=null where objectClass='${className}' and setId=? and lft>=? and rgt<=?", [setId, node.lft, node.rgt])
        NestedSet.executeUpdate("delete from NestedSet where objectClass='${className}' and setId=? and lft>=? and rgt<=?", [setId, node.lft, node.rgt])
        def n = node.rgt - node.lft + 1
        NestedSet.executeUpdate("update versioned NestedSet set lft=lft-? where objectClass='${className}' and setId=? and lft>=?", [n, setId, node.lft])
        NestedSet.executeUpdate("update versioned NestedSet set rgt=rgt-? where objectClass='${className}' and setId=? and rgt>=?", [n, setId, node.rgt])
    }

    void nestedSetAddChild(NestedSetMarker child) { nestedSetAddChild([child: child]) }

    void nestedSetAddChild(Map params) {
        String setId = getDefaultSetId()
        def child = params?.child
        Integer position = params?.position
        def childMap
        if (child instanceof Map) {
            childMap = child
            child = map2list(child, [])
            log.trace "nestedSetAddChild ${nestedSetToString()} <- [Map of ${child.size()} objects] ${position}"
        } else if (child instanceof List) {
            log.trace "nestedSetAddChild ${nestedSetToString()} <- [List of ${child.size()} objects] ${position}"
        } else {
            log.trace "nestedSetAddChild ${nestedSetToString()} <- ${child.nestedSetToString()} ${position}"
            child = [child]
        }
        NestedSet node = NestedSet.findNode(setId, this)
        if (node == null) {
            throw new RuntimeException("Object not in the nestedset: ${nestedSetToString()}")
        }
        Class clazz = this.class
        child.each { c ->
            log.trace "nestedSetAddChild  " + c.class + ">>>>> " + c.class
            def childClazz = c.class
            if (clazz != childClazz) {
                throw new RuntimeException("Incompatible classes for nestedset: ${clazz.name}, ${childClazz.name}")
            }
            if (!c.hasProperty('nestedSetCheckMembershipBeforeAdding')) {
                def childNode = NestedSet.findNode(setId, c)
                if (childNode != null) {
                    throw new RuntimeException("Object already in the nestedset: ${c.nestedSetToString()}")
                }
            }
        }
        String className = clazz.name
        Long lft
        if (position == 0) {
            lft = node.lft + 1
        } else if (position == null) {
            lft = node.rgt
        } else {
            def nodes = NestedSet.getChildren(setId, this, position - 1, 1, true)
            lft = nodes ? nodes[0].rgt + 1 : node.rgt
        }
        Long rgt = lft + 1
        NestedSet.evictAll()
        Integer childrenCount = child.size()
        NestedSet.executeUpdate("update versioned NestedSet set lft=lft+${2 * childrenCount} where objectClass='${className}' and setId=? and lft>=?", [setId, lft])
        NestedSet.executeUpdate("update versioned NestedSet set rgt=rgt+${2 * childrenCount} where objectClass='${className}' and setId=? and rgt>=?", [setId, lft])
        if (childMap != null) {
            NestedSet.createNodesFromMap(map: childMap, setId: setId, lft: lft, parent: node, objectClass: className)
        } else {
            child.each { c ->
                def childNode = new NestedSet(setId: setId, lft: lft, rgt: rgt, parent: node, objectClass: className, objectId: c.id).save()
                if (childNode.hasErrors()) {
                    throw new RuntimeException("NestedSet:" + childNode.errors)
                }
                lft += 2; rgt += 2
            }
        }
        NestedSet.flush()
    }

    void nestedSetMoveTo(params) {
        if (!(params instanceof Map)) {
            params = [parent: params]
        }
        String setId = getDefaultSetId()
        NestedSetMarker prnt = params?.parent
        Integer position = params?.position
        log.trace "nestedSetMoveTo ${nestedSetToString()} -> ${prnt?.nestedSetToString()} ${position}"
        NestedSet node = NestedSet.findNode(setId, this)
        if (node == null) {
            throw new RuntimeException("Object not in the nestedset: ${nestedSetToString()}")
        }
        def clazz = this.class
        String className = clazz.name
        Long lft
        if (prnt == null) {
            if (position == 0) {
                lft = 1
            } else if (position != null) {
                def nodes = NestedSet.getChildren(setId, this.class, position - 1, 2, true)
                if (nodes) {
                    if (nodes[0].parent == node.parent && node.lft <= nodes[0].lft) {
                        if (nodes.size() > 1) lft = nodes[1].rgt + 1
                    } else {
                        lft = nodes[0].rgt + 1
                    }
                }
            }
            if (lft == null) {
                lft = (NestedSet.executeQuery("select max(rgt) from NestedSet where objectClass='${className}' and setId=?", [setId])[0] ?: 0) + 1
            }
            node.parent = null
        } else {
            def parentNode = NestedSet.findNode(setId, prnt)
            if (parentNode == null) {
                throw new RuntimeException("Object not in the nestedset: ${prnt.nestedSetToString()}")
            }
            def parentClazz = prnt.class
            if (clazz != parentClazz) {
                throw new RuntimeException("Incompatible classes for nestedset: ${clazz.name}, ${parentClazz.name}")
            }
            if (node.lft <= parentNode.lft && parentNode.rgt <= node.rgt) {
                throw new RuntimeException("Can't move to subtree: ${nestedSetToString()}, ${prnt.nestedSetToString()}")
            }
            if (position == 0) {
                lft = parentNode.lft + 1
            } else if (position == null) {
                lft = parentNode.rgt
            } else {
                def nodes = NestedSet.getChildren(setId, prnt, position - 1, 2, true)
                if (nodes) {
                    if (nodes[0].parent == node.parent && node.lft <= nodes[0].lft) {
                        if (nodes.size() > 1) lft = nodes[1].rgt + 1
                    } else {
                        lft = nodes[0].rgt + 1
                    }
                    if (lft == null) lft = parentNode.rgt
                }
            }
            node.parent = parentNode
        }
        node.save(flush: true)
        NestedSet.evictAll()
        NestedSet.executeUpdate("update versioned NestedSet set lft=-lft, rgt=-rgt where objectClass='${className}' and setId=? and lft>=? and rgt<=?", [setId, node.lft, node.rgt])
        if (node.rgt > lft) {
            NestedSet.executeUpdate("update versioned NestedSet set lft=lft+? where objectClass='${className}' and setId=? and lft>=? and lft<=?", [node.rgt - node.lft + 1, setId, lft, node.rgt])
            NestedSet.executeUpdate("update versioned NestedSet set rgt=rgt+? where objectClass='${className}' and setId=? and rgt>=? and rgt<=?", [node.rgt - node.lft + 1, setId, lft, node.rgt])
        } else {
            NestedSet.executeUpdate("update versioned NestedSet set lft=lft-? where objectClass='${className}' and setId=? and lft>? and lft<?", [node.rgt - node.lft + 1, setId, node.rgt, lft])
            NestedSet.executeUpdate("update versioned NestedSet set rgt=rgt-? where objectClass='${className}' and setId=? and rgt>? and rgt<?", [node.rgt - node.lft + 1, setId, node.rgt, lft])
            lft -= node.rgt - node.lft + 1
        }
        NestedSet.executeUpdate("update versioned NestedSet set lft=?-lft, rgt=?-rgt where objectClass='${className}' and setId=? and lft<0", [lft - node.lft, lft - node.lft, setId])
    }

    T nestedSetGetParent() { nestedSetGetParent([:]) }

    T nestedSetGetParent(Map params) {
        String setId = getDefaultSetId()
        String className = this.class.name
        def tmp = executeQuery("""select o from ${className} o, NestedSet n, NestedSet nn
					where nn.objectClass='${className}' and nn.setId = ? and nn.objectId=?
					and n.objectClass=nn.objectClass and n.setId=nn.setId and o.id=n.objectId
					and n = nn.parent
					order by n.rgt""", [setId, this.id])
        tmp ? tmp[0] : null
    }

    List<T> nestedSetGetChildren() {
        nestedSetGetChildren([:])
    }

    List<T> nestedSetGetChildren(Map params) {
        String setId = getDefaultSetId()
        NestedSet.getChildren(setId, this, params?.offset, params?.max)
    }

    List<T> nestedSetGetAncestors() { nestedSetGetAncestors([:]) }

    List<T> nestedSetGetAncestors(params) {
        log.trace "this.class :::: " + this.class
        String setId = getDefaultSetId()
        String className = this.class.name
        log.trace "nestedSetGetAncestors className: $className, setId: $setId, id: $id"
        executeQuery("""select o from ${className} o, NestedSet n, NestedSet nn
					where nn.objectClass='${className}' and nn.setId = ? and nn.objectId=?
					and n.objectClass=nn.objectClass and n.setId=nn.setId and o.id=n.objectId
					and n.lft<nn.lft and nn.rgt<n.rgt
					order by n.rgt""", [setId, id])
    }

    List<T> nestedSetGetDescendants(params) {
        String setId = getDefaultSetId()
        String className = this.class.name
        executeQuery("""select o from ${className} o, NestedSet n, NestedSet nn
					where nn.objectClass='${className}' and nn.setId = ? and nn.objectId=?
					and n.objectClass=nn.objectClass and n.setId=nn.setId and o.id=n.objectId
					and nn.lft<n.lft and n.rgt<nn.rgt
					order by n.lft""", [setId, this.id])
    }

    NestedSet nestedMe(Map params) {
        String setId = getDefaultSetId()
        String className = this.class.name
        NestedSet.executeQuery("""select n from NestedSet n where n.setId = ? and n.objectId=? and n.objectClass=? """, [setId, this.id, className])[0]
    }

    List<NestedSet> descendantNestedSets(Map params) {
        String setId = getDefaultSetId()
        NestedSet me = nestedMe()
        Long left = me.lft
        Long right = me.rgt
        String className = this.class.name
        NestedSet.executeQuery("""select n from NestedSet n
					where n.objectClass='${className}' and n.setId = ? and n.lft>? and n.rgt<?
					order by n.lft""", [setId, left, right])
    }

    NestedSetTreeNode nestedSetGetAsTree() {
        Long start = System.currentTimeMillis()
        NestedSet me = nestedMe()
        def domainClass = this.class
        List<NestedSet> nestedSets = descendantNestedSets()
        Map<Long, NestedSetTreeNode> treeMap = new HashMap<Long, NestedSetTreeNode<T>>()
        for (NestedSet nestedSet : nestedSets) {
            NestedSetTreeNode treeNode = new NestedSetTreeNode()
            treeNode.nestedSet = nestedSet
            treeNode.object = domainClass.read(nestedSet.objectId)
            treeMap.put(nestedSet.id, treeNode)
        }
        NestedSetTreeNode meNode = new NestedSetTreeNode()
        meNode.nestedSet = me
        meNode.object = this
        treeMap.put(me.id, meNode)
        for (NestedSet nestedSet : nestedSets) {
            NestedSetTreeNode treeNode = treeMap.get(nestedSet.id)
            NestedSetTreeNode treeNodesParent = treeMap.get(nestedSet.parent.id)
            if (treeNodesParent != null) {
                treeNodesParent.children.add(treeNode)
            }
        }
        Long stop = System.currentTimeMillis()
        log.trace "TimeTaken: ${stop - start}ms"
        return treeMap.get(me.id)
    }

    static List map2list(Map map, List list) {
        map.each { node, children ->
            list << node
            map2list(children, list)
        }
        return list
    }
}
