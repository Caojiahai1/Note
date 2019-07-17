package proxy.handler;

import java.lang.reflect.Method;

/**
 * @author Yan liang
 * @create 2019/7/4
 * @since 1.0.0
 */
public interface MyInvocationHandler {
    Object invoke(Method method, Object[] args);
}