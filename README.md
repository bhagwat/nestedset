# Nestedset plugin for Grails 3.x

[![Build Status](https://travis-ci.org/bhagwat/nestedset.svg?branch=master)](https://travis-ci.org/bhagwat/nestedset)

Grails Nestedset plugin implements Nested Set data structure to store trees in a database.

The plugin is a rewrite of a old Grails 1.x plugin.

## Installation

Add dependency in build.gradle

```groovy
compile "com.ttnd.plugins:nestedset:0.1"
```

### Create domain class which implements NestedSetMarker traits

```groovy
package com.ttnd.demo.nestedset

import com.ttnd.plugins.util.NestedSetMarker

class ProductCategory implements NestedSetMarker<ProductCategory> {
    String name

    public String toString() {
        "$name($id)"
    }

    ProductCategory(String name) {
        this.name = name
    }
}
```

## Bootstrap Nested set

```groovy
        ProductCategory root = new ProductCategory("root").save()
        ProductCategory child1 = new ProductCategory("Child 1").save()
        ProductCategory child2 = new ProductCategory("Child 2").save()
        ProductCategory child11 = new ProductCategory("Child 1.1").save()
        ProductCategory child12 = new ProductCategory("Child 1.2").save()
        ProductCategory child21 = new ProductCategory("Child 2.1").save()

        root.nestedSetMakeRoot()
        root.nestedSetAddChild(child1)
        root.nestedSetAddChild(child2)

        child1.nestedSetAddChild(child11)
        child1.nestedSetAddChild(child12)

        child2.nestedSetAddChild(child21)

```

## Access Nested set
```
package com.ttnd.demo.nestedset

class ApplicationController {
    static responseFormats = ['json', 'xml']

    def ancestor() {
        respond ProductCategory.list()
                .collectEntries { ProductCategory productCategory ->
            [
                    "${productCategory.name}": [
                            ancestors: productCategory.nestedSetGetAncestors()*.name,
                            children : productCategory.nestedSetGetChildren()*.name
                    ]
            ]
        }
    }

    def tree() {
        respond ProductCategory.read(1).nestedSetGetAsTree()
    }
}
```
