package com.example.demo;

/**
 * 测试夹具：模拟宿主应用代码栈帧（位于 SDK 包之外），用于抛出点与聚合键断言。
 *
 * <p>不能放在 io.github.cheneyzhang93.bellringer 包内——SDK 自身包会被 {@code AppFrames} 判为基础设施。
 */
public final class DemoService {

    private DemoService() {
    }

    public static void failWith(String message) {
        throw new IllegalStateException(message);
    }
}
