/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.optimizer;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.ScriptNode;

/**
 * Generates class files from script sources.
 *
 * since 1.5 Release 5
 * @author Igor Bukanov
 */

public class ClassCompiler
{
    /**
     * Construct ClassCompiler that uses the specified compiler environment
     * when generating classes.
     */
    public ClassCompiler(CompilerEnvirons compilerEnv)
    {
        if (compilerEnv == null) throw new IllegalArgumentException();
        this.compilerEnv = compilerEnv;
        this.mainMethodClassName = Codegen.DEFAULT_MAIN_METHOD_CLASS;
    }

    /**
     * Set the class name to use for main method implementation.
     * The class must have a method matching
     * <tt>public static void main(Script sc, String[] args)</tt>, it will be
     * called when <tt>main(String[] args)</tt> is called in the generated
     * class. The class name should be fully qulified name and include the
     * package name like in <tt>org.foo.Bar<tt>.
     */
    public void setMainMethodClass(String className)
    {
        // XXX Should this check for a valid class name?
        mainMethodClassName = className;
    }

    /**
     * Get the name of the class for main method implementation.
     * @see #setMainMethodClass(String)
     */
    public String getMainMethodClass()
    {
        return mainMethodClassName;
    }

    /**
     * Get the compiler environment the compiler uses.
     */
    public CompilerEnvirons getCompilerEnv()
    {
        return compilerEnv;
    }

    /**
     * Get the class that the generated target will extend.
     */
    public Class<?> getTargetExtends()
    {
        return targetExtends;
    }

    /**
     * Set the class that the generated target will extend.
     *
     * @param extendsClass the class it extends
     */
    public void setTargetExtends(Class<?> extendsClass)
    {
        targetExtends = extendsClass;
    }

    /**
     * Get the interfaces that the generated target will implement.
     */
    public Class<?>[] getTargetImplements()
    {
        return targetImplements == null ? null : (Class[])targetImplements.clone();
    }

    /**
     * Set the interfaces that the generated target will implement.
     *
     * @param implementsClasses an array of Class objects, one for each
     *                          interface the target will extend
     */
    public void setTargetImplements(Class<?>[] implementsClasses)
    {
        targetImplements = implementsClasses == null ? null : (Class[])implementsClasses.clone();
    }

    /**
     * Build class name for a auxiliary class generated by compiler.
     * If the compiler needs to generate extra classes beyond the main class,
     * it will call this function to build the auxiliary class name.
     * The default implementation simply appends auxMarker to mainClassName
     * but this can be overridden.
     */
    protected String makeAuxiliaryClassName(String mainClassName,
                                            String auxMarker)
    {
        return mainClassName+auxMarker;
    }

    /**
     * 将JavaScript源代码编译为一个或多个Java类文件。
     * *第一个编译的类将具有名称mainClassName。
     * *如果{@link #getTargetExtends（）}或
     * * {@link #getTargetImplements（）}的结果不为空，则第一个编译的
     * *类将扩展指定的超类并实现
     * *指定的接口。
     *
     * @return array where elements with even indexes specifies class name
     *         and the following odd index gives class file body as byte[]
     *         array. The initial element of the array always holds
     *         mainClassName and array[1] holds its byte code.
     */
    public Object[] compileToClassFiles(String source,
                                        String sourceLocation,
                                        int lineno,
                                        String mainClassName)
    {
        Parser p = new Parser(compilerEnv);
        AstRoot ast = p.parse(source, sourceLocation, lineno);
        IRFactory irf = new IRFactory(compilerEnv);
        ScriptNode tree = irf.transformTree(ast);

        // release reference to original parse tree & parser
        irf = null;
        ast = null;
        p = null;

        Class<?> superClass = getTargetExtends();
        Class<?>[] interfaces = getTargetImplements();
        String scriptClassName;
        boolean isPrimary = (interfaces == null && superClass == null);
        if (isPrimary) {
            scriptClassName = mainClassName;
        } else {
            scriptClassName = makeAuxiliaryClassName(mainClassName, "1");
        }

        Codegen codegen = new Codegen();
        codegen.setMainMethodClass(mainMethodClassName);
        byte[] scriptClassBytes
            = codegen.compileToClassFile(compilerEnv, scriptClassName,
                                         tree, tree.getEncodedSource(),
                                         false);

        if (isPrimary) {
            return new Object[] { scriptClassName, scriptClassBytes };
        }else{
            System.out.println("#### isPrimary: false");
        }
        int functionCount = tree.getFunctionCount();
        ObjToIntMap functionNames = new ObjToIntMap(functionCount);
        for (int i = 0; i != functionCount; ++i) {
            FunctionNode ofn = tree.getFunctionNode(i);
            String name = ofn.getName();
            if (name != null && name.length() != 0) {
                functionNames.put(name, ofn.getParamCount());
            }
        }
        if (superClass == null) {
            superClass = ScriptRuntime.ObjectClass;
        }
        byte[] mainClassBytes
            = JavaAdapter.createAdapterCode(
                functionNames, mainClassName,
                superClass, interfaces, scriptClassName);

        return new Object[] { mainClassName, mainClassBytes,
                              scriptClassName, scriptClassBytes };
    }

    private String mainMethodClassName;
    private CompilerEnvirons compilerEnv;
    private Class<?> targetExtends;
    private Class<?>[] targetImplements;

}

