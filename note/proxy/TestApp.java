package proxy;

import proxy.dao.UserDao;
import proxy.dao.UserDaoImpl;

/**
 * @author Yan liang
 * @create 2019/7/3
 * @since 1.0.0
 */
public class TestApp {
    public static void main(String[] args) {
        UserDaoImpl userDao = new UserDaoImpl();
        UserDao proxy = (UserDao) ProxyUtil.newInstance(userDao);
        proxy.saySomething("hello");
        proxy.doSomething("eat");
    }
}