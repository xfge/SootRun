import model.LayoutTreeNode;
import model.Widget;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import soot.Scene;
import soot.SootClass;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Dom4jParser {

    Logger logger = Logger.getLogger(Dom4jParser.class.toString());

    private final String fp;
    private List<String> tokens;
    private LayoutTreeNode layoutTreeRoot;
    private Set<LayoutTreeNode> treeNodeSet;

    public Dom4jParser(String fp) {
        logger.setLevel(Level.OFF);
        this.fp = fp;
        this.treeNodeSet = new HashSet<>();
        this.tokens = new ArrayList<>();
    }

    public List<String> getTokens() {
        return tokens;
    }

    public LayoutTreeNode getLayoutTreeRoot() {
        return layoutTreeRoot;
    }

    public Set<LayoutTreeNode> getTreeNodeSet() {
        return treeNodeSet;
    }

    /***
     * 该方法中包含了读取XML、分析SOOT结果、制作token序列等过程
     */
    public void parse() {
        File file = new File(fp);
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(file);

            layoutTreeRoot = treeWalk(document);
            setAllNodesAncestors();
            setAllNodesTypes(); // 由 SOOT 补充完整信息后再执行
            makeTokens();

        } catch (DocumentException e) {
            logger.severe(e.toString() + " XML file parsing failed: " + fp);
        }
    }

    public void setAllNodesTypes() {
        for (LayoutTreeNode node : treeNodeSet) {
            Widget inferredWidgetType = inferWidgetType(node);
            node.setType(inferredWidgetType.toString());
        }
    }

    /***
     * 根据 Soot 运行结果为
     */
    public void setAllNodesAncestors() {
        for (LayoutTreeNode node : getTreeNodeSet()) {
            String className = node.getClassName();
            if (Scene.v().containsClass(className)) {
                SootClass sc = Scene.v().getSootClass(className);
                List<String> ancestors = new ArrayList<>();
                while (sc.hasSuperclass()) {
                    ancestors.add(sc.getName());
                    sc = sc.getSuperclass();
                }
                ancestors.add(sc.getName());
                node.setAncestors(ancestors);
            }
        }
    }

    public void makeTokens() {
        makeTokensSweep(layoutTreeRoot);
    }

    public void makeTokensSweep(LayoutTreeNode node) {
        if (node != null) {
            tokens.add(node.getType());
            if (node.getChildren().size() > 0) {
                tokens.add("{");
                for (LayoutTreeNode child : node.getChildren()) {
                    makeTokensSweep(child);
                }
                tokens.add("}");
            }
        }
    }

    private LayoutTreeNode treeWalk(Document document) {
        Element rootElement = document.getRootElement();
        return treeWalk(rootElement, null);
    }

    private LayoutTreeNode treeWalk(Element element, LayoutTreeNode parent) {
        if (element.getName().equals("include")) {
            String attrValue = element.attribute("layout").getValue();
            if (attrValue.startsWith("@layout/")) {
                // <include layout="@layout/xxx" />
                // fixme: 这里没有考虑不同平台路径分隔符
                String includedLayoutPath = fp.substring(0, fp.lastIndexOf('\\') + 1) + attrValue.substring(8) + ".xml";
                System.out.println(attrValue);
                Dom4jParser parser = new Dom4jParser(includedLayoutPath);
                parser.parse();
                LayoutTreeNode includedRoot = parser.getLayoutTreeRoot();
                parent.addChild(includedRoot);
                treeNodeSet.addAll(parser.getTreeNodeSet());
                logger.info("<include> tag processed with " + includedLayoutPath);
                return includedRoot;
            } else {
                logger.severe("Unhandled attribute of <include> tag: " + attrValue);
            }
        } else if (element.getName().equals("view")) {
            logger.info("[TODO] Unhandled attribute of <view> tag.");
        } else if (element.getName().equals("merge")) {
            logger.info("[TODO] Unhandled attribute of <merge> tag.");
        } else if (element.getName().equals("fragment")) {
            logger.info("[TODO] Unhandled attribute of <fragment> tag.");
        } else {
            LayoutTreeNode currentNode = new LayoutTreeNode();
            currentNode.setClassName(inferClassName(element.getName()));
            Attribute idAttribute = element.attribute("id");
            if (idAttribute != null) {
                if (idAttribute.getNamespacePrefix().equals("android") && idAttribute.getValue().startsWith("@id/")) {
                    currentNode.setId(idAttribute.getValue().substring(4));
                } else {
                    // 未获取到标准化控件 id
                    logger.info("[TODO] Unhandled 'id' attribute: " + idAttribute.getNamespace() + ":" + idAttribute.getName() + "=" + idAttribute.getValue());
                }
            }

            currentNode.setClickable(element.attribute("onClick") != null);

            // 递归遍历
            for (Iterator<Element> it = element.elementIterator(); it.hasNext(); ) {
                treeWalk(it.next(), currentNode);
            }
            if (parent != null) {
                parent.addChild(currentNode);
            }
            treeNodeSet.add(currentNode);
            return currentNode;
        }
        return null;
    }


    private String inferClassName(String tag) {
        if (tag.contains(".")) {
            return tag;
        } else {
            switch (tag) {
                case "View":
                case "SurfaceView":
                case "TextureView":
                    return "android.view." + tag;
                case "WebView":
                    return "android.webkit." + tag;
                case "ActivityView":
                    return "android.app." + tag;
                default:
                    return "android.widget." + tag;
            }
        }
    }

    private String getStdClassName(String clz, List<String> ancestors) {
        if (clz.startsWith("android.widget") || clz.equals("android.support.v7.widget.Toolbar") ||
                clz.equals("android.support.v7.widget.RecyclerView") || clz.equals("android.view.View")) {
            return clz;
        }
        for (String ancestor : ancestors) {
            if (ancestor.startsWith("android.widget") || ancestor.equals("android.support.v7.widget.Toolbar") ||
                    ancestor.equals("android.support.v7.widget.RecyclerView") || ancestor.equals("android.view.View")) {
                return ancestor;
            }
        }
        return null;
    }

    private Widget inferWidgetTypeFromStdClass(String stdClassName) {
        switch (stdClassName) {
            case "android.view.View":
                return Widget.Unclassified;
            case "android.support.v7.widget.Toolbar":
                return Widget.Toolbar;
            case "android.support.v7.widget.RecyclerView":
            case "android.widget.AdapterView":
            case "android.widget.ListView":
                return Widget.List;
            case "android.widget.ToggleButton":
            case "android.widget.Switch":
                return Widget.Switch;
            case "android.widget.RadioButton":
                return Widget.RadioButton;
            case "android.widget.ImageButton":
            case "android.widget.Button":
            case "android.widget.CompoundButton":
                return Widget.Button;
            case "android.widget.CheckBox":
            case "android.widget.CheckedTextView":
                return Widget.CheckBox;
            case "android.widget.ImageView":
                return Widget.ImageView;
            case "android.widget.EditText":
            case "android.widget.AutoCompleteTextView":
            case "android.widget.MultiAutoCompleteTextView":
                return Widget.EditText;
            case "android.widget.TextView":
                return Widget.TextView;
            default:
                logger.info(stdClassName + " not supported.");
                return Widget.Unclassified;
        }
    }

    private Widget inferWidgetType(LayoutTreeNode node) {
        List<String> ancestors = node.getAncestors();
        String firstStdClass = getStdClassName(node.getClassName(), ancestors);

        if (ancestors == null) {
            logger.severe("[WARNING] ancestors not retrieved. " + node.getClassName());
        } else {
            if (ancestors.contains("android.widget.AdapterView")) {
                return Widget.List;
            }
            if (ancestors.contains("android.view.ViewGroup")) {
                return Widget.Layout;
            }
        }

        if (firstStdClass == null) {
            logger.severe("[WARNING] No first standard Android widget class. " + node.getClassName() + " " + node.getAncestors());
            return Widget.Unclassified;
        }

        return inferWidgetTypeFromStdClass(firstStdClass);
    }
}
