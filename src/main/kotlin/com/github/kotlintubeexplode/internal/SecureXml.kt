package com.github.kotlintubeexplode.internal

import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One XXE-hardened [DocumentBuilderFactory], cached per thread. Building the factory runs a JAXP
 * service lookup (DocumentBuilderFactory.newInstance) that we don't want to repeat on every parse
 * now that channel-page meta-tag parsing drives this in a loop. DocumentBuilderFactory is not safe
 * for concurrent newDocumentBuilder() calls, so each thread gets its own instance instead of
 * sharing one.
 */
private val hardenedFactory: ThreadLocal<DocumentBuilderFactory> = ThreadLocal.withInitial {
    DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
}

/**
 * Parses XML into a DOM [Document] with an XXE-hardened parser (doctype declarations,
 * external entities, and external DTD loading are all disabled). Shared by the DASH
 * manifest parser, the closed-caption track parser, and channel-page meta-tag parsing.
 */
internal fun parseXmlSecurely(xml: String): Document {
    // A fresh DocumentBuilder per call — builders can't be reused across concurrent parses, but
    // creating one is cheap next to the factory's service lookup, which is what the cache avoids.
    return hardenedFactory.get().newDocumentBuilder().parse(xml.byteInputStream())
}
