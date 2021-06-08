package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class LayoutParser extends DefaultHandler {

    private final Configuration configuration;
    private Map<String, XMLNode> xmlElementsMap;
    private XMLNode currentNode;
    private String currentRoot;
    private boolean isParsing;

    private LayoutParser(Configuration configuration) {
        xmlElementsMap = new HashMap<String, XMLNode>();
        this.configuration = configuration;
    }

    public static LayoutParser getInstance(Configuration configuration) {
        return new LayoutParser(configuration);
    }

    public XMLNode parseXML(String root) {
        if (xmlElementsMap.containsKey(root)) {
            return xmlElementsMap.get(root);
        }
        try {
            currentRoot = root;
            isParsing = false;
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            InputStream in = configuration.getBuilderXML();
            saxParser.parse(in, this);
            return xmlElementsMap.get(root);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new DocletAbortException(t);
        }
    }

    @Override
    public void startElement(String namespaceURI, String sName, String qName,
                             Attributes attrs)
            throws SAXException {
        if (isParsing || qName.equals(currentRoot)) {
            isParsing = true;
            currentNode = new XMLNode(currentNode, qName);
            for (int i = 0; i < attrs.getLength(); i++)
                currentNode.attrs.put(attrs.getLocalName(i), attrs.getValue(i));
            if (qName.equals(currentRoot))
                xmlElementsMap.put(qName, currentNode);
        }
    }

    @Override
    public void endElement(String namespaceURI, String sName, String qName)
            throws SAXException {
        if (!isParsing) {
            return;
        }
        currentNode = currentNode.parent;
        isParsing = !qName.equals(currentRoot);
    }
}
