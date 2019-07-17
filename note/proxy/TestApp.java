package proxy;

import proxy.dao.UserDao;
import proxy.dao.UserDaoImpl;
import proxy.handler.UserInvocationHandler;
import sun.misc.ProxyGenerator;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author Yan liang
 * @create 2019/7/3
 * @since 1.0.0
 */
public class TestApp {
    public static void main(String[] args) {
//        UserDao userDao = new UserDaoImpl();
//        UserDao proxy = (UserDao) MyJDKProxyUtil.newInstance(UserDao.class, new UserInvocationHandler(userDao));
//        proxy.test();
//        System.out.println(proxy.saySomething("hello"));
//        proxy.doSomething("eat");
//        proxy.sayAnddo("hello","walk");

//        UserDao u = (UserDao) Proxy.newProxyInstance(TestApp.class.getClassLoader(), new Class[]{UserDao.class}, new InvocationHandler() {
//            @Override
//            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//                System.out.println("jdk动态代理");
//                return method.invoke(new UserDaoImpl(), args);
//            }
//        });
//        u.test();

        byte[] myProxies = ProxyGenerator.generateProxyClass(
                "MyProxy", new Class[]{UserDao.class});
        try {
            FileOutputStream fos = new FileOutputStream("D:\\MyProxy.class");
            fos.write(myProxies);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}