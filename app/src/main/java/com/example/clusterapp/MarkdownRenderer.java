package com.example.clusterapp;

/**
 * Converts a Markdown subset to an HTML page suitable for display in a dark-themed WebView.
 * Supports: h1/h2/h3, bold, italic, inline code, fenced code blocks, unordered lists,
 * blockquotes, horizontal rules, and [text](url) links.
 */
final class MarkdownRenderer {

    private static final String CSS =
        "body{background:#1a1a1a;color:#cccccc;font-family:sans-serif;"
        + "font-size:14px;padding:12px 14px;margin:0;line-height:1.6}"
        + "h1,h2,h3{color:#ffffff;margin:16px 0 4px}"
        + "h1{font-size:20px}h2{font-size:17px}h3{font-size:15px}"
        + "code{background:#2a2a2a;color:#80cbc4;padding:2px 5px;"
        + "border-radius:3px;font-size:12px}"
        + "pre{background:#2a2a2a;padding:10px 12px;border-radius:4px;"
        + "overflow-x:auto;white-space:pre-wrap}"
        + "pre code{background:none;padding:0;color:#a5d6a7}"
        + "blockquote{border-left:3px solid #444;margin:4px 0;"
        + "padding-left:12px;color:#888}"
        + "a{color:#42a5f5;text-decoration:none}"
        + "hr{border:none;border-top:1px solid #333;margin:14px 0}"
        + "ul,ol{padding-left:22px;margin:6px 0}"
        + "li{margin-bottom:3px}"
        + "p{margin:8px 0}";

    static String toHtml(String markdown) {
        if (markdown == null) markdown = "";
        StringBuilder out = new StringBuilder(markdown.length() * 2);
        out.append("<html><head><meta charset=\"UTF-8\"><style>")
           .append(CSS)
           .append("</style></head><body>");

        String[] lines = markdown.split("\n", -1);
        boolean inCodeBlock  = false;
        boolean inList       = false;
        boolean inParagraph  = false;

        for (String raw : lines) {
            String trimmed = raw.trim();

            // Fenced code block toggle
            if (trimmed.startsWith("```")) {
                if (inParagraph) { out.append("</p>"); inParagraph = false; }
                if (inList)      { out.append("</ul>"); inList = false; }
                if (inCodeBlock) {
                    out.append("</code></pre>");
                    inCodeBlock = false;
                } else {
                    out.append("<pre><code>");
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                out.append(esc(raw)).append('\n');
                continue;
            }

            // Close list when current line is not a list item
            if (inList && !trimmed.startsWith("- ") && !trimmed.startsWith("* ")) {
                out.append("</ul>");
                inList = false;
            }

            if (trimmed.isEmpty()) {
                if (inParagraph) { out.append("</p>"); inParagraph = false; }
                continue;
            }

            if (trimmed.startsWith("### ")) {
                closePara(out, inParagraph); inParagraph = false;
                out.append("<h3>").append(inline(trimmed.substring(4))).append("</h3>");
            } else if (trimmed.startsWith("## ")) {
                closePara(out, inParagraph); inParagraph = false;
                out.append("<h2>").append(inline(trimmed.substring(3))).append("</h2>");
            } else if (trimmed.startsWith("# ")) {
                closePara(out, inParagraph); inParagraph = false;
                out.append("<h1>").append(inline(trimmed.substring(2))).append("</h1>");
            } else if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                closePara(out, inParagraph); inParagraph = false;
                out.append("<hr>");
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                closePara(out, inParagraph); inParagraph = false;
                if (!inList) { out.append("<ul>"); inList = true; }
                out.append("<li>").append(inline(trimmed.substring(2))).append("</li>");
            } else if (trimmed.startsWith("> ")) {
                closePara(out, inParagraph); inParagraph = false;
                out.append("<blockquote>").append(inline(trimmed.substring(2))).append("</blockquote>");
            } else {
                if (!inParagraph) { out.append("<p>"); inParagraph = true; }
                else out.append(' ');
                out.append(inline(trimmed));
            }
        }

        if (inList)      out.append("</ul>");
        if (inParagraph) out.append("</p>");
        if (inCodeBlock) out.append("</code></pre>");
        out.append("</body></html>");
        return out.toString();
    }

    private static void closePara(StringBuilder out, boolean inParagraph) {
        if (inParagraph) out.append("</p>");
    }

    /** Apply bold, italic, inline-code, and link formatting. */
    private static String inline(String text) {
        text = esc(text);
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>");
        text = text.replaceAll("`([^`]+)`", "<code>$1</code>");
        text = text.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        return text;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private MarkdownRenderer() {}
}
