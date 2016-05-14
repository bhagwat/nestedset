package com.ttnd.plugins

import com.ttnd.plugins.util.Identifier
import com.ttnd.plugins.util.NestedSetMarker
import grails.compiler.GrailsCompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.Session
import org.hibernate.engine.spi.EntityKey

@GrailsCompileStatic
class NestedSet implements Identifier {
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

    static NestedSet findNode(String setId, NestedSetMarker object) {
        log.trace "NestedSet.findNode : ${setId}, object: ${object}"
        if (!object.id) {
            throw new RuntimeException("Object must have assigned its ID first: setID: ${setId}, object: ${object}")
        }
        NestedSet.find("from NestedSet where setId=? and objectClass=? and objectId=?", [setId, object.class.name, object.id])
    }

    static List getChildren(String setId, NestedSetMarker parent, Integer offset, Integer max, Boolean returnNested = false) {
        String className = parent.class.name
        Map paginateParams = [offset: offset ?: 0]
        if (max != null) {
            paginateParams.max = max
        }
        String query = "select ${returnNested ? 'n' : 'o'} " +
                "from ${className} o," +
                " NestedSet n ${parent == null ? '' : ', NestedSet nn'}" +
                " where n.objectClass='${className}' and n.setId=? and o.id=n.objectId"
        List<String> args = [setId]
        if (parent == null) {
            query += " and n.parent = null"
        } else {
            query += " and nn.objectClass='${className}' and nn.setId=? and nn.objectId=? and n.parent = nn"
            args += [setId, parent.id.toString()]
        }
        executeQuery("$query order by n.lft", args, paginateParams)
    }

    static Integer createNodesFromMap(Map params) {
        Map map = params.map as Map
        String setId = params.setId
        Long lft = params.lft as Long
        NestedSet parent = params.parent as NestedSet
        String objectClass = params.objectClass
        Integer numberOfNodesCreated = 0
        map.each { node, children ->
            NestedSet newNode = new NestedSet(setId: setId, lft: lft, rgt: lft, parent: parent, objectClass: objectClass, objectId: ((Identifier) node).id).save()
            if (newNode.hasErrors()) {
                throw new RuntimeException("NestedSet:" + newNode.errors)
            }
            lft++
            Integer subtreeSize = createNodesFromMap(map: children, setId: setId, lft: lft, parent: newNode, objectClass: objectClass)
            Long rgt = lft + 2 * subtreeSize
            newNode.rgt = rgt
            lft = rgt + 1
            numberOfNodesCreated += subtreeSize + 1
        }
        return numberOfNodesCreated
    }

    static void evictAll() {
        NestedSet.withSession { Session session ->
            session.statistics.entityKeys.grep { EntityKey entityKey ->
                entityKey.entityName == NestedSet.class.canonicalName
            }.each { EntityKey entityKey ->
                session.evict(session.load(NestedSet, entityKey.identifier))
            }
        }
    }

    static void flush() {
        NestedSet.withSession { Session session ->
            session.flush()
        }
    }
}
