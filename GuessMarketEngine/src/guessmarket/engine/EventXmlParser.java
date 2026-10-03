package guessmarket.engine;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses and validates an Exercise-3 XML stream (GM-events only, no ids, no users).
 * The content is read from memory only; nothing is written to disk.
 */
public final class EventXmlParser {
    public static final int MIN_OPTIONS = 2;

    public List<EventDefinition> parse(InputStream xml) throws GuessMarketException {
        if (xml == null) {
            throw new GuessMarketException("No XML content was received.");
        }
        Document document = readDocument(xml);
        Element root = document.getDocumentElement();
        if (!"Guess-Market".equals(root.getNodeName())) {
            throw new GuessMarketException("The XML root element must be Guess-Market (found: " + root.getNodeName() + ").");
        }
        Element eventsElement = firstChildElement(root, "GM-events");
        if (eventsElement == null) {
            throw new GuessMarketException("The XML file is missing the GM-events element.");
        }
        List<Element> eventElements = childElements(eventsElement, "GM-event");
        if (eventElements.isEmpty()) {
            throw new GuessMarketException("The XML file does not contain any events.");
        }

        List<EventDefinition> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Element eventElement : eventElements) {
            EventDefinition definition = parseEvent(eventElement);
            if (!names.add(definition.name())) {
                throw new GuessMarketException("Duplicate event name in the file: '" + definition.name()
                        + "'. Every event name must be unique.");
            }
            result.add(definition);
        }
        return result;
    }

    private EventDefinition parseEvent(Element eventElement) throws GuessMarketException {
        String name = eventElement.getAttribute("name").trim();
        if (name.isEmpty()) {
            throw new GuessMarketException("An event is missing its name attribute.");
        }
        String label = "event '" + name + "'";
        String description = requiredText(eventElement, "description", "description of " + label);

        Element commissionElement = firstChildElementAny(eventElement, "commission", "comision");
        if (commissionElement == null) {
            throw new GuessMarketException("The " + label + " is missing its commission element.");
        }
        int commission = parseInt(commissionElement.getTextContent().trim(), "commission of " + label);
        if (commission < 0 || commission > 90) {
            throw new GuessMarketException("Invalid commission for " + label + ": " + commission
                    + ". Commission must be between 0 and 90 inclusive.");
        }
        CommissionType commissionType;
        try {
            commissionType = CommissionType.fromXml(commissionElement.getAttribute("type"));
        } catch (IllegalArgumentException ex) {
            throw new GuessMarketException("Invalid commission type for " + label
                    + ". Allowed values: on-close, on-purchase.", ex);
        }

        Element optionsElement = firstChildElement(eventElement, "GM-options");
        if (optionsElement == null) {
            throw new GuessMarketException("The " + label + " is missing GM-options.");
        }
        List<String> options = new ArrayList<>();
        Set<String> optionKeys = new HashSet<>();
        for (Element optionElement : childElements(optionsElement, "GM-option")) {
            String option = optionElement.getTextContent().trim();
            if (option.isEmpty()) {
                throw new GuessMarketException("The " + label + " has an empty GM-option.");
            }
            if (!optionKeys.add(option.toLowerCase(Locale.ROOT))) {
                throw new GuessMarketException("The " + label + " has the option '" + option + "' more than once.");
            }
            options.add(option);
        }
        if (options.size() < MIN_OPTIONS) {
            throw new GuessMarketException("The " + label + " must contain at least " + MIN_OPTIONS
                    + " GM-option elements (found " + options.size() + ").");
        }

        Element methodElement = firstChildElement(eventElement, "GM-method");
        if (methodElement == null) {
            throw new GuessMarketException("The " + label + " is missing GM-method.");
        }
        Element lmsr = firstChildElement(methodElement, "GM-LMSR");
        Element orderBook = firstChildElement(methodElement, "GM-order-book");
        if (lmsr != null && orderBook == null) {
            int b = parseInt(requiredText(lmsr, "b", "LMSR b of " + label), "LMSR b of " + label);
            if (b <= 0) {
                throw new GuessMarketException("Invalid LMSR b for " + label + ": b must be greater than zero.");
            }
            return new EventDefinition(name, description, commission, commissionType, List.copyOf(options),
                    MarketType.LMSR, (double) b, null, null, false);
        }
        if (orderBook != null && lmsr == null) {
            String initialAttr = firstAttribute(orderBook, "initial", "inital");
            if (initialAttr == null || initialAttr.isBlank()) {
                throw new GuessMarketException("The order book of " + label + " is missing the initial attribute.");
            }
            int initial = parseInt(initialAttr.trim(), "initial of " + label);
            if (initial < 0) {
                throw new GuessMarketException("Invalid initial investment for " + label + ": must be 0 or more.");
            }
            if (!orderBook.hasAttribute("d")) {
                throw new GuessMarketException("The order book of " + label + " is missing the d attribute.");
            }
            int d = parseInt(orderBook.getAttribute("d").trim(), "d of " + label);
            if (d <= 0) {
                throw new GuessMarketException("Invalid base value d for " + label + ": d must be greater than zero.");
            }
            String mint = orderBook.getAttribute("allow-mint").trim().toLowerCase(Locale.ROOT);
            if (!mint.equals("true") && !mint.equals("false")) {
                throw new GuessMarketException("The order book of " + label
                        + " must have allow-mint=\"true\" or allow-mint=\"false\".");
            }
            return new EventDefinition(name, description, commission, commissionType, List.copyOf(options),
                    MarketType.ORDER_BOOK, null, initial, d, Boolean.parseBoolean(mint));
        }
        throw new GuessMarketException("The " + label + " must define exactly one of GM-LMSR or GM-order-book.");
    }

    private static Document readDocument(InputStream xml) throws GuessMarketException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void fatalError(org.xml.sax.SAXParseException ex) throws SAXException {
                    throw ex;
                }
            });
            Document document = builder.parse(xml);
            document.getDocumentElement().normalize();
            return document;
        } catch (ParserConfigurationException ex) {
            throw new GuessMarketException("The XML parser could not be configured.", ex);
        } catch (SAXException ex) {
            throw new GuessMarketException("The file is not a well-formed XML document: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new GuessMarketException("The XML content could not be read: " + ex.getMessage(), ex);
        }
    }

    private static String requiredText(Element parent, String tagName, String label) throws GuessMarketException {
        Element child = firstChildElement(parent, tagName);
        if (child == null || child.getTextContent() == null || child.getTextContent().trim().isEmpty()) {
            throw new GuessMarketException("Missing or empty " + label + ".");
        }
        return child.getTextContent().trim();
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static Element firstChildElementAny(Element parent, String... tagNames) {
        for (String tagName : tagNames) {
            Element found = firstChildElement(parent, tagName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String firstAttribute(Element element, String... names) {
        for (String name : names) {
            if (element.hasAttribute(name)) {
                return element.getAttribute(name);
            }
        }
        return null;
    }

    private static int parseInt(String value, String label) throws GuessMarketException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new GuessMarketException("Invalid integer value for " + label + ": " + value, ex);
        }
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // Optional parser feature.
        }
    }
}
