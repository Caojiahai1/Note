package proxy.handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Yan liang
 * @create 2019/7/4
 * @since 1.0.0
 */
public class UserInvocationHandler implements MyInvocationHandler {

    private Object target;

    public UserInvocationHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Method method, Object[] args) {
        try {
            System.out.println("-------模拟jdk动态代理-------");
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}