package com.sakiv.cpi.extractor.parser;

import com.sakiv.cpi.extractor.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

// @author Vikas Singh | Created: 2026-02-21
public class IFlowXmlParser {

    private static final Logger log = LoggerFactory.getLogger(IFlowXmlParser.class);

    public IFlowContent parse(String xml) {
        IFlowContent content = new IFlowContent();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            parseParticipants(doc, content);
            parseChannels(doc, content);
            parseCallActivities(doc, content);
            parseServiceTasks(doc, content);
            parseSequenceFlows(doc, content);
            parseMessageFlows(doc, content);
            parseProcessProperties(doc, content);

        } catch (Exception e) {
            log.error("Failed to parse iFlow XML: {}", e.getMessage(), e);
        }

        return content;
    }

    private void parseParticipants(Document doc, IFlowContent content) {
        NodeList participants = doc.getElementsByTagNameNS("*", "participant");
        for (int i = 0; i < participants.getLength(); i++) {
            Element elem = (Element) participants.item(i);
            IFlowEndpoint endpoint = new IFlowEndpoint();
            endpoint.setId(elem.getAttribute("id"));
            endpoint.setName(elem.getAttribute("name"));
            endpoint.setType(getAttributeNS(elem, "type"));
            endpoint.setComponentType(getPropertyValue(elem, "ComponentType"));
            endpoint.setAddress(getPropertyValue(elem, "address"));

            String iflType = elem.getAttribute("ifl:type");
            if (iflType != null && !iflType.isEmpty()) {
                endpoint.setRole(iflType);
            }

            content.getEndpoints().add(endpoint);
        }
    }

    private void parseChannels(Document doc, IFlowContent content) {
        // Build participant ID → ifl:type map for direction determination
        Map<String, String> participantRoles = new HashMap<>();
        NodeList participants = doc.getElementsByTagNameNS("*", "participant");
        for (int i = 0; i < participants.getLength(); i++) {
            Element p = (Element) participants.item(i);
            String pid = p.getAttribute("id");
            String role = p.getAttribute("ifl:type");
            if (pid != null && !pid.isEmpty() && role != null && !role.isEmpty()) {
                participantRoles.put(pid, role);
            }
        }

        NodeList messageFlows = doc.getElementsByTagNameNS("*", "messageFlow");
        for (int i = 0; i < messageFlows.getLength(); i++) {
            Element mf = (Element) messageFlows.item(i);
            String sourceRef = mf.getAttribute("sourceRef");
            String targetRef = mf.getAttribute("targetRef");
            String sourceRole = participantRoles.get(sourceRef);
            String targetRole = participantRoles.get(targetRef);

            String direction;
            if (sourceRole != null && sourceRole.toLowerCase().contains("sender")) {
                direction = "Sender";
            } else if (targetRole != null
                    && (targetRole.toLowerCase().contains("receiver")
                        || targetRole.toLowerCase().contains("recevier"))) {
                direction = "Receiver";
            } else {
                continue;
            }

            IFlowAdapter adapter = new IFlowAdapter();
            adapter.setId(mf.getAttribute("id"));
            adapter.setName(mf.getAttribute("name"));
            String adapterType = getPropertyValue(mf, "ComponentType");
            if (adapterType == null || adapterType.isBlank()) {
                adapterType = mf.getAttribute("name");
            }
            adapter.setAdapterType(adapterType);
            adapter.setDirection(direction);
            adapter.setTransportProtocol(getPropertyValue(mf, "TransportProtocol"));
            adapter.setMessageProtocol(getPropertyValue(mf, "MessageProtocol"));
            String address = getPropertyValue(mf, "Address");
            if (address == null || address.isBlank()) {
                address = getPropertyValue(mf, "address");
            }
            adapter.setAddress(address);
            adapter.setProperties(extractAllProperties(mf));

            content.getAdapters().add(adapter);
        }
    }

    private void parseCallActivities(Document doc, IFlowContent content) {
        NodeList activities = doc.getElementsByTagNameNS("*", "callActivity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element elem = (Element) activities.item(i);
            String activityType = getPropertyValue(elem, "ActivityType");
            String componentType = getPropertyValue(elem, "ComponentType");

            if (isMappingActivity(activityType, componentType)) {
                IFlowMapping mapping = new IFlowMapping();
                mapping.setId(elem.getAttribute("id"));
                mapping.setName(elem.getAttribute("name"));
                mapping.setMappingType(componentType != null ? componentType : activityType);
                mapping.setResourceId(getPropertyValue(elem, "mappinguri"));
                if (mapping.getResourceId() == null) {
                    mapping.setResourceId(getPropertyValue(elem, "MappingPath"));
                }
                mapping.setProperties(extractAllProperties(elem));
                content.getMappings().add(mapping);
            }

            IFlowRoute route = new IFlowRoute();
            route.setId(elem.getAttribute("id"));
            route.setName(elem.getAttribute("name"));
            route.setType("callActivity");
            route.setActivityType(activityType);
            route.setComponentType(componentType);
            route.setProperties(extractAllProperties(elem));
            content.getRoutes().add(route);
        }
    }

    private void parseServiceTasks(Document doc, IFlowContent content) {
        NodeList tasks = doc.getElementsByTagNameNS("*", "serviceTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element elem = (Element) tasks.item(i);
            IFlowRoute route = new IFlowRoute();
            route.setId(elem.getAttribute("id"));
            route.setName(elem.getAttribute("name"));
            route.setType("serviceTask");
            route.setActivityType(getPropertyValue(elem, "ActivityType"));
            route.setComponentType(getPropertyValue(elem, "ComponentType"));
            route.setProperties(extractAllProperties(elem));
            content.getRoutes().add(route);
        }
    }

    private void parseSequenceFlows(Document doc, IFlowContent content) {
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element elem = (Element) flows.item(i);
            IFlowRoute route = new IFlowRoute();
            route.setId(elem.getAttribute("id"));
            route.setName(elem.getAttribute("name"));
            route.setType("sequenceFlow");
            route.setSourceRef(elem.getAttribute("sourceRef"));
            route.setTargetRef(elem.getAttribute("targetRef"));

            NodeList conditionExprs = elem.getElementsByTagNameNS("*", "conditionExpression");
            if (conditionExprs.getLength() > 0) {
                route.setCondition(conditionExprs.item(0).getTextContent());
            }

            route.setProperties(extractAllProperties(elem));
            content.getRoutes().add(route);
        }
    }

    private void parseMessageFlows(Document doc, IFlowContent content) {
        NodeList flows = doc.getElementsByTagNameNS("*", "messageFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element elem = (Element) flows.item(i);
            IFlowRoute route = new IFlowRoute();
            route.setId(elem.getAttribute("id"));
            route.setName(elem.getAttribute("name"));
            route.setType("messageFlow");
            route.setSourceRef(elem.getAttribute("sourceRef"));
            route.setTargetRef(elem.getAttribute("targetRef"));
            route.setProperties(extractAllProperties(elem));
            content.getRoutes().add(route);
        }
    }

    private void parseProcessProperties(Document doc, IFlowContent content) {
        NodeList processes = doc.getElementsByTagNameNS("*", "process");
        for (int i = 0; i < processes.getLength(); i++) {
            Element process = (Element) processes.item(i);
            content.getProcessProperties().putAll(extractAllProperties(process));
        }
    }

    private boolean isMappingActivity(String activityType, String componentType) {
        if (activityType != null) {
            String lower = activityType.toLowerCase();
            if (lower.contains("mapping") || lower.contains("xslt") || lower.contains("message_mapping")) {
                return true;
            }
        }
        if (componentType != null) {
            String lower = componentType.toLowerCase();
            return lower.contains("mapping") || lower.contains("xslt");
        }
        return false;
    }

    private String getPropertyValue(Element parent, String key) {
        NodeList extensions = parent.getElementsByTagNameNS("*", "property");
        for (int i = 0; i < extensions.getLength(); i++) {
            Element prop = (Element) extensions.item(i);
            NodeList keys = prop.getElementsByTagNameNS("*", "key");
            if (keys.getLength() > 0 && key.equals(keys.item(0).getTextContent().trim())) {
                NodeList values = prop.getElementsByTagNameNS("*", "value");
                if (values.getLength() > 0) {
                    return values.item(0).getTextContent().trim();
                }
            }
        }
        return null;
    }

    private Map<String, String> extractAllProperties(Element parent) {
        Map<String, String> props = new LinkedHashMap<>();
        NodeList extensions = parent.getElementsByTagNameNS("*", "property");
        for (int i = 0; i < extensions.getLength(); i++) {
            Element prop = (Element) extensions.item(i);
            if (!isDirectChild(parent, prop)) {
                continue;
            }
            NodeList keys = prop.getElementsByTagNameNS("*", "key");
            NodeList values = prop.getElementsByTagNameNS("*", "value");
            if (keys.getLength() > 0 && values.getLength() > 0) {
                String k = keys.item(0).getTextContent().trim();
                String v = values.item(0).getTextContent().trim();
                if (!k.isEmpty()) {
                    props.put(k, v);
                }
            }
        }
        return props;
    }

    private boolean isDirectChild(Element parent, Element prop) {
        Node current = prop.getParentNode();
        while (current != null) {
            if (current == parent) {
                return true;
            }
            if (current instanceof Element e) {
                String localName = e.getLocalName();
                if (localName != null && !localName.equals("extensionElements") && !localName.equals("property")) {
                    if (current != parent) {
                        return false;
                    }
                }
            }
            current = current.getParentNode();
        }
        return false;
    }

    private String getAttributeNS(Element elem, String localName) {
        NamedNodeMap attrs = elem.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (localName.equals(attr.getLocalName())) {
                return attr.getNodeValue();
            }
        }
        return null;
    }
}
