package com.ttnd.plugins

import com.ttnd.plugins.util.NestedSetPersistenceListener
import grails.plugins.Plugin
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class NestedsetGrailsPlugin extends Plugin {
    static Log log = LogFactory.getLog(NestedsetGrailsPlugin.class)
    def grailsVersion = "3.1.5 > *"
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]
    def title = "Nested Set"
    def author = "Bhagwat Kumar"
    def authorEmail = "bhagwat.kumar@tothenew.com"
    def description = '''\
                    Plugin NestedSet implements Nested Set data structure to store trees in a database
'''
    def documentation = "http://grails.org/plugin/nestedset"
    def license = "APACHE"
    def organization = [name: "TO THE NEW Digital", url: "http://wwww.tothenew.com/"]
    def developers = [[name: "Zmok s.r.o.", email: "contact@zmok.net"]]
    def issueManagement = [system: "Github", url: "https://github.com/bhagwat/nestedset/issues"]
    def scm = [url: "https://github.com/bhagwat/grails-nestedset"]

    def doWithApplicationContext = { applicationContext ->
        log.trace "Registering Persistent Listener: ${NestedSetPersistenceListener.class.name}"
        grailsApplication.mainContext.eventTriggeringInterceptor.datastores.each { k, datastore ->
            applicationContext.addApplicationListener new NestedSetPersistenceListener(datastore)
        }
    }
}
