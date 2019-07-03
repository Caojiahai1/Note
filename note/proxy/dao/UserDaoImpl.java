package proxy.dao;

/**
 * @author Yan liang
 * @create 2019/7/3
 * @since 1.0.0
 */
public class UserDaoImpl implements UserDao {

    @Override
    public String saySomething(String say) {
        System.out.println("say:" + say);
        return say;
    }

    @Override
    public void doSomething(String d) {
        System.out.println("do:" + d);
    }
}