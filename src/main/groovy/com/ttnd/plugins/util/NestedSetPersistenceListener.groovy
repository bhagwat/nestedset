package com.ttnd.plugins.util

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.springframework.context.ApplicationEvent

import static org.grails.datastore.mapping.engine.event.EventType.PostDelete

class NestedSetPersistenceListener extends AbstractPersistenceEventListener {
    static Log log = LogFactory.getLog(NestedSetPersistenceListener.class)

    NestedSetPersistenceListener(final Datastore datastore) {
        super(datastore)
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if (event.eventType == PostDelete) {
            onPostDelete(event)
        }
    }

    public void onPostDelete(final AbstractPersistenceEvent event) {
        log.trace "DELETED ${event.entity.class.name}:${event}"
        if (event.entity.metaClass.hasProperty(null, 'nestedSet')) {
            event.entity.nestedSetOnDelete()
        }
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> aClass) {
        return true
    }
}
