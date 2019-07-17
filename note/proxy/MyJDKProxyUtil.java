package proxy;

import proxy.handler.MyInvocationHandler;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Yan liang
 * @create 2019/7/3
 * @since 1.0.0
 */
public class MyJDKProxyUtil {


    /**
     * 模拟动态代理
     * @param clazz 接口
     * @param handler 处理器
     * @return 代理对象
     */
    public static Object newInstance(Class clazz, MyInvocationHandler handler) {
        String line = "\n";
        String tab = "\t";
        String packageContent = "package com.myproxy;" + line;
        String importContent = "import " + clazz.getName() + ";" + line
                + "import java.lang.reflect.Method;" + line
                + "import proxy.handler.MyInvocationHandler;" + line;
        String classContent = "public class $Proxy implements " + clazz.getSimpleName() + "{" + line;
        String fieldContent = tab + "private MyInvocationHandler h;" + line;
        String constructorContent = tab + "public $Proxy(MyInvocationHandler h) {" + line
                + tab + tab + "this.h = h;" + line
                + tab + "}" + line;
        String methodContent = "";
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            String returnTypeName = method.getReturnType().getSimpleName();
            String returnStr = "void".equals(returnTypeName) ? "" : "return (" + returnTypeName + ")";
            String methodName = method.getName();
            String argsContent = "";
            String useArgsContent = "";
            String parameterTypesContent = "Class[] parameterTypes = new Class[]{";
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i != 0) {
                    argsContent += ", ";
                    useArgsContent += ", ";
                    parameterTypesContent += ", ";
                }
                String pName = parameterTypes[i].getSimpleName();
                argsContent += pName + " p" + i;
                useArgsContent += "p" + i;
                parameterTypesContent += pName + ".class";
            }
            parameterTypesContent += "};";

            methodContent += tab + "public " + returnTypeName + " " + methodName + "(" + argsContent + ") {" + line
                    + tab + tab + parameterTypesContent + line
                    + tab + tab + "Object[] args = new Object[]{" + useArgsContent + "};" + line
                    + tab + tab + "Method method = null;" + line
                    + tab + tab + "try {" + line
                    + tab + tab + tab + "method = Class.forName(\"" + clazz.getName() + "\").getDeclaredMethod(\"" + methodName + "\", parameterTypes);" + line
                    + tab + tab + "} catch (Exception e) {" + line
                    + tab + tab + tab + "e.printStackTrace();" + line
                    + tab + tab + "}" + line
                    + tab + tab + returnStr + "h.invoke(method, args);" + line
                    + tab + "}" + line;

        }
        String endContent = "}";
        // 拼接java文件字符串
        String javaContent = packageContent + importContent + classContent + fieldContent + constructorContent + methodContent + endContent;
        File file = new File("D:\\com\\myproxy\\$Proxy.java");
        try {
            new File("D:\\com\\myproxy").mkdirs();
            if (!file.exists()) {
                file.createNewFile();
            }
            // 写入java文件
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(javaContent);
            fileWriter.flush();
            fileWriter.close();

            // 编译java文件
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            Iterable iterable = fileManager.getJavaFileObjects("D:\\com\\myproxy\\$Proxy.java");
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, iterable);
            task.call();
            fileManager.close();

            // 加载class文件创建代理对象
            URL[] urls = new URL[]{new URL("file:D:\\\\")};
            URLClassLoader urlClassLoader = new URLClassLoader(urls);
            Class<?> aClass = urlClassLoader.loadClass("com.myproxy.$Proxy");
            Constructor<?> constructor = aClass.getConstructor(handler.getClass().getInterfaces()[0]);
            return constructor.newInstance(handler);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}