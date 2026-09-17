package io.github.cheneyzhang93.bellringer.protocol;

/**
 * starter → 控制台的出站 HTTP 接口常量（F1/F2 冻结）。
 *
 * <p>starter 是客户端、控制台是服务端；路径与鉴权头以本类为唯一事实来源，两仓不得各自硬编码。
 */
public final class ApiPaths {

    /** F1 事件上报：{@code POST}，请求体见 {@link EventEnvelope}，成功返回 202。 */
    public static final String EVENTS = "/api/v1/events";

    /** F2 实例心跳：{@code POST}，请求体见 {@link HeartbeatEnvelope}。 */
    public static final String INSTANCE_HEARTBEAT = "/api/v1/instances/heartbeat";

    /** 鉴权头名：控制台签发的租户 Token，可轮换。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** 鉴权头值前缀（含尾空格）。 */
    public static final String BEARER_PREFIX = "Bearer ";

    private ApiPaths() {
    }
}
