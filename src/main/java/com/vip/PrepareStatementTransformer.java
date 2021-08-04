package com.vip;

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ARETURN;
import org.apache.bcel.generic.ASTORE;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.ILOAD;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Objects;

public class PrepareStatementTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!className.equals("com/mysql/jdbc/ConnectionImpl")) {
            return classfileBuffer;
        }
        try {
            JavaClass clazz = Repository.lookupClass(className);
            for (Method method : clazz.getMethods()) {
                ClassGen classGen = genPrepareStatement(clazz, method);
                if (Objects.nonNull(classGen)) {
                    return classGen.getJavaClass().getBytes();
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return classfileBuffer;
    }

    private ClassGen genPrepareStatement(JavaClass clazz, Method method) {
        ClassGen res = null;
        if ("prepareStatement".equals(method.getName())
                && "(Ljava/lang/String;II)Ljava/sql/PreparedStatement;".equals(method.getSignature())) {
            ClassGen cg = new ClassGen(clazz);
            ConstantPoolGen cp = cg.getConstantPool();
            MethodGen mg = new MethodGen(method, cg.getClassName(), cp);
            MethodGen copy = mg.copy(cg.getClassName(), cp);
            copy.setName("prepareStatementOrg");
            cg.addMethod(copy.getMethod());

            InstructionList il = new InstructionList();
            il.append(new ALOAD(0));
            il.append(new ALOAD(1));
            il.append(new ILOAD(2));
            il.append(new ILOAD(3));
            int psoIdx = cp.addMethodref(cg.getClassName(), "prepareStatementOrg", "(Ljava/lang/String;II)Ljava/sql/PreparedStatement;");
            il.append(new INVOKEVIRTUAL(psoIdx));
            il.append(new ASTORE(4));
            int pspIdx  = cp.addClass("com/vip/PreparedStatementProxy");
            InstructionHandle l1 = il.append(new NEW(pspIdx));
            il.append(new DUP());
            il.append(new ALOAD(1));
            il.append(new ALOAD(4));
            int initPspIdx = cp.addMethodref("com/vip/PreparedStatementProxy",
                    "<init>", "(Ljava/lang/String;Ljava/sql/PreparedStatement;)V");
            il.append(new INVOKESPECIAL(initPspIdx));
            InstructionHandle l2 = il.append(new ARETURN());
            MethodGen newMg = new MethodGen(Const.ACC_PUBLIC, new ObjectType("java.sql.PreparedStatement"), new Type[] {Type.STRING, Type.INT, Type.INT},
                    new String[] {"sql", "resultSetType", "resultSetConcurrency"}, "prepareStatement", cg.getClassName(), il, cp);
            newMg.addLocalVariable("pStmt", new ObjectType("java.sql.PreparedStatement"), l1, l2);
            newMg.addException("java/sql/SQLException");
            newMg.setInstructionList(il);
            newMg.setMaxStack();
            newMg.setMaxLocals();
            cg.replaceMethod(method, newMg.getMethod());
            res = cg;
        }
        return res;
    }

}
