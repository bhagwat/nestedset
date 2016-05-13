package com.ttnd.plugins

import com.ttnd.plugins.util.NestedSetMarker
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class NestedSet {
    static Log log = LogFactory.getLog(NestedSet.class)

    String setId
    Long lft
    Long rgt
    NestedSet parent
    String objectClass
    Long objectId

    static constraints = {
        parent nullable: true
    }

    static mapping = {
        setId index: 'nested_set_obj_idx,nested_set_lftrgt_idx'
        objectClass index: 'nested_set_lftrgt_idx,nested_set_obj_idx'
        objectId index: 'nested_set_obj_idx'
        lft index: 'nested_set_lftrgt_idx'
        rgt index: 'nested_set_lftrgt_idx'
        cache true
    }

    static NestedSet findNode(setId, NestedSetMarker object) {
        log.trace "NestedSet.findNode : ${setId}, object: ${object}"
        if (!object.id) {
            throw new RuntimeException("Object must have assigned its ID first: setID: ${setId}, object: ${object}")
        }
        (NestedSet) find("from NestedSet where setId=? and objectClass=? and objectId=?", [setId, object.class.name, object.id])
    }

    static List getChildren(String setId, NestedSetMarker parent, Integer offset, Integer max, Boolean returnNested = false) {
        String className = parent.class.name
        Map paginateParams = [offset: offset ?: 0]
        if (max != null) {
            paginateParams.max = max
        }
        String query = "select ${returnNested ? 'n' : 'o'} from ${className} o, NestedSet n ${parent == null ? '' : ', NestedSet nn'} where n.objectClass='${className}' and n.setId=? and o.id=n.objectId"
        List args = [setId]
        if (parent == null) {
            query += " and n.parent = null"
        } else {
            query += " and nn.objectClass='${className}' and nn.setId=? and nn.objectId=? and n.parent = nn"
            args += [setId, parent.id]
        }
        executeQuery("$query order by n.lft", args, paginateParams)
    }

    static Integer createNodesFromMap(Map params) {
        Map map = params.map
        String setId = params.setId
        Long lft = params.lft
        NestedSet parent = params.parent
        String objectClass = params.objectClass
        Integer numberOfNodesCreated = 0
        map.each { node, children ->
            NestedSet newNode = new NestedSet(setId: setId, lft: lft, rgt: lft, parent: parent, objectClass: objectClass, objectId: node.id).save()
            if (newNode.hasErrors()) {
                throw new RuntimeException("NestedSet:" + newNode.errors)
            }
            lft++
            Integer subtreeSize = createNodesFromMap(map: children, setId: setId, lft: lft, parent: newNode, objectClass: objectClass)
            Integer rgt = lft + 2 * subtreeSize
            newNode.rgt = rgt
            lft = rgt + 1
            numberOfNodesCreated += subtreeSize + 1
        }
        return numberOfNodesCreated
    }

    static void evictAll() {
        log.trace "evictAll NestedSet"
        NestedSet.withSession { session ->
            def keysToEvict = session.statistics.entityKeys.grep { it.entityName == 'NestedSet' }
            keysToEvict.each { session.evict(session.get('NestedSet', it.identifier)) }
        }
    }

    static void flush() {
        log.trace "Flush NestedSet"
        NestedSet.withSession { session ->
            session.flush()
        }
    }
}
