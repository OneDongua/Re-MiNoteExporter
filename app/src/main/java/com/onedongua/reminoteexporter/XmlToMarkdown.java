package com.onedongua.reminoteexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.List;

public class XmlToMarkdown {

    public static String convert(String xml) {
        // 使用 XML 解析器，避免 Jsoup 自动补全 HTML 标签（如 <html><body>）
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        StringBuilder md = new StringBuilder();

        // 遍历所有根节点（包括纯文本节点）
        List<Node> nodes = doc.childNodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node instanceof Element el) {
                processNode(el, md);

                // 防重复机制：如果当前是 input/bullet/order，且下一个节点是被它“吸收”的文本节点，则跳过
                String tag = el.tagName().toLowerCase();
                if (tag.equals("input") || tag.equals("bullet") || tag.equals("order")) {
                    if (i + 1 < nodes.size() && nodes.get(i + 1) instanceof TextNode) {
                        if (el.nextSibling() == nodes.get(i + 1)) {
                            i++; // 跳过已被消费的文本节点
                        }
                    }
                }
            } else if (node instanceof TextNode) {
                // 完全没有标签的纯文本，直接保留原文
                md.append(((TextNode) node).getWholeText());
            }
        }

        // 去除末尾多余的换行符
        return md.toString().replaceAll("\n+$", "");
    }

    private static void processNode(Element el, StringBuilder md) {
        String tagName = el.tagName().toLowerCase();

        switch (tagName) {
            case "new-format":
                // 忽略
                break;
            case "input":
                boolean isChecked = el.hasAttr("checked") && el.attr("checked").equalsIgnoreCase("true");
                String checkMark = isChecked ? "- [x] " : "- [ ] ";
                int inputIndent = el.hasAttr("indent") ? Integer.parseInt(el.attr("indent")) : 1;
                String inputIndentStr = (inputIndent > 1) ? "\t".repeat(Math.max(0, inputIndent - 1)) : "";
                md.append(inputIndentStr).append(checkMark).append(getNextSiblingText(el)).append("\n");
                break;
            case "bullet":
                int bulletIndent = el.hasAttr("indent") ? Integer.parseInt(el.attr("indent")) : 1;
                String bulletIndentStr = (bulletIndent > 1) ? "\t".repeat(Math.max(0, bulletIndent - 1)) : "";
                md.append(bulletIndentStr).append("- ").append(getNextSiblingText(el)).append("\n");
                break;
            case "order":
                int orderIndent = el.hasAttr("indent") ? Integer.parseInt(el.attr("indent")) : 1;
                String orderIndentStr = (orderIndent > 1) ? "\t".repeat(Math.max(0, orderIndent - 1)) : "";
                md.append(orderIndentStr).append("1. ").append(getNextSiblingText(el)).append("\n");
                break;
            case "hr":
                md.append("---\n");
                break;
            case "quote":
                StringBuilder quoteContent = new StringBuilder();
                for (Element child : el.children()) {
                    if (child.tagName().equalsIgnoreCase("text")) {
                        processTextElement(child, quoteContent);
                    }
                }
                String quoted = quoteContent.toString().replaceAll("(?m)^", "> ");
                md.append(quoted.trim()).append("\n");
                break;
            case "text":
                processTextElement(el, md);
                break;
            default:
                // 未知标签，递归处理其子节点（确保未知标签内的纯文本也被保留）
                for (Node child : el.childNodes()) {
                    if (child instanceof Element) processNode((Element) child, md);
                    else if (child instanceof TextNode)
                        md.append(((TextNode) child).getWholeText());
                }
                break;
        }
    }

    private static void processTextElement(Element el, StringBuilder md) {
        String prefix = "";
        String suffix = "\n";
        boolean isBlock = false;
        Element blockEl = null;

        for (Element child : el.children()) {
            String tag = child.tagName().toLowerCase();
            switch (tag) {
                case "size":
                    prefix = "# ";
                    isBlock = true;
                    blockEl = child;
                    break;
                case "mid-size":
                    prefix = "## ";
                    isBlock = true;
                    blockEl = child;
                    break;
                case "h3-size":
                    prefix = "### ";
                    isBlock = true;
                    blockEl = child;
                    break;
                case "center":
                    prefix = "<center>";
                    suffix = "</center>\n";
                    isBlock = true;
                    blockEl = child;
                    break;
                case "right":
                    prefix = "<p align=\"right\">";
                    suffix = "</p>\n";
                    isBlock = true;
                    blockEl = child;
                    break;
            }
            if (isBlock) break;
        }

        if (isBlock) {
            StringBuilder inline = new StringBuilder();
            for (Node child : blockEl.childNodes()) {
                inline.append(parseInline(child));
            }
            md.append(prefix).append(inline.toString().trim()).append(suffix);
        } else {
            int indent = el.hasAttr("indent") ? Integer.parseInt(el.attr("indent")) : 1;
            String indentStr = (indent > 1) ? "\t".repeat(Math.max(0, indent - 1)) : "";

            StringBuilder inline = new StringBuilder();
            for (Node child : el.childNodes()) {
                inline.append(parseInline(child));
            }
            md.append(indentStr).append(inline.toString().trim()).append("\n");
        }
    }

    private static String parseInline(Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).getWholeText();
        }
        if (node instanceof Element el) {
            String tag = el.tagName().toLowerCase();
            StringBuilder inner = new StringBuilder();
            for (Node child : el.childNodes()) {
                inner.append(parseInline(child));
            }

            return switch (tag) {
                case "b" -> "**" + inner + "**";
                case "i" -> "*" + inner + "*";
                case "delete" -> "~~" + inner + "~~";
                case "u" -> "<u>" + inner + "</u>";
                case "background" -> {
                    String color = el.attr("color");
                    yield String.format("<span style=\"background-color: %s\">%s</span>", color, inner);
                }
                default -> inner.toString();
            };
        }
        return "";
    }

    // 获取标签后面的文本节点（仅提取纯文本，避免误把相邻的 XML 标签当成字符串）
    private static String getNextSiblingText(Element el) {
        Node next = el.nextSibling();
        if (next instanceof TextNode) {
            return ((TextNode) next).getWholeText().trim();
        }
        return "";
    }
}