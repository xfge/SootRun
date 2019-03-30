package utils;

import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;

import java.math.BigInteger;
import java.util.*;
import java.util.logging.Logger;

public class LayoutRetriever extends BodyTransformer {

    Logger logger = Logger.getLogger(LayoutRetriever.class.toString());

    private final String packageName;
    private final String resLayoutClassName;
    private final String resIdClassName;
    private Map<Integer, String> layoutIdMap;
    private List<Integer> validLayoutValueList;

    public LayoutRetriever(String packageName) {
        this.packageName = packageName;
        this.resLayoutClassName = packageName + ".R$layout";
        this.resIdClassName = packageName + ".R$id";
        layoutIdMap = new HashMap<>();
        validLayoutValueList = new ArrayList<>();
    }

    public Map<Integer, String> getLayoutIdMap() {
        return layoutIdMap;
    }

    public List<Integer> getValidLayoutValueList() {
        return validLayoutValueList;
    }

    public List<String> getValidLayoutFileName() {
        List<String> fileNames = new ArrayList<>();
        for (Integer k : validLayoutValueList) {
            if (layoutIdMap.containsKey(k)) {
                fileNames.add(layoutIdMap.get(k));
            } else {
                logger.info("[TODO] Unhandled layout id exception.");
            }
        }
        return fileNames;
    }

    @Override
    protected void internalTransform(Body body, String s, Map<String, String> map) {

        // exclude Android built-in files
        SootMethod method = body.getMethod();

        if (method.getDeclaringClass().getName().startsWith(packageName)) {
            // 在 R.layout 中检索每个 layout 的对应值
            if (method.getDeclaringClass().getName().equals(resLayoutClassName)) {
                if (layoutIdMap.size() == 0) {
                    for (SootField sf : method.getDeclaringClass().getFields()) {
                        for (Tag tag : sf.getTags()) {
                            if (tag.getName().equals("IntegerConstantValueTag")) {
                                layoutIdMap.put(new BigInteger(tag.getValue()).intValue(), sf.getName());
                            }
                        }
                    }
                }
            } else if (method.getDeclaringClass().getName().equals(resIdClassName)) {

            } else {
                Map<Value, Integer> valueIntegerMap = new HashMap<>();
                final PatchingChain<Unit> units = body.getUnits();
                Iterator<Unit> iter = units.snapshotIterator();
                while (iter.hasNext()) {
                    final Unit u = iter.next();
                    u.apply(new AbstractStmtSwitch() {

                        @Override
                        public void caseAssignStmt(AssignStmt stmt) {
                            if (method.getName().equals("onCreate")) {
                                valueIntegerMap.put(stmt.getDefBoxes().get(0).getValue(), 1);
//                                System.out.println(stmt.toString() + " ... " + stmt.getRightOp() + " ... " + stmt.getRightOp().getClass());
//                                System.out.println(valueIntegerMap);
                            }
                        }

                        @Override
                        public void caseInvokeStmt(InvokeStmt stmt) {
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            SootMethod invokeMethod = invokeExpr.getMethod();

                            if (invokeMethod.getName().equals("setContentView")) {
                                if (invokeMethod.getParameterCount() == 1) {
                                    // setContentView(int)
                                    if (invokeMethod.getParameterType(0) == IntType.v()) {
                                        Value arg = invokeExpr.getArg(0);
                                        if (arg.getClass() == IntConstant.class) {
                                            validLayoutValueList.add(((IntConstant) arg).value);
                                        } else {
//                                            System.out.println("[TODO] unhandled setContentView(int)");
                                            // TODO
                                        }
                                    } else {
//                                        System.out.println("[TODO] unhandled setContentView(View)");
                                        // TODO: setContentView(View)
                                    }
                                } else {
//                                    System.out.println("[TODO] unhandled void setContentView(android.view.View,android.view.ViewGroup$LayoutParams)");
                                    // TODO: setContentView(android.view.View,android.view.ViewGroup$LayoutParams)
                                }
                            } else if (invokeMethod.getName().equals("inflate")) {
//                                System.out.println("[TODO] unhandled inflate");
                                // TODO
                            }


//                            else if (invokeMethod.getName().equals("setOnClickListener")) {
//                                if (invokeExpr.getClass() == JVirtualInvokeExpr.class) {
//                                    Value caller = ((JVirtualInvokeExpr) invokeExpr).getBase();
//                                    if (caller.getClass() == JimpleLocal.class) {
//                                        JimpleLocal callerLocal = (JimpleLocal) caller;
////                                        System.out.println(callerLocal.getType());
//                                    }
//                                }
//                            }
                        }
                    });
                }
            }
        }
    }
}
