package com.github.kotlintubeexplode.internal

import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses XML into a DOM [Document] with an XXE-hardened parser (doctype declarations,
 * external entities, and external DTD loading are all disabled). Shared by the DASH
 * manifest parser, the closed-caption track parser, and channel-page meta-tag parsing.
 */
internal fun parseXmlSecurely(xml: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    return factory.newDocumentBuilder().parse(xml.byteInputStream())
}
