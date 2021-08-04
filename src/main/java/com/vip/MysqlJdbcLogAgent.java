package com.vip;

import java.lang.instrument.Instrumentation;

public class MysqlJdbcLogAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new PrepareStatementTransformer());
    }

    /**
     * 动态 attach 方式启动，运行此方法
     *
     * manifest需要配置属性Agent-Class
     *
     * @param agentArgs
     * @param inst
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
    }
}
