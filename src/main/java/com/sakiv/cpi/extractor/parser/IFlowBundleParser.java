package com.sakiv.cpi.extractor.parser;

import com.sakiv.cpi.extractor.model.IFlowContent;
import com.sakiv.cpi.extractor.model.ScriptInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// @author Vikas Singh | Created: 2026-02-21
public class IFlowBundleParser {

    private static final Logger log = LoggerFactory.getLogger(IFlowBundleParser.class);

    private final IFlowXmlParser xmlParser = new IFlowXmlParser();

    public IFlowContent parse(byte[] zipBytes, String flowId, String version) throws IOException {
        IFlowContent content = null;
        List<ScriptInfo> scripts = new ArrayList<>();
        List<String> mappingFiles = new ArrayList<>();
        String iflwXml = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                String lowerName = name.toLowerCase();

                if (lowerName.endsWith(".iflw")) {
                    iflwXml = readEntryAsString(zis);
                    log.debug("Found iFlow XML: {} ({} chars)", name, iflwXml.length());

                } else if (lowerName.endsWith(".groovy")) {
                    String scriptContent = readEntryAsString(zis);
                    scripts.add(new ScriptInfo(extractFileName(name), "Groovy", scriptContent));
                    log.debug("Found Groovy script: {}", name);

                } else if (lowerName.endsWith(".js")) {
                    String scriptContent = readEntryAsString(zis);
                    scripts.add(new ScriptInfo(extractFileName(name), "JavaScript", scriptContent));
                    log.debug("Found JavaScript: {}", name);

                } else if (lowerName.endsWith(".mmap")) {
                    mappingFiles.add(extractFileName(name));
                    log.debug("Found message mapping: {}", name);

                } else if (lowerName.endsWith(".xsl") || lowerName.endsWith(".xslt")) {
                    mappingFiles.add(extractFileName(name));
                    log.debug("Found XSLT: {}", name);
                }

                zis.closeEntry();
            }
        }

        if (iflwXml != null) {
            content = xmlParser.parse(iflwXml);
            content.setRawXml(iflwXml);
        } else {
            log.warn("No .iflw file found in ZIP bundle for flow: {}", flowId);
            content = new IFlowContent();
        }

        content.setFlowId(flowId);
        content.setVersion(version);
        content.setScripts(scripts);
        content.setMappingFiles(mappingFiles);

        log.info("Parsed bundle for {}: {} adapters, {} routes, {} mappings, {} scripts, {} mapping files",
                flowId, content.getAdapters().size(), content.getRoutes().size(),
                content.getMappings().size(), scripts.size(), mappingFiles.size());

        return content;
    }

    private String readEntryAsString(ZipInputStream zis) throws IOException {
        return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String extractFileName(String entryName) {
        int lastSlash = entryName.lastIndexOf('/');
        return lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;
    }
}
