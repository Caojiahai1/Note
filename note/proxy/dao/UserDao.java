package proxy.dao;

/**
 * @author Yan liang
 * @create 2019/7/3
 * @since 1.0.0
 */
public interface UserDao {
    String saySomething(String say);
    void doSomething(String d);
    void sayAnddo(String say, String d);
    void test();
}