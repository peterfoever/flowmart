# FM-007 本地验证记录 · 2026-10-26

- 源码基线：`ce2b92b02f6f7a97e6ed111a67aa8428a5021137` + 本次未提交工作区修改。
- 验证时间：2026-10-26 10:52:53，Asia/Shanghai。
- Maven 3.9.9，JDK 21，全量 `clean verify`，BUILD SUCCESS，30.716秒。
- product：853个测试，0失败、0错误、0跳过。
- bootstrap：27个测试，0失败、0错误、0跳过。
- 合计880个通过。数据库测试已启用，不是只跑Mockito的结果。
- MySQL：临时容器`flowmart-fm007-20261026-check`、镜像mysql:8.0、隔离库fm007_check；不连接dev/test。
- 覆盖HTTP控制器（MockMvc）、真实Spring事务代理、Flyway V1～V12、真实Mapper、故障注入回滚及并发唯一约束。
- `git diff --check`通过；Postman集合JSON及CI YAML语法检查通过；test/prod Compose配置检查通过。
- jar：`flowmart-bootstrap/target/flowmart.jar`。
- jar SHA-256：`6cacf4fcb464a9df15a02e69c14e785b521a62f47af9c5a58a4f50d45fe248d4`。

执行命令（本次端口62398仅用于临时容器，复跑需换成新容器端口）：

```bash
/Users/a1/tools/apache-maven-3.9.9/bin/mvn clean verify \
  '-Dimport.mysql.url=jdbc:mysql://127.0.0.1:62398/fm007_check?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai' \
  -Dimport.mysql.user=root \
  -Dimport.mysql.password=fm007-local-only \
  -DargLine=-javaagent:/Users/a1/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar
```

密码仅为本次临时库测试值。原始执行日志位于本机`/private/tmp/fm007-final-verify.log`，
Surefire报告位于各模块target/surefire-reports；临时文件及target可能被清理，复跑命令为长期验证入口。
人工Postman、远程CI、PR及test环境发布尚未执行，不能用本记录代替合并版本验收。
