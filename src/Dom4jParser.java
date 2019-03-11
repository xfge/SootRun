import model.LayoutTreeNode;
import model.Widget;
import org.dom4j.*;
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

    // 只需执行该方法
    public void parse() {
        File file = new File(fp);
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(file);

            layoutTreeRoot = treeWalk(document);
            setAncestorsForNodes();
            setAllNodesTypes(); // 由 SOOT 补充完整信息后再执行
            makeTokens();

        } catch (DocumentException e) {
            logger.severe(e.toString() + " XML file parsing failed.");
        }
    }

    public void setAllNodesTypes() {
        for (LayoutTreeNode node : treeNodeSet) {
//            System.out.println("[INFO] " + node.getClassName() + " ancestors: " + node.getAncestors());
            node.setType(inferWidgetType(node).toString());
        }
    }

    public void setAncestorsForNodes() {
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

    public Set<LayoutTreeNode> getTreeNodeSet() {
        return treeNodeSet;
    }

    private LayoutTreeNode treeWalk(Element element, LayoutTreeNode parent) {
        if (element.getName().equals("include")) {
            String attrValue = element.attribute("layout").getValue();
            if (attrValue.startsWith("@layout/")) {
                String includedLayoutPath = fp.substring(0, fp.lastIndexOf('/') + 1) + attrValue.substring(8) + ".xml";
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
            logger.severe("[TODO] Unhandled attribute of <view> tag.");
        } else if (element.getName().equals("merge")) {
            logger.severe("[TODO] Unhandled attribute of <merge> tag.");
        } else if (element.getName().equals("fragment")) {
            logger.severe("[TODO] Unhandled attribute of <fragment> tag.");
        } else {
            LayoutTreeNode currentNode = new LayoutTreeNode();

            currentNode.setClassName(inferClassName(element.getName()));

            Attribute idAttribute = element.attribute("id");
            if (idAttribute != null) {
                if (idAttribute.getNamespacePrefix().equals("android") && idAttribute.getValue().startsWith("@id/")) {
                    currentNode.setId(idAttribute.getValue().substring(4));
                } else {
                    logger.severe("[TODO] Unhandled 'id' attribute: " + idAttribute.getNamespace() + ":" + idAttribute.getName() + "=" + idAttribute.getValue());
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

    private Widget inferWidgetType(LayoutTreeNode node) {
        String firstStdClass = null;
        if (node.getClassName().startsWith("android.widget") || node.getClassName().equals("android.view.View")) {
            firstStdClass = node.getClassName();
        } else {
            if (node.getAncestors() != null) {
                for (String ancestor : node.getAncestors()) {
                    if (ancestor.startsWith("android.widget")) {
                        firstStdClass = ancestor;
                        break;
                    }
                }
            }
        }
        if (node.getAncestors() == null) {
            logger.severe("[WARNING] ancestors not retrieved. " + node.getClassName());
            return Widget.Unclassified;
        }
        if (node.getAncestors().contains("android.widget.AdapterView")) {
            // TODO 单独处理List类型
            return Widget.Layout;
        }
        if (node.getAncestors().contains("android.view.ViewGroup")) {
            return Widget.Layout;
        }
        if (firstStdClass == null) {
            logger.severe("[WARNING] No first standard android.widget class. " + node.getClassName() + " " + node.getAncestors());
            return Widget.Unclassified;
        }
        switch (firstStdClass) {
            case "android.widget.TextView":
            case "android.widget.CheckedTextView":
                return Widget.TextView;
            case "android.widget.ImageView":
                return Widget.ImageView;
            case "android.widget.ImageButton":
            case "android.widget.Button":
            case "android.widget.CompoundButton":
                return Widget.Button;
            case "android.widget.EditText":
            case "android.widget.AutoCompleteTextView":
            case "android.widget.MultiAutoCompleteTextView":
                return Widget.EditText;
            case "android.widget.CheckBox":
                return Widget.CheckBox;
            case "android.widget.RadioButton":
                return Widget.RadioButton;
            case "android.widget.ToggleButton":
            case "android.widget.Switch":
                return Widget.Switch;
            default:
                logger.info(firstStdClass + " not supported.");
                return Widget.Unclassified;
        }
    }
}
