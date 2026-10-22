api:
advice：统一处理 HTTP 响应和异常
config：存放配置类、配置绑定类
dto：存放 HTTP 请求和响应的数据对象
metrics：存放业务监控指标
client：存放下游 RPC 客户端
controller：存放 HTTP 请求入口
interceptor：存放请求拦截、JWT 鉴权逻辑
service：存放业务用例及相关辅助组件

rpc:
bootstrap：存放应用启动时执行的初始化、注册和校验逻辑
config：存放配置绑定类和配置类
domain：存放数据库实体类
exception：存放自定义业务异常
mapper：存放数据库访问接口和 SQL 操作
metrics：存放业务监控指标
service：存放业务逻辑和 gRPC 服务实现
service/impl：存放 gRPC 接口实现类
client：存放下游模块