package com.sakiv.cpi.extractor.model;

// @author Vikas Singh | Created: 2026-02-22
public class ScriptInfo {

    private String fileName;
    private String language;
    private String content;
    private String contentSnippet;

    private static final int SNIPPET_MAX_LINES = 20;

    public ScriptInfo() {}

    public ScriptInfo(String fileName, String language, String content) {
        this.fileName = fileName;
        this.language = language;
        this.content = content;
        this.contentSnippet = buildSnippet(content);
    }

    private String buildSnippet(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\n", SNIPPET_MAX_LINES + 1);
        if (lines.length <= SNIPPET_MAX_LINES) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SNIPPET_MAX_LINES; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("// ... (").append(lines.length - SNIPPET_MAX_LINES).append(" more lines)");
        return sb.toString();
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getContent() { return content; }
    public void setContent(String content) {
        this.content = content;
        this.contentSnippet = buildSnippet(content);
    }

    public String getContentSnippet() { return contentSnippet; }
    public void setContentSnippet(String contentSnippet) { this.contentSnippet = contentSnippet; }

    @Override
    public String toString() {
        return String.format("ScriptInfo[file=%s, lang=%s]", fileName, language);
    }
}
