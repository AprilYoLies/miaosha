package top.aprilyolies.miaosha.access;

import top.aprilyolies.miaosha.domain.MiaoshaUser;

/**
 * 用户上下文环境，将用户信息保存到线程本地变量中
 */
public class UserContext {
    private static ThreadLocal<MiaoshaUser> userHolder = new ThreadLocal<MiaoshaUser>();

    public static void setUser(MiaoshaUser user) {
        userHolder.set(user);
    }

    public static MiaoshaUser getUser() {
        return userHolder.get();
    }

}
