package proxy;

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
public class ProxyUtil {

    /**
     * 手写动态代理
     * @param target 目标对象
     * @return 代理对象
     */
    public static Object newInstance(Object target) {
        // 接口类型
        Class clazz = target.getClass().getInterfaces()[0];
        String line = "\n";
        String tab = "\t";
        String packageContent = "package com.myproxy;" + line;
        String importContent = "import " + clazz.getName() + ";" + line;
        String classContent = "public class $Proxy implements " + clazz.getSimpleName() + "{" + line;
        String fieldContent = tab + "private " + clazz.getSimpleName() + " target;" + line;
        String constructorContent = tab + "public $Proxy(" + clazz.getSimpleName() + " target) {" + line
                + tab + tab + "this.target = target;" + line
                + tab + "}" + line;
        String methodContent = "";
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            String returnTypeName = method.getReturnType().getSimpleName();
            String returnStr = "void".equals(returnTypeName) ? "" : "return ";
            String methodName = method.getName();
            String argsContent = "";
            String useArgsContent = "";
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i != 0) {
                    argsContent += ", ";
                    useArgsContent += ", ";
                }
                String pName = parameterTypes[i].getSimpleName();
                argsContent += pName + " p" + i;
                useArgsContent += "p" + i;
            }
            methodContent += tab + "public " + returnTypeName + " " + methodName + "(" + argsContent + ") {" + line
                    + tab + tab + "System.out.println(\"-----执行代理逻辑-----\");" + line
                    + tab + tab + returnStr + "target." + methodName + "(" + useArgsContent + ");" + line
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
            Constructor<?> constructor = aClass.getConstructor(clazz);
            return constructor.newInstance(target);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}