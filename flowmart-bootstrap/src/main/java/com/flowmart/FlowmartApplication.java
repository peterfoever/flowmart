package com.flowmart;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * flowmart 启动类。
 */
@Slf4j
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.flowmart.**.mapper")
public class FlowmartApplication {

    public static void main(String[] args) {
        Environment env = SpringApplication.run(FlowmartApplication.class, args).getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String profile = String.join(",", env.getActiveProfiles());
        log.info("""

                ----------------------------------------------------------
                  flowmart 启动成功
                  环境:     {}
                  接口文档: http://localhost:{}/doc.html
                  健康检查: http://localhost:{}/actuator/health
                ----------------------------------------------------------""",
                profile.isEmpty() ? "default" : profile, port, port);
    }
}
